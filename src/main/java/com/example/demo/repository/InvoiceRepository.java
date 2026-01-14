package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>{
    Optional<Invoice> findTopByOrderByIdDesc();
    List<Invoice> findByStatus(InvoiceStatus status);
}
