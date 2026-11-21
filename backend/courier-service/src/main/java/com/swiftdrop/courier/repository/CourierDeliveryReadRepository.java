package com.swiftdrop.courier.repository;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourierDeliveryReadRepository {
    private final JdbcTemplate jdbc;
    public CourierDeliveryReadRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Assignment> assignments(Long profileId) {
        return jdbc.query("""
            SELECT a.*,coalesce(a.delivery_date,p.delivery_date) AS scheduled_date
            FROM courier_delivery_assignments a JOIN courier_profiles c ON c.dispatch_courier_id=a.dispatch_courier_id
            JOIN courier_delivery_parcels p ON p.parcel_id=a.parcel_id
            WHERE c.id=? ORDER BY a.assigned_at DESC,a.parcel_id
            """,(rs,row)->new Assignment(rs.getObject("projection_id",UUID.class),rs.getObject("parcel_id",UUID.class),profileId,
                rs.getInt("attempt_no"),rs.getBoolean("active"),rs.getLong("assignment_version"),
                rs.getObject("scheduled_date",java.time.LocalDate.class),rs.getTimestamp("assigned_at").toInstant()),profileId);
    }

    public ParcelDetails parcel(UUID id) {
        var history=jdbc.query("SELECT * FROM courier_delivery_history WHERE parcel_id=? ORDER BY recorded_at,id",
            (rs,row)->new ParcelHistory(rs.getObject("id",UUID.class),rs.getString("status"),rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("location_description"),rs.getObject("attempt_no",Integer.class),rs.getString("actor_role")),id);
        var rows=jdbc.query("SELECT * FROM courier_delivery_parcels WHERE parcel_id=?",(rs,row)->new ParcelDetails(id,
            rs.getString("tracking_number"),rs.getString("status")==null?"UNKNOWN":rs.getString("status"),rs.getString("pickup_address"),
            rs.getString("delivery_address"),rs.getString("receiver_name"),rs.getString("receiver_phone"),rs.getBigDecimal("weight"),
            rs.getString("size"),rs.getString("pickup_contact_name"),rs.getString("pickup_contact_phone"),rs.getString("contents_description"),
            rs.getBigDecimal("length_cm"),rs.getBigDecimal("width_cm"),rs.getBigDecimal("height_cm"),history),id);
        if(rows.isEmpty())throw new RemoteFailure("Courier parcel data",false);
        return rows.getFirst();
    }
}
