package com.readmanga.backend.comic;

import com.readmanga.backend.TestcontainersConfiguration;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperJobRepository;
import com.readmanga.backend.paper.PaperSentence;
import com.readmanga.backend.paper.PaperSentenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만화 하나를 조회할 때 DB 질의가 몇 번 나가는지 센다 (N+1 문제 확인용).
 * 실제 만화와 같은 크기(4쪽 × 8컷, 컷마다 대사 2개·근거 1개)로 저장한 뒤 조회한다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
@Transactional
class ComicQueryTest {

    private static final int PAGES = 4;
    private static final int PANELS_PER_PAGE = 8;

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperSentenceRepository paperSentenceRepository;

    @Autowired
    ComicRepository comicRepository;

    @Autowired
    ComicQuery comicQuery;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void 만화_하나를_조회할_때_DB_질의는_쪽_컷_수와_상관없이_몇_번이면_된다() {
        PaperJob job = paperJobRepository.save(new PaperJob("test.pdf"));
        Comic comic = new Comic(job, "데이터 만화 이야기", "콩이가 그래프를 어려워한다", "콩이가 데이터를 이해한다");
        int number = 1;
        for (int pageNumber = 1; pageNumber <= PAGES; pageNumber++) {
            ComicPage page = comic.addPage(pageNumber, "기", "흐름");
            for (int panelNumber = 1; panelNumber <= PANELS_PER_PAGE; panelNumber++, number++) {
                PaperSentence sentence = paperSentenceRepository.save(new PaperSentence(job, number, "Results", pageNumber,
                        "Sentence " + number + " reports a finding."));
                ComicPanel panel = page.addPanel("장면 " + number, false, "사실 " + number);
                panel.addLine(Speaker.NURI, "냥박사님, 이건 뭐예요?");
                panel.addLine(Speaker.NARRATION, "사실 " + number);
                panel.addEvidence(sentence);
            }
        }
        comicRepository.save(comic);
        entityManager.flush();  // 저장한 것을 DB에 보내고
        entityManager.clear();  // 메모리에서 비워서, 조회할 때 정말 DB에서 읽게 한다

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        ComicResponse response = comicQuery.find(job.getId());
        long queries = statistics.getPrepareStatementCount();
        System.out.printf("만화 하나(%d쪽 %d컷) 조회에 나간 DB 질의 수: %d%n", PAGES, PAGES * PANELS_PER_PAGE, queries);

        assertThat(response.pages()).hasSize(PAGES);
        assertThat(response.pages()).allSatisfy(page -> assertThat(page.panels()).hasSize(PANELS_PER_PAGE));
        assertThat(queries).as("만화 하나(%d쪽 %d컷) 조회에 나간 DB 질의 수", PAGES, PAGES * PANELS_PER_PAGE)
                .isLessThanOrEqualTo(10);
    }

    @Test
    void 내보내기는_작업번호_논문제목_파일_이름과_근거_원문이_든_Markdown이다() {
        PaperJob job = paperJobRepository.save(new PaperJob("test.pdf"));
        job.recordTitle("Data Comics for Education");
        PaperSentence sentence = paperSentenceRepository.save(new PaperSentence(job, 12, "Results", 3, "Sentence 12 reports a finding."));
        Comic comic = new Comic(job, "데이터 만화 이야기", "콩이가 그래프를 어려워한다", "콩이가 데이터를 이해한다");
        ComicPanel panel = comic.addPage(1, "기", "흐름").addPanel("장면", false, "사실 12");
        panel.addLine(Speaker.DOCTOR, "그래프는 이렇게 읽는 거야.");
        panel.addEvidence(sentence);
        comicRepository.save(comic);
        entityManager.flush();
        entityManager.clear();

        ComicScript script = comicQuery.script(job.getId());

        assertThat(script.fileName()).isEqualTo(job.getId() + "_Data-Comics-for-Education.md");
        assertThat(script.markdown()).contains("- **냥박사**: 그래프는 이렇게 읽는 거야.")
                .contains("<summary>근거 원문 (3쪽 12번)</summary>")
                .contains("> **3쪽 12번** · Results<br>Sentence 12 reports a finding.");
    }
}
