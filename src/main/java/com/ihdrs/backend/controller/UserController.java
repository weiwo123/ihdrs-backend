// UserController.java - 用户管理控制器
package com.ihdrs.backend.controller;

import com.ihdrs.backend.annotation.LogOperation;
import com.ihdrs.backend.annotation.LogOperation.OperationType;
import com.ihdrs.backend.common.PageResult;
import com.ihdrs.backend.common.Result;
import com.ihdrs.backend.dto.request.ChangePasswordRequest;
import com.ihdrs.backend.dto.request.UpdateProfileRequest;
import com.ihdrs.backend.dto.request.PageRequest;
import com.ihdrs.backend.dto.response.UserResponse;
import com.ihdrs.backend.dto.response.UserLogResponse;
import com.ihdrs.backend.service.AuthService;
import com.ihdrs.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@Tag(name = "用户管理", description = "用户管理相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "获取用户列表", description = "分页查询所有用户")
    @GetMapping("/list")
    @LogOperation(value = "查询用户列表", type = OperationType.QUERY)
    public Result<PageResult<UserResponse>> getUserList(@Valid PageRequest pageRequest) {
        return userService.getUserList(pageRequest);
    }

    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @GetMapping("/{userId}")
    @LogOperation(value = "查询用户详情", type = OperationType.QUERY)
    public Result<UserResponse> getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId);
    }

    @Operation(summary = "更新用户状态", description = "启用或禁用用户账号")
    @PutMapping("/{userId}/status")
    @LogOperation(value = "更新用户状态", type = OperationType.UPDATE)
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean status) {
        return userService.updateUserStatus(userId, status);
    }

    @Operation(summary = "获取活跃用户数", description = "获取最近30天活跃的用户数量")
    @GetMapping("/active-count")
    @LogOperation(value = "查询活跃用户数", type = OperationType.QUERY)
    public Result<Long> getActiveUserCount() {
        return userService.getActiveUserCount();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    @LogOperation(value = "查询个人信息", type = OperationType.QUERY)
    public UserResponse getMe(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        // 直接返回“裸”的 UserResponse（不要再用 Result 包一层）
        return authService.validateToken(token).getData();
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping("/me")
    @LogOperation(value = "修改个人资料", type = OperationType.UPDATE)
    public Result<String> updateMe(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody UpdateProfileRequest req) {

        String token = authorization.replace("Bearer ", "");
        Long userId = authService.validateToken(token).getData().getUserId();

        return userService.updateProfile(userId, req);
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/me/password")
    @LogOperation(value = "修改密码", type = OperationType.UPDATE)
    public Result<Void> changeMyPassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangePasswordRequest req) {

        String token = authorization.replace("Bearer ", "");
        Long userId = authService.validateToken(token).getData().getUserId();

        return userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
    }

    @Operation(summary = "检查用户名是否存在")
    @GetMapping("/check-username")
    @LogOperation(value = "检查用户名", type = OperationType.QUERY)
    public Result<Boolean> checkUsername(
            @RequestParam String username,
            @RequestHeader("Authorization") String authorization) {

        String token = authorization.replace("Bearer ", "");
        Long currentUserId = authService.validateToken(token).getData().getUserId();

        // 检查用户名是否被其他用户占用
        boolean exists = userService.usernameExistsExcludingUser(username, currentUserId);
        return Result.success(exists);
    }

    @Operation(summary="修改用户角色", description = "管理员修改用户角色")
    @PutMapping("/{userId}/role")
    @LogOperation(value = "修改用户角色", type = OperationType.UPDATE)
    public Result<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String role) {
        return userService.updateUserRole(userId, role);
    }

    @Operation(summary = "获取用户日志", description = "分页查询用户的操作日志")
    @GetMapping("/{userId}/logs")
    @LogOperation(value = "查询用户日志", type = OperationType.QUERY)
    public Result<PageResult<UserLogResponse>> getUserLogs(
            @PathVariable Long userId,
            @Valid PageRequest pageRequest) {
        return userService.getUserLogs(userId, pageRequest);
    }

}