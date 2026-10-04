package com.readmanga.backend.comic;

import com.readmanga.backend.comic.write.LineRules;
import com.readmanga.backend.comic.write.WrittenComic;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperJobRepository;
import com.readmanga.backend.paper.PaperSentence;
import com.readmanga.backend.paper.PaperSentenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI가 쓴 대본을 만화 테이블에 저장하고 작업을 완료로 표시한다.
 * 쪽·컷·대사·근거 저장과 완료 표시를 한 트랜잭션으로 묶는다 (만화가 반쯤 저장된 채 '완료'가 되지 않게).
 */
@Service
public class ComicStore {

    private final PaperJobRepository paperJobRepository;
    private final PaperSentenceRepository paperSentenceRepository;
    private final ComicRepository comicRepository;

    public ComicStore(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository,
                      ComicRepository comicRepository) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
        this.comicRepository = comicRepository;
    }

    @Transactional
    public Long save(Long jobId, WrittenComic written) {
        PaperJob job = paperJobRepository.findById(jobId).orElseThrow();
        comicRepository.findByJob_Id(jobId).ifPresent(old -> {
            comicRepository.delete(old);   // 다시 만들면 예전 만화를 지우고 새로 저장한다 (작업 하나에 만화 하나)
            comicRepository.flush();
        });
        Map<Integer, PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(jobId).stream()
                .collect(Collectors.toMap(PaperSentence::getNumber, Function.identity()));

        Comic comic = new Comic(job, written.title(), written.setting(), written.ending());
        for (WrittenComic.Page writtenPage : written.pages()) {
            ComicPage page = comic.addPage(writtenPage.pageNumber(), writtenPage.role(), writtenPage.beat());
            for (WrittenComic.Panel writtenPanel : writtenPage.panels()) {
                ComicPanel panel = page.addPanel(writtenPanel.scene(), writtenPanel.staging(), writtenPanel.fact());
                for (LineRules.Line line : writtenPanel.lines()) {
                    panel.addLine(Speaker.fromLabel(line.speaker()), line.text());
                }
                for (int number : writtenPanel.evidence()) {
                    PaperSentence sentence = sentences.get(number);
                    if (sentence != null) {
                        panel.addEvidence(sentence);
                    }
                }
            }
        }
        comicRepository.save(comic);
        job.complete();
        return comic.getId();
    }
}
