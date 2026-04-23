import java.util.*;
import java.util.concurrent.*;

public class SimpleBuggyApp {
    
    // BUG 1: Race condition in user creation
    private static final Map<Long, User> users = new ConcurrentHashMap<>();
    private static long idCounter = 1;
    
    // BUG 2: Memory leak - password history without size limit
    private static final List<String> passwordHistory = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== Buggy User Management System ===\n");
        
        // Test the application
        testUserCreation();
        testConcurrentAccess();
        testAuthentication();
        
        System.out.println("\n=== Application Test Completed ===");
        System.out.println("Total users created: " + users.size());
        System.out.println("Password history size: " + passwordHistory.size());
    }
    
    // BUG 3: No validation for duplicate usernames/emails
    public static User createUser(String username, String email, String password) {
        long newId = idCounter++;
        
        // BUG: Race condition - multiple threads can get same ID
        User user = new User(newId, username, email, password);
        
        // BUG: Password stored in plain text
        users.put(newId, user);
        
        // BUG: Memory leak - password history grows without bound
        passwordHistory.add(password);
        
        System.out.println("Created user: " + username + " (ID: " + newId + ")");
        return user;
    }
    
    // BUG 4: Deep bug - Complex business logic with race condition
    public static boolean updateUserPassword(long userId, String oldPassword, String newPassword) {
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
        
        // Add to password history without size limit
        passwordHistory.add(newPassword);
        
        return true;
    }
    
    // BUG 5: Timing attack vulnerability
    public static boolean authenticateUser(String username, String password) {
        User user = findUserByUsername(username);
        if (user == null) {
            // BUG: Different response time for non-existent users
            return false;
        }
        
        if (!user.isActive()) {
            return false;
        }
        
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
    
    public static User findUserByUsername(String username) {
        for (User user : users.values()) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }
    
    private static void testUserCreation() {
        System.out.println("=== Testing User Creation ===");
        
        // Create some test users
        createUser("alice", "alice@test.com", "password123");
        createUser("bob", "bob@test.com", "password456");
        
        // BUG: Duplicate usernames allowed
        createUser("alice", "alice2@test.com", "password789");
        
        System.out.println("Users created: " + users.size());
    }
    
    private static void testConcurrentAccess() {
        System.out.println("\n=== Testing Concurrent Access (Race Condition) ===");
        
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
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
        
        System.out.println("Total users after concurrent access: " + users.size());
    }
    
    private static void testAuthentication() {
        System.out.println("\n=== Testing Authentication ===");
        
        // Test correct authentication
        boolean auth1 = authenticateUser("alice", "password123");
        System.out.println("Alice authentication (correct): " + auth1);
        
        // Test incorrect authentication
        boolean auth2 = authenticateUser("alice", "wrongpassword");
        System.out.println("Alice authentication (wrong): " + auth2);
        
        // Test non-existent user (timing attack vulnerability)
        boolean auth3 = authenticateUser("nonexistent", "password");
        System.out.println("Non-existent user authentication: " + auth3);
    }
}

class User {
    private long id;
    private String username;
    private String email;
    private String password;
    private boolean active;
    private int loginAttempts;
    
    public User(long id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.active = true;
        this.loginAttempts = 0;
    }
    
    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public boolean isActive() { return active; }
    public int getLoginAttempts() { return loginAttempts; }
    
    public void setPassword(String password) { this.password = password; }
    public void setActive(boolean active) { this.active = active; }
    public void setLoginAttempts(int loginAttempts) { this.loginAttempts = loginAttempts; }
    
    public void incrementLoginAttempts() { this.loginAttempts++; }
    public void resetLoginAttempts() { this.loginAttempts = 0; }
}