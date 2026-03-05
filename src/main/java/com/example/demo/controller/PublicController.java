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

    @GetMapping("/invoice/{uuid}")
    public String viewPublicInvoice(@PathVariable String uuid, Model model) {
        Invoice invoice = invoiceService.getPublicInvoice(uuid);

        model.addAttribute("invoice", invoice);
        model.addAttribute("subtotal", invoice.getSubtotal());
        model.addAttribute("tax", invoice.getTax());

        return "public-invoice";
    }
}
