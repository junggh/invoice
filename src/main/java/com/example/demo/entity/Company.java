package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 초기 가입 화면의 회사 정보 ---
    @Column(nullable = false)
    private String businessName;

    @Column(unique = true) // ABN은 고유해야 함
    private String abn;

    // [추가] ABN API 연동 정보
    private String entityName;      // API에서 받은 원본 Entity Name
    private String entityTypeName;  // 예: Australian Private Company
    private String abnStatus;       // 예: Active
    private String addressPostcode; // 예: 2025
    private String addressState;    // 예: NSW
    private String gst;             // 예: 2000-07-01 (Null일 수 있음)

    // --- Set your business 화면의 상세 정보 ---
    private String industry;           // Business industry
    private String country;            // Country
    @Enumerated(EnumType.STRING)
    private Timezone timezone;         // Timezone
    private String currency;           // Currency

    // Financial Year (화면에 입력칸이 2개로 보임: 예: 01 / July)
    private String financialYearDay;
    private String financialYearMonth;

    private String website;            // Website

    // --- 회사의 연락처 (직원 연락처와 구별됨) ---
    private String companyEmail;       // Email (회사 대표)
    private String companyPhoneCountryCode;
    private String companyPhoneNumber; // Phone (회사 대표)
    private String fax;                // Fax

    // --- 구독 및 결제 정보 ---
    // 초기 생성 시에는 null (결제 안 함)
    // 결제 후 LITE, BASIC, PRO 중 하나로 설정됨
    @Enumerated(EnumType.STRING)
    private PlanType plan;

    // PayPal 구독 ID (결제 전에는 null)
    private String subscriptionId;
}
