package com.clouddisk.validation;

import java.nio.charset.StandardCharsets;

public final class SharePasswordPolicy {

    public static final int MAX_UTF8_BYTES = 72;
    public static final String LENGTH_MESSAGE =
            "password must not exceed 72 UTF-8 bytes";

    private SharePasswordPolicy() {
    }

    public static boolean hasValidLength(String password) {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }
}
