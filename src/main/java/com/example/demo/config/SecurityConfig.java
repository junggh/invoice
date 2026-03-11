package com.example.demo.config;

import com.example.demo.service.AuthService;
import com.example.demo.service.CompanyInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 * 접근 권한 규칙, 로그인/로그아웃 처리, CSRF 설정, 403 예외 처리를 담당한다.
 */
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
                        // SUPER_ADMIN 전용 경로
                        .requestMatchers("/super-admin/**").hasAuthority("SUPER_ADMIN")
                        // ADMIN 이상 접근 가능 경로
                        .requestMatchers("/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        // 인증 없이 접근 가능한 경로 (로그인, 회원가입, 정적 리소스, 공개 API)
                        .requestMatchers(
                                "/login", "/signup",                                    // 인증 페이지
                                "/css/**", "/js/**", "/data/**", "/images/**",          // 정적 리소스
                                "/api/auth/**",                                          // 회원가입용 공개 API (중복 체크, ABN 등)
                                "/invitations/accept",                                  // 초대 링크
                                "/public/**"                                            // 공개 인보이스 뷰
                        ).permitAll()
                        // 그 외 모든 경로는 로그인 필요
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")         // 로그인 폼의 name="email"을 아이디로 인식
                        .successHandler((request, response, authentication) -> {
                            String email = authentication.getName();
                            // 마지막 로그인 시각 및 회사 활동 시각 업데이트 (트랜잭션 보장)
                            authService.updateLoginAndActivityDates(email);

                            // 로그인 폼에 초대 토큰이 포함된 경우 즉시 초대 수락 처리
                            String tokenParam = null;
                            String tokenParamKey = null;
                            String token = request.getParameter("token");
                            if (token != null && !token.isEmpty()) {
                                try {
                                    String companyName = invitationService.acceptInvitation(token, email);
                                    tokenParam = "You have been connected to " + companyName + ".";
                                    tokenParamKey = "tokenSuccess";
                                } catch (Exception e) {
                                    tokenParam = e.getMessage();
                                    tokenParamKey = "tokenError";
                                }
                            }

                            // SUPER_ADMIN은 회사 목록 페이지로, 그 외는 인보이스 목록으로 리다이렉트
                            boolean isSuperAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("SUPER_ADMIN"));

                            String redirectUrl = isSuperAdmin ? "/super-admin/companies" : "/invoices";
                            if (tokenParam != null) {
                                redirectUrl += "?" + tokenParamKey + "=" + URLEncoder.encode(tokenParam, StandardCharsets.UTF_8);
                            }
                            response.sendRedirect(redirectUrl);
                        })
                        .failureHandler((request, response, exception) -> {
                            String email = request.getParameter("email");
                            // 로그인 실패 시 입력했던 이메일을 세션에 저장하여 폼에 재표시
                            request.getSession().setAttribute("lastEmail", email);
                            response.sendRedirect("/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // /api/auth/**는 비인증 공개 API이므로 CSRF 검사에서 제외
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**"))
                .exceptionHandling(ex -> ex
                        // 403 접근 거부 시 에러 페이지 대신 이전 페이지로 리다이렉트
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String referer = request.getHeader("Referer");
                            String redirectUrl = (referer != null && !referer.isEmpty()) ? referer : "/invoices";
                            redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "forbidden=true";
                            response.sendRedirect(redirectUrl);
                        })
                );

        return http.build();
    }
}