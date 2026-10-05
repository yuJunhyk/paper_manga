package com.readmanga.backend.paper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(paperService.upload(file));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable("id") Long id) {
        return paperService.getJob(id);
    }

    /** 작업 취소. 지금 하던 AI 요청이 끝나는 대로 멈춘다. */
    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@PathVariable("id") Long id) {
        return paperService.cancel(id);
    }

    /** 다시 시도: 실패·취소된 작업을 저장된 문장으로 핵심 사실 추출부터 다시 돌린다. 접수되면 202. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<JobResponse> retry(@PathVariable("id") Long id) {
        return ResponseEntity.accepted().body(paperService.retry(id));
    }

    @GetMapping("/{id}/result")
    public PaperStructure getResult(@PathVariable("id") Long id) {
        return paperService.getResult(id);
    }
}
