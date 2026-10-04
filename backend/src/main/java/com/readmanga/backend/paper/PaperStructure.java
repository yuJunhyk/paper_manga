package com.readmanga.backend.paper;

import java.util.List;

public record PaperStructure(String title, List<Section> sections) {

    public record Section(String title, List<Sentence> sentences) {}

    public record Sentence(int number, String text, Integer page) {}
}
