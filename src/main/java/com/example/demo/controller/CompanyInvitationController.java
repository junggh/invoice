package com.example.demo.controller;

import com.example.demo.entity.CompanyInvitation;
import com.example.demo.service.CompanyInvitationService;
import com.example.demo.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CompanyInvitationController {

    private final CompanyInvitationService invitationService;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    // ===================================================================================
    // 1. 초대 생성
    // ===================================================================================

    /**
     * 팀원 초대 API. 관리자가 초대 모달에서 'Send Invite' 버튼을 누를 때 호출된다.
     * 초대 토큰을 생성하여 DB에 저장하고, 수락 링크가 포함된 브랜드 스타일 이메일을 발송한다.
     */
    @PostMapping("/api/invitations")
    @ResponseBody
    public ResponseEntity<String> inviteUser(@RequestBody Map<String, String> request,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        String inviteeEmail = request.get("email");
        try {
            CompanyInvitation invitation = invitationService.createInvitation(userDetails.getUsername(), inviteeEmail);

            String inviteLink = baseUrl + "/invitations/accept?token=" + invitation.getToken();
            String companyName = invitation.getCompany().getBusinessName();

            String subject = "[ZeniBooks] You've been invited to join " + companyName + "!";
            String content =
                "<div style='font-family: Arial, sans-serif; background-color: #f5f7fa; padding: 40px 20px;'>" +
                "  <div style='max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08);'>" +
                "    <div style='background-color: #00A3FF; padding: 32px 40px;'>" +
                "      <h1 style='margin: 0; color: #ffffff; font-size: 22px; font-weight: 700;'>" + companyName + "</h1>" +
                "      <p style='margin: 6px 0 0; color: #d0efff; font-size: 14px;'>Team Invitation</p>" +
                "    </div>" +
                "    <div style='padding: 36px 40px;'>" +
                "      <p style='margin: 0 0 16px; font-size: 15px; color: #555; line-height: 1.6;'>" +
                "        You have been invited to join <strong style='color: #222;'>" + companyName + "</strong> on ZeniBooks.</p>" +
                "      <p style='margin: 0 0 28px; font-size: 15px; color: #555; line-height: 1.6;'>" +
                "        Click the button below to accept the invitation and get started.</p>" +
                "      <div style='text-align: center; margin-bottom: 28px;'>" +
                "        <a href='" + inviteLink + "' style='display: inline-block; padding: 14px 36px; background-color: #00A3FF; color: #ffffff;" +
                "           text-decoration: none; border-radius: 6px; font-weight: 700; font-size: 15px;'>Accept Invitation</a>" +
                "      </div>" +
                "      <p style='margin: 0; font-size: 12px; color: #aaa; text-align: center; line-height: 1.6;'>" +
                "        If the button doesn't work, copy and paste this link into your browser:<br>" +
                "        <a href='" + inviteLink + "' style='color: #00A3FF; word-break: break-all;'>" + inviteLink + "</a></p>" +
                "    </div>" +
                "    <div style='background: #f8fafc; padding: 20px 40px; text-align: center; border-top: 1px solid #eee;'>" +
                "      <p style='margin: 0; font-size: 12px; color: #bbb;'>Powered by ZeniBooks &mdash; " + companyName + "</p>" +
                "    </div>" +
                "  </div>" +
                "</div>";

            emailService.sendEmail(inviteeEmail, subject, content);

            return ResponseEntity.ok("Invitation email successfully sent.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===================================================================================
    // 2. 초대 수락
    // ===================================================================================

    /**
     * 초대 링크 수락 처리. 직원이 이메일의 초대 링크를 클릭했을 때 진입한다.
     * 비로그인 상태면 토큰을 유지한 채 로그인 화면으로 이동하고,
     * 로그인 상태면 토큰을 검증하여 해당 회사에 연결한 뒤 결과 알림과 함께 대시보드로 리다이렉트한다.
     */
    @GetMapping("/invitations/accept")
    public String acceptInvitation(@RequestParam String token,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        // 비로그인 상태면 토큰을 들고 로그인 화면으로 이동
        if (userDetails == null) {
            return "redirect:/login?token=" + token;
        }

        try {
            String companyName = invitationService.acceptInvitation(token, userDetails.getUsername());
            String encoded = URLEncoder.encode("You have been connected to " + companyName + ".", StandardCharsets.UTF_8);
            return "redirect:/invoices?tokenSuccess=" + encoded;
        } catch (Exception e) {
            String encoded = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return "redirect:/invoices?tokenError=" + encoded;
        }
    }
}