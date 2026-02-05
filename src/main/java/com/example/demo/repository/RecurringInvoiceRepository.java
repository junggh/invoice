package com.example.demo.repository;

import com.example.demo.entity.Company;
import com.example.demo.entity.Invoice;
import com.example.demo.entity.RecurringInvoice;
import com.example.demo.entity.RecurringStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, Long> {

    // ===================================================================================
    // 1. Basic Lookups (목록 조회 및 유틸)
    // ===================================================================================

    // 목록 조회 (삭제된 것 제외, ID순 정렬)
    List<RecurringInvoice> findByStatusNotOrderByIdAsc(RecurringStatus status);

    // 상태별 필터링 조회 (정확히 일치하는 상태만)
    List<RecurringInvoice> findByStatusOrderByIdAsc(RecurringStatus status);

    // 마지막 템플릿 번호 조회 (INVT-0000# 생성용)
    Optional<RecurringInvoice> findTopByTemplateNumberStartingWithOrderByTemplateNumberDesc(String prefix);
    Optional<RecurringInvoice> findTopByCompanyAndTemplateNumberStartingWithOrderByTemplateNumberDesc(Company company, String s);

    // UUID 주소로 탬플릿 조회
    Optional<RecurringInvoice> findByUuid(String uuid);

    // ===================================================================================
    // 2. Scheduler Support (자동 생성용)
    // ===================================================================================

    // [자동생성] 상태가 ACTIVE이고, 다음 예정일이 오늘 이전(포함)인 템플릿 조회
    List<RecurringInvoice> findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus status, LocalDate date);
}