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

    @GetMapping("/{id}/result")
    public PaperStructure getResult(@PathVariable("id") Long id) {
        return paperService.getResult(id);
    }
}
