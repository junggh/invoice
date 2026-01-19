package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>{
    Optional<Invoice> findTopByOrderByIdDesc();
    List<Invoice> findByStatus(InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i WHERE (:status IS NULL OR i.status = :status)")
    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i WHERE (:status IS NULL OR i.status = :status)")
    BigDecimal sumBalanceDueByStatus(@Param("status") InvoiceStatus status);
}
