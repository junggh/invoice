package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 호주 ABN Lookup API (abr.business.gov.au) 응답 DTO.
 * 알 수 없는 필드는 무시하고, API 응답의 PascalCase 키를 @JsonProperty로 매핑한다.
 */
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbnApiResponse {

    @JsonProperty("Abn")
    private String abn;                 // 호주 사업자 등록 번호

    @JsonProperty("EntityName")
    private String entityName;          // 등록된 법인명 (예: ABC Pty Ltd)

    @JsonProperty("EntityTypeName")
    private String entityTypeName;      // 사업체 유형 (예: Australian Private Company)

    @JsonProperty("Message")
    private String message;             // 오류 메시지 (조회 실패 시 채워짐)

    @JsonProperty("AbnStatus")
    private String abnStatus;           // ABN 상태 (예: Active)

    @JsonProperty("AddressPostcode")
    private String addressPostcode;     // 사업장 우편번호 (예: 2025)

    @JsonProperty("AddressState")
    private String addressState;        // 사업장 주 (예: NSW)

    @JsonProperty("Gst")
    private String gst;                 // GST 등록일 (미등록 시 null)
}