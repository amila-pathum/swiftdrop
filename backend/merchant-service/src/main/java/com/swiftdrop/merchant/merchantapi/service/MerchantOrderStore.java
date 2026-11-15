package com.swiftdrop.merchant.merchantapi.service;

import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MerchantOrderStore {
    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();
    private final JdbcTemplate jdbc;
    public MerchantOrderStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Reservation(String hash, MerchantOrderResponse result) { }
    public record Page(List<MerchantOrderResponse> items, long total, int page, int size) { }
    public boolean reserve(String merchant, String reference, String hash) {
        return jdbc.update("""
                INSERT INTO merchant_integration_orders(merchant_id,merchant_order_reference,request_hash)
                VALUES (?,?,?) ON CONFLICT (merchant_id,merchant_order_reference) DO NOTHING
                """, merchant, reference, hash) == 1;
    }
    public Reservation reservation(String merchant, String reference) {
        return findReservation(merchant, reference).orElseThrow(() ->
                new org.springframework.dao.EmptyResultDataAccessException(1));
    }
    public Optional<Reservation> findReservation(String merchant, String reference) {
        return jdbc.query("""
                SELECT * FROM merchant_integration_orders WHERE merchant_id=? AND merchant_order_reference=?
                """, (rs,row) -> new Reservation(rs.getString("request_hash"),
                rs.getString("tracking_number") == null ? null : response(rs)), merchant, reference).stream().findFirst();
    }
    public void complete(String merchant, MerchantOrderResponse result) {
        String details = result.parcel() == null ? null : JSON.writeValueAsString(result.parcel());
        jdbc.update("""
                UPDATE merchant_integration_orders SET tracking_number=?, parcel_status=?, parcel_details=CAST(? AS JSONB)
                WHERE merchant_id=? AND merchant_order_reference=? AND tracking_number IS NULL
                """, result.trackingNumber(), result.status(), details, merchant, result.merchantOrderReference());
    }
    private static MerchantOrderResponse response(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MerchantOrderResponse(rs.getString("merchant_order_reference"),
                rs.getString("tracking_number"),rs.getString("parcel_status"), rs.getString("parcel_details") == null ? null :
                JSON.readValue(rs.getString("parcel_details"), MerchantOrderResponse.ParcelSnapshot.class));
    }
    public Optional<MerchantOrderResponse> detail(String merchant, String tracking) {
        return jdbc.query("SELECT * FROM merchant_integration_orders WHERE merchant_id=? AND tracking_number=?",
                (rs,row) -> response(rs), merchant, tracking).stream().findFirst();
    }
    public Page list(String merchant, int page, int size) {
        var items = jdbc.query("""
                SELECT * FROM merchant_integration_orders WHERE merchant_id=? AND tracking_number IS NOT NULL
                ORDER BY created_at DESC, merchant_order_reference LIMIT ? OFFSET ?
                """, (rs,row) -> response(rs), merchant, size, (long) page * size);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM merchant_integration_orders WHERE merchant_id=? AND tracking_number IS NOT NULL
                """, Long.class, merchant);
        return new Page(items,total,page,size);
    }
}
