package com.jarvis.application;

import com.jarvis.domain.model.Test;
import com.jarvis.domain.port.out.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TestService {

    private final TestRepository testRepository;

    @Transactional(readOnly = true)
    public List<Test> findAll() {
        log.info("Fetching all tests");
        return testRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Test> findById(Long id) {
        log.info("Fetching test by id: {}", id);
        return testRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Test> findByTitle(String title) {
        log.info("Fetching tests by title: {}", title);
        return testRepository.findByTitleContainingIgnoreCase(title);
    }

    @Transactional(readOnly = true)
    public List<Test> findByStatus(String status) {
        log.info("Fetching tests by status: {}", status);
        return testRepository.findByStatus(status);
    }

    public Test create(Test test) {
        log.info("Creating test: {}", test.getTitle());
        if (test.getStatus() == null || test.getStatus().isBlank()) {
            test.setStatus("PENDING");
        }
        return testRepository.save(test);
    }

    public Optional<Test> update(Long id, Test updated) {
        log.info("Updating test id: {}", id);
        return testRepository.findById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setDescription(updated.getDescription());
            existing.setStatus(updated.getStatus());
            existing.setScore(updated.getScore());
            return testRepository.save(existing);
        });
    }

    public boolean delete(Long id) {
        log.info("Deleting test id: {}", id);
        if (testRepository.existsById(id)) {
            testRepository.deleteById(id);
            return true;
        }
        return false;
    }
}