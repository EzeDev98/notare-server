package com.notare.feature.user.service.impl;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

import static com.notare.common.constant.GeneralConstant.*;

/** Generates random, readable Notarian ids. */
@Component
public class NotarianIdGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generateId() {
        StringBuilder stringBuilder = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            char[] pool = (i % 2 == 0) ? CONSONANTS : VOWELS;
            stringBuilder.append(pool[random.nextInt(pool.length)]);
        }

        return stringBuilder.toString();
    }
}
