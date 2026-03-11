package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.PdfService;
import com.example.demo.service.RecurringInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final RecurringInvoiceService recurringService;
    private final ProductRepository productRepository;
    private final ContactRepository contactRepository;
    private final PdfService pdfService;

    // ===================================================================================
    // 1. Dashboard (대시보드)
    // ===================================================================================

    /**
     * 대시보드 메인 화면.
     * 탭(status), 검색어(keyword), 정렬, 페이징 파라미터를 받아 인보이스 또는 반복 템플릿 목록을 조회하고,
     * 기간별 통계(총액, 미수금, 연체액)와 슬라이딩 페이징 블록을 함께 계산하여 뷰에 전달한다.
     */
    @GetMapping("/invoices")
    public String home(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "LAST_30_DAYS") String period,
                       @RequestParam(required = false) String sortField,
                       @RequestParam(required = false) String sortDir,
                       @RequestParam(required = false) String recurringStatus,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") int page,
                       Model model,
                       @AuthenticationPrincipal CustomUserDetails user) {

        Company company = user.getMember().getCompany();
        String currentStatus = (status == null || status.isEmpty()) ? "Overview" : status;

        model.addAttribute("currentStatus", currentStatus);
        model.addAttribute("selectedPeriod", period);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("recurringStatus", recurringStatus);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);

        model.addAttribute("totalAmount", invoiceService.calculateGlobalTotal(period, company));
        model.addAttribute("totalBalance", invoiceService.calculateGlobalBalance(period, company));
        model.addAttribute("totalOverdue", invoiceService.calculateGlobalOverdue(period, company));

        int totalPages = 0;

        // Recurring 탭과 일반 인보이스 탭을 분기하여 각각 페이징 조회
        if ("Recurring".equals(currentStatus)) {
            Page<RecurringInvoice> templatePage = recurringService.getTemplates(recurringStatus, company, keyword, page);
            model.addAttribute("recurringInvoices", templatePage.getContent());
            model.addAttribute("invoices", Collections.emptyList());
            totalPages = templatePage.getTotalPages();
            model.addAttribute("totalPages", totalPages);
        } else {
            Page<Invoice> invoicePage = invoiceService.getInvoices(currentStatus, sortField, sortDir, company, keyword, page);
            model.addAttribute("invoices", invoicePage.getContent());
            model.addAttribute("recurringInvoices", Collections.emptyList());
            totalPages = invoicePage.getTotalPages();
            model.addAttribute("totalPages", totalPages);
        }

        // 슬라이딩 페이징 블록 계산: 화면에 최대 5개 버튼만 표시
        int maxPageButtons = 5;
        int startPage = Math.max(1, page - maxPageButtons / 2);
        int endPage = Math.min(totalPages, startPage + maxPageButtons - 1);

        // 끝 페이지 근처에 도달했을 때 앞쪽 버튼 개수 유지 (예: 총 10쪽에서 10쪽 클릭 시 6~10 표시)
        if (endPage - startPage + 1 < maxPageButtons) {
            startPage = Math.max(1, endPage - maxPageButtons + 1);
        }

        // 데이터가 아예 없을 때(0페이지) 에러 방지
        if (totalPages == 0) {
            startPage = 1;
            endPage = 1;
        }

        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        return "home";
    }

    // ===================================================================================
    // 2. Standard Invoice Operations (일반 인보이스 CRUD 및 상태 변경)
    // ===================================================================================

    /** 인보이스 상세 조회 */
    @GetMapping("/invoices/{uuid}")
    public String viewInvoice(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Invoice invoice = invoiceService.getInvoiceByUuid(uuid, user.getMember().getCompany());

        model.addAttribute("invoice", invoice);
        model.addAttribute("subtotal", invoice.getSubtotal());
        model.addAttribute("tax", invoice.getTax());

        return "view-invoice";
    }

    /** 인보이스 PDF 다운로드 */
    @GetMapping("/api/invoices/{uuid}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable String uuid,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        Invoice invoice = invoiceService.getInvoiceByUuid(uuid, user.getMember().getCompany());
        byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * 인보이스 작성 폼 이동.
     * copyId가 있으면 해당 인보이스를 복사하여 초기값으로 설정하고,
     * 없으면 새 인보이스를 생성하여 번호와 날짜를 초기화한다.
     */
    @GetMapping("/invoices/new")
    public String createInvoiceForm(@RequestParam(required = false) Long copyId, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Invoice invoice;
        Company company = user.getMember().getCompany();

        if (copyId != null) {
            invoice = invoiceService.copyInvoice(copyId);
        } else {
            invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber(company));
            invoice.setStatus(InvoiceStatus.DRAFT);
            invoice.setIssuedDate(LocalDate.now(company.getTimezone() != null ? company.getTimezone().toZoneId() : java.time.ZoneId.of("UTC")));
            invoice.getItems().add(new InvoiceItem());
        }

        prepareFormModel(model, invoice, company);
        return "new-invoice";
    }

    /**
     * 인보이스 등록 처리.
     * 폼 제출 시점에 번호 중복이 발생하면 새 번호를 자동 채번하고 폼을 재렌더링하여 입력값을 유지한다.
     * sendEmail 파라미터가 true이면 ADMIN의 Save & Send 흐름으로 즉시 이메일을 발송한다.
     */
    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice,
                                @RequestParam(defaultValue = "false") boolean sendEmail,
                                Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Member member = user.getMember();
        Company company = member.getCompany();

        if (invoiceService.isInvoiceNumberExists(invoice.getInvoiceNumber(), company)) {
            String nextNum = invoiceService.generateNextInvoiceNumber(company);
            String oldNum = invoice.getInvoiceNumber();
            invoice.setInvoiceNumber(nextNum);

            model.addAttribute("warningMessage",
                    "Invoice # " + oldNum + " already exists, auto generated to " + nextNum);

            // 번호 충돌로 폼 재렌더링 시 Product 상세 정보(설명, 가격 등)를 DB에서 다시 조회하여 복구
            if (invoice.getItems() != null) {
                for (InvoiceItem item : invoice.getItems()) {
                    if (item.getProduct() != null && item.getProduct().getId() != null) {
                        productRepository.findById(item.getProduct().getId())
                                .ifPresent(item::setProduct);
                    }
                }
            }

            prepareFormModel(model, invoice, company);
            return "new-invoice";
        }

        invoiceService.createInvoice(invoice, member);

        // ADMIN의 Save & Send 처리: 저장 후 이메일 발송
        if (sendEmail) {
            Invoice saved = invoiceService.getInvoice(invoice.getId());
            invoiceService.sendUnpaidInvoiceEmail(saved);
        }

        return "redirect:/invoices";
    }

    /** 인보이스 수정 폼 이동. DRAFT 상태가 아니면 상세 조회 화면으로 리다이렉트한다. */
    @GetMapping("/invoices/{uuid}/edit")
    public String editInvoice(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        Invoice invoice = invoiceService.getInvoiceByUuid(uuid, company);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/invoices/" + uuid;
        }

        prepareFormModel(model, invoice, company);
        return "edit-invoice";
    }

    /** 인보이스 수정 처리. DRAFT 상태일 때만 수정을 허용하며, sendEmail이 true이면 수정 후 이메일을 발송한다. */
    @PostMapping("/api/invoices/update")
    public String updateInvoice(Invoice invoice,
                                @RequestParam(defaultValue = "false") boolean sendEmail) {
        Invoice existingInvoice = invoiceService.getInvoice(invoice.getId());

        if (existingInvoice.getStatus() == InvoiceStatus.DRAFT) {
            invoiceService.updateInvoice(invoice);

            // ADMIN의 Save & Send 처리: 저장 후 이메일 발송
            if (sendEmail) {
                Invoice saved = invoiceService.getInvoice(invoice.getId());
                invoiceService.sendUnpaidInvoiceEmail(saved);
            }
        }
        return "redirect:/invoices";
    }

    /** 결제 기록. 부분 결제와 전액 결제를 모두 처리하며, 처리 후 해당 인보이스 상세 화면으로 이동한다. */
    @PostMapping("/api/invoices/{uuid}/pay")
    public String recordPayment(@PathVariable String uuid,
                                @RequestParam BigDecimal paymentAmount,
                                @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        invoiceService.recordPayment(uuid, paymentAmount, company);
        return "redirect:/invoices/" + uuid;
    }

    /** 인보이스 제출 (DRAFT → IN_REVIEW). 목록에서 선택한 인보이스를 일괄 제출한다. */
    @PostMapping("/api/invoices/submit")
    public String submitInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) invoiceService.submitInvoices(ids);
        return "redirect:/invoices?status=DRAFT";
    }

    /** 인보이스 일괄 승인 (IN_REVIEW → UNPAID). 목록의 체크박스 선택 후 일괄 처리한다. */
    @PostMapping("/api/invoices/approve")
    public String approveInvoices(@RequestParam List<Long> ids, @AuthenticationPrincipal CustomUserDetails user) {
        if (ids != null && !ids.isEmpty()) invoiceService.approveInvoices(ids, user.getMember());
        return "redirect:/invoices?status=IN_REVIEW";
    }

    /**
     * 단건 승인 + 이메일 발송 (view-invoice 화면의 Approve 버튼에서 사용).
     * 이메일 모달에서 Send 또는 Send Later를 선택할 수 있으며, sendEmail 파라미터로 분기된다.
     */
    @PostMapping("/api/invoices/{uuid}/approve")
    public String approveSingleInvoice(@PathVariable String uuid,
                                       @RequestParam(defaultValue = "false") boolean sendEmail,
                                       @RequestParam(required = false) String email,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        invoiceService.approveSingleInvoice(uuid, user.getMember(), sendEmail, email);
        return "redirect:/invoices/" + uuid;
    }

    /**
     * 인보이스 소프트 삭제.
     * 삭제 후 현재 탭(status)과 기간(period) 파라미터를 유지하여 리다이렉트한다.
     */
    @PostMapping("/api/invoices/delete")
    public String deleteInvoices(@RequestParam List<Long> ids,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String period) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.deleteInvoices(ids);
        }

        // 삭제 전 보던 탭과 기간을 유지하여 리다이렉트
        StringBuilder redirectUrl = new StringBuilder("redirect:/invoices");
        boolean hasQuery = false;
        if (status != null && !status.isEmpty()) {
            redirectUrl.append("?status=").append(status);
            hasQuery = true;
        }
        if (period != null && !period.isEmpty()) {
            redirectUrl.append(hasQuery ? "&" : "?").append("period=").append(period);
        }
        return redirectUrl.toString();
    }

    // ===================================================================================
    // 3. Recurring Invoice Operations (반복 인보이스 템플릿 CRUD 및 상태 변경)
    // ===================================================================================

    /** 반복 템플릿 상세 조회 */
    @GetMapping("/invoices/recurring/{uuid}")
    public String viewRecurringInvoice(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        RecurringInvoice template = recurringService.getRecurringInvoiceByUuid(uuid, user.getMember().getCompany());

        model.addAttribute("invoice", template);
        model.addAttribute("subtotal", template.getSubtotal());
        model.addAttribute("tax", template.getTax());

        return "view-template";
    }

    /**
     * 반복 템플릿 작성 폼 이동.
     * copyId가 있으면 해당 템플릿을 복사하여 초기값으로 설정하고,
     * 없으면 새 템플릿을 생성하여 번호와 시작일을 초기화한다.
     */
    @GetMapping("/invoices/new/recurring")
    public String createRecurringInvoiceForm(@RequestParam(required = false) Long copyId, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        RecurringInvoice template;
        Company company = user.getMember().getCompany();

        if (copyId != null) {
            template = recurringService.copyRecurringInvoice(copyId);
        } else {
            template = new RecurringInvoice();
            template.setTemplateNumber(recurringService.generateNextTemplateNumber(company));
            template.setStatus(RecurringStatus.DRAFT);
            template.setStartDate(LocalDate.now(company.getTimezone() != null ? company.getTimezone().toZoneId() : java.time.ZoneId.of("UTC")));
            template.getItems().add(new RecurringInvoiceItem());
        }

        prepareFormModel(model, template, company);
        return "new-template";
    }

    /**
     * 반복 템플릿 등록 처리.
     * 번호 중복 시 자동 채번 후 폼을 재렌더링하며, Product와 Contact 정보도 함께 복구한다.
     */
    @PostMapping("/api/invoices/recurring")
    public String createRecurringInvoice(RecurringInvoice template, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Member member = user.getMember();
        Company company = member.getCompany();

        if (recurringService.isTemplateNumberExists(template.getTemplateNumber(), company)) {
            String nextNum = recurringService.generateNextTemplateNumber(company);
            String oldNum = template.getTemplateNumber();
            template.setTemplateNumber(nextNum);

            model.addAttribute("warningMessage",
                    "Template # " + oldNum + " already exists, auto generated to " + nextNum);

            // 번호 충돌로 폼 재렌더링 시 Product 및 Contact 정보를 DB에서 다시 조회하여 복구
            if (template.getItems() != null) {
                for (RecurringInvoiceItem item : template.getItems()) {
                    if (item.getProduct() != null && item.getProduct().getId() != null) {
                        productRepository.findById(item.getProduct().getId())
                                .ifPresent(item::setProduct);
                    }
                }
            }
            if (template.getContact() != null && template.getContact().getId() != null) {
                contactRepository.findById(template.getContact().getId())
                        .ifPresent(template::setContact);
            }

            prepareFormModel(model, template, company);
            return "new-template";
        }

        recurringService.createRecurringInvoice(template, member);
        return "redirect:/invoices?status=Recurring";
    }

    /** 반복 템플릿 수정 폼 이동. DRAFT 상태가 아니면 상세 조회 화면으로 리다이렉트한다. */
    @GetMapping("/invoices/recurring/{uuid}/edit")
    public String editRecurringInvoiceForm(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        RecurringInvoice template = recurringService.getRecurringInvoiceByUuid(uuid, company);

        if (template.getStatus() != RecurringStatus.DRAFT) {
            return "redirect:/invoices/recurring/" + uuid;
        }

        prepareFormModel(model, template, company);
        return "edit-template";
    }

    /** 반복 템플릿 수정 처리. DRAFT 상태일 때만 수정을 허용한다. */
    @PostMapping("/api/invoices/recurring/update")
    public String updateRecurringInvoice(RecurringInvoice template) {
        RecurringInvoice existing = recurringService.getRecurringInvoice(template.getId());

        if (existing.getStatus() == RecurringStatus.DRAFT) {
            recurringService.updateRecurringInvoice(template);
        }
        return "redirect:/invoices?status=Recurring";
    }

    /** 반복 템플릿 일괄 승인 (IN_REVIEW → ACTIVE). 활성화되면 스케줄러가 자동으로 인보이스를 생성한다. */
    @PostMapping("/api/invoices/recurring/approve")
    public String approveRecurringInvoices(@RequestParam List<Long> ids, @AuthenticationPrincipal CustomUserDetails user) {
        if (ids != null && !ids.isEmpty()) recurringService.approveRecurringInvoices(ids, user.getMember());
        return "redirect:/invoices?status=Recurring";
    }

    /** 반복 템플릿 종료 처리 (ACTIVE → COMPLETED). */
    @PostMapping("/api/invoices/recurring/complete")
    public String completeRecurringInvoices(@RequestParam List<Long> ids, @AuthenticationPrincipal CustomUserDetails user) {
        if (ids != null && !ids.isEmpty()) recurringService.completeRecurringInvoices(ids, user.getMember().getCompany());
        return "redirect:/invoices?status=Recurring";
    }

    /** 반복 템플릿 소프트 삭제. */
    @PostMapping("/api/invoices/recurring/delete")
    public String deleteRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) recurringService.deleteRecurringInvoices(ids);
        return "redirect:/invoices?status=Recurring";
    }

    // ===================================================================================
    // 4. Helper Methods (폼 공통 처리)
    // ===================================================================================

    /**
     * 인보이스/템플릿 작성·수정 폼에 필요한 공통 Model 속성 설정.
     * invoice 엔티티 외에 Product 목록과 Contact 목록을 드롭다운용으로 함께 전달한다.
     */
    private void prepareFormModel(Model model, Object invoiceEntity, Company company) {
        model.addAttribute("invoice", invoiceEntity);
        model.addAttribute("products", productRepository.findByCompany(company));
        model.addAttribute("contacts", contactRepository.findByCompany(company));
    }

    /**
     * 인보이스 아이템 목록으로부터 소계(subtotal)와 세금(tax)을 계산하여 Model에 추가.
     * 제네릭을 사용하여 InvoiceItem과 RecurringInvoiceItem 모두 처리 가능하다.
     */
    private <T> void calculateAndAddSummary(Model model, List<T> items, Function<T, BigDecimal> amountMapper) {
        BigDecimal subtotal = items.stream()
                .map(amountMapper)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = subtotal.multiply(new BigDecimal("0.1"))
                .setScale(2, RoundingMode.HALF_UP);

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("tax", tax);
    }
}