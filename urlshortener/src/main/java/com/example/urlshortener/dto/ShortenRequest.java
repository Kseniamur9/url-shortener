package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL не может быть пустым")
    @Pattern(
            regexp = "^https?://.*",
            message = "URL должен начинаться с http:// или https://"
    )
    private String url;


    private Integer expireDays;
}
