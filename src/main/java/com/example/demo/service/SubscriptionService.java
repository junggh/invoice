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

    // PayPal 플랜 ID 상수 (각 플랜과 PayPal에 등록된 Plan ID를 매핑)
    private static final String PLAN_ID_LITE  = "P-48N26722YD0754634NGHLV5Y";
    private static final String PLAN_ID_BASIC = "P-89690056N4435424SNGF5RTQ";
    private static final String PLAN_ID_PRO   = "P-1BY31527JL143202MNGHLXEQ";

    // ===================================================================================
    // 1. 구독 활성화
    // ===================================================================================

    /**
     * PayPal 결제 완료 후 구독 활성화.
     * 요청의 planId를 내부 PlanType으로 변환하고, 회사의 plan과 subscriptionId를 업데이트한다.
     * ADMIN만 구독 설정을 변경할 수 있다.
     */
    @Transactional
    public void activateSubscription(String email, SubscriptionRequest request) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!"ADMIN".equals(member.getRole())) {
            throw new IllegalStateException("Only administrators can change subscription settings.");
        }

        Company company = member.getCompany();
        if (company == null) {
            throw new IllegalStateException("No company affiliation found.");
        }

        // PayPal Plan ID를 내부 PlanType으로 변환
        String requestPlanId = request.getPlanId();
        PlanType newPlan = null;

        if (PLAN_ID_LITE.equals(requestPlanId)) {
            newPlan = PlanType.LITE;
        } else if (PLAN_ID_BASIC.equals(requestPlanId)) {
            newPlan = PlanType.BASIC;
        } else if (PLAN_ID_PRO.equals(requestPlanId)) {
            newPlan = PlanType.PRO;
        } else {
            throw new IllegalArgumentException("Invalid plan ID.");
        }

        company.setSubscriptionId(request.getSubscriptionId());
        company.setPlan(newPlan);
        companyRepository.save(company);
    }

    // ===================================================================================
    // 2. 구독 정보 조회
    // ===================================================================================

    /** 로그인한 사용자의 회사 현재 플랜을 조회한다. 회사가 없으면 null을 반환한다. */
    @Transactional(readOnly = true)
    public PlanType getCompanyPlan(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (member.getCompany() == null) return null;
        return member.getCompany().getPlan();
    }

    /** 로그인한 사용자의 회사 PayPal 구독 ID를 조회한다. 회사가 없으면 null을 반환한다. */
    @Transactional(readOnly = true)
    public String getCompanySubscriptionId(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (member.getCompany() == null) return null;
        return member.getCompany().getSubscriptionId();
    }

    /** 로그인한 사용자가 ADMIN 권한인지 확인한다. */
    @Transactional(readOnly = true)
    public boolean isAdmin(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return "ADMIN".equals(member.getRole());
    }
}