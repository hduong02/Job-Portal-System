package com.example.job_portal_job_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.dto.response.JobTagResponse;
import com.example.job_portal_job_service.mapper.JobTagMapper;
import com.example.job_portal_job_service.model.JobTag;
import com.example.job_portal_job_service.payload.JobTagRequest;
import com.example.job_portal_job_service.repository.JobTagRepository;
import com.example.job_portal_job_service.service.JobTagService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobTagServiceImpl implements JobTagService {


    private final JobTagRepository jobTagRepository;

    @Override
    public JobTagResponse createTag(JobTagRequest req) throws Exception {

        if (jobTagRepository.existsByName(req.getName())) {
            throw new Exception("tag name already exist ");
        }
        String slug = generateUniqueSlug(req.getName());

        JobTag jobTag = JobTag.builder()
                .name(req.getName())
                .slug(slug)
                .build();
        JobTag saved = jobTagRepository.save(jobTag);
        return JobTagMapper.toTagResponse(saved);
    }

    @Override
    public List<JobTagResponse> getAllTags() {
        return jobTagRepository.findAll()
                .stream().map(JobTagMapper::toTagResponse)
                .collect(Collectors.toList());
    }

    @Override
    public JobTagResponse getById(Long id) throws Exception {
        JobTag jobTag = getTagEntityById(id);

        return JobTagMapper.toTagResponse(jobTag);
    }

    @Override
    public JobTagResponse updateTag(Long id, JobTagRequest req) throws Exception {
        JobTag jobTag = getTagEntityById(id);

        if (!jobTag.getName().equals(req.getName())
                && jobTagRepository.existsByName(req.getName())) {
            throw new Exception("tag name already exist");
        }
        jobTag.setName(req.getName());
        return JobTagMapper.toTagResponse(jobTagRepository.save(jobTag));
    }

    @Override
    public void deleteTag(Long id) throws Exception {
        JobTag jobTag = getTagEntityById(id);
        jobTagRepository.delete(jobTag);
    }

    @Override
    public JobTag getTagEntityById(Long id) throws Exception {
        return jobTagRepository.findById(id).orElseThrow(
                () -> new Exception("job tag not found")
        );
    }

    @Override
    public Set<JobTag> getTagsByIds(Set<Long> ids) throws Exception {
        List<JobTag> tags = jobTagRepository.findAllById(ids);
        return new HashSet<>(tags);
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]","")
                .trim().replaceAll("[\\s-]+","-");
        if (!jobTagRepository.existsBySlug(base)) {
            return base;
        }

        int counter = 1;
        while (jobTagRepository.existsBySlug(base + "-" + counter)) {
            counter ++;
        }
        return base + "-" + counter;
    }
}