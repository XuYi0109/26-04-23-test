package com.example.resource;

import com.example.model.User;
import com.example.service.UserService;
import jakarta.inject.Inject;
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
        // BUG: No pagination - could return huge lists and cause memory issues
        return userService.getAllUsers();
    }
    
    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") Long id) {
        // BUG: No input validation - could accept negative IDs or very large numbers
        Optional<User> user = userService.findUserById(id);
        
        if (user.isPresent()) {
            return Response.ok(user.get()).build();
        } else {
            // BUG: Information disclosure - reveals that user doesn't exist
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"User not found\"}")
                    .build();
        }
    }
    
    @POST
    public Response createUser(UserCreationRequest request) {
        // BUG: No request body validation
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Username is required\"}")
                    .build();
        }
        
        // BUG: No email format validation
        if (request.email == null || request.email.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Email is required\"}")
                    .build();
        }
        
        // BUG: No password strength validation
        if (request.password == null || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Password is required\"}")
                    .build();
        }
        
        User user = userService.createUser(request.username, request.email, request.password);
        
        // BUG: Returns password in response (security issue)
        return Response.status(Response.Status.CREATED).entity(user).build();
    }
    
    @PUT
    @Path("/{id}/password")
    public Response updatePassword(@PathParam("id") Long id, PasswordUpdateRequest request) {
        // BUG: No authentication/authorization check
        boolean success = userService.updateUserPassword(id, request.oldPassword, request.newPassword);
        
        if (success) {
            return Response.ok("{\"message\": \"Password updated successfully\"}").build();
        } else {
            // BUG: Information disclosure - reveals whether user exists
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to update password\"}")
                    .build();
        }
    }
    
    @POST
    @Path("/authenticate")
    public Response authenticate(AuthenticationRequest request) {
        // BUG: No rate limiting for authentication attempts
        boolean authenticated = userService.authenticateUser(request.username, request.password);
        
        if (authenticated) {
            return Response.ok("{\"message\": \"Authentication successful\"}").build();
        } else {
            // BUG: Timing attack vulnerability in response
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Authentication failed\"}")
                    .build();
        }
    }
    
    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        // BUG: No authorization check - anyone can delete any user
        boolean deleted = userService.deleteUser(id);
        
        if (deleted) {
            return Response.ok("{\"message\": \"User deleted successfully\"}").build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"User not found\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/count")
    public Response getUserCount() {
        int count = userService.getUserCount();
        return Response.ok("{\"count\": " + count + "}").build();
    }
    
    // Request DTOs
    public static class UserCreationRequest {
        public String username;
        public String email;
        public String password;
    }
    
    public static class PasswordUpdateRequest {
        public String oldPassword;
        public String newPassword;
    }
    
    public static class AuthenticationRequest {
        public String username;
        public String password;
    }
}