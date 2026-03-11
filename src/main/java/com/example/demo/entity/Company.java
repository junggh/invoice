package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 멀티테넌트 구조의 테넌트 루트 엔티티.
 * Member, Invoice, Contact, Product 등 모든 데이터는 Company를 기준으로 격리된다.
 */
@Entity
@Getter @Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 기본 정보 (회원가입 시 입력) ---
    @Column(nullable = false)
    private String businessName;            // 회사명

    @Column(unique = true)
    private String abn;                     // 호주 사업자 등록 번호 (중복 불가, 입력하지 않으면 null)

    // --- ABN API 연동 정보 (호주 ABN Lookup API 응답값) ---
    private String entityName;              // 등록된 법인명 (예: ABC Pty Ltd)
    private String entityTypeName;          // 사업체 유형 (예: Australian Private Company)
    private String abnStatus;              // ABN 상태 (예: Active)
    private String addressPostcode;        // 사업장 우편번호 (예: 2025)
    private String addressState;           // 사업장 주 (예: NSW)
    private String gst;                    // GST 등록일 (미등록 시 null)

    // --- 상세 설정 정보 (가입 후 설정 화면에서 입력) ---
    private String industry;               // 업종
    private String country;               // 국가
    @Enumerated(EnumType.STRING)
    private Timezone timezone;            // 시간대 (연체/반복 인보이스 스케줄러 기준 시각에 사용)
    private String currency;              // 기본 통화 (예: AUD, USD)

    private String financialYearDay;      // 회계연도 종료일 (예: "30")
    private String financialYearMonth;    // 회계연도 종료월 (예: "June")

    private String website;               // 회사 웹사이트

    // --- 회사 연락처 ---
    private String companyEmail;              // 회사 대표 이메일
    private String companyPhoneCountryCode;   // 회사 전화 국가 코드
    private String companyPhoneNumber;        // 회사 대표 전화번호
    private String fax;                       // 팩스 번호

    // --- 구독 및 결제 정보 ---
    @Enumerated(EnumType.STRING)
    private PlanType plan;                // 구독 플랜 (결제 전 null, 결제 후 LITE / BASIC / PRO)

    private String subscriptionId;        // PayPal 구독 ID (결제 전 null)

    // --- 시스템 날짜 ---
    private LocalDate joinedDate;         // 가입일 (@PrePersist로 자동 설정)
    private LocalDateTime lastActiveDate; // 마지막 활동 시각 (로그인 및 데이터 변경 시 갱신)

    /** 최초 저장 시 가입일을 현재 날짜로 자동 설정 */
    @PrePersist
    public void prePersist() {
        this.joinedDate = LocalDate.now();
    }
}
