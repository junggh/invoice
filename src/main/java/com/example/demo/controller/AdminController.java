package com.example.demo.controller;

import com.example.demo.dto.CompanyDashboardDto;
import com.example.demo.dto.MemberManagementDto;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.MemberRepository;
import com.example.demo.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        model.addAttribute("currentMemberId", admin.getId());
        return "company-users";
    }

    /** 멤버의 Role을 변경한다. 같은 회사 소속인 멤버만 변경할 수 있다. */
    @PostMapping("/admin/users/{memberId}/role")
    @ResponseBody
    public ResponseEntity<String> changeUserRole(
            @PathVariable Long memberId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Member admin = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (admin.getCompany() == null) {
            return ResponseEntity.badRequest().body("No company affiliation.");
        }

        Member target = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        // 자기 자신의 Role은 변경 불가 (세션 불일치 문제 방지)
        if (admin.getId().equals(target.getId())) {
            return ResponseEntity.badRequest().body("You cannot change your own role.");
        }

        // 같은 회사 소속인지 확인
        if (target.getCompany() == null || !admin.getCompany().getId().equals(target.getCompany().getId())) {
            return ResponseEntity.status(403).body("Access denied.");
        }

        String newRole = request.get("role");
        if (!"ADMIN".equals(newRole) && !"USER".equals(newRole)) {
            return ResponseEntity.badRequest().body("Invalid role.");
        }

        target.setRole(newRole);
        memberRepository.save(target);

        return ResponseEntity.ok("Role updated.");
    }
}