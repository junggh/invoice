package com.example.demo.controller;

import com.example.demo.entity.Invoice;
import com.example.demo.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final InvoiceService invoiceService;

    /**
     * 비회원 공개 인보이스 조회.
     * 인보이스 이메일에 포함된 공개 링크(/public/invoice/{uuid})로 접근한다.
     * DRAFT 및 DELETED 상태의 인보이스는 서비스 레이어에서 접근이 차단된다.
     */
    @GetMapping("/invoice/{uuid}")
    public String viewPublicInvoice(@PathVariable String uuid, Model model) {
        Invoice invoice = invoiceService.getPublicInvoice(uuid);

        model.addAttribute("invoice", invoice);
        model.addAttribute("subtotal", invoice.getSubtotal());
        model.addAttribute("tax", invoice.getTax());

        return "public-invoice";
    }
}
