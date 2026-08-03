package com.ggukmoney.beanzip.global.util;

public final class NameMasker {

    private NameMasker() {
    }

    public static String mask(String name) {
        if (name == null) {
            return null;
        }

        String normalized = name.strip();
        if (normalized.isEmpty()) {
            return null;
        }

        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount == 1) {
            return normalized;
        }

        int firstEndIndex = normalized.offsetByCodePoints(0, 1);
        if (codePointCount == 2) {
            return normalized.substring(0, firstEndIndex) + "*";
        }

        int lastStartIndex = normalized.offsetByCodePoints(0, codePointCount - 1);
        return normalized.substring(0, firstEndIndex)
                + "*".repeat(codePointCount - 2)
                + normalized.substring(lastStartIndex);
    }
}
