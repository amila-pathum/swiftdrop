package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.exception.DispatchException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

public record ReportRange(LocalDate from, LocalDate to) {
    public ReportRange {
        if (from == null || to == null || from.isAfter(to)) {
            throw new DispatchException(HttpStatus.BAD_REQUEST, "A valid from/to date range is required");
        }
        if (to.isAfter(from.plusYears(2))) {
            throw new DispatchException(HttpStatus.BAD_REQUEST, "Report range cannot exceed two years");
        }
    }
}
