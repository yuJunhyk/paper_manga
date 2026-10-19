package com.readmanga.backend.common;

import com.readmanga.backend.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 구조는 Flyway가 db/migration의 SQL 파일로 만든다. 빈 DB(테스트용 일회용 DB)에 V1이 실제로 돌고,
 * Hibernate의 검사(ddl-auto=validate)를 통과해야 서버가 뜬다. 이 테스트가 돈다는 것 자체가 V1과 엔티티가 맞다는 뜻이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
class MigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 빈_DB에는_V1부터_차례로_적용되고_장부에_성공으로_남는다() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState)).allMatch(state -> state == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).as("적용하지 않은 변경 파일").isEmpty();
    }

    @Test
    void V1에는_10월_5일에_손으로_고친_작업_상태_검사가_들어_있다() {
        // ddl-auto=update는 이미 있는 CHECK를 고치지 못해 CANCELLED를 손으로 추가했다(db/manual/2026-10-05_job_status_cancelled.sql)
        String check = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'paper_job_status_check'", String.class);

        assertThat(check).contains("CANCELLED");
    }
}
