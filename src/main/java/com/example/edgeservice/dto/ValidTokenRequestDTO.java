package com.example.edgeservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ValidTokenRequestDTO {
    private String token;
}
