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

    @Id // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber; // 예: INV-00001

    private String reference; // 참조 번호

    private String salesPerson; // 담당 사원

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status; // 상태 (Draft, Paid 등)

    private BigDecimal total; // 총 금액

    private BigDecimal balanceDue; // 남은 금액

    private LocalDate date; // 발행일

    private LocalDate dueDate; // 납부 기한

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // 발행 당시 스냅샷
    private String customerName;
    private String customerCurrency;
    private String customerBillTo;
    private String customerCompanyName;
    private String customerPhoneNumber;

}
