package com.readmanga.backend.paper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * GROBID 분석 결과를 DB에 저장한다.
 * 문장 저장과 다음 단계 표시를 한 트랜잭션으로 묶어서, 문장이 일부만 저장된 채 다음 단계로 넘어가는 일을 막는다.
 * (오래 걸리는 GROBID 호출은 트랜잭션 밖에서 끝낸 뒤 이 메서드를 부른다.)
 */
@Service
public class PaperResultStore {

    private final PaperJobRepository paperJobRepository;
    private final PaperSentenceRepository paperSentenceRepository;

    public PaperResultStore(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
    }

    @Transactional
    public void save(Long jobId, PaperStructure paper) {
        PaperJob job = paperJobRepository.findById(jobId).orElseThrow();

        List<PaperSentence> sentences = new ArrayList<>();
        for (PaperStructure.Section section : paper.sections()) {
            for (PaperStructure.Sentence sentence : section.sentences()) {
                sentences.add(new PaperSentence(job, sentence.number(), section.title(), sentence.page(), sentence.text()));
            }
        }
        paperSentenceRepository.saveAll(sentences);

        job.recordTitle(paper.title());  // 트랜잭션 안에서 바꾼 값은 끝날 때 자동으로 DB에 반영된다 (변경 감지)
        job.moveTo(JobStage.PLANNING, 0);   // 문장 저장이 끝나면 다음은 핵심 사실 추출
    }
}
