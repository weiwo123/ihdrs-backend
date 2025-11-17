// UserRepository.java
package com.ihdrs.backend.repository;

import com.ihdrs.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 检查用户名是否被其他用户占用
     * @param username 要检查的用户名
     * @param userId 要排除的用户ID（当前用户）
     * @return true=已存在（被其他用户占用）, false=可用
     */
    boolean existsByUsernameAndUserIdNot(String username, Long userId);

    /**
     * 查询指定时间段内注册的用户数量
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createTime BETWEEN :startTime AND :endTime")
    Long countByCreateTimeBetween(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 查询活跃用户数量(最近30天有登录记录)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.lastLoginTime >= :since")
    Long countActiveUsers(@Param("since") LocalDateTime since);

    /**
     * 根据用户名模糊查询
     */
    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.email LIKE %:keyword%")
    Page<User> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 仅搜索（用户名、邮箱、电话）
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%')")
    Page<User> findBySearch(@Param("search") String search, Pageable pageable);

    // 仅角色筛选
    Page<User> findByRole(User.UserRole role, Pageable pageable);

    // 仅状态筛选
    Page<User> findByStatus(Boolean status, Pageable pageable);

    // 搜索 + 角色
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%')) AND " +
            "u.role = :role")
    Page<User> findBySearchAndRole(@Param("search") String search,
                                   @Param("role") User.UserRole role,
                                   Pageable pageable);

    // 搜索 + 状态
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%')) AND " +
            "u.status = :status")
    Page<User> findBySearchAndStatus(@Param("search") String search,
                                     @Param("status") Boolean status,
                                     Pageable pageable);

    // 角色 + 状态
    Page<User> findByRoleAndStatus(User.UserRole role, Boolean status, Pageable pageable);

    // 搜索 + 角色 + 状态（全部条件）
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%')) AND " +
            "u.role = :role AND " +
            "u.status = :status")
    Page<User> findBySearchAndRoleAndStatus(@Param("search") String search,
                                            @Param("role") User.UserRole role,
                                            @Param("status") Boolean status,
                                            Pageable pageable);
}
