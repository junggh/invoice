package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final CompanyRepository companyRepository;
    private final PdfService pdfService;
    private final EntityManager entityManager;

    @Value("${app.base-url}")
    private String baseUrl;

    // ===================================================================================
    // 1. 조회
    // ===================================================================================

    /** ID로 인보이스 단건 조회. */
    public Invoice getInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found. id=" + id));
    }

    /**
     * 공개 링크용 단건 조회 (비회원 접근).
     * DRAFT 및 DELETED 상태의 인보이스는 접근을 차단한다.
     */
    public Invoice getPublicInvoice(String uuid) {
        Invoice invoice = invoiceRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.DELETED) {
            throw new IllegalArgumentException("This invoice is not publicly available.");
        }
        return invoice;
    }

    /**
     * UUID로 인보이스 단건 조회. 요청한 회사 소유의 인보이스가 아니면 접근을 차단한다.
     */
    public Invoice getInvoiceByUuid(String uuid, Company company) {
        Invoice invoice = invoiceRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (!invoice.getCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("You do not have permission to access this invoice.");
        }
        return invoice;
    }

    /**
     * 인보이스 목록 조회. 탭 상태, 정렬, 검색어, 페이징을 적용하여 조회한다.
     * 기본 정렬은 ID 내림차순(최신순)이며, issuedDate/dueDate 필드에 한해 정렬 방향을 지정할 수 있다.
     */
    public Page<Invoice> getInvoices(String statusCondition, String sortField, String sortDir, Company company, String keyword, int page) {
        // 기본값: ID 내림차순(최신순)
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        if ("issuedDate".equals(sortField) || "dueDate".equals(sortField)) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, sortField);
        }

        // 0-indexed이므로 page - 1, 페이지당 15개
        Pageable pageable = PageRequest.of(page - 1, 15, sort);

        if (statusCondition == null || statusCondition.isEmpty() || "Overview".equals(statusCondition)) {
            return invoiceRepository.findInvoicesByKeywordAndStatusNot(company, InvoiceStatus.DELETED, keyword, pageable);
        }

        try {
            return invoiceRepository.findInvoicesByKeywordAndStatus(company, InvoiceStatus.valueOf(statusCondition), keyword, pageable);
        } catch (IllegalArgumentException e) {
            return invoiceRepository.findInvoicesByKeywordAndStatusNot(company, InvoiceStatus.DELETED, keyword, pageable);
        }
    }

    /**
     * 대시보드 기간별 총 매출 (UNPAID + PAID + OVERDUE 합계).
     */
    public BigDecimal calculateGlobalTotal(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumTotalByCompanyAndDateBetween(
                company, range.startDate(), range.endDate(), List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    /**
     * 대시보드 기간별 미수금 합계 (Balance Due).
     */
    public BigDecimal calculateGlobalBalance(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumBalanceByCompanyAndDateBetween(
                company, range.startDate(), range.endDate(), List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    /**
     * 대시보드 기간별 연체금 합계 (OVERDUE 상태 인보이스).
     */
    public BigDecimal calculateGlobalOverdue(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumOverdueByCompanyAndDateBetween(
                company, range.startDate(), range.endDate()
        );
    }

    /**
     * 문자열 기간(period) 값을 실제 시작일·종료일(DateRange)로 변환한다.
     * 회사의 회계연도(Financial Year) 설정을 반영하며, 설정이 없으면 호주 표준(6월 말) 기준을 사용한다.
     */
    private record DateRange(LocalDate startDate, LocalDate endDate) {}

    private DateRange getDateRangeFromPeriod(String period, Company company) {
        LocalDate today = LocalDate.now(getZoneId(company));

        return switch (period) {
            case "LAST_60_DAYS" -> new DateRange(today.minusDays(60), today);
            case "LAST_90_DAYS" -> new DateRange(today.minusDays(90), today);

            case "THIS_MONTH" -> {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
                yield new DateRange(start, end);
            }
            case "LAST_MONTH" -> {
                LocalDate start = today.minusMonths(1).withDayOfMonth(1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                yield new DateRange(start, end);
            }

            // 분기 계산: 현재 달이 속한 분기의 시작 달을 구하고, 2개월 뒤 말일을 종료일로 설정
            case "THIS_QUARTER" -> {
                int startMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = today.withMonth(startMonth).withDayOfMonth(1);
                LocalDate end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
                yield new DateRange(start, end);
            }
            case "LAST_QUARTER" -> {
                int startMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = today.withMonth(startMonth).withDayOfMonth(1).minusMonths(3);
                LocalDate end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
                yield new DateRange(start, end);
            }

            // 회계연도: Company 설정의 종료 월/일을 기준으로 계산 (기본값: 호주 표준 6월 30일)
            case "THIS_FINANCIAL_YEAR", "LAST_FINANCIAL_YEAR" -> {
                int fyEndMonth = 6;
                int fyEndDay = 30;

                // 세션 프록시 대신 실제 Company를 DB에서 조회
                Company realCompany = companyRepository.findById(company.getId()).orElse(company);

                if (realCompany.getFinancialYearMonth() != null && realCompany.getFinancialYearDay() != null) {
                    try {
                        fyEndMonth = java.time.Month.valueOf(realCompany.getFinancialYearMonth().toUpperCase()).getValue();
                        fyEndDay = Integer.parseInt(realCompany.getFinancialYearDay());
                    } catch (Exception ignored) { }
                }

                // 해당 월의 최대 일수를 초과하지 않도록 보정 (예: 2월 30일 방지)
                int maxDays = java.time.YearMonth.of(today.getYear(), fyEndMonth).lengthOfMonth();
                int safeDay = Math.min(fyEndDay, maxDays);

                LocalDate currentFyEnd = LocalDate.of(today.getYear(), fyEndMonth, safeDay);

                // 오늘이 올해 종료일을 지났다면 내년이 현재 회계연도 종료일
                if (today.isAfter(currentFyEnd)) {
                    int nextYearMaxDays = java.time.YearMonth.of(today.getYear() + 1, fyEndMonth).lengthOfMonth();
                    int nextYearSafeDay = Math.min(fyEndDay, nextYearMaxDays);
                    currentFyEnd = LocalDate.of(today.getYear() + 1, fyEndMonth, nextYearSafeDay);
                }

                // 회계연도 시작일 = 종료일로부터 1년 전 다음 날
                LocalDate currentFyStart = currentFyEnd.minusYears(1).plusDays(1);

                if ("LAST_FINANCIAL_YEAR".equals(period)) {
                    yield new DateRange(currentFyStart.minusYears(1), currentFyEnd.minusYears(1));
                } else {
                    yield new DateRange(currentFyStart, currentFyEnd);
                }
            }

            // 기본값: LAST_30_DAYS
            default -> new DateRange(today.minusDays(30), today);
        };
    }

    // ===================================================================================
    // 2. 생성 및 수정
    // ===================================================================================

    /**
     * 신규 인보이스 저장.
     * balanceDue를 total로 초기화하고, company 및 통화를 설정한다.
     * UNPAID 상태로 저장될 때 납기일이 이미 지났다면 즉시 OVERDUE로 변경한다.
     */
    @Transactional
    public void createInvoice(Invoice invoice, Member member) {
        invoice.setBalanceDue(invoice.getTotal());
        invoice.setCompany(member.getCompany());
        invoice.setCustomerCurrency(member.getCompany().getCurrency());

        // UNPAID로 저장 시 납기일이 이미 지났다면 OVERDUE로 즉시 변경
        if (invoice.getStatus() == InvoiceStatus.UNPAID && invoice.getDueDate() != null) {
            if (invoice.getDueDate().isBefore(LocalDate.now(getZoneId(member.getCompany())))) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
            }
        }

        // 양방향 연관관계 설정
        if (invoice.getItems() != null) {
            invoice.getItems().forEach(item -> item.setInvoice(invoice));
        }
        invoiceRepository.save(invoice);
    }

    /**
     * 반복 템플릿 스케줄러에 의한 인보이스 자동 저장.
     * company, currency는 이미 설정된 상태로 진입하므로 별도 설정 없이 저장한다.
     */
    @Transactional
    public void autoCreateInvoice(Invoice invoice) {
        invoice.setBalanceDue(invoice.getTotal());
        if (invoice.getItems() != null) {
            invoice.getItems().forEach(item -> item.setInvoice(invoice));
        }
        invoiceRepository.save(invoice);
    }

    /**
     * 기존 인보이스를 복사하여 새 인보이스 객체를 생성한다 (DB 저장은 하지 않음).
     * 식별자/상태/날짜는 새 값으로 재설정하고, 아이템은 새 객체로 딥 카피한다.
     */
    public Invoice copyInvoice(Long sourceId) {
        Invoice source = getInvoice(sourceId);
        Invoice newInvoice = new Invoice();

        // 식별자·상태·날짜·items를 제외한 나머지 필드 복사
        BeanUtils.copyProperties(source, newInvoice,
                "id", "uuid", "invoiceNumber", "status", "issuedDate", "dueDate", "items", "balanceDue");

        // 새 값으로 재설정
        newInvoice.setInvoiceNumber(generateNextInvoiceNumber(source.getCompany()));
        newInvoice.setStatus(InvoiceStatus.DRAFT);
        newInvoice.setIssuedDate(LocalDate.now(getZoneId(source.getCompany())));
        newInvoice.setBalanceDue(source.getTotal());
        if (source.isManualContact()) {
            newInvoice.setContact(null);
        }

        // 아이템 딥 카피 (원본 items와 연관관계가 공유되지 않도록 새 객체 생성)
        List<InvoiceItem> newItems = new ArrayList<>();
        for (InvoiceItem sourceItem : source.getItems()) {
            InvoiceItem newItem = new InvoiceItem();
            BeanUtils.copyProperties(sourceItem, newItem, "id", "invoice");
            newItem.setInvoice(newInvoice);
            newItems.add(newItem);
        }
        newInvoice.setItems(newItems);

        return newInvoice;
    }

    /**
     * 인보이스 수정. DRAFT 상태의 인보이스에 대해서만 호출된다.
     * UNPAID로 수정될 때 납기일이 이미 지났다면 OVERDUE로 변경한다.
     * 아이템은 기존 목록을 지우고 새로 교체한다 (orphanRemoval 활용).
     */
    @Transactional
    public void updateInvoice(Invoice formInvoice) {
        Invoice existingInvoice = getInvoice(formInvoice.getId());

        formInvoice.setBalanceDue(formInvoice.getTotal());

        // UNPAID로 수정 시 납기일이 이미 지났다면 OVERDUE로 즉시 변경
        if (formInvoice.getStatus() == InvoiceStatus.UNPAID && formInvoice.getDueDate() != null) {
            if (formInvoice.getDueDate().isBefore(LocalDate.now(getZoneId(existingInvoice.getCompany())))) {
                formInvoice.setStatus(InvoiceStatus.OVERDUE);
            }
        }

        // id, UUID, items, company, invoiceNumber를 제외한 나머지 필드 덮어쓰기
        BeanUtils.copyProperties(formInvoice, existingInvoice, "id", "items", "uuid", "company", "invoiceNumber");

        // 아이템 리스트 교체 (orphanRemoval로 기존 아이템 자동 삭제)
        existingInvoice.getItems().clear();
        if (formInvoice.getItems() != null) {
            for (InvoiceItem item : formInvoice.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null) continue;

                item.setId(null); // 신규 아이템으로 간주
                item.setInvoice(existingInvoice);
                existingInvoice.getItems().add(item);
            }
        }
    }

    // ===================================================================================
    // 3. 상태 변경
    // ===================================================================================

    /** 인보이스 제출 (DRAFT → IN_REVIEW). */
    @Transactional
    public void submitInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == InvoiceStatus.DRAFT) {
                invoice.setStatus(InvoiceStatus.IN_REVIEW);
            }
        }
    }

    /**
     * 인보이스 일괄 승인 (IN_REVIEW → UNPAID).
     * 납기일이 이미 지난 인보이스는 UNPAID 대신 OVERDUE로 설정한다.
     */
    @Transactional
    public void approveInvoices(List<Long> ids, Member member) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        LocalDate today = LocalDate.now(getZoneId(member.getCompany()));

        for (Invoice invoice : invoices) {
            if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(today)) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
            } else {
                invoice.setStatus(InvoiceStatus.UNPAID);
            }
            if (invoice.getInvoiceNumber() == null) {
                invoice.setInvoiceNumber(generateNextInvoiceNumber(member.getCompany()));
            }
        }
    }

    /**
     * 단건 승인 + 선택적 이메일 발송 (view-invoice 화면의 Approve 버튼).
     * sendEmail이 true이면 승인과 동시에 이메일을 발송하고, false이면 Send Later(발송 없이 UNPAID 저장)로 처리된다.
     * email 파라미터가 있으면 인보이스의 수신 이메일을 해당 값으로 업데이트한다.
     */
    @Transactional
    public void approveSingleInvoice(String uuid, Member member, boolean sendEmail, String email) {
        Invoice invoice = getInvoiceByUuid(uuid, member.getCompany());
        if (invoice.getStatus() == InvoiceStatus.IN_REVIEW) {
            // 납기일 초과 여부에 따라 UNPAID 또는 OVERDUE로 설정
            if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now(getZoneId(member.getCompany())))) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
            } else {
                invoice.setStatus(InvoiceStatus.UNPAID);
            }

            if (invoice.getInvoiceNumber() == null) {
                invoice.setInvoiceNumber(generateNextInvoiceNumber(member.getCompany()));
            }
            if (email != null && !email.isBlank()) {
                invoice.setCustomerEmail(email);
            }
            if (sendEmail) {
                sendUnpaidInvoiceEmail(invoice);
            }
        }
    }

    /** 인보이스 소프트 삭제. 상태를 DELETED로 변경하며 실제 데이터는 보존된다. */
    @Transactional
    public void deleteInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        invoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.DELETED));
    }

    /**
     * 결제 기록. 부분 결제와 전액 결제를 모두 처리한다.
     * 결제 후 잔액이 0 이하가 되면 PAID로 상태를 변경하고, 잔액이 남으면 기존 상태를 유지한다.
     */
    @Transactional
    public void recordPayment(String uuid, BigDecimal paymentAmount, Company company) {
        Invoice invoice = getInvoiceByUuid(uuid, company);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("This invoice is already paid in full.");
        }

        // balanceDue가 null이면 total 금액 기준으로 계산
        BigDecimal currentBalance = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : invoice.getTotal();
        BigDecimal newBalance = currentBalance.subtract(paymentAmount);

        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setBalanceDue(BigDecimal.ZERO);
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setBalanceDue(newBalance);
        }
    }

    // ===================================================================================
    // 4. 이메일 발송
    // ===================================================================================

    /**
     * UNPAID 인보이스 이메일 발송. 브랜드 스타일 HTML 본문에 PDF를 첨부하여 발송한다.
     *
     * Save & Send 흐름에서는 form binding이 생성한 stub Product 엔티티가 Hibernate 1st-level 캐시에
     * 잔존하여 PDF에 상품 정보가 빈칸으로 나오는 문제가 발생한다.
     * 이를 방지하기 위해 entityManager.clear()로 캐시를 초기화한 뒤 JOIN FETCH로 다시 조회한다.
     */
    @Transactional(readOnly = true)
    public void sendUnpaidInvoiceEmail(Invoice invoice) {
        if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isEmpty()) {
            return;
        }

        // Hibernate 1st-level 캐시 초기화 후 JOIN FETCH로 완전한 엔티티 재조회
        entityManager.clear();
        invoice = invoiceRepository.findByIdWithItemsAndProducts(invoice.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        String companyName = invoice.getCompany().getBusinessName();
        String subject = "[Invoice] New invoice " + invoice.getInvoiceNumber() + " from " + companyName;
        String invoiceLink = baseUrl + "/public/invoice/" + invoice.getUuid();
        String currency = invoice.getCustomerCurrency() != null ? invoice.getCustomerCurrency() : "";
        String totalFormatted = currency + " " + String.format("%,.2f", invoice.getTotal());

        String content =
            "<div style='font-family: Arial, sans-serif; background-color: #f5f7fa; padding: 40px 20px;'>" +
            "  <div style='max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08);'>" +
            "    <div style='background-color: #00A3FF; padding: 32px 40px;'>" +
            "      <h1 style='margin: 0; color: #ffffff; font-size: 22px; font-weight: 700;'>" + companyName + "</h1>" +
            "      <p style='margin: 6px 0 0; color: #d0efff; font-size: 14px;'>Invoice Notification</p>" +
            "    </div>" +
            "    <div style='padding: 36px 40px;'>" +
            "      <p style='margin: 0 0 6px; font-size: 15px; color: #555;'>Hello <strong style='color: #222;'>" + invoice.getCustomerName() + "</strong>,</p>" +
            "      <p style='margin: 0 0 28px; font-size: 15px; color: #555; line-height: 1.6;'>" +
            "        You have received a new invoice from <strong style='color: #222;'>" + companyName + "</strong>. Please review the details below.</p>" +
            "      <div style='background: #f8fafc; border-radius: 8px; padding: 20px 24px; margin-bottom: 28px;'>" +
            "        <table style='width: 100%; border-collapse: collapse; font-size: 14px;'>" +
            "          <tr><td style='padding: 7px 0; color: #888;'>Invoice Number</td>" +
            "              <td style='padding: 7px 0; text-align: right; font-weight: 600; color: #222;'>" + invoice.getInvoiceNumber() + "</td></tr>" +
            "          <tr><td style='padding: 7px 0; color: #888;'>Invoice Date</td>" +
            "              <td style='padding: 7px 0; text-align: right; color: #444;'>" + invoice.getIssuedDate() + "</td></tr>" +
            "          <tr><td style='padding: 7px 0; color: #888;'>Due Date</td>" +
            "              <td style='padding: 7px 0; text-align: right; color: #444;'>" + invoice.getDueDate() + "</td></tr>" +
            "          <tr style='border-top: 1px solid #e2e8f0;'>" +
            "              <td style='padding: 12px 0 7px; font-weight: 700; color: #222; font-size: 15px;'>Amount Due</td>" +
            "              <td style='padding: 12px 0 7px; text-align: right; font-weight: 700; color: #00A3FF; font-size: 18px;'>" + totalFormatted + "</td></tr>" +
            "        </table>" +
            "      </div>" +
            "      <div style='text-align: center; margin-bottom: 28px;'>" +
            "        <a href='" + invoiceLink + "' style='display: inline-block; padding: 14px 36px; background-color: #00A3FF; color: #ffffff;" +
            "           text-decoration: none; border-radius: 6px; font-weight: 700; font-size: 15px;'>View Invoice</a>" +
            "      </div>" +
            "      <p style='margin: 0; font-size: 12px; color: #aaa; text-align: center; line-height: 1.6;'>" +
            "        If the button doesn't work, copy and paste this link into your browser:<br>" +
            "        <a href='" + invoiceLink + "' style='color: #00A3FF; word-break: break-all;'>" + invoiceLink + "</a></p>" +
            "    </div>" +
            "    <div style='background: #f8fafc; padding: 20px 40px; text-align: center; border-top: 1px solid #eee;'>" +
            "      <p style='margin: 0; font-size: 12px; color: #bbb;'>Powered by ZeniBooks &mdash; " + companyName + "</p>" +
            "    </div>" +
            "  </div>" +
            "</div>";

        byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);
        String pdfFilename = invoice.getInvoiceNumber() + ".pdf";
        emailService.sendEmailWithAttachment(invoice.getCustomerEmail(), subject, content, pdfBytes, pdfFilename);
    }

    // ===================================================================================
    // 5. 스케줄러
    // ===================================================================================

    /**
     * 매 정각 실행. 현재 시각이 자정(hour == 0)인 timezone의 회사만 대상으로
     * 납기일이 지난 UNPAID 인보이스를 OVERDUE로 변경한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void updateOverdueInvoices() {
        for (Timezone tz : Timezone.values()) {
            if (LocalTime.now(tz.toZoneId()).getHour() == 0) {
                LocalDate today = LocalDate.now(tz.toZoneId());
                List<Invoice> overdueInvoices = invoiceRepository
                        .findByCompanyTimezoneAndStatusAndDueDateBefore(tz, InvoiceStatus.UNPAID, today);
                if (!overdueInvoices.isEmpty()) {
                    overdueInvoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
                    System.out.println("[Scheduler] Marked " + overdueInvoices.size() + " invoices as OVERDUE (timezone: " + tz.getDisplayValue() + ")");
                }
            }
        }
    }

    /**
     * 서버 시작 시 모든 timezone에 대해 연체 상태를 일괄 갱신한다.
     * 서버 다운타임 중 처리되지 못한 OVERDUE 전환을 보정하기 위해 실행된다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartupUpdateOverdue() {
        for (Timezone tz : Timezone.values()) {
            LocalDate today = LocalDate.now(tz.toZoneId());
            List<Invoice> overdueInvoices = invoiceRepository
                    .findByCompanyTimezoneAndStatusAndDueDateBefore(tz, InvoiceStatus.UNPAID, today);
            if (!overdueInvoices.isEmpty()) {
                overdueInvoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
            }
        }
    }

    // ===================================================================================
    // 6. 유틸
    // ===================================================================================

    /**
     * 다음 인보이스 번호를 생성한다 (INV-#####, 5자리 zero-padded).
     * 해당 회사의 마지막 번호를 조회하여 +1 증가시킨다.
     */
    public String generateNextInvoiceNumber(Company company) {
        return invoiceRepository.findTopByCompanyAndInvoiceNumberStartingWithOrderByInvoiceNumberDesc(company, "INV-")
                .map(lastInvoice -> {
                    int num = Integer.parseInt(lastInvoice.getInvoiceNumber().substring(4));
                    return String.format("INV-%05d", num + 1);
                })
                .orElse("INV-00001");
    }

    /** 해당 회사에 동일한 인보이스 번호가 이미 존재하는지 확인한다. */
    public boolean isInvoiceNumberExists(String invoiceNumber, Company company) {
        return invoiceRepository.existsByCompanyAndInvoiceNumber(company, invoiceNumber);
    }

    /** 회사의 timezone 기반 ZoneId를 반환한다. timezone이 null이면 UTC를 폴백으로 사용한다. */
    private ZoneId getZoneId(Company company) {
        if (company != null && company.getTimezone() != null) {
            return company.getTimezone().toZoneId();
        }
        return ZoneId.of("UTC");
    }
}
