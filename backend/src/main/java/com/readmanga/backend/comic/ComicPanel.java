package com.readmanga.backend.comic;

import com.readmanga.backend.paper.PaperSentence;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

/**
 * 만화의 컷 하나.
 * - 사실 컷(staging = false): 논문의 사실 하나(fact)를 전하고, 근거 문장(evidence)을 가리킨다.
 * - 연출 컷(staging = true): 장면 전환이나 감탄처럼 논문 사실이 없는 컷. 근거가 없다.
 */
@Entity
public class ComicPanel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id")
    private ComicPage page;

    private int panelOrder;

    @Column(columnDefinition = "text")
    private String scene;

    private boolean staging;

    @Column(columnDefinition = "text")
    private String fact;

    @OneToMany(mappedBy = "panel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<PanelLine> lines = new ArrayList<>();

    /** 컷 하나는 근거 문장 여러 개를, 문장 하나는 여러 컷의 근거가 될 수 있다 (다대다). */
    @ManyToMany
    @JoinTable(
            name = "panel_evidence",
            joinColumns = @JoinColumn(name = "panel_id"),
            inverseJoinColumns = @JoinColumn(name = "sentence_id"))
    @OrderBy("number")
    private List<PaperSentence> evidence = new ArrayList<>();

    protected ComicPanel() {
    }

    ComicPanel(ComicPage page, int panelOrder, String scene, boolean staging, String fact) {
        this.page = page;
        this.panelOrder = panelOrder;
        this.scene = scene;
        this.staging = staging;
        this.fact = fact;
    }

    public PanelLine addLine(Speaker speaker, String text) {
        PanelLine line = new PanelLine(this, lines.size() + 1, speaker, text);
        lines.add(line);
        return line;
    }

    public void addEvidence(PaperSentence sentence) {
        evidence.add(sentence);
    }

    public Long getId() {
        return id;
    }

    public int getPanelOrder() {
        return panelOrder;
    }

    public String getScene() {
        return scene;
    }

    public boolean isStaging() {
        return staging;
    }

    public String getFact() {
        return fact;
    }

    public List<PanelLine> getLines() {
        return lines;
    }

    public List<PaperSentence> getEvidence() {
        return evidence;
    }
}
