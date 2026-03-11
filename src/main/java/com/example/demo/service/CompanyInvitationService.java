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

    // ===================================================================================
    // 1. 초대 생성
    // ===================================================================================

    /**
     * 팀원 초대장 생성. ADMIN만 호출할 수 있으며, 이미 같은 회사 소속인 이메일에는 중복 초대를 차단한다.
     * UUID 기반 토큰을 생성하여 DB에 저장하고 반환한다 (이메일 발송은 컨트롤러에서 처리).
     */
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

        // 이미 같은 회사 소속인 이메일에는 초대 차단
        boolean alreadyMember = memberRepository.findByCompanyId(company.getId()).stream()
                .anyMatch(m -> m.getEmail().equalsIgnoreCase(inviteeEmail));
        if (alreadyMember) {
            throw new IllegalStateException("This email is already a member of your company.");
        }

        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCompany(company);
        invitation.setInviteeEmail(inviteeEmail);
        invitation.setToken(UUID.randomUUID().toString());

        return invitationRepository.save(invitation);
    }

    // ===================================================================================
    // 2. 초대 수락
    // ===================================================================================

    /**
     * 초대 수락 처리. 토큰 유효성, 만료 여부, 로그인 이메일 일치 여부를 순서대로 검증한다.
     * 모든 검증을 통과하면 Member에 회사를 연결하고 역할을 USER로 설정한다.
     *
     * @return 연결된 회사명 (성공 알림 메시지 생성용)
     */
    @Transactional
    public String acceptInvitation(String token, String loggedInEmail) {
        CompanyInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or incorrect invitation link."));

        if (invitation.getStatus() != CompanyInvitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("This invitation has already been " + invitation.getStatus().name() + ".");
        }

        // 만료 확인 (7일 유효)
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CompanyInvitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("The invitation has expired (valid for 7 days). Please request a new one from the administrator.");
        }

        // 로그인한 사람이 초대받은 이메일과 일치하는지 확인
        if (!invitation.getInviteeEmail().equalsIgnoreCase(loggedInEmail)) {
            throw new IllegalStateException("Please log in with the invited email account (" + invitation.getInviteeEmail() + ").");
        }

        Member member = memberRepository.findByEmail(loggedInEmail)
                .orElseThrow(() -> new IllegalArgumentException("Member information not found. Please sign up first."));

        // 회사 연결 및 USER 권한 부여
        member.setCompany(invitation.getCompany());
        member.setRole("USER");

        invitation.setStatus(CompanyInvitation.InvitationStatus.ACCEPTED);

        return invitation.getCompany().getBusinessName();
    }

    // ===================================================================================
    // 3. 조회
    // ===================================================================================

    /**
     * 초대 토큰으로 초대받은 이메일 주소를 조회한다.
     * 회원가입 화면에서 이메일 필드를 미리 채우기 위해 사용된다.
     */
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        return invitationRepository.findByToken(token)
                .map(CompanyInvitation::getInviteeEmail)
                .orElse(null);
    }
}