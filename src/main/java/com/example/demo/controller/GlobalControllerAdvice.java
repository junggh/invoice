package com.example.demo.controller;

import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final MemberRepository memberRepository;

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