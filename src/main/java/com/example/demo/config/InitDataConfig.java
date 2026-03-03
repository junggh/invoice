package com.example.demo.config;

import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class InitDataConfig {

    @Value("${init.admin.email}")
    private String superAdminEmail;

    @Value("${init.admin.password}")
    private String superAdminPassword;

    @Bean
    public CommandLineRunner initSuperAdmin(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (memberRepository.findByEmail(superAdminEmail).isEmpty()) {
                Member superAdmin = new Member();
                superAdmin.setEmail(superAdminEmail);
                superAdmin.setPassword(passwordEncoder.encode(superAdminPassword));
                superAdmin.setFirstName("Super");
                superAdmin.setLastName("Admin");
                superAdmin.setRole("SUPER_ADMIN"); // 권한 부여

                // 엔티티에서 nullable = false 로 지정된 필수 값들 세팅
                superAdmin.setAgreeTerms(true);
                superAdmin.setMarketingConsent(false);

                // 핵심: 소속 회사 없음!
                superAdmin.setCompany(null);

                memberRepository.save(superAdmin);
                System.out.println("✅ SUPER_ADMIN account auto created.");
            }
        };
    }
}