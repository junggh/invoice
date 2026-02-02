package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String invoiceNumber; // 인보이스 번호 (예: INV-00001)

    private String reference;     // 참조 번호
    private String salesPerson;   // 담당자

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status; // 상태 (DRAFT, PAID...)

    // --- 금액 정보 ---
    private BigDecimal total;      // 총 청구 금액
    private BigDecimal balanceDue; // 미수금 (남은 금액)

    // --- 날짜 정보 ---
    private LocalDate issuedDate; // 발행일
    private LocalDate dueDate;    // 납부 기한

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    // --- [스냅샷] 발행 시점의 고객 정보 저장 (고객 정보가 변경되어도 인보이스는 유지) ---
    private String customerName;
    private String customerCompanyName;
    private String customerEmail;
    private String customerBillTo;
    private String customerCurrency;
}
