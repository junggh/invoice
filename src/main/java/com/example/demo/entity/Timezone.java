package com.example.demo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.ZoneId;
import java.time.ZoneOffset;

@Getter
@RequiredArgsConstructor
public enum Timezone {
    UTC_MINUS_12("-12:00", "UTC-12:00"),
    UTC_MINUS_11("-11:00", "UTC-11:00"),
    UTC_MINUS_10("-10:00", "UTC-10:00 (Hawaii)"),
    UTC_MINUS_09("-09:00", "UTC-09:00 (Alaska)"),
    UTC_MINUS_08("-08:00", "UTC-08:00 (Pacific Time)"),
    UTC_MINUS_07("-07:00", "UTC-07:00 (Mountain Time)"),
    UTC_MINUS_06("-06:00", "UTC-06:00 (Central Time)"),
    UTC_MINUS_05("-05:00", "UTC-05:00 (Eastern Time)"),
    UTC_MINUS_04("-04:00", "UTC-04:00"),
    UTC_MINUS_03("-03:00", "UTC-03:00"),
    UTC_MINUS_02("-02:00", "UTC-02:00"),
    UTC_MINUS_01("-01:00", "UTC-01:00"),
    UTC_PLUS_00("+00:00", "UTC+00:00 (GMT/London)"),
    UTC_PLUS_01("+01:00", "UTC+01:00 (Central European)"),
    UTC_PLUS_02("+02:00", "UTC+02:00 (Eastern European)"),
    UTC_PLUS_03("+03:00", "UTC+03:00"),
    UTC_PLUS_04("+04:00", "UTC+04:00"),
    UTC_PLUS_05("+05:00", "UTC+05:00"),
    UTC_PLUS_06("+06:00", "UTC+06:00"),
    UTC_PLUS_07("+07:00", "UTC+07:00"),
    UTC_PLUS_08("+08:00", "UTC+08:00 (China, Singapore)"),
    UTC_PLUS_09("+09:00", "UTC+09:00 (Korea, Japan)"),
    UTC_PLUS_10("+10:00", "UTC+10:00 (Sydney)"),
    UTC_PLUS_11("+11:00", "UTC+11:00"),
    UTC_PLUS_12("+12:00", "UTC+12:00 (New Zealand)");

    private final String offset;
    private final String displayValue;

    public ZoneId toZoneId() {
        return ZoneOffset.of(offset);
    }
}