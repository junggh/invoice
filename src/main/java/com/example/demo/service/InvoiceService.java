package com.example.demo.service;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceItem;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        for (Invoice invoice : invoices) {
            invoice.setStatus(InvoiceStatus.DELETED);
        }
    }
    /*// Status 별로 invoice 조회
    public List<Invoice> getInvoices(String statusCondition) {
        // 1. 상태가 없거나 'Overview'이면 전체 조회
        if (statusCondition == null || statusCondition.isEmpty() || statusCondition.equals("Overview")) {
            return invoiceRepository.findByStatusNotOrderByIdAsc(InvoiceStatus.DELETED);
        }

        // 2. 그 외에는 해당 상태(Enum)로 조회
        try {
            InvoiceStatus status = InvoiceStatus.valueOf(statusCondition);
            return invoiceRepository.findByStatusOrderByIdAsc(status);
        } catch (IllegalArgumentException e) {
            // 이상한 문자가 들어오면 그냥 전체 조회
            return invoiceRepository.findByStatusNotOrderByIdAsc(InvoiceStatus.DELETED);
        }
    }*/
    // [수정] 정렬 파라미터(sortField, sortDir) 추가
    public List<Invoice> getInvoices(String statusCondition, String sortField, String sortDir) {

        // 1. 정렬 객체 생성 (기본값: ID 오름차순)
        Sort sort = Sort.by(Sort.Direction.ASC, "id");

        // 사용자가 정렬 필드를 선택했다면 그에 맞춰 Sort 객체 생성
        if (sortField != null && !sortField.isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, sortField);
        }

        // 2. 조회 로직 (Repository 호출 시 sort 객체 전달)
        // 2-1. 상태가 없거나 'Overview'이면 -> DELETED 제외하고 전체 조회
        if (statusCondition == null || statusCondition.isEmpty() || statusCondition.equals("Overview")) {
            return invoiceRepository.findByStatusNot(InvoiceStatus.DELETED, sort);
        }

        // 2-2. 그 외에는 해당 상태(Enum)로 조회
        try {
            InvoiceStatus status = InvoiceStatus.valueOf(statusCondition);
            return invoiceRepository.findByStatus(status, sort);
        } catch (IllegalArgumentException e) {
            return invoiceRepository.findByStatusNot(InvoiceStatus.DELETED, sort);
        }
    }
    public Invoice copyInvoice(Long sourceId) {
        // 1. 원본 조회
        Invoice source = getInvoice(sourceId);

        // 2. 새 객체 생성 및 기본 설정
        Invoice newInvoice = new Invoice();
        newInvoice.setInvoiceNumber(generateNextInvoiceNumber());
        newInvoice.setStatus(InvoiceStatus.DRAFT); // 상태는 DRAFT
        newInvoice.setIssuedDate(LocalDate.now()); // 날짜는 오늘

        // 3. 고객 및 메타 데이터 복사 (원본의 스냅샷 데이터를 그대로 복사)
        newInvoice.setContact(source.getContact());
        newInvoice.setCustomerName(source.getCustomerName());
        newInvoice.setCustomerEmail(source.getCustomerEmail());
        newInvoice.setCustomerCompanyName(source.getCustomerCompanyName());
        newInvoice.setCustomerBillTo(source.getCustomerBillTo());
        newInvoice.setCustomerCurrency(source.getCustomerCurrency());
        newInvoice.setSalesPerson(source.getSalesPerson());
        newInvoice.setReference(source.getReference());

        // 4. 아이템 깊은 복사 (Deep Copy)
        // InvoiceItem에는 description, price 필드가 없으므로, product, quantity, discount, amount만 복사합니다.
        List<InvoiceItem> newItems = new ArrayList<>();
        for (InvoiceItem sourceItem : source.getItems()) {
            InvoiceItem newItem = new InvoiceItem();

            // [중요] ID는 복사하지 않음 (null이어야 새로 생성됨)

            // 상품 연결
            newItem.setProduct(sourceItem.getProduct());

            // 수량, 할인, 확정금액 복사
            newItem.setQuantity(sourceItem.getQuantity());
            newItem.setDiscount(sourceItem.getDiscount());
            newItem.setAmount(sourceItem.getAmount());

            // [중요] 양방향 연관관계 설정 (이게 없으면 저장이 안 되거나 FK가 null이 될 수 있음)
            newItem.setInvoice(newInvoice);

            newItems.add(newItem);
        }
        newInvoice.setItems(newItems);

        // 5. 합계 복사
        newInvoice.setTotal(source.getTotal());
        newInvoice.setBalanceDue(source.getTotal()); // 새 인보이스이므로 잔액은 총액과 같음

        return newInvoice; // 아직 DB에 저장되지 않은 상태의 객체 반환 (Controller에서 화면으로 전달됨)
    }
    // 인보이스 일괄 승인
    @Transactional
    public void approveInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        LocalDate today = LocalDate.now();
        for (Invoice invoice : invoices) {
            // 발행일이 오늘이거나 과거라면 -> 즉시 발송
            if (!invoice.getIssuedDate().isAfter(today)) {
                invoice.setStatus(InvoiceStatus.UNPAID);
                // 번호가 없으면 생성
                if (invoice.getInvoiceNumber() == null) {
                    invoice.setInvoiceNumber(generateNextInvoiceNumber());
                }
                // TODO: 여기서 이메일 발송 로직 호출 (emailService.send...)
            }
            // 발행일이 미래라면 -> 승인 상태로 대기
            else {
                invoice.setStatus(InvoiceStatus.APPROVED);
                // 아직 번호는 부여하지 않음 (발송되는 날 순서대로 부여하기 위해)
            }
        }
    }
    // [신규] 매일 00시에 실행되는 스케줄러: APPROVED -> UNPAID
    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void processScheduledInvoices() {
        LocalDate today = LocalDate.now();

        // 상태가 APPROVED이고, 날짜가 오늘(또는 그 이전)이 된 인보이스 찾기
        List<Invoice> scheduledInvoices = invoiceRepository.findByStatusAndIssuedDateLessThanEqual(
                InvoiceStatus.APPROVED, today
        );

        for (Invoice invoice : scheduledInvoices) {
            // 상태 변경 (발송 처리)
            invoice.setStatus(InvoiceStatus.UNPAID);

            // 번호 부여
            if (invoice.getInvoiceNumber() == null) {
                invoice.setInvoiceNumber(generateNextInvoiceNumber());
            }

            // TODO: 이메일 발송 로직 호출
            System.out.println("Auto-sending Invoice ID: " + invoice.getId());
        }
    }
    // 다음 INV-0000# 생성기
    public String generateNextInvoiceNumber() {
        // DB에서 가장 마지막 송장을 가져옴
        return invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc("INV-")
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

//    public BigDecimal calculateTotalAmount(String statusCondition) {
//        InvoiceStatus status = parseStatus(statusCondition);
//        return invoiceRepository.sumTotalByStatus(status);
//    }
//
//    public BigDecimal calculateTotalBalance(String statusCondition) {
//        InvoiceStatus status = parseStatus(statusCondition);
//        return invoiceRepository.sumBalanceDueByStatus(status);
//    }
    // 기간별 대시보드 지표 계산
    public BigDecimal calculateGlobalTotal(int days) {
    LocalDate startDate = LocalDate.now().minusDays(days);
    // 집계에 포함할 상태들 (Draft, In Review 제외)
    List<InvoiceStatus> targetStatuses = List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE);

    return invoiceRepository.sumTotalByDateAndStatus(startDate, targetStatuses);
    }

    // 기간별 Balance Due 합계
    public BigDecimal calculateGlobalBalance(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<InvoiceStatus> targetStatuses = List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE);

        return invoiceRepository.sumBalanceByDateAndStatus(startDate, targetStatuses);
    }

    // 기간별 Overdue 금액 합계
    public BigDecimal calculateGlobalOverdue(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return invoiceRepository.sumOverdueBalanceByDate(startDate);
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
    // 매일 자정 혹은 서버를 시작할 때 due date를 넘은 invoice들의 status 변경
    @Scheduled(cron = "0 0 0 * * *") // 1. 매일 자정에 실행
    @EventListener(ApplicationReadyEvent.class) // 2. 서버 실행 직후(준비 완료 시) 실행
    @Transactional
    public void updateOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.UNPAID, today);

        if (overdueInvoices.isEmpty()) return;

        for (Invoice invoice : overdueInvoices) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }

        System.out.println("✅ [시스템 시작/스케줄러] 연체 상태 업데이트 완료: " + overdueInvoices.size() + "건");
    }
}
