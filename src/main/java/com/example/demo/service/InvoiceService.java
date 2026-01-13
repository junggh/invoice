package com.example.demo.service;

import com.example.demo.entity.InvoiceMember;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.repository.InvoiceRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor // Repository를 자동으로 주입받기 위함 (Lombok)
@Transactional(readOnly = true) // 기본적으로는 조회만 하도록 설정 (성능 최적화)
public class InvoiceService {

    private final InvoiceRepo invoiceRepository;

    /**
     * 인보이스 생성 (저장)
     * - 번호 자동 생성 로직 포함
     * - 잔액(Balance Due) 자동 설정 포함
     */
    @Transactional // 쓰기 작업이 있으므로 읽기전용 해제
    public Long createInvoice(InvoiceMember invoice) {

        // 1. 송장 번호 자동 생성 로직 (INV-00001 패턴)
        String nextInvoiceNumber = generateNextInvoiceNumber();
        invoice.setInvoiceNumber(nextInvoiceNumber);

        // 2. 초기 잔액(Balance Due) 설정 로직
        // 만약 상태가 'PAID'라면 잔액은 0원, 아니라면 총 금액과 같음
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            invoice.setBalanceDue(java.math.BigDecimal.ZERO);
        } else {
            // 입력받은 total 금액을 그대로 잔액으로 설정
            invoice.setBalanceDue(invoice.getTotal());
        }

        // 3. DB에 저장
        InvoiceMember savedInvoice = invoiceRepository.save(invoice);
        return savedInvoice.getId();
    }

    /**
     * 송장 번호 생성기 (내부 메서드)
     * 마지막 번호를 조회해서 +1을 수행함
     */
    private String generateNextInvoiceNumber() {
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

    /**
     * 전체 조회 (Overview 화면용)
     */
    public List<InvoiceMember> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    /**
     * 상세 조회 (개별 클릭 시)
     */
    public InvoiceMember getInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 인보이스가 없습니다. id=" + id));
    }
}
