package com.example.demo.controller;

import com.example.demo.dto.CompanyDashboardDto;
import com.example.demo.dto.MemberManagementDto;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyRepository;
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
    private final CompanyRepository companyRepository;

    // ===================================================================================
    // 1. Super Admin (전체 회사 관리)
    // ===================================================================================

    /** 전체 회사 목록 조회. SUPER_ADMIN만 접근 가능하며, 각 회사의 요약 정보를 대시보드 형태로 표시한다. */
    @GetMapping("/super-admin/companies")
    public String viewAllCompanies(Model model) {
        List<CompanyDashboardDto> companies = adminDashboardService.getAllCompaniesSummary();
        model.addAttribute("companies", companies);
        return "super-admin-companies";
    }

    /** 특정 회사의 멤버 목록 조회. SUPER_ADMIN이 회사 목록에서 특정 회사를 클릭할 때 호출된다. */
    @GetMapping("/super-admin/companies/{companyId}/users")
    public String viewCompanyUsersBySuperAdmin(@PathVariable Long companyId, Model model) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        List<MemberManagementDto> users = adminDashboardService.getCompanyMembers(companyId);
        model.addAttribute("companyName", company.getBusinessName());
        model.addAttribute("users", users);
        return "super-admin-company-users";
    }

    // ===================================================================================
    // 2. Company Admin (자기 회사 관리)
    // ===================================================================================

    /**
     * 로그인한 ADMIN의 자기 회사 멤버 목록 조회.
     * 로그인한 계정의 company_id를 기준으로 조회하므로 다른 회사 데이터에 접근할 수 없다.
     */
    @GetMapping("/admin/users")
    public String viewMyCompanyUsers(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member admin = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (admin.getCompany() == null) {
            return "redirect:/?error=no_company";
        }

        List<MemberManagementDto> users = adminDashboardService.getCompanyMembers(admin.getCompany().getId());
        model.addAttribute("users", users);
        return "company-users";
    }
}