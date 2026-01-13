package com.example.demo.controller;

import com.example.demo.entity.DemoMember;
import com.example.demo.repository.DemoRepo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class DemoController {

    private final DemoRepo memberRepository;

    public DemoController(DemoRepo memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @PostMapping("/")
    public String signUp(DemoMember member) {
        // HTML의 input name 속성과 Member 객체의 필드명이 같으면 자동으로 매핑.
        memberRepository.save(member);

        return "redirect:/"; // 가입 성공 후 메인 페이지로 이동
    }
}