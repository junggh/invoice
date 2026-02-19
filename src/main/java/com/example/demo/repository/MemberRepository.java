package com.example.demo.repository;

import com.example.demo.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // Email 중복 체크 및 로그인용
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String personalEmail);
    // 특정 회사에 소속된 모든 멤버 찾기
    List<Member> findByCompanyId(Long companyId);

    // 특정 회사의 관리자(ADMIN) 찾기 (대표 이메일 표시용)
    Optional<Member> findFirstByCompanyIdAndRole(Long companyId, String role);

    // 특정 회사의 멤버 수 카운트
    int countByCompanyId(Long companyId);
}