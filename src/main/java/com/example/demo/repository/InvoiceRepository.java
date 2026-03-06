package com.example.demo.repository;

import com.example.demo.entity.Company;
import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.entity.Timezone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>{

    // ===================================================================================
    // 1. Basic Lookups (화면 목록 조회 및 번호 생성)
    // ===================================================================================

    // 회사별 상태별 조회 (정렬 포함)
    List<Invoice> findByCompanyAndStatus(Company company, InvoiceStatus status, Sort sort);

    // 회사별 특정 상태 제외 조회 (주로 DELETED 제외 용도)
    List<Invoice> findByCompanyAndStatusNot(Company company, InvoiceStatus status, Sort sort);

    // [추가] Overview용 (삭제된 것 제외) + 검색 + 페이징
    @Query("SELECT i FROM Invoice i WHERE i.company = :company AND i.status != :status AND " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(i.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(i.customerCompanyName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Invoice> findInvoicesByKeywordAndStatusNot(
            @Param("company") Company company,
            @Param("status") InvoiceStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // [추가] 탭별 상태 조회 + 검색 + 페이징
    @Query("SELECT i FROM Invoice i WHERE i.company = :company AND i.status = :status AND " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(i.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(i.customerCompanyName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Invoice> findInvoicesByKeywordAndStatus(
            @Param("company") Company company,
            @Param("status") InvoiceStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 마지막 번호 조회 (INV-0000# 생성용)
    Optional<Invoice> findTopByCompanyAndInvoiceNumberStartingWithOrderByInvoiceNumberDesc(Company company, String s);

    // UUID 주소로 Invoice 조회
    Optional<Invoice> findByUuid(String uuid);

    // ===================================================================================
    // 2. Dashboard Statistics (대시보드 차트/지표용 JPQL)
    // ===================================================================================

    // 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Total 합계
    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i " +
            "WHERE i.company = :company " +
            "AND i.issuedDate >= :startDate " +
            "AND i.issuedDate <= :endDate " +
            "AND i.status IN :statuses")
    BigDecimal sumTotalByCompanyAndDateBetween(@Param("company") Company company,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("statuses") List<InvoiceStatus> statuses);

    // 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Balance Due 합계
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.company = :company " +
            "AND i.issuedDate >= :startDate " +
            "AND i.issuedDate <= :endDate " +
            "AND i.status IN :statuses")
    BigDecimal sumBalanceByCompanyAndDateBetween(@Param("company") Company company,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("statuses") List<InvoiceStatus> statuses);

    // 기간 내 OVERDUE 상태인 것들의 Balance Due 합계 (순수 연체금)
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.company = :company " +
            "AND i.issuedDate >= :startDate " +
            "AND i.issuedDate <= :endDate " +
            "AND i.status = 'OVERDUE'")
    BigDecimal sumOverdueByCompanyAndDateBetween(@Param("company") Company company,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    // ===================================================================================
    // 3. Scheduler & System (자동화 작업용)
    // ===================================================================================

    // [연체 스케줄러] 미납 상태(UNPAID)이면서, 납기일이 지난 인보이스 찾기
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);

    // [연체 스케줄러] 특정 timezone의 회사 인보이스 중 미납+납기일 초과 조회
    List<Invoice> findByCompanyTimezoneAndStatusAndDueDateBefore(Timezone timezone, InvoiceStatus status, LocalDate date);

    boolean existsByCompanyAndInvoiceNumber(Company company, String invoiceNumber);
}
