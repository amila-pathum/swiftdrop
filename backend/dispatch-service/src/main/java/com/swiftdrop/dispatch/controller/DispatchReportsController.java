package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.CountByStatus;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierPerformance;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.FailedDelivery;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.RouteRevenue;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.VolumeByPeriod;
import com.swiftdrop.dispatch.service.DispatchService;
import com.swiftdrop.dispatch.service.ReportRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dispatch/reports")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
@Validated
public class DispatchReportsController {
    private final DispatchService dispatch;

    public DispatchReportsController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/status")
    public List<CountByStatus> statuses(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = range(from, to);
        return dispatch.reportStatuses(range.from(), range.to());
    }

    @GetMapping("/volume")
    public List<VolumeByPeriod> volume(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        ReportRange range = range(from, to);
        if (!groupBy.equals("day") && !groupBy.equals("week")) {
            throw new com.swiftdrop.dispatch.exception.DispatchException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "groupBy must be day or week");
        }
        return dispatch.reportVolume(range.from(), range.to(), groupBy.equals("week"));
    }

    @GetMapping("/courier-performance")
    public List<CourierPerformance> courierPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = range(from, to);
        return dispatch.reportCourierPerformance(range.from(), range.to());
    }

    @GetMapping("/failed-deliveries")
    public List<FailedDelivery> failedDeliveries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = range(from, to);
        return dispatch.reportFailed(range.from(), range.to());
    }

    @GetMapping("/revenue-by-route")
    public List<RouteRevenue> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = range(from, to);
        return dispatch.reportRevenue(range.from(), range.to());
    }

    @GetMapping(value = "/{report}.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@PathVariable String report,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        ReportRange range = range(from, to);
        String csv = switch (report) {
            case "status" -> csv(
                new String[]{"status", "count"},
                dispatch.reportStatuses(range.from(), range.to()).stream()
                    .map(row -> new Object[]{row.status(), row.count()}).toList());
            case "volume" -> {
                if (!groupBy.equals("day") && !groupBy.equals("week")) {
                    throw new com.swiftdrop.dispatch.exception.DispatchException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "groupBy must be day or week");
                }
                yield csv(new String[]{"period", "count"},
                    dispatch.reportVolume(range.from(), range.to(), groupBy.equals("week")).stream()
                        .map(row -> new Object[]{row.period(), row.count()}).toList());
            }
            case "courier-performance" -> csv(
                new String[]{"courierId", "courierName", "parcelsHandled", "delivered", "failed", "averageDeliveryHours"},
                dispatch.reportCourierPerformance(range.from(), range.to()).stream()
                    .map(row -> new Object[]{row.courierId(), row.courierName(), row.parcelsHandled(),
                        row.delivered(), row.failed(), row.averageDeliveryHours()}).toList());
            case "failed-deliveries" -> csv(
                new String[]{"trackingNumber", "courierName", "pickupArea", "dropoffArea", "reason", "failedAt"},
                dispatch.reportFailed(range.from(), range.to()).stream()
                    .map(row -> new Object[]{row.trackingNumber(), row.courierName(), row.pickupArea(),
                        row.dropoffArea(), row.reason(), row.failedAt()}).toList());
            case "revenue-by-route" -> csv(
                new String[]{"pickupArea", "dropoffArea", "parcelCount", "totalFees"},
                dispatch.reportRevenue(range.from(), range.to()).stream()
                    .map(row -> new Object[]{row.pickupArea(), row.dropoffArea(), row.parcelCount(),
                        row.totalFees()}).toList());
            default -> throw new com.swiftdrop.dispatch.exception.DispatchException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Report not found");
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report + ".csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static ReportRange range(LocalDate from, LocalDate to) {
        return new ReportRange(from, to);
    }

    private static String csv(String[] headers, List<Object[]> rows) {
        StringBuilder output = new StringBuilder();
        appendRow(output, headers);
        rows.forEach(row -> appendRow(output, row));
        return output.toString();
    }

    private static void appendRow(StringBuilder output, Object[] row) {
        for (int i = 0; i < row.length; i++) {
            if (i > 0) output.append(',');
            String value = row[i] == null ? "" : row[i].toString();
            output.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        output.append("\r\n");
    }
}
