package com.readmanga.backend.comic.plan;

import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperJobRepository;
import com.readmanga.backend.paper.PaperSentence;
import com.readmanga.backend.paper.PaperSentenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 만화 설계도를 바로 만들어 보는 개발용 API. AI를 여러 번 부르므로 1~2분 걸린다.
 * (업로드하면 비동기 작업이 설계도부터 만화 저장까지 한 번에 하므로, 이 API는 설계도만 따로 볼 때 쓴다)
 */
@RestController
public class ComicPlanController {

    private final PaperJobRepository paperJobRepository;
    private final PaperSentenceRepository paperSentenceRepository;
    private final ComicPlanner comicPlanner;

    public ComicPlanController(PaperJobRepository paperJobRepository, PaperSentenceRepository paperSentenceRepository,
                               ComicPlanner comicPlanner) {
        this.paperJobRepository = paperJobRepository;
        this.paperSentenceRepository = paperSentenceRepository;
        this.comicPlanner = comicPlanner;
    }

    @PostMapping("/api/papers/{id}/comic-plan")
    public ComicPlan plan(@PathVariable("id") Long id) {
        PaperJob job = paperJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: " + id));
        List<PaperSentence> sentences = paperSentenceRepository.findByJob_IdOrderByNumberAsc(id);
        if (sentences.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "저장된 문장이 없습니다. PDF를 다시 올려 주세요.");
        }
        return comicPlanner.plan(job.getTitle(), sentences);
    }
}
