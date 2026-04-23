package com.example.service;

import com.example.model.User;
import com.example.model.UserRole;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class UserService {

    private final ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    private final LinkedList<String> passwordHistory = new LinkedList<>();
    private static final int MAX_PASSWORD_HISTORY = 10;
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private final SecureRandom secureRandom = new SecureRandom();

    public synchronized User createUser(String username, String email, String password) {
        Long newId = idCounter.getAndIncrement();

        if (findUserByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (users.values().stream().anyMatch(u -> u.getEmail().equals(email))) {
            throw new IllegalArgumentException("Email already exists");
        }

        String hashedPassword = hashPassword(password);
        User user = new User(newId, username, email, hashedPassword, UserRole.USER);
        users.put(newId, user);

        addToPasswordHistory(password);

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

    public synchronized boolean updateUserPassword(Long userId, String oldPassword, String newPassword) {
        User user = users.get(userId);
        if (user == null) {
            return false;
        }

        if (!verifyPassword(oldPassword, user.getPassword())) {
            user.incrementLoginAttempts();
            if (user.getLoginAttempts() > MAX_LOGIN_ATTEMPTS) {
                user.setActive(false);
            }
            return false;
        }

        String hashedNewPassword = hashPassword(newPassword);
        user.setPassword(hashedNewPassword);
        user.resetLoginAttempts();

        addToPasswordHistory(newPassword);

        return true;
    }

    public boolean deleteUser(Long userId) {
        User removed = users.remove(userId);
        return removed != null;
    }

    public boolean authenticateUser(String username, String password) {
        Optional<User> userOpt = findUserByUsername(username);

        User user = userOpt.orElse(null);

        if (user == null) {
            dummyPasswordVerify();
            return false;
        }

        if (!user.isActive()) {
            dummyPasswordVerify();
            return false;
        }

        boolean passwordMatch = verifyPassword(password, user.getPassword());

        if (passwordMatch) {
            user.resetLoginAttempts();
            return true;
        } else {
            user.incrementLoginAttempts();
            if (user.getLoginAttempts() > MAX_LOGIN_ATTEMPTS) {
                user.setActive(false);
            }
            return false;
        }
    }

    public int getUserCount() {
        return users.size();
    }

    private void addToPasswordHistory(String password) {
        if (passwordHistory.size() >= MAX_PASSWORD_HISTORY) {
            passwordHistory.removeFirst();
        }
        passwordHistory.addLast(password);
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hashedPassword);
            return saltBase64 + ":" + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private boolean verifyPassword(String plainPassword, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            String hashBase64 = Base64.getEncoder().encodeToString(hashedPassword);
            return constantTimeEquals(hashBase64, parts[1]);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        int result = 0;
        int length = Math.min(aBytes.length, bBytes.length);

        for (int i = 0; i < length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        result |= aBytes.length ^ bBytes.length;

        return result == 0;
    }

    private void dummyPasswordVerify() {
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.digest("dummy".getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public void simulateConcurrentAccess() {
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    try {
                        createUser("user" + System.currentTimeMillis(),
                                "email" + System.currentTimeMillis() + "@test.com",
                                "password");
                    } catch (IllegalArgumentException e) {
                    }
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
