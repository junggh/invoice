package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 로그인 정보 ---
    @Column(unique = true, nullable = false)
    private String email;    // 개인 이메일 및 ID

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호

    // --- 개인 정보 ---
    private String firstName;
    private String lastName;

    private String phoneCountryCode;
    private String phoneNumber;

    private String country;

    // --- 약관 및 마케팅 동의 여부 ---
    @Column(nullable = false)
    private boolean agreeTerms;       // 이용약관 동의 (필수)

    @Column(nullable = false)
    private boolean marketingConsent; // 마케팅 수신 동의 (선택)

    // --- 연관 관계 (N:1) ---
    // 회원은 반드시 하나의 회사에 소속됨
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // 권한 (USER, ADMIN 등 - 추후 확장용)
    private String role;

    // 구독 정보
    private String subscriptionId; // PayPal에서 받은 구독 ID
    private boolean isPremium;     // 유료 회원 여부
}