package com.example.demo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Timezone {
    UTC_MINUS_12("UTC-12:00"),
    UTC_MINUS_11("UTC-11:00"),
    UTC_MINUS_10("UTC-10:00 (Hawaii)"),
    UTC_MINUS_09("UTC-09:00 (Alaska)"),
    UTC_MINUS_08("UTC-08:00 (Pacific Time)"),
    UTC_MINUS_07("UTC-07:00 (Mountain Time)"),
    UTC_MINUS_06("UTC-06:00 (Central Time)"),
    UTC_MINUS_05("UTC-05:00 (Eastern Time)"),
    UTC_MINUS_04("UTC-04:00"),
    UTC_MINUS_03("UTC-03:00"),
    UTC_MINUS_02("UTC-02:00"),
    UTC_MINUS_01("UTC-01:00"),
    UTC_PLUS_00("UTC+00:00 (GMT/London)"),
    UTC_PLUS_01("UTC+01:00 (Central European)"),
    UTC_PLUS_02("UTC+02:00 (Eastern European)"),
    UTC_PLUS_03("UTC+03:00"),
    UTC_PLUS_04("UTC+04:00"),
    UTC_PLUS_05("UTC+05:00"),
    UTC_PLUS_06("UTC+06:00"),
    UTC_PLUS_07("UTC+07:00"),
    UTC_PLUS_08("UTC+08:00 (China, Singapore)"),
    UTC_PLUS_09("UTC+09:00 (Korea, Japan)"),
    UTC_PLUS_10("UTC+10:00 (Sydney)"),
    UTC_PLUS_11("UTC+11:00"),
    UTC_PLUS_12("UTC+12:00 (New Zealand)");

    private final String displayValue;
}