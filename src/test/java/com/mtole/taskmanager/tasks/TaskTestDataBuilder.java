package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.users.User;

import java.time.OffsetDateTime;

public class TaskTestDataBuilder {

    private Long id;
    private String title = "Task title";
    private String description = null;
    private TaskStatus status = TaskStatus.PENDING;
    private Priority priority = Priority.MEDIUM;
    private OffsetDateTime dueDate = null;
    private OffsetDateTime completedAt = null;
    private User user = null;
    private Category category = null;

    public static TaskTestDataBuilder aTask() {
        return new TaskTestDataBuilder();
    }

    public TaskTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }
    public TaskTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    public TaskTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }
    public TaskTestDataBuilder withStatus(TaskStatus status) {
        this.status = status;
        return this;
    }
    public TaskTestDataBuilder withPriority(Priority priority) {
        this.priority = priority;
        return this;
    }
    public TaskTestDataBuilder withDueDate(OffsetDateTime dueDate) {
        this.dueDate = dueDate;
        return this;
    }
    public TaskTestDataBuilder withCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
        return this;
    }
    public TaskTestDataBuilder withUser(User user) {
        this.user = user;
        return this;
    }
    public TaskTestDataBuilder withCategory(Category category) {
        this.category = category;
        return this;
    }

    public Task build() {
        Task task = new Task();
        if (id != null) {
            task.setId(id);
        }
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setCompletedAt(completedAt);
        task.setUser(user);
        task.setCategory(category);
        return task;
    }
}
