package com.example.demo.service;

import com.example.demo.dto.SignupForm;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long processSignup(SignupForm form) {
        // 1. 중복 검증 (ID, ABN 등)
        if (memberRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("Username is already taken.");
        }
        if (form.getAbn() != null && !form.getAbn().isEmpty() && companyRepository.existsByAbn(form.getAbn())) {
            throw new IllegalArgumentException("ABN already exists.");
        }

        // 2. 회사(Company) 저장
        Company company = new Company();
        company.setBusinessName(form.getBusinessName());
        company.setAbn(form.getAbn());

        // [추가] ABN 추가 정보 저장
        company.setEntityName(form.getEntityName());
        company.setEntityTypeName(form.getEntityTypeName());
        company.setAbnStatus(form.getAbnStatus());
        company.setAddressPostcode(form.getAddressPostcode());
        company.setAddressState(form.getAddressState());
        company.setGst(form.getGst());

        // 상세 정보
        company.setIndustry(form.getIndustry());
        company.setCountry(form.getCountry());
        company.setTimezone(form.getCompanyTimezone());
        company.setCurrency(form.getCurrency());
        company.setFinancialYearDay(form.getFinancialYearDay());
        company.setFinancialYearMonth(form.getFinancialYearMonth());
        company.setWebsite(form.getWebsite());

        // 회사 연락처
        company.setCompanyEmail(form.getCompanyEmail());
        String fullCompanyPhone = combinePhoneNumber(form.getCompanyCountryCode(), form.getCompanyPhone());
        company.setCompanyPhone(fullCompanyPhone);
        company.setFax(form.getFax());

        Company savedCompany = companyRepository.save(company);

        // 3. 회원(Member) 저장
        Member member = new Member();
        member.setUsername(form.getUsername());

        // 비밀번호 암호화
        member.setPassword(passwordEncoder.encode(form.getPassword()));

        member.setFirstName(form.getFirstName());
        member.setMiddleName(form.getMiddleName());
        member.setLastName(form.getLastName());
        member.setEmail(form.getPersonalEmail()); // 개인 이메일
        String fullPhone = combinePhoneNumber(form.getPersonalCountryCode(), form.getPersonalPhone());
        member.setPhone(fullPhone);
        member.setTimezone(form.getMemberTimezone());
        member.setAgreeTerms(form.isAgreeTerms());
        member.setMarketingConsent(form.isMarketingConsent());
        member.setRole("ADMIN"); // 최초 가입자는 관리자 권한 부여

        // [핵심] 연관 관계 설정
        member.setCompany(savedCompany);

        memberRepository.save(member);

        return member.getId();
    }

    private String combinePhoneNumber(String code, String rawNumber) {
        // 1. 하이픈, 공백 제거
        String cleanNumber = rawNumber.replaceAll("[^0-9]", "");

        // 2. 맨 앞 '0' 제거 (한국 010 -> 10, 호주 04xx -> 4xx)
        // 국가번호가 있을 때만 제거하는 것이 안전합니다.
        if (cleanNumber.startsWith("0")) {
            cleanNumber = cleanNumber.substring(1);
        }

        // 3. 합치기 (+82 + 1012345678)
        return code + cleanNumber;
    }

    // [추가] 아이디 중복 확인용 메서드
    public boolean isUsernameAvailable(String username) {
        return !memberRepository.existsByUsername(username);
    }
    // [추가] ABN 중복 확인용
    public boolean isAbnAvailable(String abn) {
        return !companyRepository.existsByAbn(abn);
    }
}