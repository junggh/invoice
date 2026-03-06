package com.example.demo.controller;

import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Currency;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final MemberRepository memberRepository;

    // 주요 통화 코드 → 기호 매핑 (빠른 조회)
    private static final Map<String, String> CURRENCY_SYMBOLS = Map.ofEntries(
        Map.entry("AUD", "A$"),
        Map.entry("USD", "$"),
        Map.entry("EUR", "€"),
        Map.entry("GBP", "£"),
        Map.entry("JPY", "¥"),
        Map.entry("KRW", "₩"),
        Map.entry("CNY", "¥"),
        Map.entry("CAD", "CA$"),
        Map.entry("NZD", "NZ$"),
        Map.entry("SGD", "S$"),
        Map.entry("HKD", "HK$"),
        Map.entry("CHF", "Fr"),
        Map.entry("INR", "₹"),
        Map.entry("MYR", "RM"),
        Map.entry("THB", "฿"),
        Map.entry("TWD", "NT$")
    );

    // 통화 코드 → 통화 기호 변환 (매핑에 없으면 Java Currency API 폴백)
    private static String resolveCurrencySymbol(String code) {
        if (code == null || code.isBlank()) return "";
        String upper = code.toUpperCase();
        String mapped = CURRENCY_SYMBOLS.get(upper);
        if (mapped != null) return mapped;
        try {
            return Currency.getInstance(upper).getSymbol();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    // @ModelAttribute가 붙은 메서드는 모든 컨트롤러의 요청 전에 항상 실행됩니다.
    @ModelAttribute
    public void addGlobalAttributes(Principal principal, Model model) {
        // 로그인한 상태인지 확인
        if (principal != null) {
            memberRepository.findByEmail(principal.getName()).ifPresent(member -> {

                // 1. 회사 이름/통화 세팅
                if (member.getCompany() != null) {
                    model.addAttribute("globalCompanyName", member.getCompany().getBusinessName());
                    model.addAttribute("globalCompanyCurrency", member.getCompany().getCurrency());
                    model.addAttribute("globalCurrencySymbol", resolveCurrencySymbol(member.getCompany().getCurrency()));
                } else if ("SUPER_ADMIN".equals(member.getRole())) {
                    model.addAttribute("globalCompanyName", "System Admin"); // 개발자 계정용
                } else {
                    model.addAttribute("globalCompanyName", "No Company");
                }

                // 2. 유저 이니셜 세팅 (예: "June", "Young" -> "JY")
                String firstName = member.getFirstName() != null ? member.getFirstName() : "";
                String lastName = member.getLastName() != null ? member.getLastName() : "";

                String initials = "";
                if (!firstName.isEmpty()) initials += firstName.substring(0, 1).toUpperCase();
                if (!lastName.isEmpty()) initials += lastName.substring(0, 1).toUpperCase();

                // 이름이 비어있으면 기본값 "U" (User) 전달
                model.addAttribute("globalUserInitials", initials.isEmpty() ? "U" : initials);
            });
        }
    }
}