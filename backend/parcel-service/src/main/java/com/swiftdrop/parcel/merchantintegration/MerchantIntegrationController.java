package com.swiftdrop.parcel.merchantintegration;

import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Isolated merchant integration contract; existing customer and portal routes are unchanged. */
@RestController
@RequestMapping("/api/parcels/merchant/integration/orders")
@PreAuthorize("hasRole('MERCHANT') and #jwt.getClaimAsString('purpose') == 'MERCHANT_ORDER_INTEGRATION' and #jwt.audience.contains('parcel-service')")
public class MerchantIntegrationController {
    private final MerchantIntegrationService service;
    public MerchantIntegrationController(MerchantIntegrationService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<Order> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.create(userId(jwt), jwt.getTokenValue(), request));
    }
    @GetMapping
    public ResponseEntity<Page> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(userId(jwt), page, size));
    }
    @GetMapping("/by-reference")
    public ResponseEntity<Order> find(@AuthenticationPrincipal Jwt jwt, @RequestParam String reference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find(userId(jwt), reference));
    }
    private long userId(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0 || !Long.toString(id).equals(jwt.getSubject())) throw new NumberFormatException();
            return id;
        } catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }
    public record Request(@NotBlank @Size(max=100) String merchantOrderReference,
            @NotBlank @Pattern(regexp="[a-f0-9]{64}") String sourceRequestHash,
            @NotNull @Valid CreateParcelRequest parcel) {
        @Override public String toString() { return "Request[merchantOrderReference=" + merchantOrderReference + "]"; }
    }
    public record Order(String merchantOrderReference, String trackingNumber, String status,
            UUID parcelId, BigDecimal charge, Instant createdAt, BigDecimal weightKg, String size, String serviceType) { }
    public record Page(java.util.List<Order> items, long total, int page, int size) { }
}
