package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;        // 상품명

    private String description; // 설명

    private BigDecimal price;   // 기본 단가

    private Double taxRate;     // 세율
}
