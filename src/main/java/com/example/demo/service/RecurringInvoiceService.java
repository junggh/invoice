package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.RecurringInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurringInvoiceService {

    private final RecurringInvoiceRepository recurringRepository;
    private final InvoiceService invoiceService;

    // ===================================================================================
    // 1. 조회
    // ===================================================================================

    /**
     * 반복 템플릿 목록 조회. 삭제된 템플릿을 제외하며, 상태 필터와 검색어를 적용한다.
     * ID 기준 내림차순(최신순), 페이지당 15개.
     */
    public Page<RecurringInvoice> getTemplates(String statusFilter, Company company, String keyword, int page) {
        Pageable pageable = PageRequest.of(page - 1, 15, Sort.by(Sort.Direction.DESC, "id"));

        // 필터가 없거나 'ALL'이면 DELETED를 제외한 전체 조회
        if (statusFilter == null || statusFilter.isEmpty() || "ALL".equals(statusFilter)) {
            return recurringRepository.findTemplatesByKeywordAndStatusNot(company, RecurringStatus.DELETED, keyword, pageable);
        }

        try {
            RecurringStatus status = RecurringStatus.valueOf(statusFilter);
            return recurringRepository.findTemplatesByKeywordAndStatus(company, status, keyword, pageable);
        } catch (IllegalArgumentException e) {
            // 잘못된 상태값이 들어오면 전체 조회로 폴백
            return recurringRepository.findTemplatesByKeywordAndStatusNot(company, RecurringStatus.DELETED, keyword, pageable);
        }
    }

    /** ID로 반복 템플릿 단건 조회. */
    public RecurringInvoice getRecurringInvoice(Long id) {
        return recurringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found. id=" + id));
    }

    /**
     * UUID로 반복 템플릿 단건 조회. 요청한 회사 소유의 템플릿이 아니면 접근을 차단한다.
     */
    public RecurringInvoice getRecurringInvoiceByUuid(String uuid, Company company) {
        RecurringInvoice invoice = recurringRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

        if (!invoice.getCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("You do not have permission to access this template.");
        }
        return invoice;
    }

    // ===================================================================================
    // 2. 생성 및 수정
    // ===================================================================================

    /**
     * 신규 반복 템플릿 저장.
     * nextInvoiceDate가 없으면 startDate로 초기화하고, 아이템 양방향 연관관계를 설정한다.
     */
    @Transactional
    public void createRecurringInvoice(RecurringInvoice template, Member member) {
        if (template.getNextInvoiceDate() == null) {
            template.setNextInvoiceDate(template.getStartDate());
        }
        template.setCompany(member.getCompany());
        if (template.getItems() != null) {
            template.getItems().forEach(item -> item.setRecurringInvoice(template));
        }
        recurringRepository.save(template);
    }

    /**
     * 기존 반복 템플릿을 복사하여 새 객체를 생성한다 (DB 저장은 하지 않음).
     * 상태·날짜·식별자는 새 값으로 재설정하고, 아이템은 딥 카피한다.
     */
    public RecurringInvoice copyRecurringInvoice(Long copyId) {
        RecurringInvoice original = getRecurringInvoice(copyId);
        RecurringInvoice copy = new RecurringInvoice();

        // 상태·날짜·식별자를 제외한 나머지 필드 복사
        BeanUtils.copyProperties(original, copy,
                "id", "templateNumber", "status", "startDate",
                "nextInvoiceDate", "lastIssuedDate", "items", "endDate", "uuid");

        // 새 값으로 재설정
        copy.setTemplateNumber(generateNextTemplateNumber(original.getCompany()));
        copy.setStatus(RecurringStatus.DRAFT);
        copy.setStartDate(LocalDate.now(getZoneId(original.getCompany())));

        // 아이템 딥 카피
        List<RecurringInvoiceItem> newItems = new ArrayList<>();
        if (original.getItems() != null) {
            for (RecurringInvoiceItem originalItem : original.getItems()) {
                RecurringInvoiceItem newItem = new RecurringInvoiceItem();
                BeanUtils.copyProperties(originalItem, newItem, "id", "recurringInvoice");
                newItem.setRecurringInvoice(copy);
                newItems.add(newItem);
            }
        }
        copy.setItems(newItems);
        copy.setTotal(original.getTotal());

        return copy;
    }

    /**
     * 반복 템플릿 수정. startDate 변경 시 nextInvoiceDate 동기화 여부를 별도 로직으로 처리하며,
     * 아이템은 기존 목록을 지우고 새로 교체한다 (orphanRemoval 활용).
     */
    @Transactional
    public void updateRecurringInvoice(RecurringInvoice formTemplate) {
        RecurringInvoice existingTemplate = getRecurringInvoice(formTemplate.getId());

        // 일부 날짜 필드를 제외한 나머지 필드 덮어쓰기
        BeanUtils.copyProperties(formTemplate, existingTemplate,
                "id", "items", "lastIssuedDate", "nextInvoiceDate", "startDate", "uuid", "company", "templateNumber");

        // startDate 변경 시 nextInvoiceDate 동기화 처리
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

    // ===================================================================================
    // 3. 상태 변경
    // ===================================================================================

    /**
     * 반복 템플릿 일괄 승인 (IN_REVIEW → ACTIVE).
     * 승인 시점에 nextInvoiceDate가 이미 도래했다면 즉시 인보이스를 생성한다.
     */
    @Transactional
    public void approveRecurringInvoices(List<Long> ids, Member member) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        LocalDate today = LocalDate.now(getZoneId(member.getCompany()));

        for (RecurringInvoice template : templates) {
            if (template.getStatus() == RecurringStatus.IN_REVIEW) {
                template.setStatus(RecurringStatus.ACTIVE);
                if (template.getTemplateNumber() == null) {
                    template.setTemplateNumber(generateNextTemplateNumber(member.getCompany()));
                }

                // 승인 시점에 이미 예정일이 도래했다면 즉시 인보이스 생성
                if (template.getNextInvoiceDate() != null && !template.getNextInvoiceDate().isAfter(today)) {
                    processInvoiceGeneration(template);
                }
            }
        }
    }

    /**
     * 반복 템플릿 종료 처리 (ACTIVE → COMPLETED).
     * 종료일을 오늘로 설정하고 nextInvoiceDate를 null로 초기화하여 자동 생성을 중지한다.
     */
    @Transactional
    public void completeRecurringInvoices(List<Long> ids, Company company) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        LocalDate today = LocalDate.now(getZoneId(company));

        for (RecurringInvoice template : templates) {
            template.setStatus(RecurringStatus.COMPLETED);
            template.setEndDate(today);
            template.setNextInvoiceDate(null);
        }
    }

    /** 반복 템플릿 소프트 삭제. 상태를 DELETED로 변경하며 실제 데이터는 보존된다. */
    @Transactional
    public void deleteRecurringInvoices(List<Long> ids) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        templates.forEach(t -> t.setStatus(RecurringStatus.DELETED));
    }

    // ===================================================================================
    // 4. 스케줄러
    // ===================================================================================

    /**
     * 매 정각 실행. 현재 시각이 자정(hour == 0)인 timezone의 회사만 대상으로
     * nextInvoiceDate가 도래한 ACTIVE 템플릿의 인보이스를 자동 생성한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void generateRecurringInvoices() {
        for (Timezone tz : Timezone.values()) {
            if (LocalTime.now(tz.toZoneId()).getHour() == 0) {
                LocalDate today = LocalDate.now(tz.toZoneId());
                List<RecurringInvoice> templates = recurringRepository
                        .findByCompanyTimezoneAndStatusAndNextInvoiceDateLessThanEqual(tz, RecurringStatus.ACTIVE, today);
                templates.forEach(this::processInvoiceGeneration);
            }
        }
    }

    /**
     * 서버 시작 시 모든 timezone에 대해 반복 인보이스를 일괄 생성한다.
     * 서버 다운타임 중 처리되지 못한 자동 발행을 보정하기 위해 실행된다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartupGenerateRecurring() {
        for (Timezone tz : Timezone.values()) {
            LocalDate today = LocalDate.now(tz.toZoneId());
            List<RecurringInvoice> templates = recurringRepository
                    .findByCompanyTimezoneAndStatusAndNextInvoiceDateLessThanEqual(tz, RecurringStatus.ACTIVE, today);
            templates.forEach(this::processInvoiceGeneration);
        }
    }

    // ===================================================================================
    // 5. 유틸
    // ===================================================================================

    /**
     * 다음 템플릿 번호를 생성한다 (INVT-#####, 5자리 zero-padded).
     * 해당 회사의 마지막 번호를 조회하여 +1 증가시킨다.
     */
    public String generateNextTemplateNumber(Company company) {
        return recurringRepository.findTopByCompanyAndTemplateNumberStartingWithOrderByTemplateNumberDesc(company, "INVT-")
                .map(last -> {
                    int num = Integer.parseInt(last.getTemplateNumber().substring(5));
                    return String.format("INVT-%05d", num + 1);
                })
                .orElse("INVT-00001");
    }

    /** 해당 회사에 동일한 템플릿 번호가 이미 존재하는지 확인한다. */
    public boolean isTemplateNumberExists(String templateNumber, Company company) {
        return recurringRepository.existsByCompanyAndTemplateNumber(company, templateNumber);
    }

    /** 회사의 timezone 기반 ZoneId를 반환한다. timezone이 null이면 UTC를 폴백으로 사용한다. */
    private ZoneId getZoneId(Company company) {
        if (company != null && company.getTimezone() != null) {
            return company.getTimezone().toZoneId();
        }
        return ZoneId.of("UTC");
    }

    // ===================================================================================
    // 6. 내부 헬퍼
    // ===================================================================================

    /**
     * 인보이스 생성 후 템플릿의 날짜 정보를 갱신한다.
     * nextInvoiceDate를 다음 주기로 계산하고, 종료일을 초과하면 COMPLETED로 전환한다.
     */
    private void processInvoiceGeneration(RecurringInvoice template) {
        createInvoiceFromTemplate(template);

        template.setLastIssuedDate(LocalDate.now(getZoneId(template.getCompany())));
        template.calculateNextDate();

        // 다음 예정일이 종료일을 초과하면 자동 종료
        if (template.getEndDate() != null && template.getNextInvoiceDate().isAfter(template.getEndDate())) {
            template.setStatus(RecurringStatus.COMPLETED);
            template.setNextInvoiceDate(null);
        }
    }

    /**
     * 반복 템플릿으로부터 실제 Invoice 객체를 생성하여 저장한다.
     * autoSend가 true이면 UNPAID 상태로 생성하고 이메일을 발송한다.
     * false이면 DRAFT 상태로만 저장한다.
     */
    private void createInvoiceFromTemplate(RecurringInvoice template) {
        Invoice newInvoice = new Invoice();

        Company ownerCompany = template.getCompany();
        newInvoice.setCompany(ownerCompany);
        newInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber(ownerCompany));
        newInvoice.setIssuedDate(LocalDate.now(getZoneId(ownerCompany)));
        newInvoice.setDueDate(newInvoice.getIssuedDate().plusDays(template.getDueDateDays()));
        newInvoice.setStatus(template.isAutoSend() ? InvoiceStatus.UNPAID : InvoiceStatus.DRAFT);

        // 고객 정보 복사 (Contact에서 스냅샷)
        Contact c = template.getContact();
        newInvoice.setContact(c);
        newInvoice.setCustomerName(c.getName());
        newInvoice.setCustomerEmail(c.getEmail());
        newInvoice.setCustomerCompanyName(c.getCompanyName());
        newInvoice.setCustomerBillTo(c.getBillTo());
        newInvoice.setCustomerCurrency(ownerCompany.getCurrency());
        newInvoice.setSalesPerson(template.getSalesPerson());
        newInvoice.setReference(template.getReference());
        newInvoice.setTaxType(template.getTaxType());
        newInvoice.setTax(template.getTax());
        newInvoice.setSubtotal(template.getSubtotal());

        // 아이템 복사 (RecurringInvoiceItem → InvoiceItem 변환)
        List<InvoiceItem> newItems = new ArrayList<>();
        for (RecurringInvoiceItem tItem : template.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setProduct(tItem.getProduct());
            item.setQuantity(tItem.getQuantity());
            item.setDiscount(tItem.getDiscount());
            item.setDiscountType(tItem.getDiscountType());
            item.setAmount(tItem.getAmount());
            item.setGstCode(tItem.getGstCode());
            item.setTaxAmount(tItem.getTaxAmount());
            item.setInvoice(newInvoice);
            newItems.add(item);
        }
        newInvoice.setItems(newItems);
        newInvoice.setTotal(template.getTotal());
        newInvoice.setBalanceDue(template.getTotal());

        invoiceService.autoCreateInvoice(newInvoice);

        // autoSend가 활성화된 경우 이메일 발송
        if (newInvoice.getStatus() == InvoiceStatus.UNPAID) {
            invoiceService.sendUnpaidInvoiceEmail(newInvoice);
        }
    }

    /**
     * startDate 변경 시 nextInvoiceDate 동기화를 처리한다.
     * 발행 이력이 없을 때만 nextInvoiceDate를 새 startDate로 동기화하여
     * 이미 발행된 이후 시작일을 수정해도 다음 예정일이 초기화되지 않도록 한다.
     */
    private void handleStartDateChange(RecurringInvoice existing, LocalDate newStartDate) {
        if (existing.getNextInvoiceDate() == null) {
            existing.setStartDate(newStartDate);
            existing.setNextInvoiceDate(newStartDate);
        } else if (!existing.getStartDate().equals(newStartDate)) {
            existing.setStartDate(newStartDate);
            // 발행 이력이 없을 때만 다음 예정일을 시작일로 동기화
            if (existing.getLastIssuedDate() == null) {
                existing.setNextInvoiceDate(newStartDate);
            }
        }
    }
}
