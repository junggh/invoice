package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class InvoiceItem {

    @Id // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 아이템 상세 ---
    private Integer quantity;    // 수량
    private BigDecimal discount; // 개별 할인
    private BigDecimal amount;   // 최종 금액 ( (단가 * 수량) - 할인 )

    // --- 연관 관계 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
