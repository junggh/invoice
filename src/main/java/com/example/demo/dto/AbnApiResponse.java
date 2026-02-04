package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbnApiResponse {
    // API 응답 필드명과 매핑
    @JsonProperty("Abn")
    private String abn;

    @JsonProperty("EntityName")
    private String entityName;

    @JsonProperty("EntityTypeName")
    private String entityTypeName;

    @JsonProperty("Message") // 에러 메시지용
    private String message;
}