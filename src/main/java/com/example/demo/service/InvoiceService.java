package com.example.demo.service;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceItem;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor // Repository를 자동으로 주입받기 위함 (Lombok)
@Transactional(readOnly = true) // 기본적으로는 조회만 하도록 설정 (성능 최적화)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Transactional // 쓰기 작업이 있으므로 읽기전용 해제
    public Long createInvoice(Invoice invoice) {

        invoice.setBalanceDue(invoice.getTotal());
        // Items의 부모 명시
        for (InvoiceItem item : invoice.getItems()) {
            item.setInvoice(invoice);
        }
        // DB에 저장
        Invoice savedInvoice = invoiceRepository.save(invoice);
        return savedInvoice.getId();
    }

    @Transactional
    public void updateInvoice(Invoice formInvoice) {
        Invoice existingInvoice = invoiceRepository.findById(formInvoice.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 인보이스가 없습니다. id=" + formInvoice.getId()));
        formInvoice.setBalanceDue(formInvoice.getTotal());
        // formInvoice의 값들을 existingInvoice로
        // "id"와 "items"는 복사하지 않고
        BeanUtils.copyProperties(formInvoice, existingInvoice, "id", "items");

        // 아이템 리스트 업데이트
        existingInvoice.getItems().clear();
        if (formInvoice.getItems() != null) {
            for (InvoiceItem item : formInvoice.getItems()) {
                // 유효성 검사: 상품 정보가 없거나, 수량이 없는 빈 객체는 건너뜀
                if (item.getProduct() == null || item.getProduct().getId() == null) {
                    continue;
                }
                item.setId(null);
                item.setInvoice(existingInvoice);
                existingInvoice.getItems().add(item);
            }
        }
    }
    @Transactional
    public void deleteInvoices(List<Long> ids) {
        invoiceRepository.deleteAllById(ids);
    }
    // Status 별로 invoice 조회
    public List<Invoice> getInvoices(String statusCondition) {
        // 1. 상태가 없거나 'Overview'이면 전체 조회
        if (statusCondition == null || statusCondition.isEmpty() || statusCondition.equals("Overview")) {
            return invoiceRepository.findAll();
        }

        // 2. 그 외에는 해당 상태(Enum)로 조회
        try {
            InvoiceStatus status = InvoiceStatus.valueOf(statusCondition);
            return invoiceRepository.findByStatus(status);
        } catch (IllegalArgumentException e) {
            // 이상한 문자가 들어오면 그냥 전체 조회
            return invoiceRepository.findAll();
        }
    }
    // 다음 INV-0000# 생성기
    public String generateNextInvoiceNumber() {
        // DB에서 가장 마지막 송장을 가져옴
        return invoiceRepository.findTopByOrderByIdDesc()
                .map(lastInvoice -> {
                    // 예: "INV-00005"
                    String lastNumber = lastInvoice.getInvoiceNumber();
                    // "INV-" 뒤의 숫자 부분만 파싱 (인덱스 4부터 끝까지) -> "00005" -> 5
                    int num = Integer.parseInt(lastNumber.substring(4));
                    // 1을 더해서 다시 포맷팅 -> "INV-00006"
                    return String.format("INV-%05d", num + 1);
                })
                .orElse("INV-00001"); // 데이터가 하나도 없으면 1번부터 시작
    }

    // 선택 invoice 상세 조회
    public Invoice getInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 인보이스가 없습니다. id=" + id));
    }

    public BigDecimal calculateTotalAmount(String statusCondition) {
        InvoiceStatus status = parseStatus(statusCondition);
        return invoiceRepository.sumTotalByStatus(status);
    }

    public BigDecimal calculateTotalBalance(String statusCondition) {
        InvoiceStatus status = parseStatus(statusCondition);
        return invoiceRepository.sumBalanceDueByStatus(status);
    }

    // (내부 헬퍼) 문자열 -> Enum 변환 로직 공통화
    private InvoiceStatus parseStatus(String statusCondition) {
        if (statusCondition == null || statusCondition.isEmpty() || statusCondition.equals("Overview")) {
            return null; // 전체 조회
        }
        try {
            return InvoiceStatus.valueOf(statusCondition);
        } catch (IllegalArgumentException e) {
            return null; // 잘못된 값이면 전체 조회로 처리
        }
    }
}
