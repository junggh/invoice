package com.example.demo.repository;

import com.example.demo.entity.CompanyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {

    /** 초대 토큰(UUID)으로 초대 정보 조회 — 초대 수락 링크 처리 시 사용 */
    Optional<CompanyInvitation> findByToken(String token);
}