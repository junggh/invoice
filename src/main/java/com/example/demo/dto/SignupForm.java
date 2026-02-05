package com.example.demo.dto;

import com.example.demo.entity.Timezone;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SignupForm {

    // === Step 1: 기본 정보 & 개인 정보 ===
    private String businessName;   // 회사명
    private String abn;            // ABN

    // ABN Lookup에서 받아올 Hidden 데이터
    private String abnStatus;
    private String addressPostcode;
    private String addressState;
    private String entityName;     // businessName과 별도로 원본 EntityName 저장
    private String entityTypeName;
    private String gst;

    private String firstName;
    private String middleName;     // Optional
    private String lastName;

    private String personalEmail;  // 개인 이메일 (연락용)
    private String personalCountryCode;
    private String personalPhone;  // 개인 휴대폰
    private Timezone memberTimezone;

    // 약관 동의 (필요시 로직 추가)
    private boolean agreeTerms;
    private boolean marketingConsent;

    // === Step 3: 로그인 계정 정보 ===
    private String username;       // 로그인 ID
    private String password;       // 비밀번호
    private String checkPassword;  // 비밀번호 확인

    // === Step 4: 회사 상세 정보 ===
    private String industry;           // 업종
    private String country;
    private Timezone companyTimezone;
    private String currency;

    // Financial Year (예: 30 / June)
    private String financialYearDay;
    private String financialYearMonth;

    private String website;

    private String companyEmail;       // 회사 대표 이메일
    private String companyCountryCode;
    private String companyPhone;       // 회사 대표 전화번호
    private String fax;
}