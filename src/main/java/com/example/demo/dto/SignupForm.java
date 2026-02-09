package com.example.demo.dto;

import com.example.demo.entity.Timezone;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SignupForm {

    // === Step 1: 기본 정보 & 계정 정보 ===
    private String firstName;
    // middleName 삭제
    private String lastName;

    private String personalEmail;  // 로그인 ID 역할 (Member.email에 매핑)

    // Step 1으로 이동된 비밀번호 필드
    private String password;
    private String checkPassword;

    private String personalCountryCode;
    private String personalPhone;
    private Timezone memberTimezone;

    private boolean agreeTerms;
    private boolean marketingConsent;

    // === Step 3: 회사 정보 ===
    private String businessName;
    private String abn;

    // ABN Hidden Fields
    private String abnStatus;
    private String addressPostcode;
    private String addressState;
    private String entityName;
    private String entityTypeName;
    private String gst;

    private String industry;
    private String country;
    private Timezone companyTimezone;
    private String currency;
    private String financialYearDay;
    private String financialYearMonth;
    private String website;
    private String companyEmail;
    private String companyCountryCode;
    private String companyPhone;
    private String fax;
}