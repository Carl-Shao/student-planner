package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 sys_user */
@Getter
@Setter
public class User {
    private Long id;
    private String phone;
    private String passwordHash;
    private boolean assessmentDone;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
