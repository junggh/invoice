package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Getter @Setter
public class DemoMember {

    @Id // 프라이빗 키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // ID 자동 증가
    private Long id;

    private String username;
    private String email;
    private String password;
}
