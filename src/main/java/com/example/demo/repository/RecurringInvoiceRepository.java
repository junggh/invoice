package com.example.demo.repository;

import com.example.demo.entity.RecurringInvoice;
import com.example.demo.entity.RecurringStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, Long> {
    // "상태가 ACTIVE이고, 다음 예정일이 오늘 이전(포함)인 것들"
    List<RecurringInvoice> findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus status, LocalDate date);
    // 가장 최근에 생성된 템플릿 조회 (번호 생성용)
    Optional<RecurringInvoice> findTopByOrderByIdDesc();
    Optional<RecurringInvoice> findTopByTemplateNumberStartingWithOrderByTemplateNumberDesc(String prefix);

    List<RecurringInvoice> findAllByOrderByIdAsc();
    List<RecurringInvoice> findByStatusNotOrderByIdAsc(RecurringStatus status);
}