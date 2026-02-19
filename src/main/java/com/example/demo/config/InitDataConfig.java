package com.example.demo.config;

import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class InitDataConfig {

    @Bean
    public CommandLineRunner initSuperAdmin(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // 개발자 이메일이 DB에 없는 경우에만 계정 생성
            String superAdminEmail = "dev@myerp.com";

            if (memberRepository.findByEmail(superAdminEmail).isEmpty()) {
                Member superAdmin = new Member();
                superAdmin.setEmail(superAdminEmail);
                superAdmin.setPassword(passwordEncoder.encode("1234")); // 실제 사용할 비밀번호
                superAdmin.setFirstName("Super");
                superAdmin.setLastName("Admin");
                superAdmin.setRole("SUPER_ADMIN"); // 권한 부여

                // 엔티티에서 nullable = false 로 지정된 필수 값들 세팅
                superAdmin.setAgreeTerms(true);
                superAdmin.setMarketingConsent(false);

                // 핵심: 소속 회사 없음!
                superAdmin.setCompany(null);

                memberRepository.save(superAdmin);
                System.out.println("✅ 개발자(SUPER_ADMIN) 계정이 자동 생성되었습니다.");
            }
        };
    }
}