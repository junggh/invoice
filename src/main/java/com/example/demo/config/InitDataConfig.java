package com.example.demo.config;

import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 애플리케이션 시작 시 초기 데이터를 생성하는 설정 클래스.
 * SUPER_ADMIN 계정이 존재하지 않을 경우 자동으로 생성한다.
 * 계정 정보는 application-secret.yml의 init.admin.* 값을 사용한다.
 */
@Configuration
public class InitDataConfig {

    @Value("${init.admin.email}")
    private String superAdminEmail;

    @Value("${init.admin.password}")
    private String superAdminPassword;

    /** 애플리케이션 시작 시 SUPER_ADMIN 계정이 없으면 자동 생성한다. */
    @Bean
    public CommandLineRunner initSuperAdmin(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (memberRepository.findByEmail(superAdminEmail).isEmpty()) {
                Member superAdmin = new Member();
                superAdmin.setEmail(superAdminEmail);
                superAdmin.setPassword(passwordEncoder.encode(superAdminPassword));
                superAdmin.setFirstName("Super");
                superAdmin.setLastName("Admin");
                superAdmin.setRole("SUPER_ADMIN");

                // nullable = false 컬럼에 대한 필수 기본값 설정
                superAdmin.setAgreeTerms(true);
                superAdmin.setMarketingConsent(false);

                // SUPER_ADMIN은 특정 회사에 소속되지 않음
                superAdmin.setCompany(null);

                memberRepository.save(superAdmin);
                System.out.println("✅ SUPER_ADMIN account auto created.");
            }
        };
    }
}