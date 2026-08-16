package org.solujan.localstackstream.controller;

import org.solujan.localstackstream.records.CreateUserRequest;
import org.solujan.localstackstream.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Void> createUser(
            @RequestBody CreateUserRequest request) {

        userService.createUser(request);

        return ResponseEntity.noContent().build();
    }
}
