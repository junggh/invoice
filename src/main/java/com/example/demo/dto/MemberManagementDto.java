package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberManagementDto {
    private Long memberId;
    private String name;
    private String email;
    private String role; // Admin, Manager, User 등
    private String joinedDate;
    private String lastLogin;
}