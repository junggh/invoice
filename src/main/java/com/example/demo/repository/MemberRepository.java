package com.example.demo.repository;

import com.example.demo.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // ID 중복 체크 및 로그인용
    Optional<Member> findByUsername(String username);
    boolean existsByUsername(String username);
}