package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 로그인 정보 ---
    @Column(unique = true, nullable = false)
    private String email;               // 로그인 ID 겸 이메일 (중복 불가)

    @Column(nullable = false)
    private String password;            // BCrypt 암호화된 비밀번호

    // --- 개인 정보 ---
    private String firstName;           // 이름
    private String lastName;            // 성

    private String phoneCountryCode;    // 전화 국가 코드
    private String phoneNumber;         // 전화번호

    private String country;             // 거주 국가

    // --- 약관 동의 ---
    @Column(nullable = false)
    private boolean agreeTerms;         // 이용약관 동의 여부 (필수)

    @Column(nullable = false)
    private boolean marketingConsent;   // 마케팅 수신 동의 여부 (선택)

    // --- 권한 및 소속 ---
    private String role;                // 권한 (USER / ADMIN / SUPER_ADMIN)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;            // 소속 회사 (SUPER_ADMIN은 null)

    // --- 시스템 날짜 ---
    private LocalDate joinedDate;        // 가입일 (@PrePersist로 자동 설정)
    private LocalDateTime lastLoginDate; // 마지막 로그인 시각

    /** 최초 저장 시 가입일을 현재 날짜로 자동 설정 */
    @PrePersist
    public void prePersist() {
        this.joinedDate = LocalDate.now();
    }
}