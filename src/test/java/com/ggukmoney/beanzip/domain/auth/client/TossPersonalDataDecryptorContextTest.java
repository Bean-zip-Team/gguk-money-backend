package com.ggukmoney.beanzip.domain.auth.client;

import com.ggukmoney.beanzip.support.TossCryptoTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TossPersonalDataDecryptorContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TossPersonalDataDecryptor.class);

    @Test
    void failsApplicationContextWhenKeyOrAadIsMissing() {
        contextRunner.run(context -> assertStartupFailure(
                context.getStartupFailure(),
                TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING
        ));

        contextRunner
                .withPropertyValues("app.auth.toss.decryption-key=" + TossCryptoTestFixture.contextBase64Key())
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING
                ));

        contextRunner
                .withPropertyValues("app.auth.toss.aad=" + TossCryptoTestFixture.contextAad())
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING
                ));
    }

    @Test
    void failsApplicationContextWhenKeyBase64IsInvalid() {
        contextRunner
                .withPropertyValues(
                        "app.auth.toss.decryption-key=not-base64!",
                        "app.auth.toss.aad=" + TossCryptoTestFixture.contextAad()
                )
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        TossPersonalDataDecryptionException.Failure.INVALID_KEY
                ));
    }

    @Test
    void failsApplicationContextWhenDecodedKeyIsNotThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        contextRunner
                .withPropertyValues(
                        "app.auth.toss.decryption-key=" + shortKey,
                        "app.auth.toss.aad=" + TossCryptoTestFixture.contextAad()
                )
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        TossPersonalDataDecryptionException.Failure.INVALID_KEY
                ));
    }

    @Test
    void usesEnvironmentFallbackWhenApplicationPropertiesAreAbsent() {
        contextRunner
                .withPropertyValues(
                        "TOSS_DECRYPTION_KEY=" + TossCryptoTestFixture.contextBase64Key(),
                        "TOSS_DECRYPTION_AAD=" + TossCryptoTestFixture.contextAad()
                )
                .run(context -> assertThat(context).hasSingleBean(TossPersonalDataDecryptor.class));
    }

    @Test
    void applicationPropertiesTakePrecedenceOverEnvironmentFallbacks() {
        contextRunner
                .withPropertyValues(
                        "TOSS_DECRYPTION_KEY=not-base64!",
                        "TOSS_DECRYPTION_AAD=fallback-aad",
                        "app.auth.toss.decryption-key=" + TossCryptoTestFixture.contextBase64Key(),
                        "app.auth.toss.aad=" + TossCryptoTestFixture.contextAad()
                )
                .run(context -> assertThat(context).hasSingleBean(TossPersonalDataDecryptor.class));
    }

    private static void assertStartupFailure(
            Throwable startupFailure,
            TossPersonalDataDecryptionException.Failure expectedFailure
    ) {
        assertThat(startupFailure).isNotNull();
        Throwable current = startupFailure;
        while (current != null && !(current instanceof TossPersonalDataDecryptionException)) {
            current = current.getCause();
        }
        assertThat(current)
                .isInstanceOf(TossPersonalDataDecryptionException.class)
                .extracting(cause -> ((TossPersonalDataDecryptionException) cause).failure())
                .isEqualTo(expectedFailure);
    }
}
