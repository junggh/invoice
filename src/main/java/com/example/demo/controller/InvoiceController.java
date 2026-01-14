package com.example.demo.controller;

import com.example.demo.entity.Invoice;
import com.example.demo.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String status, Model model) {
        // 1. 서비스에서 리스트 가져오기 (status가 null이면 전체 가져옴)
        List<Invoice> invoices = invoiceService.getInvoices(status);

        // 2. 모델에 담기
        model.addAttribute("invoices", invoices);

        // 3. 현재 어떤 탭이 선택되었는지 알려주기 (탭 활성화 효과용)
        model.addAttribute("currentStatus", (status == null || status.isEmpty()) ? "Overview" : status);

        return "home";
    }

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