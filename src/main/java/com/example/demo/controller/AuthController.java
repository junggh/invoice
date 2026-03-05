package com.example.demo.controller;

import com.example.demo.dto.AbnApiResponse;
import com.example.demo.dto.SignupForm;
import com.example.demo.entity.Timezone;
import com.example.demo.service.AbnLookupService;
import com.example.demo.service.AuthService;
import com.example.demo.service.CompanyInvitationService;
import com.example.demo.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Month;
import java.util.Map;
import java.util.Random;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AbnLookupService abnLookupService;
    private final EmailService emailService;
    private final CompanyInvitationService invitationService;

    // 회원가입 페이지 이동
    @GetMapping("/signup")
    public String signupForm(@RequestParam(defaultValue = "admin") String type,
                             @RequestParam(required = false) String token,
                             Model model) {
        SignupForm form = new SignupForm();
        form.setAccountType(type);
        form.setToken(token);

        // 토큰이 있으면 이메일을 DB에서 가져와서 미리 폼에 채워줍니다.
        if (token != null) {
            String inviteeEmail = invitationService.getEmailByToken(token);
            if (inviteeEmail != null) {
                form.setPersonalEmail(inviteeEmail);
            }
        }

        model.addAttribute("signupForm", form);
        model.addAttribute("accountType", type);
        model.addAttribute("timezones", Timezone.values());
        model.addAttribute("months", Month.values());
        return "signup";
    }

    // 회원가입 처리
    @PostMapping("/signup")
    public String processSignup(@ModelAttribute SignupForm signupForm, Model model, HttpServletRequest request) { // request 추가
        try {
            // 1. 회원 가입 처리 (DB 저장)
            authService.processSignup(signupForm);

            // 2. [추가] 토큰이 있다면 가입과 동시에 회사 연결 처리!
            if (signupForm.getToken() != null && !signupForm.getToken().isEmpty()) {
                invitationService.acceptInvitation(signupForm.getToken(), signupForm.getPersonalEmail());
            }

            // 3. [추가] 가입 완료 즉시 자동 로그인 처리 (Spring Security)
            request.login(signupForm.getPersonalEmail(), signupForm.getPassword());
            authService.updateLoginAndActivityDates(signupForm.getPersonalEmail());

            // 4. [추가] 로그인 페이지를 거치지 않고 바로 대시보드로 이동!
            return "redirect:/invoices";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("timezones", Timezone.values());
            model.addAttribute("months", Month.values());
            return "signup";
        } catch (Exception e) {
            // request.login 실패 시 안전하게 로그인 페이지로 보냄
            return "redirect:/login";
        }
    }

    // [추가] ABN 조회 API 엔드포인트 (AJAX 요청용)
    @GetMapping("/api/auth/abn-lookup")
    @ResponseBody
    public ResponseEntity<?> lookupAbn(@RequestParam String abn) { // [중요] 리턴 타입을 <?>로 변경
        String cleanAbn = abn.replace(" ", "");

        // 1. Service를 통해 중복 확인 (Username 로직과 패턴 통일)
        boolean isAvailable = authService.isAbnAvailable(cleanAbn);

        if (!isAvailable) {
            // 중복이면 409 Conflict와 함께 문자열 메시지 반환
            return ResponseEntity.status(HttpStatus.CONFLICT).body("ABN already exists");
        }

        // 2. 사용 가능하면(DB에 없으면) 호주 정부 API 호출
        AbnApiResponse result = abnLookupService.lookupAbn(cleanAbn);

        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        }

        // 성공 시 JSON 객체 반환
        return ResponseEntity.ok(result);
    }

    // 1. 인증번호 발송 API
    @PostMapping("/api/auth/send-verification")
    public ResponseEntity<?> sendVerification(@RequestParam String email, HttpSession session) {
        // (1) 6자리 랜덤 코드 생성
        String code = String.valueOf(new Random().nextInt(900000) + 100000);

        // (2) 세션에 저장 (유효시간 검증용)
        session.setAttribute("verifyCode", code);
        session.setAttribute("verifyEmail", email);
        // 현재시간 + 3분(180초 * 1000ms)
        session.setAttribute("verifyExpiry", System.currentTimeMillis() + (3 * 60 * 1000));

        // (3) 이메일 본문 생성 (HTML)
        String subject = "[ZeniBooks] Your email verification code";
        String content =
            "<div style='font-family: Arial, sans-serif; background-color: #f5f7fa; padding: 40px 20px;'>" +
            "  <div style='max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08);'>" +
            "    <div style='background-color: #00A3FF; padding: 32px 40px;'>" +
            "      <h1 style='margin: 0; color: #ffffff; font-size: 22px; font-weight: 700;'>ZeniBooks</h1>" +
            "      <p style='margin: 6px 0 0; color: #d0efff; font-size: 14px;'>Email Verification</p>" +
            "    </div>" +
            "    <div style='padding: 36px 40px; text-align: center;'>" +
            "      <p style='margin: 0 0 24px; font-size: 15px; color: #555; line-height: 1.6;'>" +
            "        Use the verification code below to complete your sign-up.</p>" +
            "      <div style='display: inline-block; background: #f0f9ff; border: 2px dashed #00A3FF; border-radius: 10px; padding: 20px 48px; margin-bottom: 24px;'>" +
            "        <span style='font-size: 36px; font-weight: 700; color: #00A3FF; letter-spacing: 10px;'>" + code + "</span>" +
            "      </div>" +
            "      <p style='margin: 0; font-size: 13px; color: #aaa;'>This code expires in <strong style='color: #555;'>3 minutes</strong>.</p>" +
            "    </div>" +
            "    <div style='background: #f8fafc; padding: 20px 40px; text-align: center; border-top: 1px solid #eee;'>" +
            "      <p style='margin: 0; font-size: 12px; color: #bbb;'>If you didn't request this, you can safely ignore this email.</p>" +
            "    </div>" +
            "  </div>" +
            "</div>";

        // (4) 발송
        emailService.sendEmail(email, subject, content);

        return ResponseEntity.ok().body(Map.of("message", "Sent successfully"));
    }

    // 2. 인증번호 확인 API
    @PostMapping("/api/auth/verify-code")
    public ResponseEntity<Boolean> verifyCode(@RequestParam String email,
                                              @RequestParam String code,
                                              HttpSession session) {

        String savedCode = (String) session.getAttribute("verifyCode");
        String savedEmail = (String) session.getAttribute("verifyEmail");
        Long expiryTime = (Long) session.getAttribute("verifyExpiry");

        // (1) 세션에 정보가 없는 경우 (만료되었거나 발송 안 함)
        if (savedCode == null || expiryTime == null) {
            return ResponseEntity.ok(false);
        }

        // (2) 이메일 불일치
        if (!email.equals(savedEmail)) {
            return ResponseEntity.ok(false);
        }

        // (3) 시간 만료 확인 (현재시간이 만료시간보다 크면 실패)
        if (System.currentTimeMillis() > expiryTime) {
            session.removeAttribute("verifyCode"); // 만료된 코드 삭제
            return ResponseEntity.ok(false);
        }

        // (4) 코드 일치 확인
        if (savedCode.equals(code)) {
            // 인증 성공! 세션에서 코드 삭제 (재사용 방지)
            session.removeAttribute("verifyCode");
            session.removeAttribute("verifyExpiry");
            return ResponseEntity.ok(true);
        }

        return ResponseEntity.ok(false);
    }

    // 이메일 중복 확인 API
    @GetMapping("/api/auth/check-email")
    @ResponseBody
    public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(false);
        }
        boolean isAvailable = authService.isEmailAvailable(email.trim());
        return ResponseEntity.ok(isAvailable);
    }

    // 로그인 페이지
    @GetMapping("/login")
    public String loginForm(Model model, HttpSession session) {
        // 세션에서 실패했던 이메일 가져오기
        String lastEmail = (String) session.getAttribute("lastEmail");

        if (lastEmail != null) {
            model.addAttribute("lastEmail", lastEmail); // 뷰에서 th:value="${lastEmail}"로 사용
            session.removeAttribute("lastEmail");
        }
        return "login";
    }
}