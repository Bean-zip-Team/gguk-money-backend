package com.ggukmoney.beanzip.support;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class TossCryptoTestFixture {

    private static final int KEY_LENGTH_BITS = 256;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final SecretKey CONTEXT_KEY = newKey();
    private static final String CONTEXT_AAD = "test-aad-" + UUID.randomUUID();

    private TossCryptoTestFixture() {
    }

    public static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_LENGTH_BITS, SECURE_RANDOM);
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to generate test AES key", exception);
        }
    }

    public static String base64Key(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String contextBase64Key() {
        return base64Key(CONTEXT_KEY);
    }

    public static String contextAad() {
        return CONTEXT_AAD;
    }

    public static Context context() {
        return new Context(newKey(), "test-aad-" + UUID.randomUUID());
    }

    public static String encrypt(String plaintext, SecretKey key, String aad) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cipherTextWithTag.length)
                            .put(iv)
                            .put(cipherTextWithTag)
                            .array()
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt Toss test data", exception);
        }
    }

    public record Context(SecretKey key, String aad) {

        public String base64Key() {
            return TossCryptoTestFixture.base64Key(key);
        }

        public String encrypt(String plaintext) {
            return TossCryptoTestFixture.encrypt(plaintext, key, aad);
        }
    }
}
