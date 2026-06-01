package com.mtole.taskmanager.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    public boolean deleteById(Long id){
        log.info("Deleting user with id={}", id);
        boolean deleted = userRepository.deleteById(id);
        if(deleted){
            log.info("Deleted user id={}",id);
        }else {
            log.warn("User with id={} could not be deleted", id);
        }
        return deleted;
    }
}
