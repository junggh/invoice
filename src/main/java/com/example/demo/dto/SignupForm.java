package com.example.demo.dto;

import com.example.demo.entity.Timezone;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SignupForm {

    // 가입 유형 (admin: 회사 관리자 신규 가입 / member: 초대 수락 후 가입)
    private String accountType;

    // === Step 1: 개인 정보 및 계정 정보 ===
    private String firstName;              // 이름
    private String lastName;              // 성

    private String personalEmail;         // 로그인 ID 역할 (Member.email에 매핑)
    private String password;              // 비밀번호
    private String checkPassword;         // 비밀번호 확인

    private String personalCountryCode;   // 개인 전화 국가 코드
    private String personalPhone;         // 개인 전화번호
    private String memberCountry;         // 거주 국가

    private boolean agreeTerms;           // 이용약관 동의
    private boolean marketingConsent;     // 마케팅 수신 동의

    // === Step 3: 회사 정보 ===
    private String businessName;          // 회사명
    private String abn;                   // 호주 사업자 등록 번호

    // ABN Lookup API로 자동 채워지는 히든 필드
    private String abnStatus;             // ABN 상태 (예: Active)
    private String addressPostcode;       // 사업장 우편번호
    private String addressState;          // 사업장 주
    private String entityName;            // 등록된 법인명
    private String entityTypeName;        // 사업체 유형
    private String gst;                   // GST 등록일

    private String industry;              // 업종
    private String country;               // 국가
    private Timezone companyTimezone;     // 시간대
    private String currency;              // 기본 통화
    private String financialYearDay;      // 회계연도 종료일
    private String financialYearMonth;    // 회계연도 종료월
    private String website;               // 회사 웹사이트
    private String companyEmail;          // 회사 대표 이메일
    private String companyCountryCode;    // 회사 전화 국가 코드
    private String companyPhone;          // 회사 대표 전화번호
    private String fax;                   // 팩스 번호

    // 초대 링크를 통한 가입 시 URL 파라미터로 전달되는 초대 토큰
    private String token;
}