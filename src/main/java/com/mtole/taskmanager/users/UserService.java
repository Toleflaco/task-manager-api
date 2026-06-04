package com.mtole.taskmanager.users;

import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.tasks.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, CategoryRepository categoryRepository, TaskRepository taskRepository,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.userMapper = userMapper;
    }
    public User createUser(User user){
        log.info("Creating user with email={}", user.getEmail());
        User createdUser = userRepository.save(user);
        log.info("Created user with id={}", createdUser.getId());
        return createdUser;
    }
    public Optional<User> findById(Long id){
        return userRepository.findById(id);
    }
    public List<User> findAll(int page, int pageSize){
        return userRepository.findAll(page, pageSize);
    }
    public int countAll() {
        return userRepository.countAll();
    }
    public boolean deleteById(Long id) {
        if (!userRepository.existsById(id)) {
            log.warn("Cannot delete user with id={}: not found", id);
            return false;
        }
        log.info("Cascade-deleting tasks and categories of user={}", id);
        int tasksDeleted = taskRepository.deleteAllByUserId(id);
        int categoriesDeleted = categoryRepository.deleteAllByUserId(id);
        log.info("Cascade-deleted {} tasks and {} categories of user={}", tasksDeleted, categoriesDeleted, id);

        boolean deleted = userRepository.deleteById(id);
        log.info("Deleted user with id={}", id);
        return deleted;
    }
}
