package com.readmanga.backend.comic;

import com.readmanga.backend.paper.JobStatus;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 만화 조회. 쪽 → 컷 → 대사·근거를 필요할 때 읽어 오므로(LAZY 로딩),
 * 한 트랜잭션 안에서 응답 모양으로 바꿔 돌려준다.
 */
@Service
public class ComicQuery {

    private final PaperJobRepository paperJobRepository;
    private final ComicRepository comicRepository;

    public ComicQuery(PaperJobRepository paperJobRepository, ComicRepository comicRepository) {
        this.paperJobRepository = paperJobRepository;
        this.comicRepository = comicRepository;
    }

    @Transactional(readOnly = true)
    public ComicResponse find(Long jobId) {
        PaperJob job = paperJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: " + jobId));
        return comicRepository.findByJob_Id(jobId)
                .map(comic -> ComicResponse.from(jobId, comic))
                .orElseThrow(() -> isRunning(job)
                        ? new ResponseStatusException(HttpStatus.CONFLICT, "아직 만화를 만드는 중입니다: "
                        + (job.getStage() == null ? "대기" : job.getStage().getLabel()))
                        : new ResponseStatusException(HttpStatus.NOT_FOUND, "이 작업에는 만화가 없습니다. 상태: " + job.getStatus()));
    }

    private static boolean isRunning(PaperJob job) {
        return job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.PROCESSING;
    }
}
