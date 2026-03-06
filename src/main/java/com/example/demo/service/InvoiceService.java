package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.base-url}")
    private String baseUrl;

    // ===================================================================================
    // 1. Read Operations (조회 및 대시보드)
    // ===================================================================================

    // [조회] 단건 상세 조회
    public Invoice getInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found. id=" + id));
    }

    // [조회] 공개 링크용 단건 조회 (비회원 접근, DRAFT/DELETED 차단)
    public Invoice getPublicInvoice(String uuid) {
        Invoice invoice = invoiceRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.DELETED) {
            throw new IllegalArgumentException("This invoice is not publicly available.");
        }
        return invoice;
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
        if ("issuedDate".equals(sortField) || "dueDate".equals(sortField)) {
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

    // 문자열 기간(period)을 실제 시작일(LocalDate)로 변환하는 헬퍼 메서드
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

            // 분기 계산: 시작 달을 찾고, 그 달로부터 2달을 더해 해당 월의 마지막 날을 종료일로 설정
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

            // 회계연도 (Company 설정 정보 활용)
            case "THIS_FINANCIAL_YEAR", "LAST_FINANCIAL_YEAR" -> {
                int fyEndMonth = 6; // 기본값: 호주 표준 회계연도 종료월 (June)
                int fyEndDay = 30;  // 기본값: 호주 표준 회계연도 종료일 (30)

                // 세션 프록시 대신 진짜 회사 정보를 DB에서 가져옴 (에러 방지)
                Company realCompany = companyRepository.findById(company.getId()).orElse(company);

                if (realCompany.getFinancialYearMonth() != null && realCompany.getFinancialYearDay() != null) {
                    try {
                        fyEndMonth = java.time.Month.valueOf(realCompany.getFinancialYearMonth().toUpperCase()).getValue();
                        fyEndDay = Integer.parseInt(realCompany.getFinancialYearDay());
                    } catch (Exception ignored) { }
                }

                // 1. 해당 월의 마지막 날짜를 초과하지 않도록 안전하게 보정 (예: 2월 30일 방지)
                int maxDays = java.time.YearMonth.of(today.getYear(), fyEndMonth).lengthOfMonth();
                int safeDay = Math.min(fyEndDay, maxDays);

                // 2. 올해 기준 회계연도 종료일 생성
                LocalDate currentFyEnd = LocalDate.of(today.getYear(), fyEndMonth, safeDay);

                // 3. 오늘 날짜가 이미 올해의 종료일을 지났다면? -> 현재 속한 회계연도의 종료일은 '내년'이 됨
                if (today.isAfter(currentFyEnd)) {
                    int nextYearMaxDays = java.time.YearMonth.of(today.getYear() + 1, fyEndMonth).lengthOfMonth();
                    int nextYearSafeDay = Math.min(fyEndDay, nextYearMaxDays);
                    currentFyEnd = LocalDate.of(today.getYear() + 1, fyEndMonth, nextYearSafeDay);
                }

                // 4. 회계연도 시작일 계산 (종료일로부터 1년 전의 바로 다음 날)
                LocalDate currentFyStart = currentFyEnd.minusYears(1).plusDays(1);

                if ("LAST_FINANCIAL_YEAR".equals(period)) {
                    // 작년 회계연도: 시작일 1년 빼기, 종료일 1년 빼기
                    yield new DateRange(currentFyStart.minusYears(1), currentFyEnd.minusYears(1));
                } else {
                    // 올해 회계연도
                    yield new DateRange(currentFyStart, currentFyEnd);
                }
            }

            // 기본값: LAST_30_DAYS
            default -> new DateRange(today.minusDays(30), today);
        };
    }

    // [대시보드] 기간별 총 매출 (Total Amount)
    public BigDecimal calculateGlobalTotal(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumTotalByCompanyAndDateBetween(
                company, range.startDate(), range.endDate(), List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    // [대시보드] 기간별 미수금 (Balance Due)
    public BigDecimal calculateGlobalBalance(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumBalanceByCompanyAndDateBetween(
                company, range.startDate(), range.endDate(), List.of(InvoiceStatus.UNPAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE)
        );
    }

    // [대시보드] 기간별 연체금 (Overdue)
    public BigDecimal calculateGlobalOverdue(String period, Company company) {
        DateRange range = getDateRangeFromPeriod(period, company);
        return invoiceRepository.sumOverdueByCompanyAndDateBetween(
                company, range.startDate(), range.endDate()
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
        invoice.setCustomerCurrency(member.getCompany().getCurrency());

        // [추가] 저장되는 상태가 UNPAID이면서 마감일이 지났다면 OVERDUE로 변경
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
        newInvoice.setIssuedDate(LocalDate.now(getZoneId(source.getCompany())));

        // 2. 고객 및 메타데이터 복사 (스냅샷)
        newInvoice.setManualContact(source.isManualContact());
        if (source.isManualContact()) {
            newInvoice.setContact(null);
        } else {
            newInvoice.setContact(source.getContact());
        }
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
            newItem.setDiscountType(sourceItem.getDiscountType());
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

        // [추가] 폼에서 UNPAID로 요청이 왔는데 마감일이 지났다면 OVERDUE로 변경
        if (formInvoice.getStatus() == InvoiceStatus.UNPAID && formInvoice.getDueDate() != null) {
            if (formInvoice.getDueDate().isBefore(LocalDate.now(getZoneId(existingInvoice.getCompany())))) {
                formInvoice.setStatus(InvoiceStatus.OVERDUE);
            }
        }

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

    // [상태변경] 승인 (In Review -> Unpaid)
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

    // [상태변경] 단건 승인 + 선택적 이메일 발송 (view-invoice에서 사용)
    @Transactional
    public void approveSingleInvoice(String uuid, Member member, boolean sendEmail, String email) {
        Invoice invoice = getInvoiceByUuid(uuid, member.getCompany());
        if (invoice.getStatus() == InvoiceStatus.IN_REVIEW) {
            // [수정] 무조건 UNPAID로 바꾸는 대신 날짜 비교 로직 적용
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

    // [상태변경] 삭제 (Soft Delete)
    @Transactional
    public void deleteInvoices(List<Long> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        invoices.forEach(invoice -> invoice.setStatus(InvoiceStatus.DELETED));
    }

    // ===================================================================================
    // 4. Scheduled & System Operations (자동화 로직)
    // ===================================================================================

    // [이메일] 미납 인보이스 알림 메일 발송
    public void sendUnpaidInvoiceEmail(Invoice invoice) {
        if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isEmpty()) {
            return;
        }

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

    // [스케줄러] 매 정각마다 실행 — 해당 시각에 자정인 timezone의 회사만 연체 처리
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

    // [서버 시작 시] 모든 timezone에 대해 연체 상태 일괄 갱신 (다운타임 보정)
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

    // [유틸] 회사의 timezone에서 ZoneId를 안전하게 가져오기 (null이면 UTC 폴백)
    private ZoneId getZoneId(Company company) {
        if (company != null && company.getTimezone() != null) {
            return company.getTimezone().toZoneId();
        }
        return ZoneId.of("UTC");
    }
}
