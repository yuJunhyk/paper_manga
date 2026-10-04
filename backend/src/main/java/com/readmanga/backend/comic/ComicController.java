package com.readmanga.backend.comic;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComicController {

    private final ComicQuery comicQuery;

    public ComicController(ComicQuery comicQuery) {
        this.comicQuery = comicQuery;
    }

    /** 완성된 만화 대본. 컷마다 대사와 근거 원문이 들어 있다. */
    @GetMapping("/api/papers/{id}/comic")
    public ComicResponse comic(@PathVariable("id") Long id) {
        return comicQuery.find(id);
    }
}
