package com.example.demo.controller;

import com.example.demo.entity.CompanyInvitation;
import com.example.demo.service.CompanyInvitationService;
import com.example.demo.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CompanyInvitationController {

    private final CompanyInvitationService invitationService;
    private final EmailService emailService;

    // 1. 관리자가 초대 모달에서 'Send Invite' 버튼을 누를 때 호출됨
    @PostMapping("/api/invitations")
    @ResponseBody
    public ResponseEntity<String> inviteUser(@RequestBody Map<String, String> request,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        String inviteeEmail = request.get("email");
        try {
            // 초대장 토큰 생성 및 DB 저장
            CompanyInvitation invitation = invitationService.createInvitation(userDetails.getUsername(), inviteeEmail);

            // 실제 가입 링크 생성 (나중에 실제 도메인으로 변경 필요)
            String inviteLink = "http://localhost:8080/invitations/accept?token=" + invitation.getToken();
            //String inviteLink = "http://20.194.25.99/invitations/accept?token=" + invitation.getToken();

            // 이메일 제목 및 본문(HTML) 구성
            String subject = "[ZeniBooks] You've been invited to join a team!";
            String content = "<div style='font-family: Arial, sans-serif; text-align:center; border:1px solid #ddd; padding:30px; border-radius: 8px; max-width: 500px; margin: 0 auto;'>"
                    + "<h2 style='color:#333; margin-top:0;'>Team Invitation</h2>"
                    + "<p style='color:#555; font-size: 16px; line-height: 1.5; margin-bottom: 25px;'>"
                    + "You have been invited to join the company on ZeniBooks.<br>Click the button below to accept the invitation and join the team.</p>"
                    + "<a href='" + inviteLink + "' style='display:inline-block; padding:12px 24px; background-color:#00A3FF; color:#fff; text-decoration:none; border-radius:6px; font-weight:bold; font-size: 16px;'>Accept Invitation</a>"
                    + "<p style='margin-top: 30px; font-size: 12px; color: #999; word-break: break-all;'>"
                    + "If the button doesn't work, copy and paste this link into your browser:<br>" + inviteLink + "</p>"
                    + "</div>";

            // EmailService를 호출하여 실제 비동기 메일 발송
            emailService.sendEmail(inviteeEmail, subject, content);

            return ResponseEntity.ok("Invitation email successfully sent.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. 직원이 초대 링크를 클릭했을 때 들어오는 곳
    @GetMapping("/invitations/accept")
    public String acceptInvitation(@RequestParam String token,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        // 비로그인 상태면 토큰을 들고 로그인 화면으로 보냄
        if (userDetails == null) {
            return "redirect:/login?token=" + token;
        }

        try {
            invitationService.acceptInvitation(token, userDetails.getUsername());
            return "redirect:/invoices"; // 성공 시 대시보드로 즉시 이동
        } catch (Exception e) {
            return "redirect:/invoices?error=invalid_token";
        }
    }
}