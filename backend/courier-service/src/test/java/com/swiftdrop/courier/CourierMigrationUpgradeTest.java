package com.swiftdrop.courier;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Reproduces the pre-existing RDS V1 using a disposable database, never RDS. */
@Testcontainers(disabledWithoutDocker=true)
class CourierMigrationUpgradeTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    Flyway migrations(String target) {
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .locations("classpath:db/migration").baselineOnMigrate(false);
        if(target!=null)config.target(target);
        return config.load();
    }
    List<String> rows(String query) throws Exception {
        var values=new ArrayList<String>();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            var statement=c.createStatement();var result=statement.executeQuery(query)) {
            while(result.next())values.add(result.getString(1));
        }
        return values;
    }
    @Test void upgradesExistingV1WithoutRepairOrLosingLegacyCouriers() throws Exception {
        migrations("1").migrate();
        var legacy=rows("SELECT courier_id::text || ':' || display_name || ':' || active::text || ':' || available::text FROM couriers ORDER BY courier_id");
        assertThat(legacy).hasSize(3);
        assertThat(rows("SELECT checksum::text FROM flyway_schema_history WHERE version='1'")).containsExactly("1639178571");

        var upgrade=migrations(null);
        upgrade.migrate();
        upgrade.validate();

        assertThat(rows("SELECT courier_id::text || ':' || display_name || ':' || active::text || ':' || available::text FROM couriers ORDER BY courier_id")).containsExactlyElementsOf(legacy);
        assertThat(rows("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")).containsExactly("1","2");
        assertThat(rows("SELECT checksum::text FROM flyway_schema_history WHERE version='1'")).containsExactly("1639178571");
        assertThat(rows("SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name"))
            .containsExactly("courier_profiles","couriers","delivery_checkpoints","delivery_proofs","flyway_schema_history");
    }
}
