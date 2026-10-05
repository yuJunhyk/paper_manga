package com.readmanga.backend;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트 전용 DB: 테스트를 돌릴 때 Docker로 일회용 PostgreSQL을 띄우고, 끝나면 지운다.
 * 예전에는 테스트가 실제 서비스 DB를 같이 써서, 테스트가 만들고 되돌린 데이터 때문에 실제 번호(id)가 빠지고
 * 서버 시작 시 정리 기능이 실제 작업을 건드릴 위험이 있었다.
 * {@code @ServiceConnection}: 컨테이너의 주소·계정을 Spring에 자동으로 넘겨 spring.datasource.* 설정을 대신한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17")
                // 기본은 모든 네트워크(0.0.0.0)에 열린다. 실제 서비스와 같은 기준으로 이 컴퓨터 안(127.0.0.1)의 빈 포트에만 연다
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))));
    }
}
