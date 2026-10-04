package com.readmanga.backend.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * GROBID가 뽑은 논문 문장 하나. 만화 컷의 원문 근거는 이 문장을 가리킨다.
 * 한 작업(논문) 안에서 문장 번호는 겹치지 않는다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "number"}))
public class PaperSentence {

    private static final int SECTION_TITLE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private PaperJob job;

    private int number;

    @Column(length = SECTION_TITLE_MAX_LENGTH)
    private String sectionTitle;

    private Integer page;

    @Column(columnDefinition = "text", nullable = false)
    private String text;

    protected PaperSentence() {
    }

    public PaperSentence(PaperJob job, int number, String sectionTitle, Integer page, String text) {
        this.job = job;
        this.number = number;
        this.sectionTitle = truncate(sectionTitle);
        this.page = page;
        this.text = text;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= SECTION_TITLE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SECTION_TITLE_MAX_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public Integer getPage() {
        return page;
    }

    public String getText() {
        return text;
    }
}
