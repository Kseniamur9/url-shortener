package com.example.urlshortener.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String code) {
        super("Ссылка не найдена: " + code);
    }
}