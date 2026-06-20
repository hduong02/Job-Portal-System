package com.example.job_portal_ai_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.job_portal_ai_service.client.GeminiClient;
import com.example.job_portal_ai_service.payload.AiTextResponse;
import com.example.job_portal_ai_service.payload.JobDescriptionRequest;
import com.example.job_portal_ai_service.payload.SalaryRangeRequest;
import com.example.job_portal_ai_service.payload.SalaryRangeResponse;

@Service
@RequiredArgsConstructor
public class JobAiService {

    private final GeminiClient geminiClient;

    String systemPrompt = """
            You are a senior HR professional and technical recruiter with deep knowledge of
            the tech job market (2025-2026).You specialize in writing job descriptions,
            compensation benchmarking, and talent acquisition.Always write in a professional,
            engaging, inclusive, and bias-free tone.When asked for JSON, respond ONLY with
            valid JSON — no explanation, no markdown fences.
            """;

    public AiTextResponse generateJobDescription(JobDescriptionRequest req) throws Exception {

        String skills = req.getSkills() != null
                ? String.join(", ", req.getSkills())
                : "Not specified";
        String prompt = """
                Write a comprehensive, engaging, and inclusive job description.

                Job Details:
                - Title: %s
                - Required Skills: %s
                - Experience Level: %s
                - Job Type: %s
                - Work Mode: %s
                - Category: %s
                - Additional Context: %s

                Format the response in clean markdown with EXACTLY these sections:
                ## About the Role
                [2-3 compelling sentences describing the role and its impact]

                Do NOT include placeholder company names.
                """.formatted(
                        req.getTitle(),
                        skills,
                        req.getExperienceLevel() != null ? req.getExperienceLevel()
                                : "not specified",
                        req.getJobType() != null ? req.getJobType() : "not specified",
                        req.getWorkMode() != null ? req.getWorkMode() : "not specified",
                        req.getCategory() != null ? req.getCategory() : "not specified",
                        req.getAdditionalContext() != null ? req.getAdditionalContext() : "none"

        );

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt, prompt))
                .build();

    }

    public AiTextResponse generateJobRequirements(String title, String category)
            throws Exception {
        String prompt = """
                Generate professional job requirements and responsibilities for this role.

                Job Title: %s
                Category: %s

                Format in markdown:
                ## Requirements
                - [5 specific bullet points]

                Keep it concise and ATS-friendly.
                """.formatted(title, category);

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt, prompt))
                .build();
    }

    public SalaryRangeResponse suggestSalaryRange(SalaryRangeRequest req) throws Exception {
        String skills = req.getSkills() != null ? String.join(", ", req.getSkills())
                : "Not specified";
        String prompt = """
                Provide a realistic and competitive salary range for this role.

                Role Details:
                - Job Title: %s
                - Required Skills: %s
                - Experience Level: %s
                - Job Type: %s
                - Location: %s

                {
                    "minSalary": 600000,
                    "maxSalary": 1200000,
                    "currency": "INR",
                    "period": "YEARLY",
                    "marketInsight": "Brief 1-2 sentence insight about this role's
                            compensation trend in the current tech market"
                }

                minSalary and maxSalary must be numbers (not strings). Use realistic current
                        tech market rates.
                """.formatted(
                        req.getTitle(),
                        skills,
                        req.getExperienceLevel() != null ? req.getExperienceLevel()
                                : "MID",
                        req.getJobType() != null ? req.getJobType()
                                : "FULL_TIME",
                        req.getLocation() != null ? req.getLocation()
                                : "US"


        );

        return geminiClient.generateJson(systemPrompt, prompt, SalaryRangeResponse.class);

    }

    public AiTextResponse generateJobResponsibilities(String title, String category)
            throws Exception {
        String prompt = """
                Generate 6 specific, action-oriented job responsibilities for this role.

                Job Title: %s
                Category: %s

                Return ONLY a plain bullet list (no headings, no markdown headers):
                - [responsibility 1]
                - [responsibility 2]
                ...

                Keep each bullet concise (under 15 words), start with a strong action verb.
                """.formatted(title, category);

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt, prompt))
                .build();
    }

    public AiTextResponse generateJobBenefits(
            String title,
            String category,
            String jobType) throws Exception {

        String prompt = """
                Generate 6 competitive, attractive job benefits for this role.

                Job Title: %s
                Category: %s
                Job Type: %s

                Return ONLY a plain bullet list (no headings, no markdown headers):
                - [benefit 1]
                - [benefit 2]
                ...

                Include a mix of: compensation perks, health/wellness, growth, flexibility,
                and culture benefits. Keep each bullet concise and specific.
                """.formatted(
                        title,
                        category != null ? category : "General",
                        jobType != null ? jobType : "Full Time"
        );

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt, prompt))
                .build();
    }

    public AiTextResponse recommendSkillForJob(String jobTitle, String description)
            throws Exception {
        String prompt = """
                Recommend the most relevant skills for this job posting.

                Job Title: %s
                Description: %s

                List 8-10 specific, relevant skills that candidates should have.
                Return a comma-separated list of skill names only.
                Example: Java, Spring Boot, PostgreSQL, Docker, REST APIs, Microservices
                """.formatted(jobTitle, description);

        return AiTextResponse.builder().content(
                geminiClient.generateText(systemPrompt, prompt))
                .build();
    }

    public AiTextResponse recommendTagsForJob(String jobTitle, String description)
            throws Exception {
        String prompt = """
                Recommend 8-10 relevant tags/keywords for this job posting that improve
                        discoverability.

                Job Title: %s
                Description: %s

                Return ONLY a comma-separated list of short tag names (1-3 words each).
                Example: React, Frontend, JavaScript, Remote, Startup, Full Stack,
                        Web Development
                """.formatted(jobTitle, description);

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt, prompt))
                .build();
    }
}