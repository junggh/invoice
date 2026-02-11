package com.example.demo.entity;

public enum TaxType {
    TAX_EXCLUSIVE("Tax Exclusive"), // 세금 별도
    TAX_INCLUSIVE("Tax Inclusive"), // 세금 포함
    NO_TAX("No Tax");               // 세금 없음

    private final String displayValue;

    TaxType(String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }
}
