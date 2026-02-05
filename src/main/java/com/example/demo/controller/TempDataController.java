package com.example.demo.controller;

import com.example.demo.entity.Company;
import com.example.demo.entity.Contact;
import com.example.demo.entity.Product;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class TempDataController {

    private final ProductRepository productRepository;
    private final ContactRepository contactRepository;

    // ==========================================
    // 1. Product (상품) 생성
    // ==========================================

    @GetMapping("/product")
    public String productForm(Model model) {
        model.addAttribute("product", new Product());
        return "temp-product";
    }

    @PostMapping("/product")
    public String createProduct(Product product, @AuthenticationPrincipal CustomUserDetails user) {
        // 로그인한 멤버의 회사 정보 자동 주입
        Company company = user.getMember().getCompany();
        product.setCompany(company);

        productRepository.save(product);

        return "redirect:/product"; // 연속 입력을 위해 폼으로 리다이렉트
    }

    // ==========================================
    // 2. Contact (거래처/고객) 생성
    // ==========================================

    @GetMapping("/contact")
    public String contactForm(Model model) {
        model.addAttribute("contact", new Contact());
        return "temp-contact";
    }

    @PostMapping("/contact")
    public String createContact(Contact contact, @AuthenticationPrincipal CustomUserDetails user) {
        // 로그인한 멤버의 회사 정보 자동 주입
        Company company = user.getMember().getCompany();
        contact.setCompany(company);

        contactRepository.save(contact);

        return "redirect:/contact"; // 연속 입력을 위해 폼으로 리다이렉트
    }
}