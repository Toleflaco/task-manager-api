package com.mtole.taskmanager.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
