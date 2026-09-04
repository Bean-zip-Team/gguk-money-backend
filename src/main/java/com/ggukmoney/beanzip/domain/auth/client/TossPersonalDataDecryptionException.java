package com.ggukmoney.beanzip.domain.auth.client;

final class TossPersonalDataDecryptionException extends RuntimeException {

    enum Failure {
        CONFIGURATION_MISSING,
        INVALID_KEY,
        INVALID_CIPHERTEXT_BASE64,
        INVALID_CIPHERTEXT,
        AUTHENTICATION_FAILED,
        DECRYPTION_FAILED
    }

    private final Failure failure;

    TossPersonalDataDecryptionException(Failure failure) {
        super("Toss personal data decryption failed: " + failure.name());
        this.failure = failure;
    }

    TossPersonalDataDecryptionException(Failure failure, Throwable cause) {
        super("Toss personal data decryption failed: " + failure.name(), cause);
        this.failure = failure;
    }

    Failure failure() {
        return failure;
    }
}
