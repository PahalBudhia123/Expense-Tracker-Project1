const form= document.getElementById('expense-form');
const expenseList =document.getElementById('expense-list');
const balanceEl = document.getElementById('balance');
const nameInput = document.getElementById('expense-name');
const amountInput=document.getElementById('expense-amount');
let expenses = [];// store all the expense objects
let editingID = null;
let monthlyBudget =0;
let chart;


document.getElementById("saveBudget").addEventListener("click",()=>{
           monthlyBudget = parseFloat(document.getElementById("monthlyBudget").value) || 0;
           localStorage.setItem("MonthlyBudget", monthlyBudget);
           updateRemaining();
           updateChart();
});
localStorage.setItem("MonthlyBudget",monthlyBudget);

/*const userId = localStorage.getItem("userId");
console.log("User ID from localStorage:", userId);*/

// Helpers
function getAuthHeader() {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// Logout function
function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('userId');
  localStorage.removeItem('username');
  window.location.href = 'Loginpage/HTML.html';
}

// Check if user is logged in
function checkAuth() {
  const token = localStorage.getItem('token');
  if (!token) {
    alert('Please login first');
    window.location.href = 'Loginpage/HTML.html';
    return false;
  }
  return true;
}

// Fetch expenses from backend (JWT protected)
async function fetchExpenses(){
  try{
    if (!checkAuth()) return;
    
    const response = await fetch(`http://127.0.0.1:8080/api/expenses/me`, {
      headers: {
        'Content-Type': 'application/json',
        ...getAuthHeader(),
      },
    });
    
    if(!response.ok){
          throw new Error("Failed to update Ezpense");
        }
    expenses = await response.json();
    renderExpenses();
    updateRemaining();
    updateChart();  
  }catch(error){
     console.error("failed To Fetch Ezpenses : ",error);
     alert("Oops! Somethingwent wrong. Try again .")
  }
}

// Rate limiting and caching variables
let lastAiCategoryCall = 0;
let lastAiChatCall = 0;
const AI_CALL_DELAY = 1000; // 1 second delay between calls
const categoryCache = new Map();
const MAX_RETRIES = 3;

// Helper function to wait for a specified time
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Helper function for exponential backoff retry
async function retryWithBackoff(fn, maxRetries = MAX_RETRIES) {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      return await fn();
    } catch (error) {
      if (error.message.includes('429') && attempt < maxRetries) {
        const delay = Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
        console.warn(`Rate limit hit, retrying in ${delay}ms... (Attempt ${attempt}/${maxRetries})`);
        await sleep(delay);
        continue;
      }
      throw error;
    }
  }
}

// AI Categorization function with rate limiting and caching
async function getAiCategory(description) {
  try {
    // Check cache first
    const cacheKey = description.toLowerCase().trim();
    if (categoryCache.has(cacheKey)) {
      console.log('Using cached category for:', description);
      return categoryCache.get(cacheKey);
    }

    // Rate limiting: ensure minimum delay between calls
    const now = Date.now();
    const timeSinceLastCall = now - lastAiCategoryCall;
    if (timeSinceLastCall < AI_CALL_DELAY) {
      const waitTime = AI_CALL_DELAY - timeSinceLastCall;
      console.log(`Waiting ${waitTime}ms to avoid rate limit...`);
      await sleep(waitTime);
    }

    lastAiCategoryCall = Date.now();

    const result = await retryWithBackoff(async () => {
      const response = await fetch("http://127.0.0.1:8080/api/ai/categorize", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...getAuthHeader(),
        },
        body: JSON.stringify({ description: description })
      });
      
      if (!response.ok) {
        if (response.status === 429) {
          throw new Error('429 - Rate limit exceeded');
        }
        throw new Error(`Failed to get AI category: ${response.status}`);
      }
      
      return response.json();
    });
    
    const category = result.suggestedCategory;
    // Cache the result
    categoryCache.set(cacheKey, category);
    
    return category;
  } catch (error) {
    console.error("Failed to get AI category:", error);
    
    if (error.message.includes('429')) {
      alert('AI service is currently busy. Using default category. Please try again in a moment.');
    }
    
    return "Other"; // Fallback category
  }
}

// Add a new data to backend
// catch handel only networks error not server errors
async function addExpense(name, amount) {
  try {
    if (!checkAuth()) return;
    
    // Get AI-suggested category
    const suggestedCategory = await getAiCategory(name);
    
    const response = await fetch("http://127.0.0.1:8080/api/expenses", {
      method: "POST",
      headers: { 
        "Content-type": "application/json",
        ...getAuthHeader(),
      },
      body: JSON.stringify({
        name: name,
        amount: amount,
        category: suggestedCategory, // Add AI-suggested category
        date: new Date().toISOString().split('T')[0]
      })
    });
    
    if (!response.ok) {
      throw new Error("Failed to add Expense");
    }

     await fetchExpenses();
   }catch(error){
     console.error("failed to add expense: ",error);
     alert("Oops! Something went wrong. Try again .")
   }
}


  // To delete the expense with id
  async function deleteExpenses(id){
    try{
    if (!checkAuth()) return;
    
    const response =await fetch(`http://127.0.0.1:8080/api/expenses/${id}`,{
      method: "DELETE",
      headers: {
        ...getAuthHeader(),
      }
    });
    if(!response.ok){
          throw new Error("Failed to delete Expense");
        }
    await fetchExpenses();
    
  }catch(error){
    console.error("Error deleting expense: ",error);
     alert("Oops! Something went wrong while deleting expense.");

}
  }
  // To update/edit the Expenses
async function updateExpenses(id,name,amount){
    try{
    if (!checkAuth()) return;

     const response= await fetch(`http://127.0.0.1:8080/api/expenses/${id}`, { 
       method :"PUT",
       headers : { 
         "Content-type" : "application/json",
         ...getAuthHeader(),
       },
       body: JSON.stringify({
         name:name,
         amount:amount,
         date: new Date().toISOString().split('T')[0]
       })
     });
        if(!response.ok){
          throw new Error("Failed to update Ezpense");
        }
    await fetchExpenses();
  }
  catch(error){
    console.log("Error deleting expense: ",error);
    alert("Oops! Somethingwent wrong. Try again .")
    
  }
}


//element.addEventListener('eventType', callbackFunction);
// is element par jab ye event hoga tab yeh kamm krna hai 
form.addEventListener('submit',  async function(e){
    e.preventDefault();

  const name = document.getElementById('expense-name').value;
  const amount = parseFloat(document.getElementById('expense-amount').value);

 if(!name || amount<=0){
    alert("Enter valid name and amount");
    return;
 }
 if(editingID){
  /*
    expenses = expenses.map((exp)=> exp.id==editingID ? {...exp,name,amount}:exp);
    editingID=null;
    form.querySelector("button").textContent = "Add Expense";*/
    await updateExpenses(editingID,name ,amount);
    editingID=null;
    form.querySelector("button").textContent = "Add Expense";
 }
 else{

 /* 
 const expense ={
    id: Date.now(),
    name,
    amount*/
    await addExpense(name,amount);
 }
 /*expenses.push(expense);*/

/* renderExpenses();*/
 form.reset();
 fetchExpenses();
});
// render-display
function renderExpenses(list=expenses){
    expenseList.innerHTML=" ";
    let total =0;

expenses.forEach((exp) => {
    total += exp.amount;
    const li = document.createElement('li');
    
    // Show category if available, otherwise show "No Category"
    const categoryDisplay = exp.category ? `[${exp.category}]` : '[No Category]';
    
    li.innerHTML = ` 
      ${exp.name} - ₹${exp.amount} ${categoryDisplay}
      <div>
      <button onclick ="deleteExpensess(${exp.id})">❌</button>
      <button onclick ="editExpense(${exp.id})">✏️</button>
      </div>
     `;
     expenseList.appendChild(li);
});
balanceEl.textContent = `Total: ₹${total} `;
}
function deleteExpensess(id){
    /*expenses = expenses.filter((exp) => exp.id!=id);
    renderExpenses();*/
            deleteExpenses(id);
}

// (Budget-related code removed per request)
function editExpense(id){
  const expense = expenses.find((exp) => exp.id==id);
  if(expense){
    nameInput.value=expense.name;
    amountInput.value=expense.amount;
    editingID= id;
    form.querySelector("button").textContent = "Update expense";
  }
}
const themeToggle = document.getElementById('theme-toggle');
// change = click
themeToggle.addEventListener('change', () => {
  document.body.classList.toggle('dark');
  localStorage.setItem('theme', themeToggle.checked ? 'dark' : 'light');
});
window.addEventListener('DOMContentLoaded',() =>{
  const savedTheme = localStorage.getItem('theme');
  if(savedTheme == 'dark'){
    document.body.classList.add('dark');
    document.getElementById('theme-toggle').checked=true;
  }
});

function updateRemaining(){
  /*
  console.log("monthlyBudget:", monthlyBudget);
  console.log("expenses:", expenses);
  const spent = expenses.reduce((sum, exp) => sum + exp.amount, 0);
  console.log("spent:", spent);
  const remaining = monthlyBudget - spent;
  console.log("remaining:", remaining);
  document.getElementById("monthlyremaining").value = `Remaining :₹${remaining.toFixed(2)}`;
  */
  const spent = expenses.reduce((sum, exp) => sum + Number(exp.amount || 0), 0);
  const remaining = Math.max(monthlyBudget - spent, 0);
  const remainingEl = document.getElementById("monthlyremaining");
  if (remainingEl) {
    remainingEl.value = remaining.toFixed(2);
  }
}

function updateChart(){
  const spent = expenses.reduce((sum,exp)=>sum+exp.amount,0);
  const remaining = Math.max((monthlyBudget-spent),0);
 const ctx = document.getElementById("expenseChart").getContext('2d');

  if(chart){
      chart.destroy();
  }
  chart = new Chart(ctx,{
    type: 'doughnut',
    data:{
      labels:['spent','Remaining'],
      datasets:[{
        data:[spent,remaining],
        backgroundColor:['#c7a0eb', '#f6f6f6'],
        borderwidth:0
      }]
    },
    options:{
      responsive:false,
      cutout: '60%',
      plugins:{
        legend:{display:true},
        tooltip:{enabled:true}
      }
    }
  })
}
// filter js
function filterExpenses(type){
  const now = new Date();
  let filtered =[];
  if(type=='weekly'){
    const weekAgo = new Date(now);
    weekAgo.setDate(now.getDate()-7);
     const weekAgoStr = weekAgo.toISOString().split('T')[0];
    //new Date(exp.date)Converts the date string into a JavaScript Date object, so we can compare it properly.
    filtered = expenses.filter(exp =>{
       return exp.date>=weekAgoStr;
    } );
    }
    else if (type === 'monthly') {
    const monthAgo = new Date(now);
    monthAgo.setMonth(now.getMonth() - 1);
    const monthAgoStr = monthAgo.toISOString().split('T')[0];

    filtered = expenses.filter(exp => exp.date >= monthAgoStr);
  } else {
    filtered = expenses;
  }

  renderExpenses(filtered);
}
const userAvatar = document.getElementById("userAvatar");
const userDropdown = document.getElementById("userDropdown");

// Show first letter of username
const username = localStorage.getItem("username");
if (username) {
    userAvatar.textContent = username.charAt(0).toUpperCase();
}

userAvatar.addEventListener("click", () => {
    userDropdown.style.display =
        userDropdown.style.display === "block" ? "none" : "block";
});

document.getElementById("logoutBtn").addEventListener("click", (e) => {
    e.preventDefault(); // Prevent the default link behavior
    logout();
});


// Close dropdown when clicking outside
document.addEventListener("click", (event) => {
    if (!event.target.closest(".user-menu")) {
        userDropdown.style.display = "none";
    }
});
/*
function initializeApp() {
  const token = localStorage.getItem('token');

  if (token) {
    // Set the auth state in your application's state management
    setAuthState({ isAuthenticated: true, token: token });

    // Immediately try to fetch user-specific data, like expenses
    fetchExpenses(token);
    
  } else {
    window.location.href="Loginpage/HTML.html";
  }
}

// Call this function when your app first mounts
initializeApp();
document.addEventListener('DOMContentLoaded', (event) => {
    // This code will run when the entire HTML document has been loaded and parsed.
    console.log('Page has been reloaded. Running my function...');
    myFunction();
});*/


// Ai button logic 
const aibtn = document.getElementById("aiBtn");
const chatPanel = document.getElementById("chatPannel");
const closeChat= document.getElementById("closeChat")


aibtn.addEventListener("click",()=>{
  chatPanel.classList.add("open");
});

closeChat.addEventListener("click",()=>{
  chatPanel.classList.remove("open");
});
// logic for Ai chat 
const sendAiBtn = document.getElementById("sendBtn");
const aiPromptInput = document.getElementById("chatInput");
const chatMessages = document.getElementById("chatBody");

function appendChatMessage(sender, text) {
  const msgDiv = document.createElement("div");
  msgDiv.classList.add("chat-body");
  msgDiv.innerHTML = `<strong>${sender}:</strong> ${text}`;
  chatMessages.appendChild(msgDiv);
  chatMessages.scrollTop = chatMessages.scrollHeight; // scroll to bottom
}

// Variable to track if AI request is in progress
let isAiRequestInProgress = false;

async function sendAiPrompt() {
  const prompt = aiPromptInput.value.trim();
  if (!prompt) return;
  
  // Prevent multiple simultaneous requests
  if (isAiRequestInProgress) {
    appendChatMessage("System", "Please wait for the current request to complete...");
    return;
  }

  appendChatMessage("You", prompt);
  aiPromptInput.value = "";
  
  // Disable send button and show loading
  isAiRequestInProgress = true;
  sendAiBtn.disabled = true;
  sendAiBtn.textContent = "Sending...";
  
  appendChatMessage("AI", "🤔 Thinking...");

  try {
    // Rate limiting: ensure minimum delay between calls
    const now = Date.now();
    const timeSinceLastCall = now - lastAiChatCall;
    if (timeSinceLastCall < AI_CALL_DELAY) {
      const waitTime = AI_CALL_DELAY - timeSinceLastCall;
      await sleep(waitTime);
    }

    lastAiChatCall = Date.now();

    const result = await retryWithBackoff(async () => {
      const response = await fetch("http://127.0.0.1:8080/api/ai/ask", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...getAuthHeader(),
        },
        body: JSON.stringify({ prompt })
      });
      
      if (!response.ok) {
        if (response.status === 429) {
          throw new Error('429 - Rate limit exceeded');
        }
        throw new Error(`Failed to get AI response: ${response.status}`);
      }
      
      return response.json();
    });

    const aiText = result.reply || "Sorry, I could not generate a response.";
    
    // Remove the "Thinking..." message
    const thinkingMsg = chatMessages.lastElementChild;
    if (thinkingMsg && thinkingMsg.textContent.includes('🤔 Thinking...')) {
      chatMessages.removeChild(thinkingMsg);
    }
    
    appendChatMessage("AI", aiText);

  } catch (error) {
    console.error("AI request failed:", error);
    
    // Remove the "Thinking..." message
    const thinkingMsg = chatMessages.lastElementChild;
    if (thinkingMsg && thinkingMsg.textContent.includes('🤔 Thinking...')) {
      chatMessages.removeChild(thinkingMsg);
    }
    
    if (error.message.includes('429')) {
      appendChatMessage("AI", "⚠️ I'm currently receiving too many requests. Please wait a moment and try again.");
    } else {
      appendChatMessage("AI", "❌ Error: Could not get a response. Please try again.");
    }
  } finally {
    // Re-enable send button
    isAiRequestInProgress = false;
    sendAiBtn.disabled = false;
    sendAiBtn.textContent = "Send";
  }
}
  sendAiBtn.addEventListener("click", ()=> sendAiPrompt());

aiPromptInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    sendAiPrompt();
  }
});


























































// Add logout event listener when page loads
// document.addEventListener('DOMContentLoaded', function() {
//   // Check if user is logged in
//   if (!checkAuth()) return;
  
//   // Load initial data
//   fetchExpenses();
  
//   // Update remaining and chart with saved budget
//   if (monthlyBudget > 0) {
//     updateRemaining();
//     updateChart();
//   }
// });

