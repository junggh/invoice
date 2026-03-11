package com.example.demo.entity;

/**
 * 인보이스 상태 흐름: DRAFT → IN_REVIEW → (APPROVED) 현재 로직에선 삭제 → UNPAID → PAID / OVERDUE
 */
public enum InvoiceStatus {
    DRAFT,      // 초안 (작성 중, 발행 전)
    IN_REVIEW,  // 검토 요청 (관리자 승인 대기)
    APPROVED,   // 승인됨 (발행 가능 상태, 현재 로직에선 제외된 상태)
    UNPAID,     // 발행됨 (미납)
    PAID,       // 결제 완료
    OVERDUE,    // 연체 (납기일 초과 후 스케줄러가 자동 변경)
    DELETED     // 소프트 삭제 (목록에서 숨김, DB에는 유지)
}
