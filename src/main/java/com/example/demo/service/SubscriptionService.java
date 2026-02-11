package com.example.demo.service;

import com.example.demo.dto.SubscriptionRequest;
import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final MemberRepository memberRepository;

    @Transactional
    public void activateSubscription(String email, SubscriptionRequest request) {
        Member member = memberRepository.findByEmail(email) // 기존에 구현하신 메서드 사용 (혹은 findByEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        member.setSubscriptionId(request.getSubscriptionId());
        member.setPremium(true); // 유료 회원으로 전환

        memberRepository.save(member);
    }

    // 사용자가 현재 프리미엄 회원인지 확인
    @Transactional(readOnly = true)
    public boolean isPremiumUser(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        return member.isPremium();
    }
}