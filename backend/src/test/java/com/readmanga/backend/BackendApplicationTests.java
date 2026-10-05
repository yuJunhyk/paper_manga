package com.readmanga.backend;

import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
