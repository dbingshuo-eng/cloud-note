package com.clouddisk.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureShareCodeGenerator implements ShareCodeGenerator {

    private static final char[] ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final int CODE_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
