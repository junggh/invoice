package com.example.demo.service;

import com.example.demo.entity.Invoice;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    private final TemplateEngine templateEngine;

    public PdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 인보이스 PDF 생성.
     * invoice-pdf.html 템플릿을 Thymeleaf로 렌더링한 뒤 openhtmltopdf로 PDF로 변환하여 byte[] 반환한다.
     */
    public byte[] generateInvoicePdf(Invoice invoice) {
        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("subtotal", invoice.getSubtotal());
        context.setVariable("tax", invoice.getTax());

        String html = templateEngine.process("invoice-pdf", context);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }
}
