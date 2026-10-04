package com.readmanga.backend.comic;

import com.readmanga.backend.paper.PaperJob;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 논문 하나로 만든 학습 만화. 쪽 → 컷 → 대사를 함께 저장하고 지운다.
 * (만화를 저장하면 쪽·컷·대사도 같이 저장되고, 만화를 지우면 같이 지워진다)
 */
@Entity
public class Comic {

    private static final int TITLE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", unique = true)
    private PaperJob job;

    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    @Column(columnDefinition = "text")
    private String setting;

    @Column(columnDefinition = "text")
    private String ending;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "comic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNumber")
    private List<ComicPage> pages = new ArrayList<>();

    protected Comic() {
    }

    public Comic(PaperJob job, String title, String setting, String ending) {
        this.job = job;
        this.title = title;
        this.setting = setting;
        this.ending = ending;
        this.createdAt = LocalDateTime.now();
    }

    public ComicPage addPage(int pageNumber, String role, String beat) {
        ComicPage page = new ComicPage(this, pageNumber, role, beat);
        pages.add(page);
        return page;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSetting() {
        return setting;
    }

    public String getEnding() {
        return ending;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ComicPage> getPages() {
        return pages;
    }
}
