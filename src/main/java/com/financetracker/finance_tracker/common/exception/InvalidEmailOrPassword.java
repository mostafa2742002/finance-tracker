package com.financetracker.finance_tracker.common.exception;

public class InvalidEmailOrPassword extends RuntimeException {
    private String message;

    public InvalidEmailOrPassword(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
