package com.swiftdrop.parcel.merchantintegration;

import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.ParcelCreationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static com.swiftdrop.parcel.merchantintegration.MerchantIntegrationController.*;

@Service
public class MerchantIntegrationService {
    private final JdbcTemplate jdbc;
    private final ParcelCreationService creation;
    private final ParcelRepository parcels;
    private final ObjectMapper mapper;
    public MerchantIntegrationService(JdbcTemplate jdbc, ParcelCreationService creation,
            ParcelRepository parcels, ObjectMapper mapper) {
        this.jdbc = jdbc; this.creation = creation; this.parcels = parcels; this.mapper = mapper;
    }

    @Transactional
    public Order create(long userId, String token, Request request) {
        String hash;
        try { hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(request))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        // A concurrent INSERT waits for the first transaction; failures roll the reservation back.
        int inserted = jdbc.update("""
                INSERT INTO merchant_order_idempotency(merchant_user_id,merchant_order_reference,request_hash)
                VALUES (?,?,?) ON CONFLICT DO NOTHING
                """, userId, request.merchantOrderReference(), hash);
        if (inserted == 0) {
            String saved = jdbc.queryForObject("""
                    SELECT request_hash FROM merchant_order_idempotency
                    WHERE merchant_user_id=? AND merchant_order_reference=?
                    """, String.class, userId, request.merchantOrderReference());
            if (!hash.equals(saved)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Reference already used with different details");
            return find(userId, request.merchantOrderReference());
        }
        var created = creation.createMerchantIntegrationParcel(request.parcel(), userId, token, request.merchantOrderReference());
        jdbc.update("""
                UPDATE merchant_order_idempotency SET parcel_id=?
                WHERE merchant_user_id=? AND merchant_order_reference=?
                """, created.id(), userId, request.merchantOrderReference());
        return find(userId, request.merchantOrderReference());
    }

    @Transactional(readOnly=true)
    public Order find(long userId, String reference) {
        var ids = jdbc.queryForList("""
                SELECT parcel_id FROM merchant_order_idempotency
                WHERE merchant_user_id=? AND merchant_order_reference=? AND parcel_id IS NOT NULL
                """, UUID.class, userId, reference);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        var parcel = parcels.findById(ids.getFirst()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Long.valueOf(userId).equals(parcel.getMerchantId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new Order(reference, parcel.getTrackingNumber(), parcel.getStatus().name(), parcel.getId(), parcel.getCharge(),
                parcel.getCreatedAt(), parcel.getWeight(), parcel.getSize(), parcel.getParcelType());
    }

    @Transactional(readOnly=true)
    public Page list(long userId, int page, int size) {
        var references = jdbc.queryForList("""
                SELECT merchant_order_reference FROM merchant_order_idempotency
                WHERE merchant_user_id=? AND parcel_id IS NOT NULL
                ORDER BY created_at DESC,merchant_order_reference LIMIT ? OFFSET ?
                """, String.class, userId, size, (long)page*size);
        long total = jdbc.queryForObject("SELECT count(*) FROM merchant_order_idempotency WHERE merchant_user_id=? AND parcel_id IS NOT NULL", Long.class, userId);
        return new Page(references.stream().map(reference -> find(userId, reference)).toList(), total, page, size);
    }
}
