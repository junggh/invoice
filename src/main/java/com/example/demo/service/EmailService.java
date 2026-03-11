package com.example.demo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${mail.user}")
    private String senderEmail;

    /**
     * HTML 이메일 발송. @Async로 비동기 처리되어 이메일 발송이 완료될 때까지 화면을 블로킹하지 않는다.
     */
    @Async
    public void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);
            helper.setFrom(senderEmail);

            javaMailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send email.");
        }
    }

    /**
     * PDF 첨부 이메일 발송. @Async로 비동기 처리된다.
     * pdfBytes를 ByteArrayDataSource로 감싸 MimeMessageHelper에 첨부한다.
     */
    @Async
    public void sendEmailWithAttachment(String to, String subject, String text, byte[] pdfBytes, String pdfFilename) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);
            helper.setFrom(senderEmail);
            helper.addAttachment(pdfFilename, new ByteArrayDataSource(pdfBytes, "application/pdf"));

            javaMailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send email with attachment.");
        }
    }
}