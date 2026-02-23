package com.example.demo.controller;

import com.example.demo.dto.AbnApiResponse;
import com.example.demo.dto.SignupForm;
import com.example.demo.entity.Timezone;
import com.example.demo.service.AbnLookupService;
import com.example.demo.service.AuthService;
import com.example.demo.service.EmailService;
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

    // 회원가입 페이지 이동
    @GetMapping("/signup")
    public String signupForm(@RequestParam(defaultValue = "admin") String type,Model model) {
        SignupForm form = new SignupForm();
        form.setAccountType(type); // 가입 유형 폼에 저장

        model.addAttribute("signupForm", form);
        model.addAttribute("accountType", type);
        model.addAttribute("timezones", Timezone.values());
        model.addAttribute("months", Month.values());
        return "signup"; // signup.html (Wizard 형식의 뷰)
    }

    // 회원가입 처리
    @PostMapping("/signup")
    public String processSignup(@ModelAttribute SignupForm signupForm, Model model) {
        try {
            authService.processSignup(signupForm);
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            // 에러 발생 시 다시 가입 페이지로 (에러 메시지 전달)
            model.addAttribute("errorMessage", e.getMessage());
            //model.addAttribute("signupForm", signupForm); // 입력했던 정보 유지
            model.addAttribute("timezones", Timezone.values());
            model.addAttribute("months", Month.values());
            return "signup";
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
        String subject = "[Service Name] Your verification code";
        String content = "<div style='text-align:center; border:1px solid #ddd; padding:20px;'>"
                + "<h2>Your Verification Code</h2>"
                + "<h1 style='color:#00A3FF; letter-spacing:5px;'>" + code + "</h1>"
                + "<p>This code will expire in 3 minutes.</p>"
                + "</div>";

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