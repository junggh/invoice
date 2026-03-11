package com.example.demo.service;

import com.example.demo.dto.SignupForm;
import com.example.demo.entity.Company;
import com.example.demo.entity.Member;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // ===================================================================================
    // 1. 회원가입
    // ===================================================================================

    /**
     * 회원가입 처리. 이메일 중복 확인 후 accountType에 따라 분기한다.
     * - "member" 타입: 회사 없이 USER 권한으로 가입 (초대 수락 후 회사에 연결됨)
     * - "admin" 타입: 회사를 새로 생성하고 ADMIN 권한으로 가입
     */
    @Transactional
    public Long processSignup(SignupForm form) {
        if (memberRepository.existsByEmail(form.getPersonalEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        Member member = new Member();
        member.setEmail(form.getPersonalEmail());
        member.setPassword(passwordEncoder.encode(form.getPassword()));
        member.setFirstName(form.getFirstName());
        member.setLastName(form.getLastName());
        member.setPhoneCountryCode(form.getPersonalCountryCode());
        member.setPhoneNumber(cleanPhoneNumber(form.getPersonalPhone()));
        member.setCountry(form.getMemberCountry());
        member.setAgreeTerms(form.isAgreeTerms());
        member.setMarketingConsent(form.isMarketingConsent());

        if ("member".equals(form.getAccountType())) {
            // 일반 직원(USER) 가입: 회사 정보 없음, 초대 수락 후 연결됨
            member.setRole("USER");
            member.setCompany(null);
        } else {
            // 관리자(ADMIN) 가입: 회사 신규 생성
            if (form.getAbn() != null && !form.getAbn().isEmpty() && companyRepository.existsByAbn(form.getAbn())) {
                throw new IllegalArgumentException("ABN already exists.");
            }

            Company company = new Company();
            company.setBusinessName(form.getBusinessName());

            // 빈 문자열 ABN은 null로 저장 (unique 제약 위반 방지)
            String inputAbn = form.getAbn();
            if (inputAbn != null && inputAbn.trim().isEmpty()) {
                company.setAbn(null);
            } else {
                company.setAbn(inputAbn);
            }
            company.setEntityName(form.getEntityName());
            company.setEntityTypeName(form.getEntityTypeName());
            company.setAbnStatus(form.getAbnStatus());
            company.setAddressPostcode(form.getAddressPostcode());
            company.setAddressState(form.getAddressState());
            company.setGst(form.getGst());
            company.setIndustry(form.getIndustry());
            company.setCountry(form.getCountry());
            company.setTimezone(form.getCompanyTimezone());
            company.setCurrency(form.getCurrency());
            company.setFinancialYearDay(form.getFinancialYearDay());
            company.setFinancialYearMonth(form.getFinancialYearMonth());
            company.setWebsite(form.getWebsite());
            company.setCompanyEmail(form.getCompanyEmail());
            company.setCompanyPhoneCountryCode(form.getCompanyCountryCode());
            company.setCompanyPhoneNumber(cleanPhoneNumber(form.getCompanyPhone()));
            company.setFax(form.getFax());

            Company savedCompany = companyRepository.save(company);

            member.setRole("ADMIN");
            member.setCompany(savedCompany);
        }

        memberRepository.save(member);
        return member.getId();
    }

    /**
     * 전화번호 포맷 정규화.
     * 하이픈을 공백으로 변환하고, 숫자·'+'·공백 외의 문자를 제거하며 중복 공백을 정리한다.
     */
    private String cleanPhoneNumber(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            return "";
        }
        String str = rawNumber.replace("-", " ");
        str = str.replaceAll("[^0-9+ ]", "");
        str = str.replaceAll("\\s+", " ");
        return str.trim();
    }

    // ===================================================================================
    // 2. 유효성 검사
    // ===================================================================================

    /** 이메일 사용 가능 여부 확인. 사용 가능하면 true를 반환한다. */
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }

    /** ABN 사용 가능 여부 확인. 사용 가능하면 true를 반환한다. */
    public boolean isAbnAvailable(String abn) {
        return !companyRepository.existsByAbn(abn);
    }

    // ===================================================================================
    // 3. 로그인 이후 처리
    // ===================================================================================

    /**
     * 로그인 성공 시 Member의 lastLoginDate와 Company의 lastActiveDate를 현재 시각(UTC)으로 갱신한다.
     */
    @Transactional
    public void updateLoginAndActivityDates(String email) {
        memberRepository.findByEmail(email).ifPresent(member -> {
            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            member.setLastLoginDate(nowUtc);
            if (member.getCompany() != null) {
                member.getCompany().setLastActiveDate(nowUtc);
            }
        });
    }
}