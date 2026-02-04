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
    private String companyPhone;       // Phone (회사 대표)
    private String fax;                // Fax
}
