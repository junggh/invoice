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
                .orElseThrow(() -> new IllegalArgumentException("관리자를 찾을 수 없습니다."));

        if (!"ADMIN".equals(admin.getRole())) {
            throw new IllegalStateException("초대 권한이 없습니다. 관리자만 가능합니다.");
        }

        Company company = admin.getCompany();
        if (company == null) {
            throw new IllegalStateException("소속된 회사가 없습니다.");
        }

        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCompany(company);
        invitation.setInviteeEmail(inviteeEmail);
        invitation.setToken(UUID.randomUUID().toString()); // 해킹이 불가능한 랜덤 문자열 생성

        return invitationRepository.save(invitation);
    }

    // 2. 초대장 수락 로직
    @Transactional
    public void acceptInvitation(String token, String loggedInEmail) {
        CompanyInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 잘못된 초대 링크입니다."));

        if (invitation.getStatus() != CompanyInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("이미 수락되었거나 만료된 초대장입니다.");
        }

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CompanyInvitation.InvitationStatus.EXPIRED);
            throw new IllegalStateException("초대 유효기간(7일)이 만료되었습니다. 관리자에게 재요청하세요.");
        }

        // 로그인한 사람이 초대받은 사람이 맞는지 확인 (보안)
        if (!invitation.getInviteeEmail().equalsIgnoreCase(loggedInEmail)) {
            throw new IllegalStateException("초대받은 이메일(" + invitation.getInviteeEmail() + ") 계정으로 로그인해주세요.");
        }

        Member member = memberRepository.findByEmail(loggedInEmail)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보가 없습니다. 먼저 회원가입을 진행해주세요."));

        // 회사 연결 및 권한 일반 사용자(USER)로 부여
        member.setCompany(invitation.getCompany());
        member.setRole("USER");

        // 초대장 상태를 '수락됨'으로 변경
        invitation.setStatus(CompanyInvitation.InvitationStatus.ACCEPTED);
    }

    // [추가] 토큰으로 초대받은 이메일 주소 알아내기 (회원가입 창 자동 입력을 위해)
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        return invitationRepository.findByToken(token)
                .map(CompanyInvitation::getInviteeEmail)
                .orElse(null);
    }
}