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
public class RecurringInvoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String templateNumber;

    // --- 스케줄링 설정 ---
    @Enumerated(EnumType.STRING)
    private RecurringFrequency frequency; // 주기 (DAILY, WEEKLY...)

    // [추가] "몇" 주/달마다 인지 설정 (기본값 1)
    // 예: frequency=WEEKLY, frequencyInterval=2 -> "2주마다(격주)"
    @Column(nullable = false)
    private int frequencyInterval = 1;

    private LocalDate startDate;       // 시작일
    private LocalDate nextInvoiceDate; // 다음 예정일
    private LocalDate lastIssuedDate;  // [추가됨] 최근 발행일
    private LocalDate endDate;         // 종료일 (옵션)

    @Column(nullable = false)
    private Integer dueDateDays = 7;

    private boolean autoSend;

    @Enumerated(EnumType.STRING)
    private RecurringStatus status; // ACTIVE, PAUSED, COMPLETED

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    private String salesPerson;
    private String reference;
    private BigDecimal total;

    @OneToMany(mappedBy = "recurringInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringInvoiceItem> items = new ArrayList<>();

    // --- [핵심] 날짜 계산 로직 수정 ---
    public void calculateNextDate() {
        if (this.nextInvoiceDate == null) {
            this.nextInvoiceDate = this.startDate;
            return;
        }

        // 안전장치: 0이나 음수가 들어오면 1로 처리
        int interval = (this.frequencyInterval < 1) ? 1 : this.frequencyInterval;

        switch (this.frequency) {
            case DAILY -> this.nextInvoiceDate = this.nextInvoiceDate.plusDays(interval);
            case WEEKLY -> this.nextInvoiceDate = this.nextInvoiceDate.plusWeeks(interval);
            case MONTHLY -> this.nextInvoiceDate = this.nextInvoiceDate.plusMonths(interval);
            case YEARLY -> this.nextInvoiceDate = this.nextInvoiceDate.plusYears(interval);
        }
    }
}