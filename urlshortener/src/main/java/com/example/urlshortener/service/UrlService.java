package com.example.urlshortener.service;

import com.example.urlshortener.exception.LinkExpiredException;
import com.example.urlshortener.exception.LinkNotFoundException;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
import com.example.urlshortener.model.Url;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.util.Base62;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final String CACHE_PREFIX = "url:";
    private static final Duration MAX_CACHE_TTL = Duration.ofHours(24);

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redis;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        Url url = Url.builder()
                .code("tmp")
                .originalUrl(request.getUrl())
                .clickCount(0L)
                .build();

        if (request.getExpireDays() != null) {
            url.setExpiresAt(LocalDateTime.now().plusDays(request.getExpireDays()));
        }

        url = urlRepository.save(url);


        String code = Base62.encode(url.getId());
        url.setCode(code);
        urlRepository.save(url);

        log.info("Создана короткая ссылка: {} -> {}", code, request.getUrl());

        return ShortenResponse.builder()
                .shortUrl(baseUrl + "/" + code)
                .originalUrl(request.getUrl())
                .clickCount(0L)
                .expiresAt(url.getExpiresAt() != null ? url.getExpiresAt().toString() : null)
                .build();
    }

    public String getOriginalUrl(String code) {
        String cached = redis.opsForValue().get(CACHE_PREFIX + code);
        if (cached != null) {
            log.info("Cache HIT для кода: {}", code);
            incrementClickAsync(code);
            return cached;
        }

        Url url = urlRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        LocalDateTime now = LocalDateTime.now();
        if (url.getExpiresAt() != null && !url.getExpiresAt().isAfter(now)) {
            throw new LinkExpiredException(code);
        }

        redis.opsForValue().set(CACHE_PREFIX + code, url.getOriginalUrl(), cacheTtl(url, now));
        log.info("Cache MISS, загружено из БД: {}", code);

        incrementClickAsync(code);
        return url.getOriginalUrl();
    }

    private Duration cacheTtl(Url url, LocalDateTime now) {
        if (url.getExpiresAt() == null) {
            return MAX_CACHE_TTL;
        }
        Duration untilExpiry = Duration.between(now, url.getExpiresAt());
        return untilExpiry.compareTo(MAX_CACHE_TTL) < 0 ? untilExpiry : MAX_CACHE_TTL;
    }

    public ShortenResponse getStats(String code) {
        Url url = urlRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        return ShortenResponse.builder()
                .shortUrl(baseUrl + "/" + code)
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .expiresAt(url.getExpiresAt() != null ? url.getExpiresAt().toString() : null)
                .build();
    }


    private void incrementClickAsync(String code) {
        Thread.ofVirtual().start(() -> urlRepository.incrementClickCount(code));
    }
}