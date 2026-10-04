package com.readmanga.backend.paper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
