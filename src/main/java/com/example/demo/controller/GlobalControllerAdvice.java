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

/**
 * 모든 컨트롤러 요청 전에 실행되어 공통 뷰 속성을 주입하는 전역 Advice.
 * 사이드바/상단바에 표시되는 회사명, 통화 기호, 유저 이니셜을 뷰에 전달한다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final MemberRepository memberRepository;

    // 주요 통화 코드 → 기호 정적 매핑 (빠른 조회를 위해 Map으로 관리)
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

    /**
     * 통화 코드를 통화 기호로 변환.
     * 정적 매핑에 없는 통화 코드는 Java Currency API로 폴백하며, 그것도 실패하면 코드 자체를 반환한다.
     */
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

    /**
     * 모든 컨트롤러 요청 전에 실행되어 뷰 공통 속성을 Model에 주입.
     * - globalCompanyName: 상단바/사이드바에 표시되는 회사명
     * - globalCompanyCurrency: 통화 코드 (예: AUD)
     * - globalCurrencySymbol: 통화 기호 (예: A$)
     * - globalUserInitials: 프로필 아바타 이니셜 (예: "June Young" → "JY")
     */
    @ModelAttribute
    public void addGlobalAttributes(Principal principal, Model model) {
        if (principal != null) {
            memberRepository.findByEmail(principal.getName()).ifPresent(member -> {

                // 회사 이름 및 통화 세팅
                if (member.getCompany() != null) {
                    model.addAttribute("globalCompanyName", member.getCompany().getBusinessName());
                    model.addAttribute("globalCompanyCurrency", member.getCompany().getCurrency());
                    model.addAttribute("globalCurrencySymbol", resolveCurrencySymbol(member.getCompany().getCurrency()));
                } else if ("SUPER_ADMIN".equals(member.getRole())) {
                    model.addAttribute("globalCompanyName", "System Admin");
                } else {
                    model.addAttribute("globalCompanyName", "No Company");
                }

                // 유저 이니셜 세팅 (이름이 없으면 기본값 "U")
                String firstName = member.getFirstName() != null ? member.getFirstName() : "";
                String lastName = member.getLastName() != null ? member.getLastName() : "";

                String initials = "";
                if (!firstName.isEmpty()) initials += firstName.substring(0, 1).toUpperCase();
                if (!lastName.isEmpty()) initials += lastName.substring(0, 1).toUpperCase();

                model.addAttribute("globalUserInitials", initials.isEmpty() ? "U" : initials);
            });
        }
    }
}