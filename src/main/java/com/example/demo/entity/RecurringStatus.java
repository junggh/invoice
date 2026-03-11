package com.example.demo.entity;

/**
 * 반복 인보이스 템플릿 상태 흐름: DRAFT → IN_REVIEW → ACTIVE → PAUSED(현재 구현 안됨) / COMPLETED
 */
public enum RecurringStatus {
    DRAFT,      // 초안 (설정 중)
    IN_REVIEW,  // 검토 요청 (관리자 승인 대기)
    ACTIVE,     // 활성 (스케줄러가 자동 발행)
    PAUSED,     // 일시 중지 (현재 구현 안됨)
    COMPLETED,  // 완료 (종료일 도래 또는 수동 종료)
    DELETED     // 소프트 삭제 (목록에서 숨김, DB에는 유지)
}
