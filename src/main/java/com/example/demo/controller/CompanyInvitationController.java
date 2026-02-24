package com.example.demo.controller;

import com.example.demo.entity.CompanyInvitation;
import com.example.demo.service.CompanyInvitationService;
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

    // 1. 관리자가 초대 모달에서 'Send Invite' 버튼을 누를 때 호출됨
    @PostMapping("/api/invitations")
    @ResponseBody
    public ResponseEntity<String> inviteUser(@RequestBody Map<String, String> request,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        String inviteeEmail = request.get("email");
        try {
            CompanyInvitation invitation = invitationService.createInvitation(userDetails.getUsername(), inviteeEmail);

            // [임시 처리] 실제 이메일 발송은 JavaMailSender 등을 세팅해야 하므로, 우선 콘솔창에 링크를 출력합니다!
            String inviteLink = "http://localhost:8080/invitations/accept?token=" + invitation.getToken();

            System.out.println("\n========================================================");
            System.out.println("💌 [가상 이메일 발송] " + inviteeEmail + " 님에게 초대장이 도착했습니다!");
            System.out.println("👉 가입 링크: " + inviteLink);
            System.out.println("========================================================\n");

            return ResponseEntity.ok("초대장이 성공적으로 발송되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. 직원이 초대 링크를 클릭했을 때 들어오는 곳
    @GetMapping("/invitations/accept")
    public String acceptInvitation(@RequestParam String token,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        // [수정] 비로그인 상태면 토큰을 들고 로그인 화면으로 보냅니다!
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