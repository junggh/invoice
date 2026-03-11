package com.example.demo.repository;

import com.example.demo.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** 회원가입 시 ABN 중복 여부 확인 */
    boolean existsByAbn(String abn);
}