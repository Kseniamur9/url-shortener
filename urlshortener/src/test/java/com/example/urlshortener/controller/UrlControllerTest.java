package com.example.urlshortener.controller;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
import com.example.urlshortener.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.example.urlshortener.exception.LinkExpiredException;
import com.example.urlshortener.exception.LinkNotFoundException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UrlService urlService;

    @Test
    void shortenValidUrl() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setUrl("https://google.com");

        when(urlService.shorten(any())).thenReturn(
                ShortenResponse.builder()
                        .shortUrl("http://localhost:8080/1")
                        .originalUrl("https://google.com")
                        .clickCount(0L)
                        .build()
        );

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/1"));
    }

    @Test
    void shortenInvalidUrl() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setUrl("не-урл");

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
    @Test
    void shortenWithInvalidExpireDays() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setUrl("https://google.com");
        req.setExpireDays(0);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void redirectReturns302WithLocation() throws Exception {
        when(urlService.getOriginalUrl("abc")).thenReturn("https://google.com");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void redirectUnknownCodeReturns404() throws Exception {
        when(urlService.getOriginalUrl("nope")).thenThrow(new LinkNotFoundException("nope"));

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void redirectExpiredLinkReturns410() throws Exception {
        when(urlService.getOriginalUrl("old")).thenThrow(new LinkExpiredException("old"));

        mockMvc.perform(get("/old"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }
}