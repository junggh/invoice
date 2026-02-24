package com.example.demo.repository;

import com.example.demo.entity.CompanyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {
    Optional<CompanyInvitation> findByToken(String token);
}