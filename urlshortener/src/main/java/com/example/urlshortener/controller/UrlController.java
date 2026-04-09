package com.example.urlshortener.controller;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
import com.example.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    // POST /shorten — создать короткую ссылку
    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shorten(request);
        return ResponseEntity.ok(response);
    }

    // GET /{code} — редирект на оригинальный URL
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = urlService.getOriginalUrl(code);
        return ResponseEntity.status(302)
                .location(URI.create(originalUrl))
                .build();
    }

    // GET /stats/{code} — статистика по ссылке
    @GetMapping("/stats/{code}")
    public ResponseEntity<ShortenResponse> stats(@PathVariable String code) {
        return ResponseEntity.ok(urlService.getStats(code));
    }
}
