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
import java.util.List;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final RecurringInvoiceService recurringService;
    private final ProductRepository productRepository;
    private final ContactRepository contactRepository;

    // 메인 화면
    @GetMapping("/invoices")
    public String home(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "30") int days,
                       @RequestParam(required = false) String sortField,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        // 현재 탭 활성화용
        String currentStatus = (status == null || status.isEmpty()) ? "Overview" : status;
        model.addAttribute("currentStatus", currentStatus);
        model.addAttribute("selectedDays", days);

        BigDecimal totalAmount = invoiceService.calculateGlobalTotal(days);
        BigDecimal totalBalance = invoiceService.calculateGlobalBalance(days);
        BigDecimal totalOverdue = invoiceService.calculateGlobalOverdue(days);

        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("totalOverdue", totalOverdue);

        // 탭에 따라 데이터 분기 처리
        if ("Recurring".equals(currentStatus)) {
            // Recurring 탭이면 탬플릿 목록 조회
            List<RecurringInvoice> templates = recurringService.getAllTemplates();
            model.addAttribute("recurringInvoices", templates);
            // 빈 리스트라도 넣어둬야 타임리프 에러 방지 (일반 invoices는 비움)
            model.addAttribute("invoices", List.of());
        } else {
            // 기존 로직: 일반 인보이스 목록 조회
            //List<Invoice> invoices = invoiceService.getInvoices(status);
            List<Invoice> invoices = invoiceService.getInvoices(status, sortField, sortDir);
            model.addAttribute("invoices", invoices);
            model.addAttribute("recurringInvoices", List.of());
        }
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);

        return "home";
    }
    // 새 인보이스 작성 화면
    @GetMapping("/invoices/new")
    public String createInvoiceForm(@RequestParam(required = false) Long copyId, Model model) {
        Invoice invoice;

        if (copyId != null) {
            // [복사] 서비스에게 "이거 복사본 만들어줘"라고 시킴 (한 줄로 끝!)
            invoice = invoiceService.copyInvoice(copyId);
        } else {
            // [신규] 깡통 인보이스 생성
            invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
            invoice.setStatus(InvoiceStatus.DRAFT);
            invoice.setIssuedDate(LocalDate.now());
            invoice.getItems().add(new InvoiceItem());
        }

        model.addAttribute("invoice", invoice);
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("contacts", contactRepository.findAll());

        return "new-invoice";
    }
    // 새 인보이스 Post
    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice) {
        // HTML의 input name 속성과 Member 객체의 필드명이 같으면 자동으로 매핑
        invoiceService.createInvoice(invoice);

        return "redirect:/invoices";
    }
    // 수정 불가 인보이스 확인
    @GetMapping("/invoices/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);

        // Subtotal 계산: 모든 아이템의 amount 합계
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getAmount)
                .filter(Objects::nonNull) // null 방지
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.1"))
                .setScale(2, RoundingMode.HALF_UP);

        model.addAttribute("invoice", invoice);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("tax", tax);
        return "view-invoice";
    }
    // 수정 가능 인보이스 확인
    @GetMapping("/invoices/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);
        // 상태가 DRAFT가 아니라면 수정 화면 진입을 막고, 강제로 조회(View) 화면으로 리다이렉트
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/invoices/" + id;
        }
        model.addAttribute("invoice", invoice);
        List<Product> products = productRepository.findAll();
        model.addAttribute("products", products);
        List<Contact> contacts = contactRepository.findAll();
        model.addAttribute("contacts", contacts);
        return "edit-invoice";
    }
    // 인보이스 수정 Post
    @PostMapping("/api/invoices/update")
    public String updateInvoice(Invoice invoice) {
        // DB에 있는 원본 데이터를 먼저 조회해서 상태 확인
        Invoice existingInvoice = invoiceService.getInvoice(invoice.getId());

        // DRAFT가 아닌데 수정 요청이 오면 무시하고 목록으로 리다이렉트 (혹은 에러 처리)
        if (existingInvoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/invoices";
        }

        invoiceService.updateInvoice(invoice);

        return "redirect:/invoices";
    }
    // 인보이스 제출 (DRAFT -> IN_REVIEW)
    @PostMapping("/api/invoices/submit")
    public String submitInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.submitInvoices(ids);
        }
        // 처리 후 DRAFT 목록으로 돌아가거나, IN_REVIEW 목록으로 이동
        // 보통 제출했으면 목록에서 사라지는 게 자연스러우므로 현재 페이지(DRAFT) 유지 -> 목록에서 사라짐
        return "redirect:/invoices?status=DRAFT";
    }
    // 인보이스 승인 (IN_REVIEW -> UNPAID)
    @PostMapping("/api/invoices/approve")
    public String approveInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.approveInvoices(ids);
        }
        // 승인 후 'Unpaid' 탭으로 이동 (혹은 원래 탭 유지)
        return "redirect:/invoices?status=IN_REVIEW";
    }
    // 인보이스 삭제 Post
    @PostMapping("/api/invoices/delete")
    public String deleteInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.deleteInvoices(ids);
        }
        return "redirect:/invoices"; // 삭제 후 목록 새로고침
    }

    // 새 Recurring 인보이스 작성 화면
    @GetMapping("/invoices/new/recurring")
    public String createRecurringInvoiceForm(@RequestParam(required = false) Long copyId, Model model) {
        RecurringInvoice template;

        if (copyId != null) {
            // [복사] 서비스에게 "이거 복사본 만들어줘"라고 시킴 (한 줄로 끝!)
            template = recurringService.copyRecurringInvoice(copyId);
        } else {
            // [신규] 깡통 인보이스 생성
            template = new RecurringInvoice();
            template.setTemplateNumber(recurringService.generateNextTemplateNumber());
            template.setStatus(RecurringStatus.DRAFT);
            template.setStartDate(LocalDate.now());
            template.getItems().add(new RecurringInvoiceItem());
        }

        model.addAttribute("invoice", template);
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("contacts", contactRepository.findAll());

        return "new-template";
    }
    // 새 Recurring 인보이스 Post
    @PostMapping("/api/invoices/recurring")
    public String createRecurringInvoice(RecurringInvoice template) {
        // HTML의 input name 속성과 Member 객체의 필드명이 같으면 자동으로 매핑
        recurringService.createRecurringInvoice(template);

        return "redirect:/invoices?status=Recurring";
    }
    // 탬플릿 상세 보기 (View)
    @GetMapping("/invoices/recurring/{id}")
    public String viewRecurringInvoice(@PathVariable Long id, Model model) {
        RecurringInvoice template = recurringService.getRecurringInvoice(id);

        // Subtotal 계산 (아이템 Amount 합계)
        BigDecimal subtotal = template.getItems().stream()
                .map(RecurringInvoiceItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tax 계산 (10% + 반올림)
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.1"))
                .setScale(2, RoundingMode.HALF_UP);

        model.addAttribute("invoice", template);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("tax", tax);

        return "view-template";
    }
    // [추가] 탬플릿 수정 화면 진입 (GET)
    @GetMapping("/invoices/recurring/{id}/edit")
    public String editRecurringInvoiceForm(@PathVariable Long id, Model model) {
        RecurringInvoice template = recurringService.getRecurringInvoice(id);

        // [핵심] 상태가 DRAFT가 아니면 수정 불가 -> 상세 조회나 목록으로 튕김
        if (template.getStatus() != RecurringStatus.DRAFT) {
            // 알림 메시지를 띄우거나 할 수 있지만, 일단 목록으로 리다이렉트
            return "redirect:/invoices?status=Recurring";
        }

        model.addAttribute("invoice", template); // 폼에서 'invoice'라는 이름으로 받음
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("contacts", contactRepository.findAll());

        // 뷰는 new-template과 구조가 같으므로 edit-template을 새로 만들거나 공유
        return "edit-template";
    }
    // [추가] 탬플릿 수정 처리 (POST)
    @PostMapping("/api/invoices/recurring/update")
    public String updateRecurringInvoice(RecurringInvoice template) {
        // DB 데이터 조회하여 상태 재확인 (보안)
        RecurringInvoice existing = recurringService.getRecurringInvoice(template.getId());

        if (existing.getStatus() != RecurringStatus.DRAFT) {
            return "redirect:/invoices?status=Recurring";
        }

        recurringService.updateRecurringInvoice(template);

        return "redirect:/invoices?status=Recurring";
    }
    // Recurring 탬플릿 삭제 처리
    @PostMapping("/api/invoices/recurring/delete")
    public String deleteRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            recurringService.deleteRecurringInvoices(ids);
        }
        return "redirect:/invoices?status=Recurring";
    }
    // [추가] 탬플릿 승인 (IN_REVIEW -> ACTIVE)
    @PostMapping("/api/invoices/recurring/approve")
    public String approveRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            recurringService.approveRecurringInvoices(ids);
        }
        // 승인 후 Recurring 목록으로 복귀
        return "redirect:/invoices?status=Recurring";
    }
    // 탬플릿 종료
    @PostMapping("/api/invoices/recurring/complete")
    public String completeRecurringInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            recurringService.completeRecurringInvoices(ids);
        }
        // 처리가 끝나면 Recurring 탭으로 리다이렉트
        return "redirect:/invoices?status=Recurring";
    }
}