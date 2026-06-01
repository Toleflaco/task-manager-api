package com.mtole.taskmanager.users;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    List<User> findAll(int page, int pageSize);
    int countAll();
    boolean deleteById(Long id);
}
