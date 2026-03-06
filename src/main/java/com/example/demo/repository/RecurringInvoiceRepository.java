package com.example.demo.repository;

import com.example.demo.entity.Company;
import com.example.demo.entity.RecurringInvoice;
import com.example.demo.entity.RecurringStatus;
import com.example.demo.entity.Timezone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, Long> {

    // ===================================================================================
    // 1. Basic Lookups (목록 조회 및 유틸)
    // ===================================================================================

    // 회사별 목록 조회 (삭제된 것 제외, ID순 정렬)
    List<RecurringInvoice> findByCompanyAndStatusNotOrderByIdAsc(Company company, RecurringStatus status);

    // 회사별 상태별 필터링 조회 (정확히 일치하는 상태만)
    List<RecurringInvoice> findByCompanyAndStatusOrderByIdAsc(Company company, RecurringStatus status);

    // [추가] ALL 필터용 (삭제된 것 제외) + 검색 + 페이징
    @Query("SELECT r FROM RecurringInvoice r LEFT JOIN r.contact c " +
            "WHERE r.company = :company AND r.status != :status AND " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(r.templateNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<RecurringInvoice> findTemplatesByKeywordAndStatusNot(
            @Param("company") Company company,
            @Param("status") RecurringStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // [추가] 특정 상태별 조회 + 검색 + 페이징
    @Query("SELECT r FROM RecurringInvoice r LEFT JOIN r.contact c " +
            "WHERE r.company = :company AND r.status = :status AND " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(r.templateNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<RecurringInvoice> findTemplatesByKeywordAndStatus(
            @Param("company") Company company,
            @Param("status") RecurringStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 마지막 템플릿 번호 조회 (INVT-0000# 생성용)
    Optional<RecurringInvoice> findTopByCompanyAndTemplateNumberStartingWithOrderByTemplateNumberDesc(Company company, String s);

    // UUID 주소로 탬플릿 조회
    Optional<RecurringInvoice> findByUuid(String uuid);

    // ===================================================================================
    // 2. Scheduler Support (자동 생성용)
    // ===================================================================================

    // [자동생성] 상태가 ACTIVE이고, 다음 예정일이 오늘 이전(포함)인 템플릿 조회
    List<RecurringInvoice> findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus status, LocalDate date);

    // [자동생성] 특정 timezone의 회사 템플릿 중 ACTIVE+예정일 도래 조회
    List<RecurringInvoice> findByCompanyTimezoneAndStatusAndNextInvoiceDateLessThanEqual(Timezone timezone, RecurringStatus status, LocalDate date);

    boolean existsByCompanyAndTemplateNumber(Company company, String templateNumber);
}