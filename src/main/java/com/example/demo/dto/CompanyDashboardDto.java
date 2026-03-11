package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Super Admin 대시보드의 회사 목록 행 데이터 DTO.
 * AdminDashboardService.getAllCompaniesSummary()에서 생성된다.
 */
@Data
@Builder
public class CompanyDashboardDto {
    private Long companyId;        // 회사 ID
    private String businessName;   // 회사명
    private String adminEmail;     // 회사 ADMIN 계정 이메일 (없으면 "N/A")
    private String plan;           // 구독 플랜 (LITE / BASIC / PRO, 없으면 "None")
    private int userCount;         // 소속 멤버 수
    private String status;         // 상태 (플랜 존재 시 "Active", 없으면 "Inactive")
    private String joinedDate;     // 가입일 (예: "Jan 15 2025")
    private String lastActive;     // 마지막 활동 상대 시간 (예: "5 mins ago")
}