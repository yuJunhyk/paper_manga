package com.readmanga.backend.comic;

import org.springframework.context.annotation.Import;
import com.readmanga.backend.TestcontainersConfiguration;
import com.readmanga.backend.comic.write.LineRules;
import com.readmanga.backend.comic.write.WrittenComic;
import com.readmanga.backend.paper.JobStage;
import com.readmanga.backend.paper.JobStatus;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperJobRepository;
import com.readmanga.backend.paper.PaperSentence;
import com.readmanga.backend.paper.PaperSentenceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 대본을 만화 테이블에 저장하고 작업을 완료로 표시하는지 실제 DB로 확인한다. 끝나면 되돌린다(롤백). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
@Transactional
class ComicStoreTest {

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperSentenceRepository paperSentenceRepository;

    @Autowired
    ComicRepository comicRepository;

    @Autowired
    ComicStore comicStore;

    @Autowired
    ComicQuery comicQuery;

    @Autowired
    EntityManager entityManager;

    private static WrittenComic written(String title) {
        WrittenComic.Panel opening = new WrittenComic.Panel("콩이의 방", true, null,
                List.of(new LineRules.Line("해설", "콩이는 고민에 빠졌다.")), List.of(), List.of());
        WrittenComic.Panel fact = new WrittenComic.Panel("연구실 칠판 앞", false, "잠재 확산 모델은 GAN보다 샘플링이 느리다.",
                List.of(new LineRules.Line("콩이", "단점은 없어요?"), new LineRules.Line("냥박사", "조금 느려."),
                        new LineRules.Line("해설", "잠재 확산 모델은 GAN보다 샘플링이 느리다.")), List.of(239), List.of());
        WrittenComic.Page page = new WrittenComic.Page(4, "결", "한계를 배운다", List.of(opening, fact));
        return new WrittenComic(title, "풍경화 숙제", "그림 완성", List.of(page), 1000, List.of("주 서버"));
    }

    @Test
    void 대본을_저장하면_근거가_문장에_연결되고_작업이_완료된다() {
        PaperJob job = new PaperJob("test.pdf");
        job.start();
        job.moveTo(JobStage.WRITING, 13);
        paperJobRepository.save(job);
        paperSentenceRepository.save(new PaperSentence(job, 239, "Limitations", 9, "sampling is slower than that of GANs."));

        comicStore.save(job.getId(), written("만화 1"));
        entityManager.flush();
        entityManager.clear();

        ComicResponse comic = comicQuery.find(job.getId());
        assertThat(comic.title()).isEqualTo("만화 1");
        ComicResponse.PanelResponse factPanel = comic.pages().getFirst().panels().get(1);
        assertThat(factPanel.lines()).extracting(ComicResponse.LineResponse::speaker).containsExactly("콩이", "냥박사", "해설");
        assertThat(factPanel.evidence()).extracting(ComicResponse.EvidenceResponse::number).containsExactly(239);
        assertThat(factPanel.evidence().getFirst().page()).isEqualTo(9);

        PaperJob saved = paperJobRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(saved.getStage()).isEqualTo(JobStage.DONE);
        assertThat(saved.getProgressDone()).isEqualTo(13);
    }

    @Test
    void 다시_저장하면_예전_만화를_지우고_새_만화로_바꾼다() {
        PaperJob job = paperJobRepository.save(new PaperJob("test.pdf"));
        paperSentenceRepository.save(new PaperSentence(job, 239, "Limitations", 9, "sampling is slower than that of GANs."));

        comicStore.save(job.getId(), written("예전 만화"));
        comicStore.save(job.getId(), written("새 만화"));
        entityManager.flush();
        entityManager.clear();

        assertThat(comicQuery.find(job.getId()).title()).isEqualTo("새 만화");
        assertThat(comicRepository.findAll()).filteredOn(comic -> comic.getTitle().equals("예전 만화")).isEmpty();
    }
}
