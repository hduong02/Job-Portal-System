package com.example.dto.response;

import com.example.domain.ProficiencyLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeSkillResponse {

    private Long id;
    private String skillName;
    private ProficiencyLevel proficiencyLevel;
    private Integer yearsOfExperience;
    private Integer displayOrder;
}
