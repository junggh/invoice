package com.example.demo.service;

import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyInvitation;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyInvitationRepository;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyInvitationService {

    private final CompanyInvitationRepository invitationRepository;
    private final MemberRepository memberRepository;

    // 1. 초대장 생성 로직
    @Transactional
    public CompanyInvitation createInvitation(String adminEmail, String inviteeEmail) {
        Member admin = memberRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found."));

        if (!"ADMIN".equals(admin.getRole())) {
            throw new IllegalStateException("You do not have permission to invite. Only administrators can do this.");
        }

        Company company = admin.getCompany();
        if (company == null) {
            throw new IllegalStateException("No company affiliation found.");
        }

        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCompany(company);
        invitation.setInviteeEmail(inviteeEmail);
        invitation.setToken(UUID.randomUUID().toString()); // 해킹이 불가능한 랜덤 문자열 생성

        return invitationRepository.save(invitation);
    }

    // 2. 초대장 수락 로직
    @Transactional
    public String acceptInvitation(String token, String loggedInEmail) {
        CompanyInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or incorrect invitation link."));

        if (invitation.getStatus() != CompanyInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("This invitation has already been " + invitation.getStatus().name() + ".");
        }

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CompanyInvitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("The invitation has expired (valid for 7 days). Please request a new one from the administrator.");
        }

        // 로그인한 사람이 초대받은 사람이 맞는지 확인 (보안)
        if (!invitation.getInviteeEmail().equalsIgnoreCase(loggedInEmail)) {
            throw new IllegalStateException("Please log in with the invited email account (" + invitation.getInviteeEmail() + ").");
        }

        Member member = memberRepository.findByEmail(loggedInEmail)
                .orElseThrow(() -> new IllegalArgumentException("Member information not found. Please sign up first."));

        // 회사 연결 및 권한 일반 사용자(USER)로 부여
        member.setCompany(invitation.getCompany());
        member.setRole("USER");

        // 초대장 상태를 '수락됨'으로 변경
        invitation.setStatus(CompanyInvitation.InvitationStatus.ACCEPTED);

        return invitation.getCompany().getBusinessName();
    }

    // [추가] 토큰으로 초대받은 이메일 주소 알아내기 (회원가입 창 자동 입력을 위해)
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        return invitationRepository.findByToken(token)
                .map(CompanyInvitation::getInviteeEmail)
                .orElse(null);
    }
}