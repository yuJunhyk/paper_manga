package com.readmanga.backend.paper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @PostMapping
    public ResponseEntity<JobResponse> upload(@RequestParam("file") MultipartFile file) {
        requirePdf(file);
        return ResponseEntity.accepted().body(paperService.upload(file));
    }

    /**
     * PDF만 받는다. 아니면 작업을 만들지 않고 400으로 돌려보낸다 (전에는 받아 놓고 GROBID 단계에서 실패했다).
     * 파일 이름과 Content-Type은 보내는 쪽이 마음대로 붙일 수 있어서, 파일 맨 앞의 '%PDF-' 표시로 확인한다.
     */
    private static void requirePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일입니다. PDF 파일을 올려 주세요.");
        }
        try (InputStream input = file.getInputStream()) {
            if (!Arrays.equals(input.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 파일만 올릴 수 있습니다.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드한 파일을 읽지 못했습니다.", e);
        }
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
