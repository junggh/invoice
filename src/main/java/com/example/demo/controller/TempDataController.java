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

    // ===================================================================================
    // 1. Product (상품)
    // ===================================================================================

    /** 상품 목록 조회. 로그인한 회사에 속한 상품만 표시한다. */
    @GetMapping("/product")
    public String productList(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        Company company = user.getMember().getCompany();
        model.addAttribute("products", productRepository.findByCompany(company));
        return "product-list";
    }

    /** 상품 등록 폼 이동. */
    @GetMapping("/product/new")
    public String productForm(Model model) {
        model.addAttribute("product", new Product());
        return "temp-product";
    }

    /** 상품 등록 처리. 로그인한 회사를 자동으로 설정하여 저장한다. */
    @PostMapping("/product")
    public String createProduct(Product product, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        product.setCompany(company);
        productRepository.save(product);
        return "redirect:/product";
    }

    // ===================================================================================
    // 2. Contact (거래처/고객)
    // ===================================================================================

    /** 거래처 목록 조회. 로그인한 회사에 속한 거래처만 표시한다. */
    @GetMapping("/contact")
    public String contactList(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        Company company = user.getMember().getCompany();
        model.addAttribute("contacts", contactRepository.findByCompany(company));
        return "contact-list";
    }

    /** 거래처 등록 폼 이동. */
    @GetMapping("/contact/new")
    public String contactForm(Model model) {
        model.addAttribute("contact", new Contact());
        return "temp-contact";
    }

    /** 거래처 등록 처리. 로그인한 회사를 자동으로 설정하여 저장한다. */
    @PostMapping("/contact")
    public String createContact(Contact contact, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        contact.setCompany(company);
        contactRepository.save(contact);
        return "redirect:/contact";
    }
}