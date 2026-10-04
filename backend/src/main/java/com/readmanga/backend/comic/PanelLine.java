package com.readmanga.backend.comic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 컷 안의 대사 한 줄. 누리, 박사님, 해설 중 누가 말하는지(speaker)를 담는다. */
@Entity
public class PanelLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "panel_id")
    private ComicPanel panel;

    private int lineOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Speaker speaker;

    @Column(columnDefinition = "text", nullable = false)
    private String text;

    protected PanelLine() {
    }

    PanelLine(ComicPanel panel, int lineOrder, Speaker speaker, String text) {
        this.panel = panel;
        this.lineOrder = lineOrder;
        this.speaker = speaker;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public Speaker getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }
}
