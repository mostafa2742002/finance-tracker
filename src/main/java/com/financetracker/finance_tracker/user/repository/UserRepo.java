package com.financetracker.finance_tracker.user.repository;

import org.springframework.stereotype.Repository;

import com.financetracker.finance_tracker.user.entity.User;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface UserRepo extends JpaRepository<User, UUID> {

}
