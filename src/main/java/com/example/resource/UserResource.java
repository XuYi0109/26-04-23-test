package com.example.resource;

import com.example.model.User;
import com.example.service.UserService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserService userService;

    @GET
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") Long id) {
        if (id == null || id <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid ID format\"}")
                    .build();
        }

        Optional<User> user = userService.findUserById(id);

        if (user.isPresent()) {
            return Response.ok(user.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Resource not found\"}")
                    .build();
        }
    }

    @POST
    public Response createUser(@Valid UserCreationRequest request) {
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Username is required\"}")
                    .build();
        }

        if (request.username.length() < 3 || request.username.length() > 50) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Username must be between 3 and 50 characters\"}")
                    .build();
        }

        if (!request.username.matches("^[a-zA-Z0-9_]+$")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Username can only contain letters, numbers, and underscores\"}")
                    .build();
        }

        if (request.email == null || request.email.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Email is required\"}")
                    .build();
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!request.email.matches(emailRegex)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid email format\"}")
                    .build();
        }

        if (request.password == null || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Password is required\"}")
                    .build();
        }

        if (request.password.length() < 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Password must be at least 8 characters long\"}")
                    .build();
        }

        try {
            User user = userService.createUser(request.username, request.email, request.password);
            UserResponse response = new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\": \"User already exists\"}")
                    .build();
        }
    }

    @PUT
    @Path("/{id}/password")
    public Response updatePassword(@PathParam("id") Long id, @Valid PasswordUpdateRequest request) {
        if (id == null || id <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid ID format\"}")
                    .build();
        }

        if (request.oldPassword == null || request.oldPassword.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Old password is required\"}")
                    .build();
        }

        if (request.newPassword == null || request.newPassword.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"New password is required\"}")
                    .build();
        }

        if (request.newPassword.length() < 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"New password must be at least 8 characters long\"}")
                    .build();
        }

        boolean success = userService.updateUserPassword(id, request.oldPassword, request.newPassword);

        if (success) {
            return Response.ok("{\"message\": \"Password updated successfully\"}").build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to update password\"}")
                    .build();
        }
    }

    @POST
    @Path("/authenticate")
    public Response authenticate(@Valid AuthenticationRequest request) {
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Username is required\"}")
                    .build();
        }

        if (request.password == null || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Password is required\"}")
                    .build();
        }

        boolean authenticated = userService.authenticateUser(request.username, request.password);

        if (authenticated) {
            return Response.ok("{\"message\": \"Authentication successful\"}").build();
        } else {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Authentication failed\"}")
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        if (id == null || id <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid ID format\"}")
                    .build();
        }

        boolean deleted = userService.deleteUser(id);

        if (deleted) {
            return Response.ok("{\"message\": \"User deleted successfully\"}").build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Resource not found\"}")
                    .build();
        }
    }

    @GET
    @Path("/count")
    public Response getUserCount() {
        int count = userService.getUserCount();
        return Response.ok("{\"count\": " + count + "}").build();
    }

    public static class UserCreationRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        public String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        public String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        public String password;
    }

    public static class PasswordUpdateRequest {
        @NotBlank(message = "Old password is required")
        public String oldPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters long")
        public String newPassword;
    }

    public static class AuthenticationRequest {
        @NotBlank(message = "Username is required")
        public String username;

        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class UserResponse {
        public Long id;
        public String username;
        public String email;
        public String role;

        public UserResponse(Long id, String username, String email, Enum<?> role) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role.name();
        }
    }
}
