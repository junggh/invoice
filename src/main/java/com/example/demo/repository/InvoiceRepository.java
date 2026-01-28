package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>{
    Optional<Invoice> findTopByOrderByIdDesc();
    Optional<Invoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);
    List<Invoice> findByStatusOrderByIdAsc(InvoiceStatus status);
    List<Invoice> findAllByOrderByIdAsc();
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);

//    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i WHERE (:status IS NULL OR i.status = :status)")
//    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);
//
//    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i WHERE (:status IS NULL OR i.status = :status)")
//    BigDecimal sumBalanceDueByStatus(@Param("status") InvoiceStatus status);

    // 1. 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Total 합계
    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status IN :statuses")
    BigDecimal sumTotalByDateAndStatus(@Param("startDate") LocalDate startDate,
                                       @Param("statuses") List<InvoiceStatus> statuses);

    // 2. 기간 내 유효한 상태(UNPAID, PAID, OVERDUE)의 Balance Due 합계
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status IN :statuses")
    BigDecimal sumBalanceByDateAndStatus(@Param("startDate") LocalDate startDate,
                                         @Param("statuses") List<InvoiceStatus> statuses);

    // 3. 기간 내 OVERDUE 상태인 것들의 Balance Due 합계 (Overdue 금액)
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
            "WHERE i.issuedDate >= :startDate " +
            "AND i.status = 'OVERDUE'")
    BigDecimal sumOverdueBalanceByDate(@Param("startDate") LocalDate startDate);
}
