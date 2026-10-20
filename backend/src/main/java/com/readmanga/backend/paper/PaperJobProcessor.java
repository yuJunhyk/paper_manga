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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 업로드된 논문을 뒤에서(비동기) 만화로 만든다.
 * ① 논문 분석(GROBID) → 문장 저장 ② 핵심 사실 추출(gemma4) ③ 대본 쓰기(gemma4) ④ 만화 저장 → 완료
 * 취소되면 진행을 기록하는 다음 순간(AI 요청 하나가 끝난 뒤) 멈춘다.
 * 실패·취소된 작업은 다시 시도할 수 있다: ②가 끝나 설계도가 저장돼 있으면 ③부터, 아니면 ②부터.
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
    private final JsonMapper jsonMapper;

    public PaperJobProcessor(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository,
                             GrobidClient grobidClient, TeiParser teiParser, PaperResultStore paperResultStore,
                             JobProgress jobProgress, ComicPlanner comicPlanner, ComicWriter comicWriter, ComicStore comicStore,
                             JsonMapper jsonMapper) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
        this.grobidClient = grobidClient;
        this.teiParser = teiParser;
        this.paperResultStore = paperResultStore;
        this.jobProgress = jobProgress;
        this.comicPlanner = comicPlanner;
        this.comicWriter = comicWriter;
        this.comicStore = comicStore;
        this.jsonMapper = jsonMapper;
    }

    @Async("jobExecutor")  // 논문 처리 전용 실행기(AsyncConfig): 한 번에 하나, 나머지는 대기 줄
    public void process(Long jobId, byte[] pdf, String fileName) {
        log.info("작업 {} 처리 시작: {}", jobId, fileName);
        runSafely(jobId, () -> {
            jobProgress.start(jobId);

            // ① 논문 분석
            String teiXml = grobidClient.processFulltext(pdf, fileName);
            PaperStructure paper = teiParser.parse(teiXml);
            paperResultStore.save(jobId, paper);
            List<PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(jobId);
            log.info("작업 {} 논문 분석 완료: 문장 {}개", jobId, sentences.size());

            write(jobId, plan(jobId, paper.title(), sentences), sentences);
        });
    }

    /**
     * 다시 시도: 실패·취소된 작업을 DB에 저장된 결과로 멈춘 곳부터 다시 돌린다.
     * 어느 단계부터 할지는 다시 시도를 접수할 때(PaperService.retry) 정해 둔 단계를 따른다.
     * PDF는 저장하지 않으므로 논문 분석(GROBID)은 다시 하지 않는다.
     */
    @Async("jobExecutor")  // 논문 처리 전용 실행기(AsyncConfig): 한 번에 하나, 나머지는 대기 줄
    public void retry(Long jobId) {
        runSafely(jobId, () -> {
            PaperJob job = paperJobRepository.findById(jobId).orElseThrow();
            List<PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(jobId);
            Optional<ComicPlan> saved = job.getStage() == JobStage.WRITING && job.hasPlan() ? savedPlan(jobId, job) : Optional.empty();
            if (saved.isPresent()) {
                log.info("작업 {} 다시 시도: 저장된 설계도로 대본 쓰기부터", jobId);
                jobProgress.resume(jobId, JobStage.WRITING);
                write(jobId, saved.get(), sentences);
            } else {
                log.info("작업 {} 다시 시도: 저장된 문장으로 핵심 사실 추출부터", jobId);
                jobProgress.resume(jobId, JobStage.PLANNING);
                write(jobId, plan(jobId, job.getTitle(), sentences), sentences);
            }
        });
    }

    /** 저장된 설계도. 설계도의 모양(ComicPlan)이 바뀌어 예전 JSON을 읽지 못하면 없는 것으로 보고 다시 만든다. */
    private Optional<ComicPlan> savedPlan(Long jobId, PaperJob job) {
        try {
            return Optional.of(jsonMapper.readValue(job.getPlanJson(), ComicPlan.class));
        } catch (JacksonException e) {
            log.warn("작업 {} 저장된 설계도를 읽지 못해 핵심 사실 추출부터 다시 합니다: {}", jobId, e.getOriginalMessage());
            return Optional.empty();
        }
    }

    /** ② 핵심 사실 추출. 끝나면 설계도를 저장하고 대본 쓰기 단계로 넘어간다. */
    private ComicPlan plan(Long jobId, String title, List<PaperSentence> sentences) {
        ComicPlan plan = comicPlanner.plan(title, sentences, (done, total) -> jobProgress.update(jobId, done, total));
        log.info("작업 {} 핵심 사실 추출 완료: 사실 {}개", jobId, plan.pages().stream().mapToInt(page -> page.facts().size()).sum());
        jobProgress.finishPlanning(jobId, jsonMapper.writeValueAsString(plan));
        return plan;
    }

    /** ③ 대본 쓰기 → ④ 저장 */
    private void write(Long jobId, ComicPlan plan, List<PaperSentence> sentences) {
        Map<Integer, PaperSentence> byNumber = sentences.stream()
                .collect(Collectors.toMap(PaperSentence::getNumber, Function.identity()));
        WrittenComic written = comicWriter.write(plan, byNumber, (done, total) -> jobProgress.update(jobId, done, total));

        comicStore.save(jobId, written);
        log.info("작업 {} 완료: 만화 '{}', 대본 쓰기 {}초", jobId, written.title(), written.millis() / 1000);
    }

    /** 처리 중 오류를 정리한다: 취소면 그대로 두고, 그 밖의 오류는 실패로 기록한다. */
    private void runSafely(Long jobId, Runnable work) {
        try {
            work.run();
        } catch (JobCancelledException e) {
            log.info("작업 {} 취소되어 처리를 멈췄습니다", jobId);
        } catch (RuntimeException e) {
            // 취소와 진행 기록이 동시에 일어나면 진행 기록 쪽이 낙관적 락 오류로 끝난다. 그때는 실패가 아니라 취소다
            if (jobProgress.isCancelled(jobId)) {
                log.info("작업 {} 취소되어 처리를 멈췄습니다 ({})", jobId, e.getClass().getSimpleName());
                return;
            }
            jobProgress.fail(jobId, e.getMessage());
            log.warn("작업 {} 실패: {}", jobId, e.getMessage(), e);
        }
    }
}
