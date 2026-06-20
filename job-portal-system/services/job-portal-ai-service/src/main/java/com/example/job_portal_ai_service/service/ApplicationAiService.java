package com.example.job_portal_ai_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.job_portal_ai_service.client.GeminiClient;
import com.example.job_portal_ai_service.payload.AiTextResponse;
import com.example.job_portal_ai_service.payload.CoverLetterRequest;
import com.example.job_portal_ai_service.payload.ScreeningScoreRequest;
import com.example.job_portal_ai_service.payload.ScreeningScoreResponse;
import com.example.job_portal_ai_service.payload.SkillsGapRequest;
import com.example.job_portal_ai_service.payload.SkillsGapResponse;

@Service
@RequiredArgsConstructor
public class ApplicationAiService {

    private final GeminiClient geminiClient;

    private static final String systemPrompt = """
            You are a senior technical recruiter and career coach with 15+ years of experience
            in the tech industry. You specialize in candidate evaluation, cover letter writing,
            skills gap analysis, and career development.Always provide objective, fair, and
            actionable assessments based only on the information provided.When asked for JSON,
            respond ONLY with valid JSON — no explanation, no markdown fences.
            """;

    public AiTextResponse generateCoverLetter(CoverLetterRequest request) throws Exception {

        String skills = request.getCandidateSkills() != null
                ? String.join(", ", request.getCandidateSkills())
                : "Not Provided";
        String experience= request.getCandidateExperience() != null
                ? String.join(", ", request.getCandidateExperience())
                : "Not Provided";

        String prompt = """
                Write a compelling, personalized cover letter.

                Position: %s
                Job Description: %s
                Target Company: %s

                Candidate Profile:
                - Name: %s
                - Professional Summary: %s
                - Key Skills: %s
                - Relevant Experience: %s

                Write a 3-paragraph cover letter:
                Paragraph 1 (Opening): Express specific enthusiasm for this exact role and
                        company. Mention 1 specific thing about the role that excites you.
                Paragraph 2 (Body): Connect 2-3 of the candidate's strongest experiences/skills
                        directly to the job requirements. Be specific with examples.
                Paragraph 3 (Closing): Confident call to action. Express eagerness to discuss
                        further.

                Rules:
                - Write as the candidate (first person)
                - Be specific — avoid generic statements
                - Maximum 300 words
                - Professional but warm tone
                - Do NOT use placeholders like [Company Name] — use the actual company name
                        or say "your team"
                - Do NOT include subject line or date
                """.formatted(
                request.getJobTitle(),
                request.getJobDescription() != null
                        ? request.getJobDescription()
                        : "Not provided",
                request.getTargetCompanyName() != null
                        ? request.getTargetCompanyName()
                        : "your organization",
                request.getCandidateName(),
                request.getCandidateSummary() != null
                        ? request.getCandidateSummary()
                        : "Experienced Professional",
                skills,
                experience
        );

        return AiTextResponse.builder()
                .content(geminiClient.generateText(systemPrompt,prompt))
                .build();
    }

    public ScreeningScoreResponse scoreCandidate(ScreeningScoreRequest req) throws Exception {

        String requiredSkills = req.getRequiredSkills() != null
                ? String.join(", ", req.getRequiredSkills())
                : "Not Provided";

        String candidateSkills = req.getRequiredSkills() != null
                ? String.join(", ", req.getRequiredSkills())
                : "Not Provided";

        String candidateExperience = req.getCandidateExperience() != null
                ? String.join(", ", req.getCandidateExperience())
                : "Not Provided";

        String prompt = """
                Score this job application based on how well the candidate matches the
                requirements.

                Job Requirements:
                - Title: %s
                - Experience Level Required: %s
                - Required Skills: %s
                - Key Responsibilities: %s

                Candidate Profile:
                - Professional Summary: %s
                - Skills: %s
                - Experience History: %s

                {
                    "score": 85,
                    "skillsMatchScore": 90,
                    "experienceMatchScore": 80,
                    "educationMatchScore": 75,
                    "matchedSkills": ["skill1", "skill2"],
                    "missingSkills": ["skill3"],
                    "strengths": ["strength1", "strength2"],
                    "concerns": ["concern1"],
                    "summary": "2-3 sentence honest assessment of this candidate's fit"
                }

                Score scale: 0-100 where 100 is a perfect match.
                skillsMatchScore: how well candidate skills match required skills (0-100).
                experienceMatchScore: how well candidate experience matches required
                        level (0-100).
                educationMatchScore: how well candidate education/background fits the
                        role (0-100).
                score: overall weighted match considering all factors. Be objective and fair.
                """.formatted(
                        req.getJobTitle() != null ? req.getJobTitle() : "Not Provided",
                        req.getExperienceLevel() != null
                                ? req.getExperienceLevel()
                                : "not provided",
                        requiredSkills,
                        req.getResponsibilities() != null
                                ? req.getResponsibilities()
                                : "not provided",
                        req.getCandidateSummary() != null
                                ? req.getCandidateSummary()
                                : "not provided",
                        candidateSkills,
                        candidateExperience
        );

        return geminiClient.generateJson(systemPrompt, prompt, ScreeningScoreResponse.class);
    }

    public SkillsGapResponse analyzeSkillsGap(SkillsGapRequest req) throws Exception {
        String candidateSkills = req.getCandidateSkills() != null
                ? String.join(", ", req.getCandidateSkills())
                : "Not Provided";

        String requiredSkills = req.getRequiredSkills() != null
                ? String.join(", ", req.getRequiredSkills())
                : "Not Provided";

        String prompt = """
                Analyze the skills gap between a candidate and a job requirement.

                Job Title: %s
                Candidate's Current Skills: %s
                Skills Required for the Job: %s

                {
                    "matchedSkills": ["skills candidate has that are required"],
                    "missingSkills": ["required skills candidate completely lacks"],
                    "partialMatch": ["skills candidate has partially or related version of"],
                    "prioritySkillsToLearn": ["top 3 skills to learn first, in order of
                            importance"],
                    "learningRecommendations": [
                            { "skill": "skill name", "why": "why this skill is important for
                            the role", "howToLearn": "specific learning resource or approach" }
                    ],
                    "overallReadiness": "Ready or Partially Ready or Needs Development",
                    "summary": "2-sentence honest assessment"
                }
                """.formatted(
                        req.getJobTitle(),
                        candidateSkills,
                        requiredSkills
        );

        return geminiClient.generateJson(systemPrompt, prompt, SkillsGapResponse.class);
    }
}