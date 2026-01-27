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

        // 기본 정보 복사 (ID, Items는 제외)
        // salesPerson, reference, contact, 스케줄링 옵션 등을 덮어씌움
        BeanUtils.copyProperties(formTemplate, existingTemplate, "id", "items", "lastIssuedDate", "nextInvoiceDate");

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
    // 스케줄러 (매일 자정 실행)
    @Scheduled(cron = "0 0 0 * * *")
    public void generateRecurringInvoices() {
        LocalDate today = LocalDate.now();
        List<RecurringInvoice> templates = recurringRepository
                .findByStatusAndNextInvoiceDateLessThanEqual(RecurringStatus.ACTIVE, today);

        for (RecurringInvoice template : templates) {
            createInvoiceFromTemplate(template); // 인보이스 생성

            template.setLastIssuedDate(LocalDate.now()); // 최근 발행일 갱신
            template.calculateNextDate(); // 다음 예정일 계산

            // 종료일 체크
            if (template.getEndDate() != null && template.getNextInvoiceDate().isAfter(template.getEndDate())) {
                template.setStatus(RecurringStatus.COMPLETED);
            }
        }
    }
    // 내부 로직: 템플릿 -> 실제 인보이스 변환
    private void createInvoiceFromTemplate(RecurringInvoice template) {
        Invoice newInvoice = new Invoice();

        newInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        newInvoice.setIssuedDate(LocalDate.now()); // 발행일 = 오늘

        // [수정] 템플릿에 설정된 dueDateDays를 사용하여 납기일 계산
        // 예: 오늘(1일) + 7일 = 8일
        int days = template.getDueDateDays() != null ? template.getDueDateDays() : 7; // null 방어
        newInvoice.setDueDate(newInvoice.getIssuedDate().plusDays(days));

        // 기본 정보 복사
        newInvoice.setContact(template.getContact());
        newInvoice.setSalesPerson(template.getSalesPerson());
        newInvoice.setReference(template.getReference());

        // 스냅샷 정보 복사 (Contact에서 가져옴)
        Contact c = template.getContact();
        newInvoice.setCustomerName(c.getName());
        newInvoice.setCustomerEmail(c.getEmail());
        newInvoice.setCustomerCompanyName(c.getCompanyName());
        newInvoice.setCustomerBillTo(c.getBillTo());
        newInvoice.setCustomerCurrency(c.getCurrency());

        // 상태 설정 (자동발송이면 UNPAID, 아니면 DRAFT)
        newInvoice.setStatus(template.isAutoSend() ? InvoiceStatus.UNPAID : InvoiceStatus.DRAFT);

        // 아이템 복사
        List<InvoiceItem> newItems = new ArrayList<>();

        for (RecurringInvoiceItem tItem : template.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setProduct(tItem.getProduct());
            item.setQuantity(tItem.getQuantity());
            item.setDiscount(tItem.getDiscount());
            item.setAmount(tItem.getAmount());
            item.setInvoice(newInvoice); // 부모 연결

            newItems.add(item);
        }

        newInvoice.setItems(newItems);
        newInvoice.setTotal(template.getTotal());
        newInvoice.setBalanceDue(template.getTotal()); // 새 인보이스는 전액 미수금

        // 저장
        invoiceService.createInvoice(newInvoice);

        // (선택) 자동 발송 로직이 있다면 여기서 호출
        if (template.isAutoSend()) {
            // emailService.sendInvoice(newInvoice);
        }
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
}
