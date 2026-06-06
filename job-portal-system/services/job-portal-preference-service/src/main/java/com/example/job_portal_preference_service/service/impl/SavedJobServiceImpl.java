package com.example.job_portal_preference_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.dto.response.SavedJobResponse;
import com.example.job_portal_preference_service.mapper.PreferenceMapper;
import com.example.job_portal_preference_service.model.SavedJob;
import com.example.job_portal_preference_service.payload.SaveJobRequest;
import com.example.job_portal_preference_service.repository.SavedJobRepository;
import com.example.job_portal_preference_service.service.SavedJobService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedJobServiceImpl implements SavedJobService{
    private final SavedJobRepository savedJobRepository;

    @Override
    public SavedJobResponse saveJob(Long candidateId, SaveJobRequest req)
            throws Exception {
        if (isSaved(candidateId, req.getJobId())) {
            throw new Exception("Job is already saved");
        }

        SavedJob savedJob = SavedJob.builder()
                .candidateId(candidateId)
                .jobId(req.getJobId())
                .build();
        savedJob = savedJobRepository.save(savedJob);
        return PreferenceMapper.toSavedJobResponse(savedJob);
    }

    @Override
    public void unsaveJob(Long candidateId, Long savedJobId) throws Exception {

        SavedJob savedJob = savedJobRepository.findById(savedJobId).orElseThrow(
                () -> new Exception("Job not found")
        );
        if (!savedJob.getCandidateId().equals(candidateId)) {
            throw new Exception("Job is not saved");
        }
        savedJobRepository.delete(savedJob);
    }

    @Override
    public List<SavedJobResponse> getSavedJob(Long candidateId) {
        return savedJobRepository.findByCandidateId(candidateId)
                .stream().map(PreferenceMapper::toSavedJobResponse).toList();
    }

    @Override
    public boolean isSaved(Long candidateId, Long jobId) {
        return savedJobRepository.existsByCandidateIdAndJobId(candidateId, jobId);
    }
}
