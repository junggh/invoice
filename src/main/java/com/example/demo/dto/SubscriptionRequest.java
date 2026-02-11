package com.example.demo.dto;

import lombok.Data;

@Data
public class SubscriptionRequest {
    private String subscriptionId; // PayPal이 생성해준 ID
    private String planId;         // 구독한 상품 ID
}