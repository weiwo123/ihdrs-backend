// UserService.java
package com.ihdrs.backend.service;

import com.ihdrs.backend.common.PageResult;
import com.ihdrs.backend.common.Result;
import com.ihdrs.backend.dto.request.PageRequest;
import com.ihdrs.backend.dto.request.UpdateProfileRequest;
import com.ihdrs.backend.dto.response.UserResponse;
import com.ihdrs.backend.dto.response.UserLogResponse;
import com.ihdrs.backend.entity.User;
import com.ihdrs.backend.entity.UserLog;
import com.ihdrs.backend.repository.UserRepository;
import com.ihdrs.backend.repository.UserLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLogRepository userLogRepository;

    /**
     * 根据ID获取用户信息
     */
    public Result<UserResponse> getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        return Result.success(convertToUserResponse(user));
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public Result<Void> updateUserStatus(Long userId, Boolean status) {
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        user.setStatus(status);
        userRepository.save(user);

        log.info("更新用户状态: userId={}, status={}", userId, status);
        return Result.success("更新成功", null);
    }

    /**
     * 获取活跃用户数量
     */
    public Result<Long> getActiveUserCount() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Long count = userRepository.countActiveUsers(since);
        return Result.success(count);
    }

    /**
     * 转换为用户响应对象
     */
    private UserResponse convertToUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .email(user.getEmail())
                .phone(user.getPhone())
                .lastLoginTime(user.getLastLoginTime())
                .loginCount(user.getLoginCount())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * 检查用户名是否已被其他用户占用
     * @param username 要检查的用户名
     * @param excludeUserId 排除的用户ID（当前用户）
     * @return true=已存在（不可用）, false=不存在（可用）
     */
    public boolean usernameExistsExcludingUser(String username, Long excludeUserId) {
        return userRepository.existsByUsernameAndUserIdNot(username, excludeUserId);
    }

    /**
     * 更新当前用户资料（用户名、邮箱、电话）
     */
    @Transactional
    public Result<String> updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        List<String> updatedFields = new ArrayList<>();
        // 1. 处理用户名更新
        if (StringUtils.hasText(req.getUsername()) &&
                !req.getUsername().equals(user.getUsername())) {
            if (usernameExistsExcludingUser(req.getUsername(), userId)) {
                return Result.error(400, "用户名已存在");
            }
            user.setUsername(req.getUsername());
            updatedFields.add("用户名");
        }

            // 2. 处理邮箱更新（可以为空）
            if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
                user.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail() : null);
                updatedFields.add("邮箱");
            }

            // 3. 处理电话更新（可以为空）
            if (req.getTelephone() != null && !req.getTelephone().equals(user.getPhone())) {
                user.setPhone(StringUtils.hasText(req.getTelephone()) ? req.getTelephone() : null);
                updatedFields.add("电话");
            }

            if (updatedFields.isEmpty()) {
                return Result.success("未做任何修改", null);  // ✅ 没有修改
            }

            userRepository.save(user);

            String message = String.join("、", updatedFields) + "已更新";

            log.info("用户资料更新成功: userId={}, fields={}", userId, updatedFields);
            return Result.success(message, null);  // ✅ 返回具体的修改信息
        }

    /**
     * 修改当前用户密码
     */
    @Transactional
    public Result<Void> changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 验证原密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return Result.error(400, "原密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("用户 {} 密码修改成功", userId);
        return Result.success("密码修改成功", null);
    }

    /**
     * 修改用户角色
     */
    @Transactional
    public Result<Void> updateUserRole(Long userId, String role) {
        try {
            // 验证角色是否合法
            if (!role.equals("USER") && !role.equals("ADMIN")) {
                return Result.error(400, "角色参数错误");
            }

            // 查询用户是否存在
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return Result.error(404, "用户不存在");
            }

            // 将字符串转换为枚举并设置角色
            User.UserRole userRole = User.UserRole.valueOf(role);
            user.setRole(userRole);

            userRepository.save(user);

            log.info("修改用户角色成功: userId={}, role={}", userId, role);
            return Result.success("角色修改成功", null);
        } catch (IllegalArgumentException e) {
            log.error("角色参数错误: {}", role);
            return Result.error(400, "角色参数错误");
        } catch (Exception e) {
            log.error("修改用户角色失败: userId={}, role={}", userId, role, e);
            return Result.error(500, "修改角色失败");
        }
    }

    /**
     * 获取用户行为日志
     */
    public Result<PageResult<UserLogResponse>> getUserLogs(Long userId, PageRequest pageRequest) {
        try {
            org.springframework.data.domain.PageRequest springPageRequest =
                    org.springframework.data.domain.PageRequest.of(
                            pageRequest.getCurrent().intValue() - 1,
                            pageRequest.getSize().intValue()
                    );

            Page<UserLog> logPage = userLogRepository.findByUserIdOrderByCreateTimeDesc(
                    userId,
                    springPageRequest
            );

            List<UserLogResponse> logResponses = logPage.getContent().stream()
                    .map(log -> UserLogResponse.builder()
                            .logId(log.getLogId())
                            .userId(log.getUserId())
                            .action(log.getAction())
                            .ipAddress(log.getIpAddress())
                            .userAgent(log.getUserAgent())
                            .createTime(log.getCreateTime())
                            .build())
                    .collect(Collectors.toList());

            PageResult<UserLogResponse> result = PageResult.of(
                    logResponses,
                    logPage.getTotalElements(),
                    pageRequest.getSize(),
                    pageRequest.getCurrent()
            );

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取用户日志失败: userId={}", userId, e);
            return Result.error(500, "获取用户日志失败");
        }
    }

    /**
     * 检查字符串是否有效（非 null 且非空）
     */
    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 检查布尔值是否有效（非 null）
     */
    private boolean hasValue(Boolean bool) {
        return bool != null;
    }

    /**
     * 分页查询用户列表（支持搜索和筛选）
     */
    public Result<PageResult<UserResponse>> getUserList(PageRequest pageRequest) {
        try {
            org.springframework.data.domain.PageRequest springPageRequest =
                    org.springframework.data.domain.PageRequest.of(
                            pageRequest.getCurrent().intValue() - 1,
                            pageRequest.getSize().intValue(),
                            Sort.by(Sort.Direction.DESC, "createTime")
                    );

            Page<User> userPage;

            String search = pageRequest.getSearch();
            String role = pageRequest.getRole();
            Boolean status = pageRequest.getStatus();

            boolean hasSearch = hasValue(search);
            boolean hasRole = hasValue(role);
            boolean hasStatus = hasValue(status);

            if (hasSearch && hasRole && hasStatus) {
                userPage = userRepository.findBySearchAndRoleAndStatus(
                        search, User.UserRole.valueOf(role), status, springPageRequest);
            } else if (hasSearch && hasRole) {
                userPage = userRepository.findBySearchAndRole(
                        search, User.UserRole.valueOf(role), springPageRequest);
            } else if (hasSearch && hasStatus) {
                userPage = userRepository.findBySearchAndStatus(search, status, springPageRequest);
            } else if (hasRole && hasStatus) {
                userPage = userRepository.findByRoleAndStatus(
                        User.UserRole.valueOf(role), status, springPageRequest);
            } else if (hasSearch) {
                userPage = userRepository.findBySearch(search, springPageRequest);
            } else if (hasRole) {
                userPage = userRepository.findByRole(User.UserRole.valueOf(role), springPageRequest);
            } else if (hasStatus) {
                userPage = userRepository.findByStatus(status, springPageRequest);
            } else {
                userPage = userRepository.findAll(springPageRequest);
            }

            List<UserResponse> userList = userPage.getContent().stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            PageResult<UserResponse> result = PageResult.of(
                    userList,
                    userPage.getTotalElements(),
                    pageRequest.getSize(),
                    pageRequest.getCurrent()
            );

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return Result.error(500, "获取用户列表失败：" + e.getMessage());
        }
    }
}
