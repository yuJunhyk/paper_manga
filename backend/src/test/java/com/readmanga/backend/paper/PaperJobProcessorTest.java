package com.readmanga.backend.paper;

import com.readmanga.backend.comic.ComicStore;
import com.readmanga.backend.comic.ProgressListener;
import com.readmanga.backend.comic.plan.ComicPlan;
import com.readmanga.backend.comic.plan.ComicPlanner;
import com.readmanga.backend.comic.write.ComicWriter;
import com.readmanga.backend.comic.write.WrittenComic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 다시 시도할 때 어디부터 하는지 확인한다: 설계도가 저장돼 있으면 대본 쓰기부터, 아니면 핵심 사실 추출부터.
 * 실제 AI·DB 없이 처리기만 만들어 부른다(비동기 실행기를 거치지 않으므로 바로 끝난다).
 */
class PaperJobProcessorTest {

    private static final long JOB_ID = 7L;

    /** 실제 설계도와 같은 모양: 쪽 → 조각 → 사실(근거 문장 번호), 용어 번역 */
    private static final ComicPlan PLAN = new ComicPlan("주의 집중! 트랜스포머",
            List.of(new ComicPlan.PagePlan(4, "결", "결과",
                    List.of(new ComicPlan.ChunkPlan(150, 160, List.of("Results"), "번역 성능", "BLEU 28.4", List.of(), List.of())),
                    List.of(new ComicPlan.FactPlan("영어→독일어 번역에서 BLEU 28.4를 달성했다.", List.of(154), "번역 성능")))),
            new ComicPlan.Stats(1200, 95000, List.of("주 서버"), 1, List.of()),
            List.of(new ComicPlan.Term("attention", "주의 집중", List.of("어텐션"))));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final PaperJobRepository paperJobRepository = mock(PaperJobRepository.class);
    private final PaperSentenceRepository paperSentenceRepository = mock(PaperSentenceRepository.class);
    private final JobProgress jobProgress = mock(JobProgress.class);
    private final ComicPlanner comicPlanner = mock(ComicPlanner.class);
    private final ComicWriter comicWriter = mock(ComicWriter.class);
    private final ComicStore comicStore = mock(ComicStore.class);
    private final PaperJobProcessor processor = new PaperJobProcessor(paperJobRepository, paperSentenceRepository,
            mock(GrobidClient.class), mock(TeiParser.class), mock(PaperResultStore.class),
            jobProgress, comicPlanner, comicWriter, comicStore, jsonMapper);

    @BeforeEach
    void setUp() {
        when(paperSentenceRepository.findByJob_IdOrderByNumberAsc(JOB_ID)).thenReturn(List.of());
        when(comicWriter.write(any(), anyMap(), any(ProgressListener.class)))
                .thenReturn(new WrittenComic("주의 집중! 트랜스포머", "", "", List.of(), 110_000, List.of("주 서버")));
    }

    /** 대본 쓰기 중에 실패한 작업. 다시 시도를 접수하면 단계가 stage로 바뀐다(PaperService.retry) */
    private void failedWhileWriting(String planJson, JobStage stage) {
        PaperJob job = new PaperJob("attention.pdf");
        job.start();
        job.finishPlanning(planJson);
        job.fail("주 서버와 예비 서버가 모두 응답하지 않습니다");
        job.moveTo(stage, 0);
        when(paperJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    }

    @Test
    void 대본_쓰기에서_멈춘_작업은_저장된_설계도로_대본_쓰기부터_다시_한다() {
        failedWhileWriting(jsonMapper.writeValueAsString(PLAN), JobStage.WRITING);

        processor.retry(JOB_ID);

        verify(jobProgress).resume(JOB_ID, JobStage.WRITING);
        verify(comicPlanner, never()).plan(anyString(), anyList(), any(ProgressListener.class));
        verify(comicWriter).write(eq(PLAN), anyMap(), any(ProgressListener.class));
        verify(comicStore).save(eq(JOB_ID), any());
    }

    @Test
    void 저장된_설계도를_읽지_못하면_핵심_사실_추출부터_다시_한다() {
        failedWhileWriting("{\"comicTitle\": ", JobStage.WRITING);  // 설계도 모양이 바뀌었거나 깨진 JSON
        when(comicPlanner.plan(any(), anyList(), any(ProgressListener.class))).thenReturn(PLAN);

        processor.retry(JOB_ID);

        verify(jobProgress).resume(JOB_ID, JobStage.PLANNING);
        verify(comicPlanner).plan(any(), anyList(), any(ProgressListener.class));
        verify(comicStore).save(eq(JOB_ID), any());
    }

    @Test
    void 핵심_사실_추출부터_다시_하면_새_설계도를_저장하고_대본을_쓴다() {
        failedWhileWriting(jsonMapper.writeValueAsString(PLAN), JobStage.PLANNING);
        when(comicPlanner.plan(any(), anyList(), any(ProgressListener.class))).thenReturn(PLAN);

        processor.retry(JOB_ID);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(jobProgress).resume(JOB_ID, JobStage.PLANNING);
        verify(jobProgress).finishPlanning(eq(JOB_ID), saved.capture());
        assertThat(jsonMapper.readValue(saved.getValue(), ComicPlan.class)).as("저장한 JSON을 다시 읽으면 같은 설계도").isEqualTo(PLAN);
        verify(comicWriter).write(eq(PLAN), anyMap(), any(ProgressListener.class));
    }
}
