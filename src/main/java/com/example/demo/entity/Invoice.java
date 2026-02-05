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
// 회사ID + 인보이스번호 조합이 유니크해야 함 (회사별로 번호 따로 채번)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_invoice_number",
                        columnNames = {"company_id", "invoice_number"}
                )
        }
)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부 노출용 ID (URL에 사용)
    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(nullable = false)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // --- [스냅샷] 발행 시점의 고객 정보 저장 (고객 정보가 변경되어도 인보이스는 유지) ---
    private String customerName;
    private String customerCompanyName;
    private String customerEmail;
    private String customerBillTo;
    private String customerCurrency;

    @PrePersist
    public void generateUuid() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }
}
