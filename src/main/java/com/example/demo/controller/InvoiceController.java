package com.example.demo.controller;

import com.example.demo.entity.Contact;
import com.example.demo.entity.Invoice;
import com.example.demo.entity.Product;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ProductRepository productRepository;
    private final ContactRepository contactRepository;

    // 메인 화면
    @GetMapping("/invoices")
    public String home(@RequestParam(required = false) String status, Model model) {
        List<Invoice> invoices = invoiceService.getInvoices(status);
        model.addAttribute("invoices", invoices);
        // 1. Total 합계 (null은 제외하고 0으로 처리)
        BigDecimal totalAmount = invoices.stream()
                .map(Invoice::getTotal)       // total 필드만 뽑기
                .filter(Objects::nonNull)     // null인 데이터는 제외 (에러 방지!)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // 다 더하기

        // 2. Balance Due 합계
        BigDecimal totalBalance = invoices.stream()
                .map(Invoice::getBalanceDue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 계산된 결과를 모델에 담기
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalBalance", totalBalance);
        // 현재 탭 활성화용
        model.addAttribute("currentStatus", (status == null || status.isEmpty()) ? "Overview" : status);

        return "home";
    }
    // 새 인보이스 작성 화면
    @GetMapping("/invoices/new")
    public String createInvoiceForm(Model model) {
        String nextNum = invoiceService.generateNextInvoiceNumber();
        model.addAttribute("nextInvoiceNum", nextNum);
        List<Product> products = productRepository.findAll();
        model.addAttribute("products", products);
        List<Contact> contacts = contactRepository.findAll();
        model.addAttribute("contacts", contacts);
        return "new-invoice";
    }

    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice) {
        // HTML의 input name 속성과 Member 객체의 필드명이 같으면 자동으로 매핑
        invoiceService.createInvoice(invoice);

        return "redirect:/invoices"; // 가입 성공 후 메인 페이지
    }

    // 선택한 인보이스 확인
    @GetMapping("/invoices/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);
        model.addAttribute("invoice", invoice);
        return "view-invoice";
    }

    @GetMapping("/invoices/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);
        model.addAttribute("invoice", invoice);
        return "edit-invoice";
    }
}