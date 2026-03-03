package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class RecurringInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 아이템 상세 ---
    private Integer quantity;
    private BigDecimal discount;
    @Enumerated(EnumType.STRING)
    private DiscountType discountType = DiscountType.AMOUNT; // 할인 방식 (기본값: 금액 할인)
    @Enumerated(EnumType.STRING)
    private GstCode gstCode = GstCode.GST_ON_INCOME; // 기본값: 10%
    private BigDecimal amount;
    private BigDecimal taxAmount;

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_invoice_id")
    private RecurringInvoice recurringInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}