package com.swiftdrop.parcel;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class IdentityIdMigrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    String database() throws Exception {
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var s = c.createStatement()) { s.execute("CREATE DATABASE " + name); }
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name);
    }
    Flyway flyway(String url, String target) {
        var configuration = Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration").target(target);
        // Historical fixtures must retain V1/V2 column types for seeding.
        // The preserved runtime repeatable runs normally on the V3 upgrade.
        if ("1".equals(target) || "2".equals(target)) {
            configuration.repeatableSqlMigrationPrefix("HISTORICAL_FIXTURE_ONLY");
        }
        return configuration.load();
    }
    String query(String url, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var s = c.createStatement(); var r = s.executeQuery(sql)) {
            r.next(); return r.getString(1);
        }
    }
    void execute(String url, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var s = c.createStatement()) { s.execute(sql); }
    }
    void seed(String url, String actor) throws Exception {
        execute(url, """
            INSERT INTO parcels(id,tracking_number,sender_id,created_by,receiver_name,receiver_phone,
              pickup_address,delivery_address,weight,size,parcel_type,charge,status,created_at,updated_at,
              merchant_id,assigned_courier_id)
            VALUES ('00000000-0000-0000-0000-000000000001','SD-LEGACY',%s,%s,'Private','0771234567',
              'Pickup','Delivery',1,'SMALL','STANDARD',100,'CREATED',now(),now(),
              '00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003');
            INSERT INTO parcel_status_history(id,parcel_id,new_status,changed_at,changed_by,note)
            VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000001',
              'CREATED',now(),%s,'Preserved note');
            """.formatted(actor, actor, actor));
    }
    @Test void freshDatabaseUsesBigintForAllIdentityReferences() throws Exception {
        String url = database(); flyway(url, "3").migrate();
        assertThat(query(url, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND data_type='bigint'
            AND ((table_name='parcels' AND column_name IN ('sender_id','created_by','merchant_id','assigned_courier_id'))
              OR (table_name='parcel_status_history' AND column_name='changed_by'))
            """)).isEqualTo("5");
        assertThat(query(url, "SELECT data_type FROM information_schema.columns WHERE table_name='parcels' AND column_name='id'"))
            .isEqualTo("uuid");
    }
    @Test void populatedPreviousVersionPreservesEveryRecordAndLegacyReference() throws Exception {
        String url = database(); flyway(url, "2").migrate(); seed(url, "42");
        String parcelBefore = query(url, "SELECT (to_jsonb(p)-'merchant_id'-'assigned_courier_id')::text FROM parcels p");
        String historyBefore = query(url, "SELECT to_jsonb(h)::text FROM parcel_status_history h");
        flyway(url, "3").migrate();
        assertThat(query(url, "SELECT (to_jsonb(p)-'merchant_id'-'assigned_courier_id'-'legacy_merchant_uuid'-'legacy_assigned_courier_uuid')::text FROM parcels p"))
            .isEqualTo(parcelBefore);
        assertThat(query(url, "SELECT to_jsonb(h)::text FROM parcel_status_history h")).isEqualTo(historyBefore);
        assertThat(query(url, "SELECT legacy_merchant_uuid::text FROM parcels")).endsWith("000000000002");
        assertThat(query(url, "SELECT legacy_assigned_courier_uuid::text FROM parcels")).endsWith("000000000003");
        assertThat(query(url, "SELECT count(*) FROM parcels WHERE merchant_id IS NULL AND assigned_courier_id IS NULL"))
            .isEqualTo("1");
        execute(url, "UPDATE parcels SET merchant_id=51, assigned_courier_id=61");
        assertThat(query(url, "SELECT sender_id || ':' || created_by || ':' || merchant_id || ':' || assigned_courier_id FROM parcels"))
            .isEqualTo("42:42:51:61");
        assertThatThrownBy(() -> execute(url, "UPDATE parcels SET assigned_courier_id=0")).isInstanceOf(SQLException.class);
    }
    @Test void populatedV1FailsSafelyWithoutInventingMappingsOrLosingRows() throws Exception {
        String url = database(); flyway(url, "1").migrate(); seed(url, "'00000000-0000-0000-0000-000000000005'");
        String before = query(url, "SELECT to_jsonb(p)::text FROM parcels p");
        String history = query(url, "SELECT to_jsonb(h)::text FROM parcel_status_history h");
        assertThatThrownBy(() -> flyway(url, "3").migrate()).hasMessageContaining("explicit UUID-to-user-ID");
        assertThat(query(url, "SELECT to_jsonb(p)::text FROM parcels p")).isEqualTo(before);
        assertThat(query(url, "SELECT to_jsonb(h)::text FROM parcel_status_history h")).isEqualTo(history);
        assertThat(query(url, "SELECT max(version) FROM flyway_schema_history WHERE success")).isEqualTo("1");
    }
}
