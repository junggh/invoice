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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long processSignup(SignupForm form) {
        // 1. 중복 검증 (ID)
        if (memberRepository.existsByEmail(form.getPersonalEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        // 공통 회원(Member) 객체 생성 및 기본 정보 세팅
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

        // 2. 가입 유형에 따른 분기 처리
        if ("member".equals(form.getAccountType())) {
            // 일반 직원(Member) 가입: 회사 정보 없음, 권한은 USER
            member.setRole("USER");
            member.setCompany(null);
        } else {
            // 관리자(Admin) 가입: 회사 생성 필요
            if (form.getAbn() != null && !form.getAbn().isEmpty() && companyRepository.existsByAbn(form.getAbn())) {
                throw new IllegalArgumentException("ABN already exists.");
            }

            Company company = new Company();
            company.setBusinessName(form.getBusinessName());
            company.setAbn(form.getAbn());
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

        // 3. 회원(Member) 저장
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

    // [추가된 부분] 로그인 성공 시 시간 업데이트 로직
    @Transactional // 읽기/쓰기가 가능하도록 트랜잭션을 새로 엽니다.
    public void updateLoginAndActivityDates(String email) {
        memberRepository.findByEmail(email).ifPresent(member -> {
            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

            // 1. 멤버 마지막 로그인 시간 갱신
            member.setLastLoginDate(nowUtc);

            // 2. 회사 마지막 활동 시간 갱신
            // @Transactional 안에서 실행되므로 LazyInitializationException이 발생하지 않습니다!
            if (member.getCompany() != null) {
                Company company = member.getCompany();
                company.setLastActiveDate(nowUtc);
            }
        });
    }
}