package com.example.demo.controller;

import com.example.demo.entity.*;
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
        // Total, Balance Due 계산 전달
        BigDecimal totalAmount = invoiceService.calculateTotalAmount(status);
        BigDecimal totalBalance = invoiceService.calculateTotalBalance(status);

        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalBalance", totalBalance);
        // 현재 탭 활성화용
        model.addAttribute("currentStatus", (status == null || status.isEmpty()) ? "Overview" : status);

        return "home";
    }
    // 새 인보이스 작성 화면
    @GetMapping("/invoices/new")
    public String createInvoiceForm(Model model) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.getItems().add(new InvoiceItem());
        model.addAttribute("invoice", invoice);
//        String nextNum = invoiceService.generateNextInvoiceNumber();
//        model.addAttribute("nextInvoiceNum", nextNum);
        List<Product> products = productRepository.findAll();
        model.addAttribute("products", products);
        List<Contact> contacts = contactRepository.findAll();
        model.addAttribute("contacts", contacts);
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
        model.addAttribute("invoice", invoice);
        return "view-invoice";
    }
    // 수정 가능 인보이스 확인
    @GetMapping("/invoices/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);
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
        invoiceService.updateInvoice(invoice);

        return "redirect:/invoices";
    }
    // 인보이스 삭제 Post
    @PostMapping("/api/invoices/delete")
    public String deleteInvoices(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            invoiceService.deleteInvoices(ids);
        }
        return "redirect:/invoices"; // 삭제 후 목록 새로고침
    }
}