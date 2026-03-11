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

    @Value("${abn.guid}")
    private String guid;

    /**
     * 호주 정부 ABN Lookup API를 호출하여 사업자 정보를 조회한다.
     * callback 파라미터를 빈 문자열로 설정하여 순수 JSON 응답을 받으며,
     * 응답이 callback(...) 형태로 오는 경우 괄호를 제거하여 파싱한다.
     */
    public AbnApiResponse lookupAbn(String abn) {
        String url = "https://abr.business.gov.au/json/AbnDetails.aspx?abn=" + abn + "&guid=" + guid + "&callback=";

        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);

            // callback(...) 형태로 응답이 올 경우 괄호 제거
            if (jsonResponse != null && jsonResponse.startsWith("callback(")) {
                jsonResponse = jsonResponse.substring(9, jsonResponse.length() - 1);
            }

            return objectMapper.readValue(jsonResponse, AbnApiResponse.class);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("ABN Lookup Failed");
        }
    }
}