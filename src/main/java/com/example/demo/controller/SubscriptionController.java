package com.example.demo.controller;

import com.example.demo.dto.SubscriptionRequest;
import com.example.demo.entity.PlanType;
import com.example.demo.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Security 설정에 따라 다름
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // 1. 구독 페이지 이동
    @GetMapping("/subscribe")
    public String subscribePage( @AuthenticationPrincipal UserDetails userDetails, Model model ) {
        String email = userDetails.getUsername();

        // [중요] Admin 권한 체크
        if (!subscriptionService.isAdmin(email)) {
            // 관리자가 아니면 인보이스 목록(메인)으로 튕겨냄 (또는 에러페이지)
            return "redirect:/invoices?error=unauthorized";
        }

        // 현재 회사의 플랜 정보 조회
        PlanType currentPlan = subscriptionService.getCompanyPlan(email);

        model.addAttribute("currentPlan", currentPlan);
        // Thymeleaf에서 Enum 비교를 위해 T(...) 문법을 쓰거나, String으로 변환해서 넘겨도 됨
        // 여기서는 Enum 자체를 넘김

        return "subscribe";
    }

    // 2. 결제 성공 시 호출될 API
    @PostMapping("/api/subscription/success")
    @ResponseBody
    public ResponseEntity<String> handleSubscriptionSuccess(
            @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) { // 현재 로그인한 사용자 정보

        try {
            subscriptionService.activateSubscription(userDetails.getUsername(), request);
            return ResponseEntity.ok("Subscription Activated.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}