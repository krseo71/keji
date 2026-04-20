package com.example.fileapp.generation.repository;

import com.example.fileapp.generation.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {
}
