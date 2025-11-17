package com.ihdrs.backend.annotation;

import java.lang.annotation.*;

/**
 * 用户操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {

    /**
     * 操作描述
     */
    String value() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作类型枚举
     */
    enum OperationType {
        LOGIN("登录"),
        LOGOUT("登出"),
        CREATE("创建"),
        UPDATE("更新"),
        DELETE("删除"),
        QUERY("查询"),
        UPLOAD("上传"),
        DOWNLOAD("下载"),
        OTHER("其他");

        private final String description;

        OperationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}