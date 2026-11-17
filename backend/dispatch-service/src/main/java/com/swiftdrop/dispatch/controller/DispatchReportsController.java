package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.DispatchSummary;
import com.swiftdrop.dispatch.service.DispatchService;
import com.swiftdrop.dispatch.service.ReportRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Reports built only from the parcel status events stored by dispatch. */
@RestController
@RequestMapping("/api/dispatch/reports")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
public class DispatchReportsController {
    private final DispatchService dispatch;

    public DispatchReportsController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    /** {@code from} and {@code to} are inclusive UTC dates, at most two years apart. */
    @GetMapping("/summary")
    public DispatchSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = new ReportRange(from, to);
        return dispatch.reportSummary(range.from(), range.to());
    }
}
