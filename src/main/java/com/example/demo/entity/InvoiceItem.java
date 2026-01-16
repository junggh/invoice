package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantity;        // 수량
    private BigDecimal discount; // 할인
    private BigDecimal amount;   // 확정 가격

    // 어떤 인보이스에 속한 상품인지 연결 (Foreign Key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id") // DB에 invoice_id 컬럼이 생김
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
