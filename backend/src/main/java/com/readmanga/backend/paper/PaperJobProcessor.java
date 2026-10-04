package com.readmanga.backend.paper;

import com.readmanga.backend.comic.ComicStore;
import com.readmanga.backend.comic.plan.ComicPlan;
import com.readmanga.backend.comic.plan.ComicPlanner;
import com.readmanga.backend.comic.write.ComicWriter;
import com.readmanga.backend.comic.write.WrittenComic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 업로드된 논문을 뒤에서(비동기) 만화로 만든다.
 * ① 논문 분석(GROBID) → 문장 저장 ② 핵심 사실 추출(qwen3) ③ 대본 쓰기(exaone) ④ 만화 저장 → 완료
 * 몇 분씩 걸리는 AI 호출은 트랜잭션 밖에서 하고, 저장만 짧은 트랜잭션으로 묶는다.
 */
@Component
public class PaperJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaperJobProcessor.class);

    private final PaperJobRepository paperJobRepository;
    private final PaperSentenceRepository paperSentenceRepository;
    private final GrobidClient grobidClient;
    private final TeiParser teiParser;
    private final PaperResultStore paperResultStore;
    private final JobProgress jobProgress;
    private final ComicPlanner comicPlanner;
    private final ComicWriter comicWriter;
    private final ComicStore comicStore;

    public PaperJobProcessor(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository,
                             GrobidClient grobidClient, TeiParser teiParser, PaperResultStore paperResultStore,
                             JobProgress jobProgress, ComicPlanner comicPlanner, ComicWriter comicWriter, ComicStore comicStore) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
        this.grobidClient = grobidClient;
        this.teiParser = teiParser;
        this.paperResultStore = paperResultStore;
        this.jobProgress = jobProgress;
        this.comicPlanner = comicPlanner;
        this.comicWriter = comicWriter;
        this.comicStore = comicStore;
    }

    @Async
    public void process(Long jobId, byte[] pdf, String fileName) {
        PaperJob job = paperJobRepository.findById(jobId).orElseThrow();
        log.info("작업 {} 처리 시작: {}", jobId, fileName);
        job.start();
        paperJobRepository.save(job);

        try {
            // ① 논문 분석
            String teiXml = grobidClient.processFulltext(pdf, fileName);
            PaperStructure paper = teiParser.parse(teiXml);
            paperResultStore.save(jobId, paper);
            List<PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(jobId);
            log.info("작업 {} 논문 분석 완료: 문장 {}개", jobId, sentences.size());

            // ② 핵심 사실 추출
            ComicPlan plan = comicPlanner.plan(paper.title(), sentences, (done, total) -> jobProgress.update(jobId, done, total));
            log.info("작업 {} 핵심 사실 추출 완료: 사실 {}개", jobId, plan.pages().stream().mapToInt(page -> page.facts().size()).sum());

            // ③ 대본 쓰기
            jobProgress.moveTo(jobId, JobStage.WRITING, 0);
            Map<Integer, PaperSentence> byNumber = sentences.stream()
                    .collect(Collectors.toMap(PaperSentence::getNumber, Function.identity()));
            WrittenComic written = comicWriter.write(plan, byNumber, (done, total) -> jobProgress.update(jobId, done, total));

            // ④ 저장
            comicStore.save(jobId, written);
            log.info("작업 {} 완료: 만화 '{}', 대본 쓰기 {}초", jobId, written.title(), written.millis() / 1000);
        } catch (RuntimeException e) {
            // 들고 있던 job 객체는 오래돼서(진행률·제목이 반영 안 됨) 쓰지 않고, DB에서 새로 읽어 실패를 기록한다
            jobProgress.fail(jobId, e.getMessage());
            log.warn("작업 {} 실패: {}", jobId, e.getMessage(), e);
        }
    }
}
