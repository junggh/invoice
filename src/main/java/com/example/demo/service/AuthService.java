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
        if (memberRepository.existsByEmail(form.getPersonalEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
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
        company.setCompanyPhoneCountryCode(form.getCompanyCountryCode()); //
        company.setCompanyPhoneNumber(cleanPhoneNumber(form.getCompanyPhone()));
        company.setFax(form.getFax());

        Company savedCompany = companyRepository.save(company);

        // 3. 회원(Member) 저장
        Member member = new Member();
        member.setEmail(form.getPersonalEmail());

        // 비밀번호 암호화
        member.setPassword(passwordEncoder.encode(form.getPassword()));

        member.setFirstName(form.getFirstName());
        member.setLastName(form.getLastName());
        member.setEmail(form.getPersonalEmail()); // 개인 이메일
        member.setPhoneCountryCode(form.getPersonalCountryCode());
        member.setPhoneNumber(cleanPhoneNumber(form.getPersonalPhone()));
        member.setCountry(form.getMemberCountry());
        member.setAgreeTerms(form.isAgreeTerms());
        member.setMarketingConsent(form.isMarketingConsent());
        member.setRole("ADMIN"); // 최초 가입자는 관리자 권한 부여

        // [핵심] 연관 관계 설정
        member.setCompany(savedCompany);

        memberRepository.save(member);

        return member.getId();
    }

    // 전화번호 포맷팅 메서드
    private String cleanPhoneNumber(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            return "";
        }

        // 1. 하이픈(-)을 공백( )으로 치환
        String str = rawNumber.replace("-", " ");

        // 2. 숫자, '+', 공백만 남기고 나머지 제거 (괄호 등 제거)
        str = str.replaceAll("[^0-9+ ]", "");

        // 3. 여러 개의 공백을 하나의 공백으로 치환 ("   " -> " ")
        str = str.replaceAll("\\s+", " ");

        // 4. 양끝 공백 제거
        return str.trim();
    }

    // [추가] 아이디 중복 확인용 메서드
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }
    // [추가] ABN 중복 확인용
    public boolean isAbnAvailable(String abn) {
        return !companyRepository.existsByAbn(abn);
    }
}