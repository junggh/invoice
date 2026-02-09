package com.example.demo.controller;

import com.example.demo.dto.AbnApiResponse;
import com.example.demo.dto.SignupForm;
import com.example.demo.entity.Timezone;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.service.AbnLookupService;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Month;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AbnLookupService abnLookupService;
    private final CompanyRepository companyRepository;

    // 회원가입 페이지 이동
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        model.addAttribute("timezones", Timezone.values());
        model.addAttribute("months", Month.values());
        return "signup"; // signup.html (Wizard 형식의 뷰)
    }

    // 회원가입 처리
    @PostMapping("/signup")
    public String processSignup(@ModelAttribute SignupForm signupForm, Model model) {
        try {
            // 비밀번호 확인 로직
            if (!signupForm.getPassword().equals(signupForm.getCheckPassword())) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }

            authService.processSignup(signupForm);

            // 가입 성공 시 로그인 페이지나 웰컴 페이지로 리다이렉트
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            // 에러 발생 시 다시 가입 페이지로 (에러 메시지 전달)
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("signupForm", signupForm); // 입력했던 정보 유지
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