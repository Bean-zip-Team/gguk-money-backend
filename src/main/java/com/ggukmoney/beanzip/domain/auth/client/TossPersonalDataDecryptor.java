package com.ggukmoney.beanzip.domain.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

@Component
public class TossPersonalDataDecryptor {

    private static final int AES_256_KEY_LENGTH_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final byte[] aad;

    public TossPersonalDataDecryptor(
            @Value("${app.auth.toss.decryption-key:${TOSS_DECRYPTION_KEY:}}") String base64EncodedKey,
            @Value("${app.auth.toss.aad:${TOSS_DECRYPTION_AAD:}}") String additionalAuthenticatedData
    ) {
        if (!StringUtils.hasText(base64EncodedKey) || !StringUtils.hasText(additionalAuthenticatedData)) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.CONFIGURATION_MISSING
            );
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(base64EncodedKey);
        } catch (IllegalArgumentException exception) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.INVALID_KEY,
                    exception
            );
        }
        if (decodedKey.length != AES_256_KEY_LENGTH_BYTES) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.INVALID_KEY
            );
        }

        this.key = new SecretKeySpec(decodedKey, "AES");
        this.aad = additionalAuthenticatedData.getBytes(StandardCharsets.UTF_8);
    }

    public String decryptNullable(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }

        byte[] decodedCipherText;
        try {
            decodedCipherText = Base64.getDecoder().decode(encryptedValue);
        } catch (IllegalArgumentException exception) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT_BASE64,
                    exception
            );
        }
        if (decodedCipherText.length < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT
            );
        }

        byte[] iv = Arrays.copyOfRange(decodedCipherText, 0, GCM_IV_LENGTH_BYTES);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            byte[] plainText = cipher.doFinal(
                    decodedCipherText,
                    GCM_IV_LENGTH_BYTES,
                    decodedCipherText.length - GCM_IV_LENGTH_BYTES
            );
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.AUTHENTICATION_FAILED,
                    exception
            );
        } catch (GeneralSecurityException exception) {
            throw new TossPersonalDataDecryptionException(
                    TossPersonalDataDecryptionException.Failure.DECRYPTION_FAILED,
                    exception
            );
        }
    }
}
