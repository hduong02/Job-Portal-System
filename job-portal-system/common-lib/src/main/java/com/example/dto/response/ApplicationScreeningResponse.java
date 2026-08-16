package com.example.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

import com.example.domain.AiShortListStatus;

@Data
@Builder
public class ApplicationScreeningResponse {

    private Long id;
    private int overallScore;
    private int skillsMatchScore;
    private int experienceMatchScore;
    private int educationMatchScore;
    private AiShortListStatus shortListStatus;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> concerns;
    private String summary;
    private LocalDateTime screenedAt;
}
