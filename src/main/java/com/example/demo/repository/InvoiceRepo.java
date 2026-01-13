package com.example.demo.repository;

import com.example.demo.entity.InvoiceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvoiceRepo extends JpaRepository<InvoiceMember, Long>{
    Optional<InvoiceMember> findTopByOrderByIdDesc();
}
