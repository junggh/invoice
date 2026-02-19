package com.example.demo.service;

import com.example.demo.dto.AbnApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AbnLookupService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ★ 발급받은 GUID를 여기에 넣으세요
    @Value("${abn.guid}")
    private String guid;
    //private static final String GUID = "4ce52402-df10-43a4-94e2-75c50807d9ef";

    public AbnApiResponse lookupAbn(String abn) {
        // 호주 정부 API URL (callback 파라미터를 빼면 순수 JSON이 나옵니다)
        String url = "https://abr.business.gov.au/json/AbnDetails.aspx?abn=" + abn + "&guid=" + guid + "&callback=";

        try {
            // 1. API 호출 (문자열로 받음)
            String jsonResponse = restTemplate.getForObject(url, String.class);

            // 2. 혹시 callback(...) 형태로 오면 괄호 제거 (안전장치)
            if (jsonResponse != null && jsonResponse.startsWith("callback(")) {
                jsonResponse = jsonResponse.substring(9, jsonResponse.length() - 1);
            }

            // 3. JSON 파싱
            return objectMapper.readValue(jsonResponse, AbnApiResponse.class);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("ABN Lookup Failed");
        }
    }
}