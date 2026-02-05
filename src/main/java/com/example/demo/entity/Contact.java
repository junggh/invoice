package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Contact {

    @Id // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 기본 정보 ---
    private String name;        // 고객명 (담당자)
    private String companyName; // 회사명
    private String email;       // 이메일
    private String phoneNumber; // 연락처

    // --- 청구 정보 ---
    private String currency;    // 통화 단위 (예: USD, KRW)
    private String billTo;      // 청구 주소

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
