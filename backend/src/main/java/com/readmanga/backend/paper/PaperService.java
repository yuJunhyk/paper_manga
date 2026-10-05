package com.readmanga.backend.paper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PaperService {

    private final PaperJobRepository paperJobRepository;
    private final PaperSentenceRepository paperSentenceRepository;
    private final PaperJobProcessor paperJobProcessor;

    public PaperService(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository,
                        PaperJobProcessor paperJobProcessor) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
        this.paperJobProcessor = paperJobProcessor;
    }

    public JobResponse upload(MultipartFile file) {
        byte[] pdf = readBytes(file);
        PaperJob job = paperJobRepository.save(new PaperJob(file.getOriginalFilename()));

        paperJobProcessor.process(job.getId(), pdf, file.getOriginalFilename());
        return JobResponse.from(job);
    }

    /**
     * 작업 취소. 대기·처리 중인 작업만 취소할 수 있다(끝난 작업은 409).
     * 뒤에서 돌던 처리는 지금 하던 AI 요청이 끝나는 대로 멈춘다(협력적 중단).
     */
    @Transactional
    public JobResponse cancel(Long id) {
        PaperJob job = findJob(id);
        int changed = paperJobRepository.cancelIfActive(id, JobStatus.CANCELLED,
                List.of(JobStatus.PENDING, JobStatus.PROCESSING), LocalDateTime.now());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 끝난 작업이라 취소할 수 없습니다. 상태: " + job.getStatus());
        }
        return JobResponse.from(findJob(id));
    }

    /**
     * 다시 시도. 실패·취소된 작업을 저장된 문장으로 핵심 사실 추출부터 다시 돌린다.
     * 논문 분석 전에 실패해 문장이 없으면 PDF를 다시 올려야 한다(409).
     * 트랜잭션으로 묶지 않는다: 상태 변경을 먼저 커밋해야 비동기 처리가 바뀐 상태를 읽는다.
     */
    public JobResponse retry(Long id) {
        PaperJob job = findJob(id);
        if (!paperSentenceRepository.existsByJob_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "논문 분석 전에 멈춘 작업이라 저장된 문장이 없습니다. PDF를 다시 올려 주세요.");
        }
        int changed = paperJobRepository.resetForRetry(id, JobStatus.PENDING, JobStage.PLANNING,
                List.of(JobStatus.FAILED, JobStatus.CANCELLED), LocalDateTime.now());
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "실패했거나 취소된 작업만 다시 시도할 수 있습니다. 상태: " + job.getStatus());
        }
        paperJobProcessor.retry(id);
        return JobResponse.from(findJob(id));
    }

    public JobResponse getJob(Long id) {
        return JobResponse.from(findJob(id));
    }

    /** DB의 문장들을 번호 순서대로 읽어 섹션별로 다시 묶는다. */
    public PaperStructure getResult(Long id) {
        PaperJob job = findJob(id);
        List<PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(id);
        if (sentences.isEmpty()) {
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.PROCESSING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 논문을 분석하는 중입니다.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "저장된 문장이 없습니다. 문장 테이블을 만들기 전에 처리된 작업이거나 분석에 실패한 작업이면 PDF를 다시 올려 주세요.");
        }

        List<PaperStructure.Section> sections = new ArrayList<>();
        String currentTitle = null;
        List<PaperStructure.Sentence> current = null;
        for (PaperSentence sentence : sentences) {
            if (current == null || !Objects.equals(sentence.getSectionTitle(), currentTitle)) {
                currentTitle = sentence.getSectionTitle();
                current = new ArrayList<>();
                sections.add(new PaperStructure.Section(currentTitle, current));
            }
            current.add(new PaperStructure.Sentence(sentence.getNumber(), sentence.getText(), sentence.getPage()));
        }
        return new PaperStructure(job.getTitle(), sections);
    }

    private PaperJob findJob(Long id) {
        return paperJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: " + id));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드한 파일을 읽지 못했습니다", e);
        }
    }
}
