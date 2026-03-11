package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 암호화 설정.
 * SecurityConfig와 순환 의존성을 피하기 위해 별도 클래스로 분리되어 있다.
 */
@Configuration
public class PasswordEncoderConfig {

    /** BCrypt 알고리즘 기반 PasswordEncoder 빈 등록 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}