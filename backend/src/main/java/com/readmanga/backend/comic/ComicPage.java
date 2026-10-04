package com.readmanga.backend.comic;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

/** 만화의 한 쪽. 기승전결 중 어떤 역할인지(role)와 이 쪽의 이야기 흐름(beat)을 담는다. */
@Entity
public class ComicPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comic_id")
    private Comic comic;

    private int pageNumber;

    @Column(length = 10)
    private String role;

    @Column(columnDefinition = "text")
    private String beat;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("panelOrder")
    private List<ComicPanel> panels = new ArrayList<>();

    protected ComicPage() {
    }

    ComicPage(Comic comic, int pageNumber, String role, String beat) {
        this.comic = comic;
        this.pageNumber = pageNumber;
        this.role = role;
        this.beat = beat;
    }

    public ComicPanel addPanel(String scene, boolean staging, String fact) {
        ComicPanel panel = new ComicPanel(this, panels.size() + 1, scene, staging, fact);
        panels.add(panel);
        return panel;
    }

    public Long getId() {
        return id;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getRole() {
        return role;
    }

    public String getBeat() {
        return beat;
    }

    public List<ComicPanel> getPanels() {
        return panels;
    }
}
