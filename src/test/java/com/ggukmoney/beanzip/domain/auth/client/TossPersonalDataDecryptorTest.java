package com.ggukmoney.beanzip.domain.auth.client;

import com.ggukmoney.beanzip.support.TossCryptoTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPersonalDataDecryptorTest {

    private SecretKey key;
    private String base64Key;
    private String aad;
    private TossPersonalDataDecryptor decryptor;

    @BeforeEach
    void setUp() {
        key = TossCryptoTestFixture.newKey();
        base64Key = TossCryptoTestFixture.base64Key(key);
        aad = "test-aad";
        decryptor = new TossPersonalDataDecryptor(base64Key, aad);
    }

    @Test
    void decryptsAes256GcmCiphertext() {
        String encrypted = TossCryptoTestFixture.encrypt("Bean", key, aad);

        assertThat(decryptor.decryptNullable(encrypted)).isEqualTo("Bean");
    }

    @Test
    void decryptsKoreanUtf8Name() {
        String encrypted = TossCryptoTestFixture.encrypt("김토스", key, aad);

        assertThat(decryptor.decryptNullable(encrypted)).isEqualTo("김토스");
    }

    @Test
    void returnsNullForNullEmptyAndBlankValues() {
        assertThat(decryptor.decryptNullable(null)).isNull();
        assertThat(decryptor.decryptNullable("")).isNull();
        assertThat(decryptor.decryptNullable("   ")).isNull();
    }

    @Test
    void rejectsInvalidCiphertextBase64() {
        assertFailure("not-base64!", TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT_BASE64);
    }

    @Test
    void rejectsPayloadShorterThanIvAndAuthenticationTag() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[27]);

        assertFailure(tooShort, TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT);
    }

    @Test
    void failsConstructionWhenKeyOrAadIsMissing() {
        assertConfigurationFailure("", aad, TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING);
        assertConfigurationFailure(base64Key, "", TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING);
        assertConfigurationFailure(base64Key, "   ", TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING);
    }

    @Test
    void failsConstructionWhenKeyBase64IsInvalid() {
        assertConfigurationFailure("not-base64!", aad, TossPersonalDataDecryptionException.Failure.INVALID_KEY);
    }

    @Test
    void failsConstructionWhenDecodedKeyIsNotThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        assertConfigurationFailure(shortKey, aad, TossPersonalDataDecryptionException.Failure.INVALID_KEY);
    }

    @Test
    void treatsWrongValidLengthKeyAsAuthenticationFailure() {
        String encrypted = TossCryptoTestFixture.encrypt("김토스", key, aad);
        TossPersonalDataDecryptor wrongKeyDecryptor = new TossPersonalDataDecryptor(
                TossCryptoTestFixture.base64Key(TossCryptoTestFixture.newKey()),
                aad
        );

        assertFailure(wrongKeyDecryptor, encrypted, TossPersonalDataDecryptionException.Failure.AUTHENTICATION_FAILED);
    }

    @Test
    void treatsWrongAadAsAuthenticationFailure() {
        String encrypted = TossCryptoTestFixture.encrypt("김토스", key, aad);
        TossPersonalDataDecryptor wrongAadDecryptor = new TossPersonalDataDecryptor(base64Key, "different-aad");

        assertFailure(wrongAadDecryptor, encrypted, TossPersonalDataDecryptionException.Failure.AUTHENTICATION_FAILED);
    }

    @Test
    void treatsTamperedCiphertextAsAuthenticationFailure() {
        byte[] decoded = Base64.getDecoder().decode(TossCryptoTestFixture.encrypt("김토스", key, aad));
        decoded[12] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertFailure(tampered, TossPersonalDataDecryptionException.Failure.AUTHENTICATION_FAILED);
    }

    private void assertFailure(String encrypted, TossPersonalDataDecryptionException.Failure failure) {
        assertFailure(decryptor, encrypted, failure);
    }

    private void assertFailure(
            TossPersonalDataDecryptor target,
            String encrypted,
            TossPersonalDataDecryptionException.Failure failure
    ) {
        assertThatThrownBy(() -> target.decryptNullable(encrypted))
                .isInstanceOf(TossPersonalDataDecryptionException.class)
                .satisfies(exception -> assertThat(((TossPersonalDataDecryptionException) exception).failure())
                        .isEqualTo(failure));
    }

    private void assertConfigurationFailure(
            String configuredKey,
            String configuredAad,
            TossPersonalDataDecryptionException.Failure failure
    ) {
        assertThatThrownBy(() -> new TossPersonalDataDecryptor(configuredKey, configuredAad))
                .isInstanceOf(TossPersonalDataDecryptionException.class)
                .satisfies(exception -> assertThat(((TossPersonalDataDecryptionException) exception).failure())
                        .isEqualTo(failure));
    }
}
