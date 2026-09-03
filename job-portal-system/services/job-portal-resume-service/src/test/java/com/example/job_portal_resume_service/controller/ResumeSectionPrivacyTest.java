package com.example.job_portal_resume_service.controller;

import com.example.domain.ResumeVisibility;
import com.example.job_portal_resume_service.model.Education;
import com.example.job_portal_resume_service.model.Language;
import com.example.job_portal_resume_service.model.Project;
import com.example.job_portal_resume_service.model.ResumeSkill;
import com.example.job_portal_resume_service.model.WorkExperience;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.repository.*;
import com.example.job_portal_resume_service.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeSectionPrivacyTest {
    private ResumeRepository resumes;
    private EducationRepository educations;
    private LanguageRepository languages;
    private ProjectRepository projects;
    private ResumeSkillRepository skills;
    private WorkExperienceRepository experiences;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        resumes = mock(ResumeRepository.class);
        educations = mock(EducationRepository.class);
        languages = mock(LanguageRepository.class);
        projects = mock(ProjectRepository.class);
        skills = mock(ResumeSkillRepository.class);
        experiences = mock(WorkExperienceRepository.class);
        var resumeService = new ResumeServiceImpl(resumes, experiences, educations, skills, projects, languages);
        mvc = MockMvcBuilders.standaloneSetup(
                new ResumeController(resumeService),
                new EducationController(new EducationServiceImpl(educations, resumeService)),
                new LanguageController(new LanguageServiceImpl(resumeService, languages)),
                new ProjectController(new ProjectServiceImpl(resumeService, projects)),
                new ResumeSkillController(new ResumeSkillServiceImpl(skills, resumeService)),
                new WorkExperienceController(new WorkExperienceServiceImpl(resumeService, experiences)))
                .build();
        when(resumes.findById(10L)).thenReturn(Optional.of(Resume.builder()
                .id(10L).candidateId(1L).visibility(ResumeVisibility.PRIVATE).build()));
        when(educations.findByResume_IdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of(Education.builder().id(20L).institutionName("Private university").build()));
        clearInvocations(educations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void anotherCandidateCannotReadPrivateSections(String section) throws Exception {
        mvc.perform(get("/api/resumes/10" + section).header("X-User-Id", "2"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verifyNoInteractions(educations, languages, projects, skills, experiences);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void ownerCanReadPrivateSections(String section) throws Exception {
        mvc.perform(get("/api/resumes/10" + section).header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void anotherCandidateCannotEditSummaryOrDeleteResume() throws Exception {
        mvc.perform(patch("/api/resumes/10/summary")
                        .header("X-User-Id", "2").param("summary", "changed"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/resumes/10")
                        .header("X-User-Id", "2"))
                .andExpect(status().isNotFound());
        verify(resumes, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"educations", "languages", "projects", "skills", "work-experiences"})
    void anotherCandidateCannotDeleteResumeSections(String section) throws Exception {
        Resume resume = resumes.findById(10L).orElseThrow();
        when(educations.findById(20L)).thenReturn(Optional.of(Education.builder().id(20L).resume(resume).build()));
        when(languages.findById(20L)).thenReturn(Optional.of(Language.builder().id(20L).resume(resume).build()));
        when(projects.findById(20L)).thenReturn(Optional.of(Project.builder().id(20L).resume(resume).build()));
        when(skills.findById(20L)).thenReturn(Optional.of(ResumeSkill.builder().id(20L).resume(resume).build()));
        when(experiences.findById(20L)).thenReturn(Optional.of(WorkExperience.builder().id(20L).resume(resume).build()));
        mvc.perform(delete("/api/resumes/10/" + section + "/20")
                        .header("X-User-Id", "2"))
                .andExpect(status().isNotFound());
        verify(educations, never()).delete(any(Education.class));
        verify(languages, never()).delete(any(Language.class));
        verify(projects, never()).delete(any(Project.class));
        verify(skills, never()).delete(any(ResumeSkill.class));
        verify(experiences, never()).delete(any(WorkExperience.class));
    }

    @Test
    void ownerReceivesEducationData() throws Exception {
        mvc.perform(get("/api/resumes/10/educations").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].institutionName").value("Private university"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void callerIdentityIsRequired(String section) throws Exception {
        mvc.perform(get("/api/resumes/10" + section))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resumes, educations, languages, projects, skills, experiences);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void deletedResumeCannotBeReadEvenByOwner(String section) throws Exception {
        when(resumes.findById(10L)).thenReturn(Optional.of(Resume.builder()
                .id(10L).candidateId(1L).isActive(false).visibility(ResumeVisibility.PRIVATE).build()));
        mvc.perform(get("/api/resumes/10" + section).header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verifyNoInteractions(educations, languages, projects, skills, experiences);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void missingResumeCannotBeRead(String section) throws Exception {
        when(resumes.findById(10L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/resumes/10" + section).header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verifyNoInteractions(educations, languages, projects, skills, experiences);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/educations", "/languages", "/projects", "/skills", "/work-experiences"})
    void sectionsFollowFullResumeOwnerPolicyForPublicResumes(String section) throws Exception {
        when(resumes.findById(10L)).thenReturn(Optional.of(Resume.builder()
                .id(10L).candidateId(1L).visibility(ResumeVisibility.PUBLIC).build()));
        mvc.perform(get("/api/resumes/10" + section).header("X-User-Id", "2"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(educations, languages, projects, skills, experiences);
    }
}
