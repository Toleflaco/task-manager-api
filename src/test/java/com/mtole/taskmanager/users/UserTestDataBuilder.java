package com.mtole.taskmanager.users;

public class UserTestDataBuilder {

    private Long id;
    private String name = "Test User";       // default
    private String email = "test@example.com"; // default
    private String password = "$2a$10$dummyBCryptHashForTesting";

    public static UserTestDataBuilder aUser() {
        return new UserTestDataBuilder();
    }

    public UserTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }
    public UserTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }
    public UserTestDataBuilder withEmail(String email) {
        this.email = email;
        return this;
    }
    public UserTestDataBuilder withPassword(String password) {
        this.password = password;
        return this;
    }
    public User build() {
        User user = new User();
        if (id!= null) user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }
}
