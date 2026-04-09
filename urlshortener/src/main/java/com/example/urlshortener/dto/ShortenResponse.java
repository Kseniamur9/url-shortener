package com.example.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShortenResponse {
    private String shortUrl;
    private String originalUrl;
    private Long clickCount;
    private String expiresAt;
}