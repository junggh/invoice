package com.example.demo.repository;

import com.example.demo.entity.Company;
import com.example.demo.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    /** 특정 회사의 모든 Contact 조회 — 인보이스 작성 시 고객 선택 목록 및 Contact 목록 화면에 사용 */
    List<Contact> findByCompany(Company company);
}
