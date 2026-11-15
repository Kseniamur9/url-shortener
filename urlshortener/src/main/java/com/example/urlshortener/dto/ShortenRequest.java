package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL не может быть пустым")
    @Pattern(
            regexp = "^https?://.*",
            message = "URL должен начинаться с http:// или https://"
    )
    private String url;

    @Min(value = 1, message = "expireDays должен быть не меньше 1")
    @Max(value = 365, message = "expireDays должен быть не больше 365")
    private Integer expireDays;
}
