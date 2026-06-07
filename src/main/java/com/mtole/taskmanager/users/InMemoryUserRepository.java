package com.mtole.taskmanager.users;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public InMemoryUserRepository() {
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(counter.incrementAndGet());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }

        users.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }


    @Override
    public boolean deleteById(Long id) {
        return users.remove(id) != null; // true si había algo, false si no
    }

    @Override
    public boolean existsById(Long id) {
        return users.containsKey(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return(users.values().stream()
                .filter(user -> email.equalsIgnoreCase(user.getEmail()))
                .findFirst());
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream()
                .anyMatch(user -> email.equalsIgnoreCase(user.getEmail()));
    }
}
