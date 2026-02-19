package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyDashboardDto {
    private Long companyId;
    private String businessName;
    private String adminEmail; // 회사 대표 계정 이메일
    private String plan;       // Lite, Basic, Pro
    private int userCount;     // 소속 멤버 수
    private String status;     // Active, Inactive 등 (결제 여부로 판단)
    private String joinedDate;
    private String lastActive;
}