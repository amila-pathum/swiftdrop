package com.swiftdrop.courier.event;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CourierOutbox {
    public static final String ASSIGNED="courier.delivery-assigned.v1";
    public static final String CANCELLED="courier.delivery-assignment-cancelled.v1";
    public static final String STATUS="courier.delivery-status-changed.v1";
    public static final String AVAILABILITY="courier.availability-changed.v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public CourierOutbox(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}

    @Transactional(propagation=Propagation.MANDATORY)
    public UUID save(String route,String type,String aggregate,Map<String,?> data) {
        UUID id=UUID.randomUUID();Instant now=Instant.now();
        var event=new LinkedHashMap<String,Object>();
        event.put("eventId",id);event.put("eventType",type);event.put("schemaVersion",1);event.put("occurredAt",now);
        event.putAll(data);
        jdbc.update("INSERT INTO courier_event_outbox(event_id,aggregate_key,routing_key,payload,occurred_at) VALUES (?,?,?,?,?)",
            id,aggregate,route,mapper.writeValueAsString(event),Timestamp.from(now));
        return id;
    }
}
