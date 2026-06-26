package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.users.User;

public class CategoryTestDataBuilder {

    private Long id;
    private String name = "Category name";
    private String description = null;
    private User user = null;

    public static CategoryTestDataBuilder aCategory() {
        return new CategoryTestDataBuilder();
    }

    public CategoryTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public CategoryTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public CategoryTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public CategoryTestDataBuilder withUser(User user) {
        this.user = user;
        return this;
    }

    public Category build() {
       Category category = new Category();
       if (id != null) {
           category.setId(id);
       }
       category.setName(name);
       category.setDescription(description);
       category.setUser(user);
       return category;
    }
}
