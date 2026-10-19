package com.readmanga.backend.comic;

import org.springframework.context.annotation.Import;
import com.readmanga.backend.TestcontainersConfiguration;
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

/**
 * 만화 → 쪽 → 컷 → 대사, 그리고 컷 → 근거 문장 관계가 DB에 제대로 저장되고 다시 읽히는지 확인한다.
 * 테스트가 끝나면 넣은 데이터는 되돌린다(@Transactional 롤백).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
@Transactional
class ComicRepositoryTest {

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperSentenceRepository paperSentenceRepository;

    @Autowired
    ComicRepository comicRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 만화를_저장하면_쪽_컷_대사_근거가_함께_저장된다() {
        PaperJob job = paperJobRepository.save(new PaperJob("test.pdf"));
        PaperSentence slower = paperSentenceRepository.save(new PaperSentence(job, 239, "Limitations", 9,
                "their sequential sampling process is still slower than that of GANs."));

        Comic comic = new Comic(job, "잠재 공간에서의 고해상도 이미지 생성", "콩이의 풍경화 숙제가 끝나지 않는다", "콩이가 그림을 완성한다");
        ComicPage page = comic.addPage(4, "결", "한계를 알고 숙제를 마무리한다");
        ComicPanel staging = page.addPanel("콩이가 노트를 펼친다", true, null);
        staging.addLine(Speaker.NURI, "냥박사님, 단점은 없어요?");
        ComicPanel factPanel = page.addPanel("냥박사님이 시계를 가리킨다", false, "잠재 확산 모델은 GAN보다 샘플링이 느리다.");
        factPanel.addLine(Speaker.DOCTOR, "한 장씩 차근차근 그리느라 조금 느려.");
        factPanel.addLine(Speaker.NARRATION, "잠재 확산 모델은 GAN보다 샘플링이 느리다.");
        factPanel.addEvidence(slower);
        comicRepository.save(comic);

        entityManager.flush();  // 지금까지의 변경을 DB에 보내고
        entityManager.clear();  // 메모리에 남은 객체를 비워서, 아래에서 정말 DB에서 다시 읽게 한다

        Comic loaded = comicRepository.findByJob_Id(job.getId()).orElseThrow();
        ComicPage loadedPage = loaded.getPages().getFirst();
        List<ComicPanel> panels = loadedPage.getPanels();

        assertThat(loaded.getTitle()).isEqualTo("잠재 공간에서의 고해상도 이미지 생성");
        assertThat(loadedPage.getRole()).isEqualTo("결");
        assertThat(panels).extracting(ComicPanel::getPanelOrder).containsExactly(1, 2);
        assertThat(panels.get(0).isStaging()).isTrue();
        assertThat(panels.get(0).getEvidence()).isEmpty();
        assertThat(panels.get(1).getLines()).extracting(PanelLine::getSpeaker)
                .containsExactly(Speaker.DOCTOR, Speaker.NARRATION);
        assertThat(panels.get(1).getEvidence()).extracting(PaperSentence::getNumber).containsExactly(239);
        assertThat(panels.get(1).getEvidence().getFirst().getPage()).isEqualTo(9);
    }
}
