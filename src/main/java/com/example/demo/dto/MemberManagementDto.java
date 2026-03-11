package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Admin 대시보드의 멤버 관리 목록 행 데이터 DTO.
 * AdminDashboardService.getCompanyMembers()에서 생성된다.
 */
@Data
@Builder
public class MemberManagementDto {
    private Long memberId;      // 멤버 ID
    private String name;        // 전체 이름 (firstName + lastName)
    private String email;       // 이메일
    private String role;        // 권한 (USER / ADMIN / SUPER_ADMIN)
    private String joinedDate;  // 가입일 (예: "Jan 15 2025")
    private String lastLogin;   // 마지막 로그인 상대 시간 (예: "2 hours ago")
}