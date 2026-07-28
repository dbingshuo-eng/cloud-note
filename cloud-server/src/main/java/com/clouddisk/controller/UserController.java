package com.clouddisk.controller;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.dto.UserLoginDTO;
import com.clouddisk.service.UserService;
import com.clouddisk.vo.UserLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody UserLoginDTO request) {
        return ApiResponse.success(userService.login(request));
    }
}
