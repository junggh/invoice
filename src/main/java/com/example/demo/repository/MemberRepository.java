package com.example.demo.repository;

import com.example.demo.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // ===================================================================================
    // 1. 이메일 기반 조회 (로그인 및 중복 체크)
    // ===================================================================================

    /** 이메일로 회원 조회 — 로그인 인증 및 서비스 레이어 사용자 식별에 사용 */
    Optional<Member> findByEmail(String email);

    /** 이메일 중복 여부 확인 — 회원가입 유효성 검사용 */
    boolean existsByEmail(String personalEmail);

    // ===================================================================================
    // 2. 회사 기반 조회 (관리자 화면용)
    // ===================================================================================

    /** 특정 회사에 소속된 모든 멤버 조회 */
    List<Member> findByCompanyId(Long companyId);

    /** 특정 회사의 특정 역할(ADMIN 등) 중 첫 번째 멤버 조회 — 대표 이메일 표시용 */
    Optional<Member> findFirstByCompanyIdAndRole(Long companyId, String role);

    /** 특정 회사의 멤버 수 카운트 — 대시보드 통계용 */
    int countByCompanyId(Long companyId);
}