package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
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

    // 상태별 조회 (정렬 포함)
    List<Invoice> findByStatus(InvoiceStatus status, Sort sort);

    // 특정 상태 제외 조회 (주로 DELETED 제외 용도)
    List<Invoice> findByStatusNot(InvoiceStatus status, Sort sort);

    // 마지막 번호 조회 (INV-0000# 생성용)
    Optional<Invoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);

    // UUID 주소로 Invoice 조회
    Optional<Invoice> findByUuid(String uuid);

    // ===================================================================================
    // 2. Dashboard Statistics (대시보드 차트/지표용 JPQL)
    // ===================================================================================

    // 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Total 합계
    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status IN :statuses")
    BigDecimal sumTotalByDateAndStatus(@Param("startDate") LocalDate startDate,
                                       @Param("statuses") List<InvoiceStatus> statuses);

    // 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Balance Due 합계
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status IN :statuses")
    BigDecimal sumBalanceByDateAndStatus(@Param("startDate") LocalDate startDate,
                                         @Param("statuses") List<InvoiceStatus> statuses);

    // 기간 내 OVERDUE 상태인 것들의 Balance Due 합계 (순수 연체금)
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status = 'OVERDUE'")
    BigDecimal sumOverdueBalanceByDate(@Param("startDate") LocalDate startDate);

    // ===================================================================================
    // 3. Scheduler & System (자동화 작업용)
    // ===================================================================================

    // [발행 스케줄러] 승인 상태(APPROVED)이면서, 발행일이 도래한(오늘 포함) 인보이스 찾기
    List<Invoice> findByStatusAndIssuedDateLessThanEqual(InvoiceStatus status, LocalDate date);

    // [연체 스케줄러] 미납 상태(UNPAID)이면서, 납기일이 지난 인보이스 찾기
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
}
