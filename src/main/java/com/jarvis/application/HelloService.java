package com.jarvis.application;

import com.jarvis.domain.model.Hello;
import com.jarvis.domain.port.out.HelloRepository;
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
public class HelloService {

    private final HelloRepository helloRepository;

    @Transactional(readOnly = true)
    public List<Hello> findAll() {
        log.info("Fetching all hellos");
        return helloRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Hello> findById(Long id) {
        log.info("Fetching hello by id: {}", id);
        return helloRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Hello> findByTitle(String title) {
        log.info("Fetching hellos by title: {}", title);
        return helloRepository.findByTitleContainingIgnoreCase(title);
    }

    public Hello create(Hello hello) {
        log.info("Creating hello: {}", hello.getTitle());
        if (hello.getMessage() == null || hello.getMessage().isBlank()) {
            hello.setMessage("Hello, World!");
        }
        return helloRepository.save(hello);
    }

    public Optional<Hello> update(Long id, Hello updated) {
        log.info("Updating hello id: {}", id);
        return helloRepository.findById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setDescription(updated.getDescription());
            existing.setMessage(updated.getMessage());
            return helloRepository.save(existing);
        });
    }

    public boolean delete(Long id) {
        log.info("Deleting hello id: {}", id);
        if (helloRepository.existsById(id)) {
            helloRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
