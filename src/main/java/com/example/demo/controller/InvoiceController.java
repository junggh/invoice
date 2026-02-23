package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.RecurringInvoiceService;
import lombok.RequiredArgsConstructor;
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

    // ===================================================================================
    // 1. Dashboard & List (메인 화면)
    // ===================================================================================

    @GetMapping("/invoices")
    public String home(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "30") int days,
                       @RequestParam(required = false) String sortField,
                       @RequestParam(required = false) String sortDir,
                       @RequestParam(required = false) String recurringStatus,
                       Model model,
                       @AuthenticationPrincipal CustomUserDetails user) {

        // 0. 로그인한 유저의 회사 정보 획득
        Company company = user.getMember().getCompany();

        // 1. 상태 및 필터 설정
        String currentStatus = (status == null || status.isEmpty()) ? "Overview" : status;
        model.addAttribute("currentStatus", currentStatus);
        model.addAttribute("selectedDays", days);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("recurringStatus", recurringStatus);

        // 2. 상단 요약 정보 계산 (Total, Balance, Overdue)
        model.addAttribute("totalAmount", invoiceService.calculateGlobalTotal(days, company));
        model.addAttribute("totalBalance", invoiceService.calculateGlobalBalance(days, company));
        model.addAttribute("totalOverdue", invoiceService.calculateGlobalOverdue(days, company));

        // 3. 탭별 리스트 조회
        if ("Recurring".equals(currentStatus)) {
            model.addAttribute("recurringInvoices", recurringService.getTemplates(recurringStatus, company));
            model.addAttribute("invoices", Collections.emptyList());
        } else {
            model.addAttribute("invoices", invoiceService.getInvoices(status, sortField, sortDir, company));
            model.addAttribute("recurringInvoices", Collections.emptyList());
        }

        return "home";
    }

    // ===================================================================================
    // 2. Standard Invoice Operations (일반 인보이스)
    // ===================================================================================

    // [조회] 인보이스 상세
    @GetMapping("/invoices/{uuid}")
    public String viewInvoice(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Invoice invoice = invoiceService.getInvoiceByUuid(uuid, user.getMember().getCompany());

        model.addAttribute("invoice", invoice);
        model.addAttribute("subtotal", invoice.getSubtotal());
        model.addAttribute("tax", invoice.getTax());

        return "view-invoice";
    }

    // [등록] 화면 이동 (신규 및 복사)
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
            invoice.setIssuedDate(LocalDate.now());
            invoice.getItems().add(new InvoiceItem());
        }

        prepareFormModel(model, invoice, company);
        return "new-invoice";
    }

    // [등록] 처리
    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Member member = user.getMember();
        Company company = member.getCompany();

        // 1. 중복 체크: 사용자가 보고 있는 번호가 그새 DB에 저장되었는지 확인
        if (invoiceService.isInvoiceNumberExists(invoice.getInvoiceNumber(), company)) {

            // 2. 새 번호 생성 (로직 다시 실행)
            String nextNum = invoiceService.generateNextInvoiceNumber(company);
            String oldNum = invoice.getInvoiceNumber();

            // 3. 인보이스 객체에 새 번호 적용
            invoice.setInvoiceNumber(nextNum);

            // 4. 경고 메시지 & 데이터 유지 (저장 안 하고 폼으로 돌아감)
            model.addAttribute("warningMessage",
                    "Invoice # " + oldNum + " already exists, auto generated to " + nextNum);

            // [추가] 리로드 시 Product 상세 정보(설명, 가격 등)가 소실되는 문제 해결
            if (invoice.getItems() != null) {
                for (InvoiceItem item : invoice.getItems()) {
                    if (item.getProduct() != null && item.getProduct().getId() != null) {
                        // DB에서 상품 정보를 다시 조회하여 Item에 채워넣음
                        productRepository.findById(item.getProduct().getId())
                                .ifPresent(item::setProduct);
                    }
                }
            }

            // 드롭다운 메뉴 등 폼 데이터 복구
            prepareFormModel(model, invoice, company);

            return "new-invoice"; // 리다이렉트가 아니라 뷰를 다시 보여줌 (입력값 유지됨)
        }

        // 중복이 아니면 정상 저장
        invoiceService.createInvoice(invoice, member);
        return "redirect:/invoices";
    }

    // [수정] 화면 이동
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

    // [수정] 처리
    @PostMapping("/api/invoices/update")
    public String updateInvoice(Invoice invoice) {
        Invoice existingInvoice = invoiceService.getInvoice(invoice.getId());

        if (existingInvoice.getStatus() == InvoiceStatus.DRAFT) {
            invoiceService.updateInvoice(invoice);
        }
        return "redirect:/invoices";
    }

    // [결제] 부분 결제 및 전액 결제 기록
    @PostMapping("/api/invoices/{uuid}/pay")
    public String recordPayment(@PathVariable String uuid,
                                @RequestParam BigDecimal paymentAmount,
                                @AuthenticationPrincipal CustomUserDetails user) {

        Company company = user.getMember().getCompany();
        invoiceService.recordPayment(uuid, paymentAmount, company);

        // 결제 후 다시 해당 인보이스 상세 화면으로 리다이렉트
        return "redirect:/invoices/" + uuid;
    }

    // [상태변경] 제출 (DRAFT -> IN_REVIEW)
    @PostMapping("/api/invoices/submit")
    public String submitInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) invoiceService.submitInvoices(ids);
        return "redirect:/invoices?status=DRAFT";
    }

    // [상태변경] 승인 (IN_REVIEW -> UNPAID)
    @PostMapping("/api/invoices/approve")
    public String approveInvoices(@RequestParam List<Long> ids, @AuthenticationPrincipal CustomUserDetails user) {
        if (ids != null && !ids.isEmpty()) invoiceService.approveInvoices(ids, user.getMember());
        return "redirect:/invoices?status=IN_REVIEW";
    }

    // [삭제] 처리
    @PostMapping("/api/invoices/delete")
    public String deleteInvoices(@RequestParam List<Long> ids,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) Integer days) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.deleteInvoices(ids);
        }
        // 파라미터를 조합하여 리다이렉트 URL 생성
        StringBuilder redirectUrl = new StringBuilder("redirect:/invoices");
        boolean hasQuery = false;
        if (status != null && !status.isEmpty()) {
            redirectUrl.append("?status=").append(status);
            hasQuery = true;
        }
        if (days != null) {
            redirectUrl.append(hasQuery ? "&" : "?").append("days=").append(days);
        }
        return redirectUrl.toString();
    }

    // ===================================================================================
    // 3. Recurring Invoice Operations (정기 인보이스 템플릿)
    // ===================================================================================

    // [조회] 템플릿 상세
    @GetMapping("/invoices/recurring/{uuid}")
    public String viewRecurringInvoice(@PathVariable String uuid, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        RecurringInvoice template = recurringService.getRecurringInvoiceByUuid(uuid, user.getMember().getCompany());

        model.addAttribute("invoice", template);
        model.addAttribute("subtotal", template.getSubtotal());
        model.addAttribute("tax", template.getTax());

        return "view-template";
    }

    // [등록] 화면 이동 (신규 및 복사)
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
            template.setStartDate(LocalDate.now());
            template.getItems().add(new RecurringInvoiceItem());
        }

        prepareFormModel(model, template, company);
        return "new-template";
    }

    // [등록] 처리
    @PostMapping("/api/invoices/recurring")
    public String createRecurringInvoice(RecurringInvoice template, Model model, @AuthenticationPrincipal CustomUserDetails user) {
        Member member = user.getMember();
        Company company = member.getCompany();

        // 1. 중복 체크: 사용자가 보고 있는 번호가 이미 DB에 있는지 확인
        if (recurringService.isTemplateNumberExists(template.getTemplateNumber(), company)) {

            // 2. 새 번호 생성
            String nextNum = recurringService.generateNextTemplateNumber(company);
            String oldNum = template.getTemplateNumber();

            // 3. 템플릿 객체에 새 번호 적용
            template.setTemplateNumber(nextNum);

            // 4. 경고 메시지 설정
            model.addAttribute("warningMessage",
                    "Template # " + oldNum + " already exists, auto generated to " + nextNum);

            // Product, Contact 정보 복구
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

            // 6. 폼 데이터 복구 (드롭다운 등)
            prepareFormModel(model, template, company);

            return "new-template"; // 뷰 다시 렌더링 (입력값 유지)
        }

        // 중복이 아니면 정상 저장
        recurringService.createRecurringInvoice(template, member);
        return "redirect:/invoices?status=Recurring";
    }

    // [수정] 화면 이동
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
    public String approveRecurringInvoices(@RequestParam List<Long> ids, @AuthenticationPrincipal CustomUserDetails user) {
        if (ids != null && !ids.isEmpty()) recurringService.approveRecurringInvoices(ids, user.getMember());
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
    private void prepareFormModel(Model model, Object invoiceEntity, Company company) {
        model.addAttribute("invoice", invoiceEntity);
        model.addAttribute("products", productRepository.findByCompany(company));
        model.addAttribute("contacts", contactRepository.findByCompany(company));
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