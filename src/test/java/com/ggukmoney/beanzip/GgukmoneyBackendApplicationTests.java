package com.ggukmoney.beanzip;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.auth.jwt.secret=integration-test-secret-at-least-32-bytes-long",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.data.redis.repositories.enabled=false"
})
public class GgukmoneyBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
