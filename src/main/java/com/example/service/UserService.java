package com.example.service;

import com.example.model.User;
import com.example.model.UserRole;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class UserService {
    
    private final ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<String> passwordHistory = new ArrayList<>();
    
    // BUG 1: Race condition in user creation - multiple threads can create users with same ID
    public User createUser(String username, String email, String password) {
        Long newId = idCounter.getAndIncrement();
        
        // BUG 2: No validation for duplicate usernames/emails
        User user = new User(newId, username, email, password, UserRole.USER);
        
        // BUG 3: Password stored in plain text (security issue)
        users.put(newId, user);
        
        // BUG 4: Password history not properly managed - potential memory leak
        passwordHistory.add(password);
        
        return user;
    }
    
    public Optional<User> findUserById(Long id) {
        return Optional.ofNullable(users.get(id));
    }
    
    public Optional<User> findUserByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }
    
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }
    
    // BUG 5: Deep bug - Complex business logic with race condition and state inconsistency
    public boolean updateUserPassword(Long userId, String oldPassword, String newPassword) {
        User user = users.get(userId);
        if (user == null) {
            return false;
        }
        
        // Race condition: user object might be modified by another thread
        if (!user.getPassword().equals(oldPassword)) {
            user.incrementLoginAttempts();
            
            // BUG: No proper lock management - potential for inconsistent state
            if (user.getLoginAttempts() > 3) {
                user.setActive(false); // This might conflict with other operations
            }
            return false;
        }
        
        // Update password without proper synchronization
        user.setPassword(newPassword);
        user.resetLoginAttempts();
        
        // Add to password history without size limit (potential memory issue)
        passwordHistory.add(newPassword);
        
        return true;
    }
    
    public boolean deleteUser(Long userId) {
        User removed = users.remove(userId);
        return removed != null;
    }
    
    public boolean authenticateUser(String username, String password) {
        Optional<User> userOpt = findUserByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        if (!user.isActive()) {
            return false;
        }
        
        // BUG: Timing attack vulnerability - response time differs based on user existence
        if (user.getPassword().equals(password)) {
            user.resetLoginAttempts();
            return true;
        } else {
            user.incrementLoginAttempts();
            if (user.getLoginAttempts() > 3) {
                user.setActive(false);
            }
            return false;
        }
    }
    
    public int getUserCount() {
        return users.size();
    }
    
    // Helper method to demonstrate the deep bug
    public void simulateConcurrentAccess() {
        // This method demonstrates the race condition issue
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    createUser("user" + System.currentTimeMillis(), 
                             "email" + System.currentTimeMillis() + "@test.com", 
                             "password");
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}