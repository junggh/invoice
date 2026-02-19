package com.example.demo.controller;

import com.example.demo.dto.CompanyDashboardDto;
import com.example.demo.dto.MemberManagementDto;
import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import com.example.demo.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final MemberRepository memberRepository;

    // 1. [개발자 전용] 전체 회사 목록 보기
    @GetMapping("/super-admin/companies")
    public String viewAllCompanies(Model model) {
        List<CompanyDashboardDto> companies = adminDashboardService.getAllCompaniesSummary();
        model.addAttribute("companies", companies);
        return "super-admin-companies"; // HTML 템플릿
    }

    // 2. [개발자 전용] 특정 회사 클릭 시 멤버 보기
    @GetMapping("/super-admin/companies/{companyId}/users")
    public String viewCompanyUsersBySuperAdmin(@PathVariable Long companyId, Model model) {
        List<MemberManagementDto> users = adminDashboardService.getCompanyMembers(companyId);
        model.addAttribute("users", users);
        return "company-users"; // 공통 HTML 템플릿 사용
    }

    // 3. [회사 관리자용] 자기 회사 멤버 보기
    @GetMapping("/admin/users")
    public String viewMyCompanyUsers(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member admin = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (admin.getCompany() == null) {
            return "redirect:/?error=no_company";
        }

        // 로그인한 관리자의 회사 ID를 가져와서 조회 (보안 상 매우 중요!)
        List<MemberManagementDto> users = adminDashboardService.getCompanyMembers(admin.getCompany().getId());
        model.addAttribute("users", users);
        return "company-users"; // 공통 HTML 템플릿 사용
    }
}