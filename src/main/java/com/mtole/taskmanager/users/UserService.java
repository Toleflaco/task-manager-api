package com.mtole.taskmanager.users;

import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.tasks.TaskRepository;
import com.mtole.taskmanager.users.dto.UserCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CategoryRepository categoryRepository, TaskRepository taskRepository,
                       UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }
    public User create(UserCreateRequest request){
        log.info("Creating user with email={}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered: " + request.email());
        }
        User entity = userMapper.toEntity(request);
        entity.setPassword(passwordEncoder.encode(request.password()));
        User createdUser = userRepository.save(entity);
        log.info("Created user with id={}", createdUser.getId());
        return createdUser;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
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
