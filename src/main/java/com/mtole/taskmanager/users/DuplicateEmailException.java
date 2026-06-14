package com.mtole.taskmanager.users;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
