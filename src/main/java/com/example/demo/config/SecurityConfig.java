package com.example.demo.config;

import com.example.demo.service.AuthService;
import com.example.demo.service.CompanyInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthService authService;
    private final CompanyInvitationService invitationService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/super-admin/**").hasAuthority("SUPER_ADMIN") // 개발자 전용
                        // 1. 누구나 접근 가능한 페이지 (로그인, 회원가입, 정적 리소스, API)
                        .requestMatchers(
                                "/login", "/signup",           // 페이지
                                "/css/**", "/js/**", "/data/**", "/images/**", // 정적 리소스
                                "/api/auth/**",                 // 회원가입용 API (중복체크, ABN 등)
                                "/invitations/accept"           // 초대 링크 허용
                        ).permitAll()
                        // 2. 그 외 모든 페이지는 로그인 필요
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")             // 우리가 만든 로그인 페이지 경로
                        .loginProcessingUrl("/login")    // HTML Form의 action 경로 (스프링이 알아서 처리함)
                        .usernameParameter("email")     // 로그인 폼의 name="email"을 아이디로 인식
                        .successHandler((request, response, authentication) -> {
                            String email = authentication.getName();
                            // [수정된 부분] 트랜잭션이 보장되는 서비스 메서드 호출!
                            authService.updateLoginAndActivityDates(email);

                            // 폼에서 넘어온 토큰이 있다면 즉시 초대 수락(회사 연결) 처리
                            String token = request.getParameter("token");
                            if (token != null && !token.isEmpty()) {
                                try {
                                    invitationService.acceptInvitation(token, email);
                                } catch (Exception e) {
                                    System.out.println("invite matching failed: " + e.getMessage());
                                }
                            }

                            boolean isSuperAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("SUPER_ADMIN"));

                            // 권한에 따라 리다이렉트할 페이지 분기
                            if (isSuperAdmin) {
                                response.sendRedirect("/super-admin/companies"); // 개발자는 전체 회사 목록으로
                            } else {
                                response.sendRedirect("/invoices"); // 일반 유저는 인보이스 대시보드로
                            }
                        })
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
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder(); // 강력한 암호화 방식
//    }
}