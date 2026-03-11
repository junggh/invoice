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

    // ===================================================================================
    // 1. 로그인
    // ===================================================================================

    /**
     * 로그인 폼 이동.
     * 로그인 실패 후 리다이렉트된 경우 세션에 저장된 이메일을 꺼내 입력 필드에 미리 채워준다.
     */
    @GetMapping("/login")
    public String loginForm(Model model, HttpSession session) {
        String lastEmail = (String) session.getAttribute("lastEmail");

        if (lastEmail != null) {
            model.addAttribute("lastEmail", lastEmail);
            session.removeAttribute("lastEmail");
        }
        return "login";
    }

    // ===================================================================================
    // 2. 회원가입
    // ===================================================================================

    /**
     * 회원가입 폼 이동.
     * 초대 토큰이 있으면 DB에서 초대된 이메일을 조회하여 폼에 미리 채워준다.
     */
    @GetMapping("/signup")
    public String signupForm(@RequestParam(defaultValue = "admin") String type,
                             @RequestParam(required = false) String token,
                             Model model) {
        SignupForm form = new SignupForm();
        form.setAccountType(type);
        form.setToken(token);

        // 초대 토큰이 있으면 DB에서 이메일을 가져와 폼에 미리 입력
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

    /**
     * 회원가입 처리.
     * 가입 완료 즉시 자동 로그인(request.login)하고 대시보드로 이동한다.
     * 초대 토큰이 있으면 가입과 동시에 해당 회사에 자동 연결된다.
     */
    @PostMapping("/signup")
    public String processSignup(@ModelAttribute SignupForm signupForm, Model model, HttpServletRequest request) {
        try {
            authService.processSignup(signupForm);

            // 초대 토큰이 있으면 가입과 동시에 회사 연결 처리
            if (signupForm.getToken() != null && !signupForm.getToken().isEmpty()) {
                invitationService.acceptInvitation(signupForm.getToken(), signupForm.getPersonalEmail());
            }

            // 가입 완료 즉시 자동 로그인 (Spring Security)
            request.login(signupForm.getPersonalEmail(), signupForm.getPassword());
            authService.updateLoginAndActivityDates(signupForm.getPersonalEmail());

            return "redirect:/invoices";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("timezones", Timezone.values());
            model.addAttribute("months", Month.values());
            return "signup";
        } catch (Exception e) {
            // request.login 실패 시 안전하게 로그인 페이지로 이동
            return "redirect:/login";
        }
    }

    // ===================================================================================
    // 3. 이메일 인증 API
    // ===================================================================================

    /**
     * 이메일 인증 코드 발송 API.
     * 6자리 랜덤 코드를 생성하여 세션에 저장하고(3분 만료), 브랜드 스타일 이메일로 발송한다.
     */
    @PostMapping("/api/auth/send-verification")
    public ResponseEntity<?> sendVerification(@RequestParam String email, HttpSession session) {
        // 6자리 랜덤 코드 생성
        String code = String.valueOf(new Random().nextInt(900000) + 100000);

        // 세션에 코드, 이메일, 만료 시간 저장 (현재 시각 + 3분)
        session.setAttribute("verifyCode", code);
        session.setAttribute("verifyEmail", email);
        session.setAttribute("verifyExpiry", System.currentTimeMillis() + (3 * 60 * 1000));

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

        emailService.sendEmail(email, subject, content);

        return ResponseEntity.ok().body(Map.of("message", "Sent successfully"));
    }

    /**
     * 이메일 인증 코드 확인 API.
     * 세션의 코드·이메일·만료 시간을 순서대로 검증하며, 인증 성공 시 세션에서 코드를 삭제하여 재사용을 방지한다.
     */
    @PostMapping("/api/auth/verify-code")
    public ResponseEntity<Boolean> verifyCode(@RequestParam String email,
                                              @RequestParam String code,
                                              HttpSession session) {
        String savedCode = (String) session.getAttribute("verifyCode");
        String savedEmail = (String) session.getAttribute("verifyEmail");
        Long expiryTime = (Long) session.getAttribute("verifyExpiry");

        // 세션에 정보가 없는 경우 (만료되었거나 발송 안 함)
        if (savedCode == null || expiryTime == null) {
            return ResponseEntity.ok(false);
        }

        // 이메일 불일치
        if (!email.equals(savedEmail)) {
            return ResponseEntity.ok(false);
        }

        // 만료 시간 초과
        if (System.currentTimeMillis() > expiryTime) {
            session.removeAttribute("verifyCode");
            return ResponseEntity.ok(false);
        }

        // 코드 일치 확인 후 세션에서 삭제 (재사용 방지)
        if (savedCode.equals(code)) {
            session.removeAttribute("verifyCode");
            session.removeAttribute("verifyExpiry");
            return ResponseEntity.ok(true);
        }

        return ResponseEntity.ok(false);
    }

    /** 이메일 중복 확인 API. 사용 가능하면 true, 이미 존재하면 false를 반환한다. */
    @GetMapping("/api/auth/check-email")
    @ResponseBody
    public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(false);
        }
        boolean isAvailable = authService.isEmailAvailable(email.trim());
        return ResponseEntity.ok(isAvailable);
    }

    // ===================================================================================
    // 4. ABN 조회 API
    // ===================================================================================

    /**
     * ABN 조회 API. 회원가입 폼에서 AJAX로 호출된다.
     * DB 중복 여부를 먼저 확인하고, 중복이 아닌 경우 호주 정부 ABN Lookup API를 호출하여 사업자 정보를 반환한다.
     */
    @GetMapping("/api/auth/abn-lookup")
    @ResponseBody
    public ResponseEntity<?> lookupAbn(@RequestParam String abn) {
        String cleanAbn = abn.replace(" ", "");

        // 이미 등록된 ABN이면 409 Conflict 반환
        boolean isAvailable = authService.isAbnAvailable(cleanAbn);
        if (!isAvailable) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("ABN already exists");
        }

        // 사용 가능한 ABN이면 호주 정부 API 호출
        AbnApiResponse result = abnLookupService.lookupAbn(cleanAbn);

        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }
}