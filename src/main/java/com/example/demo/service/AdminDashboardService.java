package com.example.demo.service;

import com.example.demo.dto.CompanyDashboardDto;
import com.example.demo.dto.MemberManagementDto;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    // 날짜를 "Jan 15 2025" 형식으로 바꿔주는 포매터 (영문 표기)
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);

    // [추가] 상대 시간 계산 도우미 메서드
    private String getRelativeTimeInfo(LocalDateTime pastTime) {
        if (pastTime == null) {
            return "Never"; // 한 번도 기록된 적이 없을 때
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration duration = Duration.between(pastTime, now);
        long seconds = duration.getSeconds();

        if (seconds < 60) return "Just now";
        if (seconds < 3600) return (seconds / 60) + " mins ago";
        if (seconds < 86400) return (seconds / 3600) + " hours ago";
        return (seconds / 86400) + " days ago";
    }

    // 1. [개발자용] 모든 회사 목록 조회
    @Transactional(readOnly = true)
    public List<CompanyDashboardDto> getAllCompaniesSummary() {
        List<Company> companies = companyRepository.findAll();

        return companies.stream().map(company -> {
            int userCount = memberRepository.countByCompanyId(company.getId());
            Member admin = memberRepository.findFirstByCompanyIdAndRole(company.getId(), "ADMIN").orElse(null);

            String planName = (company.getPlan() != null) ? company.getPlan().name() : "None";
            String status = (company.getPlan() != null) ? "Active" : "Inactive";
            String formattedDate = (company.getJoinedDate() != null)
                    ? company.getJoinedDate().format(formatter) : "N/A";
            String timeAgo = getRelativeTimeInfo(company.getLastActiveDate());

            return CompanyDashboardDto.builder()
                    .companyId(company.getId())
                    .businessName(company.getBusinessName())
                    .adminEmail(admin != null ? admin.getEmail() : "N/A")
                    .plan(planName)
                    .userCount(userCount)
                    .status(status)
                    .joinedDate(formattedDate)
                    .lastActive(timeAgo)
                    .build();
        }).collect(Collectors.toList());
    }

    // 2. [공통용] 특정 회사의 멤버 목록 조회
    @Transactional(readOnly = true)
    public List<MemberManagementDto> getCompanyMembers(Long companyId) {
        List<Member> members = memberRepository.findByCompanyId(companyId);

        return members.stream().map(member -> {
            String formattedDate = (member.getJoinedDate() != null)
                    ? member.getJoinedDate().format(formatter) : "N/A";
            String loginTimeAgo = getRelativeTimeInfo(member.getLastLoginDate());

            return MemberManagementDto.builder()
                    .memberId(member.getId())
                    .name(member.getFirstName() + " " + member.getLastName())
                    .email(member.getEmail())
                    .role(member.getRole())
                    .joinedDate(formattedDate)
                    .lastLogin(loginTimeAgo)
                    .build();
        }).collect(Collectors.toList());
    }
}