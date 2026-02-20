package com.example.demo.service;

import com.example.demo.dto.SubscriptionRequest;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.entity.PlanType;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    private static final String PLAN_ID_LITE  = "P-48N26722YD0754634NGHLV5Y";
    private static final String PLAN_ID_BASIC = "P-89690056N4435424SNGF5RTQ";
    private static final String PLAN_ID_PRO   = "P-1BY31527JL143202MNGHLXEQ";

    @Transactional
    public void activateSubscription(String email, SubscriptionRequest request) {
        Member member = memberRepository.findByEmail(email) // 기존에 구현하신 메서드 사용 (혹은 findByEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 권한 체크 (안전장치)
        if (!"ADMIN".equals(member.getRole())) {
            throw new IllegalStateException("관리자만 구독 설정을 변경할 수 있습니다.");
        }

        // 2. 회사 정보 가져오기
        Company company = member.getCompany();
        if (company == null) {
            throw new IllegalStateException("소속된 회사가 없습니다.");
        }

        String requestPlanId = request.getPlanId();
        PlanType newPlan = null;

        if (PLAN_ID_LITE.equals(requestPlanId)) {
            newPlan = PlanType.LITE;
        } else if (PLAN_ID_BASIC.equals(requestPlanId)) {
            newPlan = PlanType.BASIC;
        } else if (PLAN_ID_PRO.equals(requestPlanId)) {
            newPlan = PlanType.PRO;
        } else {
            throw new IllegalArgumentException("유효하지 않은 플랜 ID입니다.");
        }

        // 3. 회사의 구독 정보 업데이트
        company.setSubscriptionId(request.getSubscriptionId());
        company.setPlan(newPlan);

        companyRepository.save(company);
    }

    // 회사의 현재 플랜 조회
    @Transactional(readOnly = true)
    public PlanType getCompanyPlan(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        if (member.getCompany() == null) {
            return null; // 회사가 없으면 기본 LITE
        }

        return member.getCompany().getPlan();
    }

    // 회사의 현재 PayPal 구독 ID 가져오기
    @Transactional(readOnly = true)
    public String getCompanySubscriptionId(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        if (member.getCompany() == null) return null;
        return member.getCompany().getSubscriptionId();
    }

    // 현재 접속자가 관리자인지 확인
    @Transactional(readOnly = true)
    public boolean isAdmin(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        return "ADMIN".equals(member.getRole());
    }
}