package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>{
    Optional<Invoice> findTopByOrderByIdDesc();
}
