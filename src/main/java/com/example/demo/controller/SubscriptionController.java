package com.example.demo.controller;

import com.example.demo.dto.SubscriptionRequest;
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
        boolean isPremium = subscriptionService.isPremiumUser(userDetails.getUsername());
        model.addAttribute("isPremium", isPremium);

        return "subscribe"; // templates/subscribe.html
    }

    // 2. 결제 성공 시 호출될 API
    @PostMapping("/api/subscription/success")
    @ResponseBody
    public ResponseEntity<String> handleSubscriptionSuccess(
            @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) { // 현재 로그인한 사용자 정보

        // 로그인한 사용자의 ID(username)을 넘겨서 처리
        subscriptionService.activateSubscription(userDetails.getUsername(), request);

        return ResponseEntity.ok("구독이 활성화되었습니다.");
    }
}