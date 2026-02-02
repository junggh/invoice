package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.RecurringInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    // ===================================================================================
    // 1. Dashboard & List (메인 화면)
    // ===================================================================================

    @GetMapping("/invoices")
    public String home(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "30") int days,
                       @RequestParam(required = false) String sortField,
                       @RequestParam(required = false) String sortDir,
                       @RequestParam(required = false) String recurringStatus,
                       Model model) {

        // 1. 상태 및 필터 설정
        String currentStatus = (status == null || status.isEmpty()) ? "Overview" : status;
        model.addAttribute("currentStatus", currentStatus);
        model.addAttribute("selectedDays", days);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("recurringStatus", recurringStatus);

        // 2. 상단 요약 정보 계산 (Total, Balance, Overdue)
        model.addAttribute("totalAmount", invoiceService.calculateGlobalTotal(days));
        model.addAttribute("totalBalance", invoiceService.calculateGlobalBalance(days));
        model.addAttribute("totalOverdue", invoiceService.calculateGlobalOverdue(days));

        // 3. 탭별 리스트 조회
        if ("Recurring".equals(currentStatus)) {
            model.addAttribute("recurringInvoices", recurringService.getTemplates(recurringStatus));
            model.addAttribute("invoices", Collections.emptyList());
        } else {
            model.addAttribute("invoices", invoiceService.getInvoices(status, sortField, sortDir));
            model.addAttribute("recurringInvoices", Collections.emptyList());
        }

        return "home";
    }

    // ===================================================================================
    // 2. Standard Invoice Operations (일반 인보이스)
    // ===================================================================================

    // [조회] 인보이스 상세
    @GetMapping("/invoices/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);

        model.addAttribute("invoice", invoice);
        calculateAndAddSummary(model, invoice.getItems(), InvoiceItem::getAmount);

        return "view-invoice";
    }

    // [등록] 화면 이동 (신규 및 복사)
    @GetMapping("/invoices/new")
    public String createInvoiceForm(@RequestParam(required = false) Long copyId, Model model) {
        Invoice invoice;

        if (copyId != null) {
            invoice = invoiceService.copyInvoice(copyId);
        } else {
            invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
            invoice.setStatus(InvoiceStatus.DRAFT);
            invoice.setIssuedDate(LocalDate.now());
            invoice.getItems().add(new InvoiceItem());
        }

        prepareFormModel(model, invoice);
        return "new-invoice";
    }

    // [등록] 처리
    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice) {
        invoiceService.createInvoice(invoice);
        return "redirect:/invoices";
    }

    // [수정] 화면 이동
    @GetMapping("/invoices/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/invoices/" + id;
        }

        prepareFormModel(model, invoice);
        return "edit-invoice";
    }

    // [수정] 처리
    @PostMapping("/api/invoices/update")
    public String updateInvoice(Invoice invoice) {
        Invoice existingInvoice = invoiceService.getInvoice(invoice.getId());

        if (existingInvoice.getStatus() == InvoiceStatus.DRAFT) {
            invoiceService.updateInvoice(invoice);
        }
        return "redirect:/invoices";
    }

    // [상태변경] 제출 (DRAFT -> IN_REVIEW)
    @PostMapping("/api/invoices/submit")
    public String submitInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) invoiceService.submitInvoices(ids);
        return "redirect:/invoices?status=DRAFT";
    }

    // [상태변경] 승인 (IN_REVIEW -> UNPAID)
    @PostMapping("/api/invoices/approve")
    public String approveInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) invoiceService.approveInvoices(ids);
        return "redirect:/invoices?status=IN_REVIEW";
    }

    // [삭제] 처리
    @PostMapping("/api/invoices/delete")
    public String deleteInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) invoiceService.deleteInvoices(ids);
        return "redirect:/invoices";
    }

    // ===================================================================================
    // 3. Recurring Invoice Operations (정기 인보이스 템플릿)
    // ===================================================================================

    // [조회] 템플릿 상세
    @GetMapping("/invoices/recurring/{id}")
    public String viewRecurringInvoice(@PathVariable Long id, Model model) {
        RecurringInvoice template = recurringService.getRecurringInvoice(id);

        model.addAttribute("invoice", template);
        calculateAndAddSummary(model, template.getItems(), RecurringInvoiceItem::getAmount);

        return "view-template";
    }

    // [등록] 화면 이동 (신규 및 복사)
    @GetMapping("/invoices/new/recurring")
    public String createRecurringInvoiceForm(@RequestParam(required = false) Long copyId, Model model) {
        RecurringInvoice template;

        if (copyId != null) {
            template = recurringService.copyRecurringInvoice(copyId);
        } else {
            template = new RecurringInvoice();
            template.setTemplateNumber(recurringService.generateNextTemplateNumber());
            template.setStatus(RecurringStatus.DRAFT);
            template.setStartDate(LocalDate.now());
            template.getItems().add(new RecurringInvoiceItem());
        }

        prepareFormModel(model, template);
        return "new-template";
    }

    // [등록] 처리
    @PostMapping("/api/invoices/recurring")
    public String createRecurringInvoice(RecurringInvoice template) {
        recurringService.createRecurringInvoice(template);
        return "redirect:/invoices?status=Recurring";
    }

    // [수정] 화면 이동
    @GetMapping("/invoices/recurring/{id}/edit")
    public String editRecurringInvoiceForm(@PathVariable Long id, Model model) {
        RecurringInvoice template = recurringService.getRecurringInvoice(id);

        if (template.getStatus() != RecurringStatus.DRAFT) {
            return "redirect:/invoices?status=Recurring";
        }

        prepareFormModel(model, template);
        return "edit-template";
    }

    // [수정] 처리
    @PostMapping("/api/invoices/recurring/update")
    public String updateRecurringInvoice(RecurringInvoice template) {
        RecurringInvoice existing = recurringService.getRecurringInvoice(template.getId());

        if (existing.getStatus() == RecurringStatus.DRAFT) {
            recurringService.updateRecurringInvoice(template);
        }
        return "redirect:/invoices?status=Recurring";
    }

    // [상태변경] 승인 (IN_REVIEW -> ACTIVE)
    @PostMapping("/api/invoices/recurring/approve")
    public String approveRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) recurringService.approveRecurringInvoices(ids);
        return "redirect:/invoices?status=Recurring";
    }

    // [상태변경] 종료 (ACTIVE -> COMPLETED)
    @PostMapping("/api/invoices/recurring/complete")
    public String completeRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) recurringService.completeRecurringInvoices(ids);
        return "redirect:/invoices?status=Recurring";
    }

    // [삭제] 처리
    @PostMapping("/api/invoices/recurring/delete")
    public String deleteRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) recurringService.deleteRecurringInvoices(ids);
        return "redirect:/invoices?status=Recurring";
    }

    // ===================================================================================
    // 4. Helper Methods (공통 로직)
    // ===================================================================================

    /**
     * 폼 화면(작성/수정)에 필요한 공통 Model 속성 설정
     */
    private void prepareFormModel(Model model, Object invoiceEntity) {
        model.addAttribute("invoice", invoiceEntity);
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("contacts", contactRepository.findAll());
    }

    /**
     * 인보이스 아이템들의 합계(Subtotal)와 세금(Tax)을 계산하여 Model에 추가
     * 제네릭을 사용하여 InvoiceItem과 RecurringInvoiceItem 모두 처리
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