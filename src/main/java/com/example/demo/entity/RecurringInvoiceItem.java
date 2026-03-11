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

    // --- 항목 상세 ---
    private Integer quantity;           // 수량

    private BigDecimal discount;        // 개별 할인 (금액 또는 비율)

    @Enumerated(EnumType.STRING)
    private DiscountType discountType = DiscountType.AMOUNT; // 할인 방식 (기본: 금액 할인)

    @Enumerated(EnumType.STRING)
    private GstCode gstCode = GstCode.GST_ON_INCOME;        // 세금 코드 (기본: 10%)

    private BigDecimal amount;          // 최종 금액 ( (단가 × 수량) − 할인 )
    private BigDecimal taxAmount;       // 세금액

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_invoice_id")
    private RecurringInvoice recurringInvoice; // 소속 반복 인보이스 템플릿

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;            // 연결된 상품 (단가, 상품명 등 참조)
}