package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;

    // ===================================================================================
    // 1. Read Operations (조회 및 대시보드)
    // ===================================================================================

    // [조회] 단건 상세 조회
    public Invoice getInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found. id=" + id));
    }

    // [조회] 주소로 단건 조회
    public Invoice getInvoiceByUuid(String uuid, Company company) {
        Invoice invoice = invoiceRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        // 내 회사의 인보이스가 아니면 에러 발생
        if (!invoice.getCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("You do not have permission to access this invoice.");
        }
        return invoice;
    }

    // [조회] 목록 조회 (필터 및 정렬 + 검색 + 페이징)
    public Page<Invoice> getInvoices(String statusCondition, String sortField, String sortDir, Company company, String keyword, int page) {
        // 1. 정렬 설정 (기본값: ID 내림차순 - 최신순)
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        if (sortField != null && !sortField.isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, sortField);
        }

        // 2. 페이징 설정 (0-indexed 이므로 page - 1, 사이즈는 15개)
        Pageable pageable = PageRequest.of(page - 1, 15, sort);

        // 3. 조회
        if (statusCondition == null || statusCondition.isEmpty() || "Overview".equals(statusCondition)) {
            return invoiceRepository.findInvoicesByKeywordAndStatusNot(company, InvoiceStatus.DELETED, keyword, pageable);
        }

        try {
            return invoiceRepository.findInvoicesByKeywordAndStatus(company, InvoiceStatus.valueOf(statusCondition), keyword, pageable);
        } catch (IllegalArgumentException e) {
            return invoiceRepository.findInvoicesByKeywordAndStatusNot(company, InvoiceStatus.DELETED, keyword, pageable);
        }
    }

    // [대시보드] 기간별 총 매출 (Total Amount)
    public BigDecimal calculateGlobalTotal(int days, Company company) {
        return invoiceRepository.sumTotalByCompanyAndDate(
                company,
                LocalDate.now().minusDays(days),
                List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    // [대시보드] 기간별 미수금 (Balance Due)
    public BigDecimal calculateGlobalBalance(int days, Company company) {
        return invoiceRepository.sumBalanceByCompanyAndDate(
                company,
                LocalDate.now().minusDays(days),
                List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    // [대시보드] 기간별 연체금 (Overdue)
    public BigDecimal calculateGlobalOverdue(int days, Company company) {
        return invoiceRepository.sumOverdueByCompanyAndDate(
                company,
                LocalDate.now().minusDays(days)
        );
    }

    // ===================================================================================
    // 2. Create & Update Operations (생성 및 수정)
    // ===================================================================================

    // [생성] 신규 인보이스 저장
    @Transactional
    public void createInvoice(Invoice invoice, Member member) {
        invoice.setBalanceDue(invoice.getTotal());
        invoice.setCompany(member.getCompany());
        // 양방향 연관관계 설정
        if (invoice.getItems() != null) {
            invoice.getItems().forEach(item -> item.setInvoice(invoice));
        }
        invoiceRepository.save(invoice);
    }

    // [생성] 탬플릿으로 인한 자동 인보이스 저장
    @Transactional
    public void autoCreateInvoice(Invoice invoice) {
        invoice.setBalanceDue(invoice.getTotal());
        // 양방향 연관관계 설정
        if (invoice.getItems() != null) {
            invoice.getItems().forEach(item -> item.setInvoice(invoice));
        }
        invoiceRepository.save(invoice);
    }

    // [생성] 기존 인보이스 복사 (메모리상 객체 생성)
    public Invoice copyInvoice(Long sourceId) {
        Invoice source = getInvoice(sourceId);
        Invoice newInvoice = new Invoice();

        // 1. 기본 정보 리셋
        newInvoice.setInvoiceNumber(generateNextInvoiceNumber(source.getCompany()));
        newInvoice.setStatus(InvoiceStatus.DRAFT);
        newInvoice.setIssuedDate(LocalDate.now());

        // 2. 고객 및 메타데이터 복사 (스냅샷)
        newInvoice.setContact(source.getContact());
        newInvoice.setCustomerName(source.getCustomerName());
        newInvoice.setCustomerEmail(source.getCustomerEmail());
        newInvoice.setCustomerCompanyName(source.getCustomerCompanyName());
        newInvoice.setCustomerBillTo(source.getCustomerBillTo());
        newInvoice.setCustomerCurrency(source.getCustomerCurrency());
        newInvoice.setSalesPerson(source.getSalesPerson());
        newInvoice.setReference(source.getReference());
        newInvoice.setTaxType(source.getTaxType());
        newInvoice.setTax(source.getTax());
        newInvoice.setSubtotal(source.getSubtotal());

        // 3. 아이템 딥 카피 (Deep Copy)
        List<InvoiceItem> newItems = new ArrayList<>();
        for (InvoiceItem sourceItem : source.getItems()) {
            InvoiceItem newItem = new InvoiceItem();
            newItem.setProduct(sourceItem.getProduct());
            newItem.setQuantity(sourceItem.getQuantity());
            newItem.setDiscount(sourceItem.getDiscount());
            newItem.setAmount(sourceItem.getAmount());
            newItem.setGstCode(sourceItem.getGstCode());
            newItem.setTaxAmount(sourceItem.getTaxAmount());

            newItem.setInvoice(newInvoice); // 연관관계 설정
            newItems.add(newItem);
        }
        newInvoice.setItems(newItems);

        // 4. 금액 복사
        newInvoice.setTotal(source.getTotal());
        newInvoice.setBalanceDue(source.getTotal());

        return newInvoice;
    }

    // [수정] 인보이스 업데이트
    @Transactional
    public void updateInvoice(Invoice formInvoice) {
        Invoice existingInvoice = getInvoice(formInvoice.getId());

        formInvoice.setBalanceDue(formInvoice.getTotal());

        // 기본 정보 복사 (ID, UUID, Items 제외)
        BeanUtils.copyProperties(formInvoice, existingInvoice, "id", "items", "uuid", "company", "invoiceNumber");

        // 아이템 리스트 교체 (OrphanRemoval 활용)
        existingInvoice.getItems().clear();
        if (formInvoice.getItems() != null) {
            for (InvoiceItem item : formInvoice.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null) continue; // 빈 아이템 스킵

                item.setId(null); // 신규 아이템으로 간주
                item.setInvoice(existingInvoice);
                existingInvoice.getItems().add(item);
            }
        }
    }

    // [상태변경 및 결제] 결제 기록
    @Transactional
    public void recordPayment(String uuid, BigDecimal paymentAmount, Company company) {
        Invoice invoice = getInvoiceByUuid(uuid, company);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("This invoice is already paid in full.");
        }

        // 현재 잔액 가져오기 (만약 null이면 Total 금액으로 간주)
        BigDecimal currentBalance = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : invoice.getTotal();

        // 새로운 잔액 계산
        BigDecimal newBalance = currentBalance.subtract(paymentAmount);

        // 잔액이 0 이하면 PAID 처리
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setBalanceDue(BigDecimal.ZERO);
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setBalanceDue(newBalance);
            // 상태가 OVERDUE였더라도 일부 결제 시 여전히 기한이 지났다면 OVERDUE 유지,
            // 아니면 UNPAID로 두는 로직이 필요할 수 있으나, 기본적으로 기존 상태를 유지합니다.
        }
    }

    // ===================================================================================
    // 3. Status Management (상태 변경 및 승인)
    // ===================================================================================

    // [상태변경] 제출 (Draft -> In Review)
    @Transactional
    public void submitInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == InvoiceStatus.DRAFT) {
                invoice.setStatus(InvoiceStatus.IN_REVIEW);
            }
        }
    }

    // [상태변경] 승인 (In Review -> Unpaid/Approved)
    @Transactional
    public void approveInvoices(List<Long> ids, Member member) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        LocalDate today = LocalDate.now();

        for (Invoice invoice : invoices) {
            // 발행일이 오늘 이전이면 즉시 발송(Unpaid)
            if (!invoice.getIssuedDate().isAfter(today)) {
                invoice.setStatus(InvoiceStatus.UNPAID);
                if (invoice.getInvoiceNumber() == null) {
                    invoice.setInvoiceNumber(generateNextInvoiceNumber(member.getCompany()));
                }
                sendUnpaidInvoiceEmail(invoice);
            } else {
                // 미래 날짜면 대기(Approved)
                invoice.setStatus(InvoiceStatus.APPROVED);
            }
        }
    }

    // [상태변경] 삭제 (Soft Delete)
    @Transactional
    public void deleteInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        invoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.DELETED));
    }

    // ===================================================================================
    // 4. Scheduled & System Operations (자동화 로직)
    // ===================================================================================

    // [스케줄러] 승인된 예약 인보이스 발송 처리 (Approved -> Unpaid)
    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void processScheduledInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> scheduledInvoices = invoiceRepository.findByStatusAndIssuedDateLessThanEqual(
                InvoiceStatus.APPROVED, today
        );

        for (Invoice invoice : scheduledInvoices) {
            invoice.setStatus(InvoiceStatus.UNPAID);
            sendUnpaidInvoiceEmail(invoice);
            System.out.println("Auto-sending Invoice ID: " + invoice.getId());
        }
    }

    // [이메일] 미납 인보이스 알림 메일 발송
    public void sendUnpaidInvoiceEmail(Invoice invoice) {
        if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isEmpty()) {
            return;
        }

        String companyName = invoice.getCompany().getBusinessName();
        String subject = "[Invoice] New invoice " + invoice.getInvoiceNumber() + " from " + companyName;

        String content = String.format(
                "<div style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
                        "<h2>New Invoice Received</h2>" +
                        "<p>Hello <strong>%s</strong>,</p>" +
                        "<p>You have received a new invoice from <strong>%s</strong>.</p>" +
                        "<p><strong>Invoice Number:</strong> %s<br>" +
                        "<strong>Total Amount:</strong> %s %s</p>" +
                        "<p>Please log in to your portal to view the details and make a payment.</p>" +
                        "<p>Thank you.</p>" +
                        "</div>",
                invoice.getCustomerName(),
                companyName,
                invoice.getInvoiceNumber(),
                invoice.getCustomerCurrency() != null ? invoice.getCustomerCurrency() : "",
                invoice.getTotal().toString()
        );

        emailService.sendEmail(invoice.getCustomerEmail(), subject, content);
    }

    // [스케줄러] 연체 상태 업데이트 (Unpaid -> Overdue)
    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void updateOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.UNPAID, today);

        if (!overdueInvoices.isEmpty()) {
            overdueInvoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
            System.out.println("✅ [System] Marked " + overdueInvoices.size() + " invoices as OVERDUE.");
        }
    }

    // [유틸] 다음 인보이스 번호 생성 (INV-0000#)
    public String generateNextInvoiceNumber(Company company) {
        return invoiceRepository.findTopByCompanyAndInvoiceNumberStartingWithOrderByInvoiceNumberDesc(company, "INV-")
                .map(lastInvoice -> {
                    int num = Integer.parseInt(lastInvoice.getInvoiceNumber().substring(4));
                    return String.format("INV-%05d", num + 1);
                })
                .orElse("INV-00001");
    }

    // [중복 체크] 해당 번호가 이미 존재하는지 확인
    public boolean isInvoiceNumberExists(String invoiceNumber, Company company) {
        return invoiceRepository.existsByCompanyAndInvoiceNumber(company, invoiceNumber);
    }
}
