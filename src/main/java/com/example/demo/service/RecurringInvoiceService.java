package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.RecurringInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurringInvoiceService {

    private final RecurringInvoiceRepository recurringRepository;
    private final InvoiceService invoiceService;

    // ===================================================================================
    // 1. Read Operations (조회)
    // ===================================================================================

    // [조회] 선택된 Status 템플릿 목록 (삭제된 것 제외)
    public List<RecurringInvoice> getTemplates(String statusFilter) {
        // 1. 필터가 없거나 'ALL'이면 기존대로 삭제된 것 빼고 전체 조회
        if (statusFilter == null || statusFilter.isEmpty() || "ALL".equals(statusFilter)) {
            return recurringRepository.findByStatusNotOrderByIdAsc(RecurringStatus.DELETED);
        }

        // 2. 특정 상태 필터링
        try {
            RecurringStatus status = RecurringStatus.valueOf(statusFilter);
            return recurringRepository.findByStatusOrderByIdAsc(status);
        } catch (IllegalArgumentException e) {
            // 잘못된 값이 들어오면 전체 조회 (안전장치)
            return recurringRepository.findByStatusNotOrderByIdAsc(RecurringStatus.DELETED);
        }
    }

    // [조회] 템플릿 상세
    public RecurringInvoice getRecurringInvoice(Long id) {
        return recurringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 템플릿이 없습니다. id=" + id));
    }

    // [조회] 주소로 탬플릿 조회
    public RecurringInvoice getRecurringInvoiceByUuid(String uuid) {
        return recurringRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));
    }

    // ===================================================================================
    // 2. Create & Update Operations (생성 및 수정)
    // ===================================================================================

    // [생성] 신규 템플릿 저장
    @Transactional
    public Long createRecurringInvoice(RecurringInvoice template) {
        if (template.getNextInvoiceDate() == null) {
            template.setNextInvoiceDate(template.getStartDate());
        }
        if (template.getItems() != null) {
            template.getItems().forEach(item -> item.setRecurringInvoice(template));
        }
        return recurringRepository.save(template).getId();
    }

    // [생성] 템플릿 복사 (메모리상 객체 생성)
    public RecurringInvoice copyRecurringInvoice(Long copyId) {
        RecurringInvoice original = getRecurringInvoice(copyId);
        RecurringInvoice copy = new RecurringInvoice();

        // 1. 기본 정보 복사 (상태값, 날짜 등 제외)
        BeanUtils.copyProperties(original, copy,
                "id", "templateNumber", "status", "startDate",
                "nextInvoiceDate", "lastIssuedDate", "items", "endDate", "uuid");

        // 2. 초기값 재설정
        copy.setTemplateNumber(generateNextTemplateNumber());
        copy.setStatus(RecurringStatus.DRAFT);
        copy.setStartDate(LocalDate.now());

        // 3. 아이템 딥 카피
        List<RecurringInvoiceItem> newItems = new ArrayList<>();
        if (original.getItems() != null) {
            for (RecurringInvoiceItem originalItem : original.getItems()) {
                RecurringInvoiceItem newItem = new RecurringInvoiceItem();
                newItem.setProduct(originalItem.getProduct());
                newItem.setQuantity(originalItem.getQuantity());
                newItem.setDiscount(originalItem.getDiscount());
                newItem.setAmount(originalItem.getAmount());
                newItem.setRecurringInvoice(copy);
                newItems.add(newItem);
            }
        }
        copy.setItems(newItems);
        copy.setTotal(original.getTotal());

        return copy;
    }

    // [수정] 템플릿 업데이트
    @Transactional
    public void updateRecurringInvoice(RecurringInvoice formTemplate) {
        RecurringInvoice existingTemplate = getRecurringInvoice(formTemplate.getId());

        // 기본 정보 복사 (일부 날짜 필드 제외)
        BeanUtils.copyProperties(formTemplate, existingTemplate,
                "id", "items", "lastIssuedDate", "nextInvoiceDate", "startDate", "uuid");

        // 날짜 관련 로직 처리
        handleStartDateChange(existingTemplate, formTemplate.getStartDate());

        // 아이템 리스트 교체
        existingTemplate.getItems().clear();
        if (formTemplate.getItems() != null) {
            for (RecurringInvoiceItem item : formTemplate.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null) continue;

                item.setId(null);
                item.setRecurringInvoice(existingTemplate);
                existingTemplate.getItems().add(item);
            }
        }
    }

    private void handleStartDateChange(RecurringInvoice existing, LocalDate newStartDate) {
        if (existing.getNextInvoiceDate() == null) {
            existing.setStartDate(newStartDate);
            existing.setNextInvoiceDate(newStartDate);
        } else if (!existing.getStartDate().equals(newStartDate)) {
            existing.setStartDate(newStartDate);
            // 발행 기록이 없을 때만 다음 예정일을 시작일로 동기화
            if (existing.getLastIssuedDate() == null) {
                existing.setNextInvoiceDate(newStartDate);
            }
        }
    }

    // ===================================================================================
    // 3. Status Management (상태 관리)
    // ===================================================================================

    // [상태변경] 승인 (Review -> Active) 및 즉시 발행 체크
    @Transactional
    public void approveRecurringInvoices(List<Long> ids) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        LocalDate today = LocalDate.now();

        for (RecurringInvoice template : templates) {
            if (template.getStatus() == RecurringStatus.IN_REVIEW) {
                template.setStatus(RecurringStatus.ACTIVE);
                if (template.getTemplateNumber() == null) {
                    template.setTemplateNumber(generateNextTemplateNumber());
                }

                // 승인 시점에 이미 예정일이 도래했다면 즉시 발행
                if (template.getNextInvoiceDate() != null && !template.getNextInvoiceDate().isAfter(today)) {
                    processInvoiceGeneration(template);
                }
            }
        }
    }

    // [상태변경] 종료 (Active -> Completed)
    @Transactional
    public void completeRecurringInvoices(List<Long> ids) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        LocalDate today = LocalDate.now();

        for (RecurringInvoice template : templates) {
            template.setStatus(RecurringStatus.COMPLETED);
            template.setEndDate(today);
            template.setNextInvoiceDate(null); // 자동 생성 중지
        }
    }

    // [상태변경] 삭제 (Soft Delete)
    @Transactional
    public void deleteRecurringInvoices(List<Long> ids) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        templates.forEach(t -> t.setStatus(RecurringStatus.DELETED));
    }

    // ===================================================================================
    // 4. Scheduler & System Operations (자동화 로직)
    // ===================================================================================

    // [스케줄러] 정기 인보이스 자동 생성
    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void generateRecurringInvoices() {
        LocalDate today = LocalDate.now();
        List<RecurringInvoice> templates = recurringRepository
                .findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus.ACTIVE, today);

        templates.forEach(this::processInvoiceGeneration);
    }

    // [유틸] 템플릿 번호 생성 (INVT-0000#)
    public String generateNextTemplateNumber() {
        return recurringRepository.findTopByTemplateNumberStartingWithOrderByTemplateNumberDesc("INVT-")
                .map(last -> {
                    int num = Integer.parseInt(last.getTemplateNumber().substring(5));
                    return String.format("INVT-%05d", num + 1);
                })
                .orElse("INVT-00001");
    }

    // ===================================================================================
    // 5. Internal Helper Methods (내부 로직)
    // ===================================================================================

    // 공통: 인보이스 생성 및 템플릿 날짜 갱신
    private void processInvoiceGeneration(RecurringInvoice template) {
        createInvoiceFromTemplate(template);

        template.setLastIssuedDate(LocalDate.now());
        template.calculateNextDate();

        // 종료일 체크
        if (template.getEndDate() != null && template.getNextInvoiceDate().isAfter(template.getEndDate())) {
            template.setStatus(RecurringStatus.COMPLETED);
            template.setNextInvoiceDate(null);
        }
    }

    // 내부: 템플릿 -> 실제 인보이스 객체 변환 및 저장 요청
    private void createInvoiceFromTemplate(RecurringInvoice template) {
        Invoice newInvoice = new Invoice();

        newInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        newInvoice.setIssuedDate(LocalDate.now());
        newInvoice.setDueDate(newInvoice.getIssuedDate().plusDays(template.getDueDateDays()));
        newInvoice.setStatus(template.isAutoSend() ? InvoiceStatus.UNPAID : InvoiceStatus.DRAFT);

        // 고객 정보 복사
        Contact c = template.getContact();
        newInvoice.setContact(c);
        newInvoice.setCustomerName(c.getName());
        newInvoice.setCustomerEmail(c.getEmail());
        newInvoice.setCustomerCompanyName(c.getCompanyName());
        newInvoice.setCustomerBillTo(c.getBillTo());
        newInvoice.setCustomerCurrency(c.getCurrency());
        newInvoice.setSalesPerson(template.getSalesPerson());
        newInvoice.setReference(template.getReference());

        // 아이템 복사
        List<InvoiceItem> newItems = new ArrayList<>();
        for (RecurringInvoiceItem tItem : template.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setProduct(tItem.getProduct());
            item.setQuantity(tItem.getQuantity());
            item.setDiscount(tItem.getDiscount());
            item.setAmount(tItem.getAmount());
            item.setInvoice(newInvoice);
            newItems.add(item);
        }
        newInvoice.setItems(newItems);
        newInvoice.setTotal(template.getTotal());
        newInvoice.setBalanceDue(template.getTotal());

        invoiceService.createInvoice(newInvoice);
    }
}
