package com.mtole.taskmanager.users;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private String password;


    public User() {
    }

    public User(Long id, String name, String email, LocalDateTime createdAt, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
