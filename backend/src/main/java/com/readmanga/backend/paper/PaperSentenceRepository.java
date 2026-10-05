package com.readmanga.backend.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperSentenceRepository extends JpaRepository<PaperSentence, Long> {

    List<PaperSentence> findByJob_IdOrderByNumberAsc(Long jobId);

    boolean existsByJob_Id(Long jobId);
}
