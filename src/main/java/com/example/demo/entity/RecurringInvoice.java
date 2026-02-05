package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    // 외부 노출용 ID (URL에 사용)
    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(unique = true)
    private String templateNumber; // 템플릿 번호 (예: INVT-00001)

    @Enumerated(EnumType.STRING)
    private RecurringStatus status; // 상태 (ACTIVE, PAUSED...)

    // --- 스케줄링 설정 ---
    @Enumerated(EnumType.STRING)
    private RecurringFrequency frequency; // 주기 (DAILY, WEEKLY...)

    @Column(nullable = false)
    private int frequencyInterval = 1; // 간격 (예: 2주마다, 3개월마다)

    @Column(nullable = false)
    private Integer dueDateDays = 7;   // 생성될 인보이스의 납기일 여유 기간

    private boolean autoSend;          // 자동 발송 여부

    // --- 실행 날짜 ---
    private LocalDate startDate;       // 시작일
    private LocalDate endDate;         // 종료일 (옵션)
    private LocalDate nextInvoiceDate; // 다음 발행 예정일
    private LocalDate lastIssuedDate;  // 최근 발행일

    // --- 기본 정보 ---
    private String salesPerson;
    private String reference;
    private BigDecimal total;

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @OneToMany(mappedBy = "recurringInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringInvoiceItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // --- 도메인 로직: 다음 날짜 계산 ---
    public void calculateNextDate() {
        if (this.nextInvoiceDate == null) {
            this.nextInvoiceDate = this.startDate;
            return;
        }

        // 최소 간격 보장 (음수 방지)
        int interval = Math.max(this.frequencyInterval, 1);

        this.nextInvoiceDate = switch (this.frequency) {
            case DAILY   -> this.nextInvoiceDate.plusDays(interval);
            case WEEKLY  -> this.nextInvoiceDate.plusWeeks(interval);
            case MONTHLY -> this.nextInvoiceDate.plusMonths(interval);
            case YEARLY  -> this.nextInvoiceDate.plusYears(interval);
        };
    }

    @PrePersist
    public void generateUuid() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }
}