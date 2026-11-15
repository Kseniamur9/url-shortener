package com.example.urlshortener.service;

import com.example.urlshortener.exception.LinkExpiredException;
import com.example.urlshortener.exception.LinkNotFoundException;
import com.example.urlshortener.model.Url;
import com.example.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock UrlRepository urlRepository;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks UrlService urlService;

    @BeforeEach
    void setUp() {
        // baseUrl приходит через @Value, в юнит-тесте его надо задать вручную
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void cacheHitReturnsUrlWithoutTouchingDatabase() {
        when(valueOps.get("url:abc")).thenReturn("https://example.com");

        String result = urlService.getOriginalUrl("abc");

        assertEquals("https://example.com", result);
        verify(urlRepository, never()).findByCode(anyString());
    }

    @Test
    void unknownCodeThrowsNotFound() {
        when(valueOps.get("url:abc")).thenReturn(null);
        when(urlRepository.findByCode("abc")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> urlService.getOriginalUrl("abc"));
    }

    @Test
    void expiredLinkThrowsExpiredAndIsNotCached() {
        when(valueOps.get("url:abc")).thenReturn(null);
        when(urlRepository.findByCode("abc")).thenReturn(Optional.of(
                url("abc", LocalDateTime.now().minusMinutes(1))));

        assertThrows(LinkExpiredException.class, () -> urlService.getOriginalUrl("abc"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void linkWithoutExpiryIsCachedFor24Hours() {
        when(valueOps.get("url:abc")).thenReturn(null);
        when(urlRepository.findByCode("abc")).thenReturn(Optional.of(url("abc", null)));

        urlService.getOriginalUrl("abc");

        verify(valueOps).set("url:abc", "https://example.com", Duration.ofHours(24));
    }

    @Test
    void cacheTtlNeverExceedsLinkLifetime() {
        when(valueOps.get("url:abc")).thenReturn(null);
        when(urlRepository.findByCode("abc")).thenReturn(Optional.of(
                url("abc", LocalDateTime.now().plusHours(1))));

        urlService.getOriginalUrl("abc");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq("url:abc"), eq("https://example.com"), ttl.capture());
        assertTrue(ttl.getValue().compareTo(Duration.ofHours(1)) <= 0);
        assertTrue(ttl.getValue().isPositive());
    }

    @Test
    void successfulRedirectIncrementsClickCount() {
        when(valueOps.get("url:abc")).thenReturn("https://example.com");

        urlService.getOriginalUrl("abc");

        // инкремент идёт в отдельном потоке, поэтому ждём до 1 секунды
        verify(urlRepository, timeout(1000)).incrementClickCount("abc");
    }

    private Url url(String code, LocalDateTime expiresAt) {
        return Url.builder()
                .code(code)
                .originalUrl("https://example.com")
                .clickCount(0L)
                .expiresAt(expiresAt)
                .build();
    }
}