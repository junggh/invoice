package com.example.demo.entity;

import lombok.Getter;

@Getter
public enum GstCode {
    // --- 매출 (Income) 관련 ---
    GST_ON_INCOME("GST on Income", 0.10),       // 일반 매출 (10%)
    GST_FREE_INCOME("GST Free Income", 0.00),   // 면세 매출 (0%)

    // --- 비용 (Expenses) 관련 ---
    GST_ON_EXPENSES("GST on Expenses", 0.10),   // 과세 매입 (10%)
    GST_FREE_EXPENSES("GST Free Expenses", 0.00), // 면세 매입 (0%)
    GST_ON_IMPORTS("GST on Imports", 0.10),     // 수입 부가세 (10%)

    // --- 공통 ---
    BAS_EXCLUDED("BAS Excluded", 0.00);         // 신고 제외 (0%)

    private final String displayValue;
    private final double rate;

    GstCode(String displayValue, double rate) {
        this.displayValue = displayValue;
        this.rate = rate;
    }
}
