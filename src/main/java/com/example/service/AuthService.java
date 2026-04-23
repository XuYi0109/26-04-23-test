package com.example.service;

import com.example.dto.AuthResponse;
import com.example.dto.LoginRequest;
import com.example.dto.RegisterRequest;
import com.example.model.User;
import com.example.model.UserRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    private final UserService userService;
    private final TokenService tokenService;

    @Inject
    public AuthService(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        User user = userService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        String token = tokenService.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public Optional<AuthResponse> login(LoginRequest request) {
        boolean authenticated = userService.authenticateUser(
                request.getUsername(),
                request.getPassword()
        );

        if (!authenticated) {
            return Optional.empty();
        }

        Optional<User> userOpt = userService.findUserByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        String token = tokenService.generateToken(user);

        return Optional.of(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        ));
    }

    public Optional<User> getCurrentUser(String token) {
        Optional<Long> userIdOpt = tokenService.extractUserId(token);
        if (userIdOpt.isEmpty()) {
            return Optional.empty();
        }

        return userService.findUserById(userIdOpt.get());
    }

    public boolean validateToken(String token) {
        return tokenService.isTokenValid(token);
    }
}
