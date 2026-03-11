package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 회사별 템플릿 번호 유니크 제약 (회사 내 중복만 방지)
@Entity
@Getter @Setter
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_template_number",
                        columnNames = {"company_id", "template_number"}
                )
        }
)
public class RecurringInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;                // 외부 공개 URL용 식별자 (DB id 노출 방지)

    // --- 기본 정보 ---
    @Column(nullable = false)
    private String templateNumber;      // 템플릿 번호 (예: INVT-00001, 회사 내 순번)

    @Enumerated(EnumType.STRING)
    private RecurringStatus status;     // 상태 (DRAFT → ACTIVE / PAUSED / COMPLETED)

    private String salesPerson;         // 담당 영업사원
    private String reference;           // 참조 번호 (거래처 PO 번호 등)

    // --- 금액 ---
    private BigDecimal subtotal;        // 공급가액 합계 (세전)
    private BigDecimal tax;             // 세금 합계
    private BigDecimal total;           // 총 청구 금액

    @Enumerated(EnumType.STRING)
    private TaxType taxType = TaxType.TAX_EXCLUSIVE; // 세금 계산 방식 (기본: 세금 별도)

    // --- 스케줄링 설정 ---
    @Enumerated(EnumType.STRING)
    private RecurringFrequency frequency;       // 발행 주기 (DAILY / WEEKLY / MONTHLY / YEARLY)

    @Column(nullable = false)
    private int frequencyInterval = 1;          // 주기 간격 (예: 2이면 격주 또는 2개월마다)

    @Column(nullable = false)
    private Integer dueDateDays = 7;            // 생성될 인보이스의 납기 여유 기간 (발행일 기준 N일 후)

    private boolean autoSend;                   // 인보이스 생성 시 고객에게 자동 이메일 발송 여부

    // --- 실행 날짜 ---
    private LocalDate startDate;                // 최초 발행 시작일
    private LocalDate endDate;                  // 발행 종료일 (설정하지 않으면 무기한)
    private LocalDate nextInvoiceDate;          // 다음 발행 예정일 (스케줄러 조회 기준)
    private LocalDate lastIssuedDate;           // 가장 최근 발행일

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;                    // 연결된 고객 Contact

    @OneToMany(mappedBy = "recurringInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringInvoiceItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;                    // 소속 회사

    /**
     * 현재 nextInvoiceDate에 frequency와 interval을 적용하여 다음 발행일로 전진시킨다.
     * nextInvoiceDate가 null이면 startDate로 초기화한다.
     */
    public void calculateNextDate() {
        if (this.nextInvoiceDate == null) {
            this.nextInvoiceDate = this.startDate;
            return;
        }

        // 최소 간격 1 보장 (0 이하 방지)
        int interval = Math.max(this.frequencyInterval, 1);

        this.nextInvoiceDate = switch (this.frequency) {
            case DAILY   -> this.nextInvoiceDate.plusDays(interval);
            case WEEKLY  -> this.nextInvoiceDate.plusWeeks(interval);
            case MONTHLY -> this.nextInvoiceDate.plusMonths(interval);
            case YEARLY  -> this.nextInvoiceDate.plusYears(interval);
        };
    }

    /** 최초 저장 시 UUID를 자동 생성 */
    @PrePersist
    public void generateUuid() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }
}