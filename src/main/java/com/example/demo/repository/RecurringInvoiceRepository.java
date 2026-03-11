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

    // ALL 탭(삭제된 것 제외) + 키워드 검색 + 페이징
    // 템플릿 번호, Contact명, Contact 회사명을 대소문자 무시하여 검색한다.
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

    // 탭별 상태 필터 + 키워드 검색 + 페이징
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

    // 템플릿 번호 중복 여부 확인 (번호 생성 시 충돌 방지용)
    boolean existsByCompanyAndTemplateNumber(Company company, String templateNumber);

    // UUID로 템플릿 조회 (공개 URL 접근용)
    Optional<RecurringInvoice> findByUuid(String uuid);

    // ===================================================================================
    // 2. Scheduler Support (자동 생성용)
    // ===================================================================================

    // 자동 생성 스케줄러용 — ACTIVE 상태이면서 다음 발행 예정일이 도래한 템플릿 전체 조회 (startup 보정 시 사용)
    List<RecurringInvoice> findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus status, LocalDate date);

    // 자동 생성 스케줄러용 — 특정 timezone 회사의 ACTIVE+예정일 도래 템플릿 조회 (야간 배치 시 사용)
    List<RecurringInvoice> findByCompanyTimezoneAndStatusAndNextInvoiceDateLessThanEqual(Timezone timezone, RecurringStatus status, LocalDate date);
}