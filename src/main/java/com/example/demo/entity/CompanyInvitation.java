package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class CompanyInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 초대하는 회사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 초대받을 직원의 이메일
    @Column(nullable = false)
    private String inviteeEmail;

    // URL에 포함될 고유 비밀 토큰 (예: a1b2c3d4-...)
    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status; // PENDING(대기중), ACCEPTED(수락됨), EXPIRED(만료됨)

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusDays(7); // 초대장 유효기간: 7일
        }
        if (this.status == null) {
            this.status = InvitationStatus.PENDING;
        }
    }

    public enum InvitationStatus {
        PENDING, ACCEPTED, EXPIRED
    }
}