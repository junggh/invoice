package com.example.demo.security;

import com.example.demo.entity.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security의 UserDetails 구현체.
 * Member 엔티티를 감싸 Spring Security 인증 컨텍스트에서 사용할 수 있도록 한다.
 */
public class CustomUserDetails implements UserDetails {

    private final Member member;

    public CustomUserDetails(Member member) {
        this.member = member;
    }

    /** 컨트롤러에서 인증된 사용자의 Member 엔티티를 꺼낼 때 사용 */
    public Member getMember() {
        return member;
    }

    /** Member의 role 필드를 GrantedAuthority로 변환 (예: "ADMIN" → SimpleGrantedAuthority("ADMIN")) */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(member.getRole()));
    }

    @Override
    public String getPassword() {
        return member.getPassword();
    }

    /** 로그인 ID로 이메일을 사용 */
    @Override
    public String getUsername() {
        return member.getEmail();
    }

    // 계정 만료/잠금/비밀번호 만료/활성화 여부는 모두 정상(true)으로 처리
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}