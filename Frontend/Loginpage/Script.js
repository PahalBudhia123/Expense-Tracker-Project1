const container = document.querySelector('.container');
const registerBtn = document.querySelector('.register-btn');
const loginBtn= document.querySelector('.login-btn');
const registrform = document.querySelector('.form-box.register');
const loginform = document.querySelector('.form-box.login');


registerBtn.addEventListener('click',function(){
          container.classList.add('active');
});

loginBtn.addEventListener('click',function(){
            container.classList.remove('active');
});

registrform.addEventListener('submit',async function(e){
       e.preventDefault();

       const username= document.getElementById('register-username').value;
       const password= document.getElementById('register-password').value;
       const email= document.getElementById('register-email').value;

    try{
        const response = await fetch('http://127.0.0.1:8080/api/users/register',{
            method: 'POST',
            headers : { 'Content-Type':'application/json' },
            body: JSON.stringify({username,password,email})
        });

        if(!response.ok){
            /*throw new  Error('Registeration Failed');*/
            const text = await response.text();
            console.error('Error Response:', text);
            throw new Error('Registration Failed');
        }
        const user = await response.json(); //  Expecting user object in return
        const token = response.headers.get('Authorization');
        if (token && token.startsWith('Bearer ')) {
            // Store only the token part, not the "Bearer " prefix
            localStorage.setItem('token', token.substring(7));
        }
        localStorage.setItem("userId", user.id);
        localStorage.setItem("username", user.username);
        alert("Registration successful!");
        window.location.href = "../index.html";

    }catch (error) {
      console.error(error);
      alert('Registration failed. Try again.');
    }
});

loginform.addEventListener('submit',async function(e){
    e.preventDefault();

    const username= document.getElementById('login-username').value;
    const password= document.getElementById('login-password').value;

    try{
        const response = await fetch('http://127.0.0.1:8080/api/users/login',{
            method: 'POST',
            headers : { 'Content-Type':'application/json' },
            body: JSON.stringify({username,password})
        });
       const user= await response.json();
/*
       if(isValid){
        alert('Login succesfull');
        localStorage.setItem('username',username);
        localStorage.setItem("userId", user.id); 
        window.location.href='expense.html';
       }
       else{
        alert('Invalid Crendentials.Try again!');
       }*/
      if(response.ok){
         alert('Login succesfull');
         const token = response.headers.get('Authorization');
         if (token && token.startsWith('Bearer ')) {
            // Store only the token part, not the "Bearer " prefix
            localStorage.setItem('token', token.substring(7));
         } else {
            console.error('No valid token received');
            alert('Login successful but no token received. Please try again.');
            return;
         }
         localStorage.setItem('username',user.username);
         localStorage.setItem("userId", user.id); 
         window.location.href='../index.html';
      }
      else{
        alert('Invalid Crendentials.Try again!');
       }
    }catch(error){
        console.error(error);
       alert('Login failed. Try again.');
    }
});
