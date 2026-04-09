package com.example.urlshortener.util;

public class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long n) {
        if (n == 0) return String.valueOf(ALPHABET.charAt(0));

        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            sb.insert(0, ALPHABET.charAt((int)(n % 62)));
            n /= 62;
        }
        return sb.toString();
    }
}
