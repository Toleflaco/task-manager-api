package com.mtole.taskmanager.tasks;

public class InvalidTaskStateException extends RuntimeException {
    public InvalidTaskStateException(String message) {
        super(message);
    }
}
