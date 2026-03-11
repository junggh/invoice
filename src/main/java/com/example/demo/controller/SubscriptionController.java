package com.example.demo.controller;

import com.example.demo.dto.SubscriptionRequest;
import com.example.demo.entity.PlanType;
import com.example.demo.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // ===================================================================================
    // 1. 구독 페이지
    // ===================================================================================

    /**
     * 구독 플랜 선택 페이지 이동.
     * ADMIN 권한이 없으면 대시보드로 리다이렉트한다.
     * 현재 회사의 플랜 정보와 PayPal 구독 ID를 함께 전달하여 현재 구독 상태를 표시한다.
     */
    @GetMapping("/subscribe")
    public String subscribePage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        String email = userDetails.getUsername();

        if (!subscriptionService.isAdmin(email)) {
            return "redirect:/invoices?error=unauthorized";
        }

        PlanType currentPlan = subscriptionService.getCompanyPlan(email);
        String currentSubscriptionId = subscriptionService.getCompanySubscriptionId(email);

        model.addAttribute("currentPlan", currentPlan);
        model.addAttribute("currentSubscriptionId", currentSubscriptionId);

        return "subscribe";
    }

    // ===================================================================================
    // 2. 구독 활성화
    // ===================================================================================

    /**
     * PayPal 결제 성공 후 구독 활성화 API.
     * 프론트엔드에서 PayPal 결제 완료 시 비동기로 호출되며, 회사의 플랜과 구독 ID를 저장한다.
     */
    @PostMapping("/api/subscription/success")
    @ResponseBody
    public ResponseEntity<String> handleSubscriptionSuccess(
            @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            subscriptionService.activateSubscription(userDetails.getUsername(), request);
            return ResponseEntity.ok("Subscription Activated.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}