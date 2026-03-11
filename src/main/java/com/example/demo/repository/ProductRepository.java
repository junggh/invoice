package com.example.demo.repository;

import com.example.demo.entity.Company;
import com.example.demo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 특정 회사의 모든 Product 조회 — 인보이스 항목 입력 시 상품 선택 목록 및 Product 목록 화면에 사용 */
    List<Product> findByCompany(Company company);
}
