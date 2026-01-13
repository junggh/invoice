package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class InvoiceMember {

    @Id // 기본키 (PK)
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto Increment
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber; // 예: INV-00001

    private String contact; // 고객 이름 (예: June Young)

    private String phoneNumber; // 전화번호

    private String billTo; // 청구 주소

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status; // 상태 (Draft, Paid 등)

    private BigDecimal total; // 총 금액

    private BigDecimal balanceDue; // 남은 금액

    private LocalDate date; // 발행일

    private LocalDate dueDate; // 납부 기한
}
