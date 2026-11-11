package com.example.urlshortener.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String code) {
        super("Срок действия ссылки истёк: " + code);
    }
}