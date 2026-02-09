package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 1. 누구나 접근 가능한 페이지 (로그인, 회원가입, 정적 리소스, API)
                        .requestMatchers(
                                "/login", "/signup",           // 페이지
                                "/css/**", "/js/**", "/images/**", // 정적 리소스
                                "/api/auth/**"                 // 회원가입용 API (중복체크, ABN 등)
                        ).permitAll()
                        // 2. 그 외 모든 페이지는 로그인 필요
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")             // 우리가 만든 로그인 페이지 경로
                        .loginProcessingUrl("/login")    // HTML Form의 action 경로 (스프링이 알아서 처리함)
                        .usernameParameter("email")     // 로그인 폼의 name="email"을 아이디로 인식
                        .defaultSuccessUrl("/invoices", true) // 로그인 성공 시 이동할 곳
                        .failureHandler((request, response, exception) -> {
                            String email = request.getParameter("email");
                            // 세션에 입력했던 아이디를 잠시 저장 ('lastUsername' 이라는 이름으로)
                            request.getSession().setAttribute("lastEmail", email);
                            // 에러 파라미터와 함께 리다이렉트
                            response.sendRedirect("/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .csrf(csrf -> csrf.disable()); // 개발 편의를 위해 CSRF 잠시 끔 (필요 시 켜야 함)

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 강력한 암호화 방식
    }
}