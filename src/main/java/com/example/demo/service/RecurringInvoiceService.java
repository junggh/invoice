package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.repository.RecurringInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringInvoiceService {

    private final RecurringInvoiceRepository recurringRepository;
    private final InvoiceService invoiceService;

    public Long createRecurringInvoice(RecurringInvoice template) {
        // [추가] 다음 예정일이 없으면 시작일로 설정 (IN_REVIEW 상태라도 스케줄은 잡아둠)
        if (template.getNextInvoiceDate() == null) {
            template.setNextInvoiceDate(template.getStartDate());
        }

        // 자식 엔티티에 부모 연결
        if (template.getItems() != null) {
            for (RecurringInvoiceItem item : template.getItems()) {
                item.setRecurringInvoice(template);
            }
        }

        // 3. 저장
        return recurringRepository.save(template).getId();
    }
    // 탬플릿 업데이트 로직
    public void updateRecurringInvoice(RecurringInvoice formTemplate) {
        // 기존 데이터 조회
        RecurringInvoice existingTemplate = getRecurringInvoice(formTemplate.getId());

        // 기본 정보 복사
        BeanUtils.copyProperties(formTemplate, existingTemplate, "id", "items", "lastIssuedDate", "nextInvoiceDate", "startDate");

        // 1. NextInvoiceDate가 아예 없는 경우 (안전장치)
        if (existingTemplate.getNextInvoiceDate() == null) {
            existingTemplate.setStartDate(formTemplate.getStartDate());
            existingTemplate.setNextInvoiceDate(formTemplate.getStartDate());
        }
        // 2. StartDate가 변경된 경우의 로직 처리
        else if (!existingTemplate.getStartDate().equals(formTemplate.getStartDate())) {
            // 일단 시작일은 업데이트
            existingTemplate.setStartDate(formTemplate.getStartDate());

            // 최근 발행일(lastIssuedDate)이 'NULL'일 때만 다음 예정일을 새 시작일로 동기화
            // 한 번이라도 발행된 적이 있으면 스케줄이 꼬이지 않도록 nextInvoiceDate를 건드리지 않음
            if (existingTemplate.getLastIssuedDate() == null) {
                existingTemplate.setNextInvoiceDate(formTemplate.getStartDate());
            }
        }

        // 아이템 리스트 업데이트
        existingTemplate.getItems().clear(); // 기존 아이템 삭제 (OrphanRemoval 동작)

        if (formTemplate.getItems() != null) {
            for (RecurringInvoiceItem item : formTemplate.getItems()) {
                // 빈 아이템 방지
                if (item.getProduct() == null || item.getProduct().getId() == null) {
                    continue;
                }

                // 새 아이템 세팅
                item.setId(null); // 신규 생성으로 처리
                item.setRecurringInvoice(existingTemplate); // 부모 연결
                existingTemplate.getItems().add(item);
            }
        }
    }
    // [추가] 탬플릿 복사 (Form에 뿌려줄 임시 객체 생성)
    public RecurringInvoice copyRecurringInvoice(Long copyId) {
        RecurringInvoice original = getRecurringInvoice(copyId); // 기존 데이터 조회
        RecurringInvoice copy = new RecurringInvoice();

        // 1. 기본 정보 복사 (ID, 번호, 날짜 등 고유값 제외)
        // 제외할 필드: id, templateNumber, status, startDate, nextInvoiceDate, lastIssuedDate, items
        BeanUtils.copyProperties(original, copy,
                "id", "templateNumber", "status", "startDate",
                "nextInvoiceDate", "lastIssuedDate", "items", "endDate");

        // 2. 고유값 재설정
        copy.setTemplateNumber(generateNextTemplateNumber()); // 새 번호 채번
        copy.setStatus(RecurringStatus.DRAFT); // 복사본은 무조건 DRAFT 시작
        copy.setStartDate(LocalDate.now());    // 시작일은 오늘로 리셋
        // next, last 날짜는 null 상태 유지 (승인 시 설정됨)

        // 3. 아이템 깊은 복사 (Deep Copy)
        // 원본의 아이템을 하나씩 꺼내서 새 아이템 객체로 만들어 넣어야 함
        List<RecurringInvoiceItem> newItems = new ArrayList<>();
        if (original.getItems() != null) {
            for (RecurringInvoiceItem originalItem : original.getItems()) {
                RecurringInvoiceItem newItem = new RecurringInvoiceItem();
                newItem.setProduct(originalItem.getProduct());
                newItem.setQuantity(originalItem.getQuantity());
                newItem.setDiscount(originalItem.getDiscount());
                newItem.setAmount(originalItem.getAmount());

                newItem.setRecurringInvoice(copy); // 부모 연결
                newItems.add(newItem);
            }
        }
        copy.setItems(newItems);
        copy.setTotal(original.getTotal());

        return copy;
    }
    // [추가] TMP-0000# 번호 생성기
    public String generateNextTemplateNumber() {
        return recurringRepository.findTopByOrderByIdDesc()
                .map(lastTemplate -> {
                    String lastNumber = lastTemplate.getTemplateNumber();
                    // "INVT-00005" -> 5 추출
                    int num = Integer.parseInt(lastNumber.substring(5));
                    return String.format("INVT-%05d", num + 1);
                })
                .orElse("INVT-00001");
    }

    // --- [변경 1] 스케줄러 로직 ---
    @Scheduled(cron = "0 0 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    public void generateRecurringInvoices() {
        LocalDate today = LocalDate.now();
        List<RecurringInvoice> templates = recurringRepository
                .findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus.ACTIVE, today);

        for (RecurringInvoice template : templates) {
            // 공통 로직 메서드 호출
            processInvoiceGeneration(template);
        }
    }

    // --- [변경 2] 탬플릿 승인 로직 (즉시 발행 추가) ---
    @Transactional
    public void approveRecurringInvoices(List<Long> ids) {
        List<RecurringInvoice> templates = recurringRepository.findAllById(ids);
        LocalDate today = LocalDate.now();

        for (RecurringInvoice template : templates) {
            // IN_REVIEW -> ACTIVE 변경
            if (template.getStatus() == RecurringStatus.IN_REVIEW) {
                template.setStatus(RecurringStatus.ACTIVE);

                // [핵심] 상태 변경 즉시 발행 조건 확인
                // 예정일이 없거나, 예정일이 오늘보다 미래가 아니라면(즉, 오늘이거나 과거라면) 즉시 실행
                if (template.getNextInvoiceDate() != null &&
                        !template.getNextInvoiceDate().isAfter(today)) {

                    processInvoiceGeneration(template); // 인보이스 생성 및 날짜 갱신 로직 실행
                }
            }
        }
    }

    // --- [신규] 공통 처리 로직 추출 (스케줄러와 승인 메서드에서 같이 사용) ---
    private void processInvoiceGeneration(RecurringInvoice template) {
        // 1. 인보이스 생성
        createInvoiceFromTemplate(template);

        // 2. 날짜 갱신
        template.setLastIssuedDate(LocalDate.now());
        template.calculateNextDate();

        // 3. 종료일 체크 및 상태 변경
        if (template.getEndDate() != null && template.getNextInvoiceDate().isAfter(template.getEndDate())) {
            template.setStatus(RecurringStatus.COMPLETED);
        }
    }

    // 내부 로직: 탬플릿 -> 실제 인보이스 변환 (기존 코드 유지)
    private void createInvoiceFromTemplate(RecurringInvoice template) {
        Invoice newInvoice = new Invoice();

        newInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        newInvoice.setIssuedDate(LocalDate.now());

        int days = template.getDueDateDays() != null ? template.getDueDateDays() : 7;
        newInvoice.setDueDate(newInvoice.getIssuedDate().plusDays(days));

        newInvoice.setContact(template.getContact());
        newInvoice.setSalesPerson(template.getSalesPerson());
        newInvoice.setReference(template.getReference());

        Contact c = template.getContact();
        newInvoice.setCustomerName(c.getName());
        newInvoice.setCustomerEmail(c.getEmail());
        newInvoice.setCustomerCompanyName(c.getCompanyName());
        newInvoice.setCustomerBillTo(c.getBillTo());
        newInvoice.setCustomerCurrency(c.getCurrency());

        newInvoice.setStatus(template.isAutoSend() ? InvoiceStatus.UNPAID : InvoiceStatus.DRAFT);

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
    // 탬플릿 목록 전체 조회
    public List<RecurringInvoice> getAllTemplates() {
        return recurringRepository.findAllByOrderByIdAsc();
    }
    // 탬플릿 단건 조회
    public RecurringInvoice getRecurringInvoice(Long id) {
        return recurringRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 탬플릿이 없습니다. id=" + id));
    }
    // 삭제
    public void deleteRecurringInvoices(List<Long> ids) {
        recurringRepository.deleteAllById(ids);
    }
}
