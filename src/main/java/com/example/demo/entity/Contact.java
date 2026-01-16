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

    private String name; // 고객 (거래처)

    private String currency; // 통화 단위

    private String billTo; // 청구 주소

    private String companyName; // 회사

    private String phoneNumber; // 연락처
}
