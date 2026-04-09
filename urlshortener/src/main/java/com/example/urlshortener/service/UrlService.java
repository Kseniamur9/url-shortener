package com.example.urlshortener.service;

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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redis;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        // Сохраняем с временным кодом, получаем ID
        Url url = Url.builder()
                .code("tmp")
                .originalUrl(request.getUrl())
                .clickCount(0L)
                .build();

        if (request.getExpireDays() != null) {
            url.setExpiresAt(LocalDateTime.now().plusDays(request.getExpireDays()));
        }

        url = urlRepository.save(url);

        // Генерируем код из ID — гарантирует уникальность, нет коллизий
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

    @Transactional
    public String getOriginalUrl(String code) {
        // 1. Проверяем Redis кэш
        String cached = redis.opsForValue().get("url:" + code);
        if (cached != null) {
            log.info("Cache HIT для кода: {}", code);
            incrementClickAsync(code);
            return cached;
        }

        // 2. Fallback в PostgreSQL
        Url url = urlRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Ссылка не найдена: " + code));

        // 3. Проверяем не истекла ли ссылка
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Ссылка истекла");
        }

        // 4. Кладём в кэш на 24 часа
        redis.opsForValue().set("url:" + code, url.getOriginalUrl(), Duration.ofHours(24));
        log.info("Cache MISS, загружено из БД: {}", code);

        incrementClickAsync(code);
        return url.getOriginalUrl();
    }

    public ShortenResponse getStats(String code) {
        Url url = urlRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Ссылка не найдена"));

        return ShortenResponse.builder()
                .shortUrl(baseUrl + "/" + code)
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .expiresAt(url.getExpiresAt() != null ? url.getExpiresAt().toString() : null)
                .build();
    }

    // Счётчик кликов не должен тормозить редирект
    private void incrementClickAsync(String code) {
        Thread.ofVirtual().start(() -> urlRepository.incrementClickCount(code));
    }
}