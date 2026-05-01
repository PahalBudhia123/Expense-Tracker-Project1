package com.Project.ExpenseTracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.Project.ExpenseTracker.Model.Expense;

import java.util.List;

public interface ExpenseTrackerRepository extends JpaRepository<Expense , Long> {
    List<Expense> findByUserId(Long userId);
}
