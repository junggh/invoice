package com.example.demo.controller;

import com.example.demo.entity.Invoice;
import com.example.demo.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/invoices/new")
    public String createInvoiceForm(Model model) {
        String nextNum = invoiceService.generateNextInvoiceNumber();
        model.addAttribute("nextInvoiceNum", nextNum);
        return "newinvoice";
    }

    @PostMapping("/api/invoices")
    public String createInvoice(Invoice invoice) {
        // HTML의 input name 속성과 Member 객체의 필드명이 같으면 자동으로 매핑.
        invoiceService.createInvoice(invoice);

        return "redirect:/invoices/new"; // 가입 성공 후 메인 페이지로 이동
    }
}