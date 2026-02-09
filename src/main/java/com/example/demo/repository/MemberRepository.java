package com.example.demo.repository;

import com.example.demo.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // Email 중복 체크 및 로그인용
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String personalEmail);
}