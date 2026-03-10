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
    // 1. Product (상품)
    // ==========================================

    @GetMapping("/product")
    public String productList(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        Company company = user.getMember().getCompany();
        model.addAttribute("products", productRepository.findByCompany(company));
        return "product-list";
    }

    @GetMapping("/product/new")
    public String productForm(Model model) {
        model.addAttribute("product", new Product());
        return "temp-product";
    }

    @PostMapping("/product")
    public String createProduct(Product product, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        product.setCompany(company);
        productRepository.save(product);
        return "redirect:/product";
    }

    // ==========================================
    // 2. Contact (거래처/고객)
    // ==========================================

    @GetMapping("/contact")
    public String contactList(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        Company company = user.getMember().getCompany();
        model.addAttribute("contacts", contactRepository.findByCompany(company));
        return "contact-list";
    }

    @GetMapping("/contact/new")
    public String contactForm(Model model) {
        model.addAttribute("contact", new Contact());
        return "temp-contact";
    }

    @PostMapping("/contact")
    public String createContact(Contact contact, @AuthenticationPrincipal CustomUserDetails user) {
        Company company = user.getMember().getCompany();
        contact.setCompany(company);
        contactRepository.save(contact);
        return "redirect:/contact";
    }
}