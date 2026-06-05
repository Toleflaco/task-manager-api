package com.mtole.taskmanager.users;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    boolean deleteById(Long id);

    boolean existsById(Long id);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
