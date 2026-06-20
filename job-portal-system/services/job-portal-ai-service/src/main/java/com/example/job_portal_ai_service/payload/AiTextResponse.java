package com.example.job_portal_ai_service.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTextResponse {
    private String content;

    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
}
