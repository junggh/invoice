package com.example.demo.entity;

/** PayPal 구독 플랜 종류. 각 플랜은 SubscriptionService의 PLAN_ID 상수와 매핑된다. */
public enum PlanType {
    LITE,  // 라이트 플랜
    BASIC, // 베이직 플랜
    PRO    // 프로 플랜
}
