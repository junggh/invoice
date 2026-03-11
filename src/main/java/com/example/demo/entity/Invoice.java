package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// 회사별 인보이스 번호 유니크 제약 (전체 중복이 아닌 회사 내 중복만 방지)
@Entity
@Getter @Setter
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

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;               // 외부 공개 URL용 식별자 (DB id 노출 방지)

    // --- 기본 정보 ---
    @Column(nullable = false)
    private String invoiceNumber;      // 인보이스 번호 (예: INV-00001, 회사 내 순번)

    private String reference;          // 참조 번호 (거래처 PO 번호 등)
    private String salesPerson;        // 담당 영업사원

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;      // 인보이스 상태 (DRAFT → IN_REVIEW → APPROVED → UNPAID → PAID / OVERDUE)

    // --- 금액 ---
    private BigDecimal subtotal;       // 공급가액 합계 (세전)
    private BigDecimal tax;            // 세금 합계
    private BigDecimal total;          // 총 청구 금액
    private BigDecimal balanceDue;     // 미수금 (부분 납부 반영 후 남은 금액)

    @Enumerated(EnumType.STRING)
    private TaxType taxType = TaxType.TAX_EXCLUSIVE; // 세금 계산 방식 (기본: 세금 별도)

    // --- 날짜 ---
    private LocalDate issuedDate;      // 발행일
    private LocalDate dueDate;         // 납부 기한

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;           // 연결된 Contact (null이면 수동 입력)

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;           // 소속 회사

    // --- 고객 정보 스냅샷 (발행 시점의 Contact 정보를 복사하여 저장) ---
    // Contact가 이후 수정/삭제되어도 인보이스에는 원본 고객 정보가 유지된다.
    private boolean manualContact;          // true: 폼에서 직접 입력, false: Contact에서 선택

    private String customerName;            // 고객 담당자명
    private String customerCompanyName;     // 고객 회사명
    private String customerEmail;           // 고객 이메일
    private String customerBillTo;          // 고객 청구지 주소
    private String customerCurrency;        // 고객 통화

    /** 최초 저장 시 UUID를 자동 생성 */
    @PrePersist
    public void generateUuid() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }
}
