package com.ggukmoney.beanzip;

import com.ggukmoney.beanzip.support.TossCryptoTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
		"app.auth.jwt.secret=integration-test-secret-at-least-32-bytes-long",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.data.redis.repositories.enabled=false"
})
public class GgukmoneyBackendApplicationTests {

	@DynamicPropertySource
	static void registerTossDecryptionProperties(DynamicPropertyRegistry registry) {
		registry.add("app.auth.toss.decryption-key", TossCryptoTestFixture::contextBase64Key);
		registry.add("app.auth.toss.aad", TossCryptoTestFixture::contextAad);
	}

	@Test
	void contextLoads() {
	}

}
