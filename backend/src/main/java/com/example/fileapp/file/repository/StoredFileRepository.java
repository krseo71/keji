package com.example.fileapp.file.repository;

import com.example.fileapp.file.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findAllByGenerationJobId(Long jobId);
}
