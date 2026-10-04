package com.readmanga.backend.comic.plan;

import com.readmanga.backend.paper.PaperSentence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 본문을 섹션 경계를 따라 고르게 나눈다. (6차 시험에서 AI에게 맡겼더니 목차가 앞부분에 몰려서 코드로 옮겼다)
 */
public final class ChunkSplitter {

    private ChunkSplitter() {
    }

    /** 섹션 하나: 이름과 문장 번호 범위. */
    record Section(String title, int start, int end) {

        int size() {
            return end - start + 1;
        }
    }

    /** 문장들을 번호 순서대로 보며 같은 섹션끼리 묶는다. 부록 섹션이 나오면 거기서 멈춘다. */
    public static List<Section> bodySections(List<PaperSentence> sentences) {
        List<Section> sections = new ArrayList<>();
        String title = null;
        int start = -1;
        int end = -1;
        for (PaperSentence sentence : sentences) {
            if (start == -1 || !Objects.equals(sentence.getSectionTitle(), title)) {
                if (start != -1) {
                    sections.add(new Section(title, start, end));
                }
                if (TextRules.isAppendixHeading(sentence.getSectionTitle())) {
                    return sections;
                }
                title = sentence.getSectionTitle();
                start = sentence.getNumber();
            }
            end = sentence.getNumber();
        }
        if (start != -1) {
            sections.add(new Section(title, start, end));
        }
        return sections;
    }

    /**
     * 섹션 경계를 따라 본문을 chunkCount개 조각으로 나눈다.
     * 평균 조각 크기의 1.5배보다 큰 섹션은 먼저 문장 단위로 나눈 뒤, 조각마다 문장 수가 비슷해지는 경계를 고른다.
     */
    public static List<Chunk> split(List<Section> sections, int chunkCount) {
        int totalSentences = sections.stream().mapToInt(Section::size).sum();
        double target = (double) totalSentences / chunkCount;
        int firstNumber = sections.getFirst().start();

        List<Section> units = new ArrayList<>();
        for (Section section : sections) {
            int size = section.size();
            int parts = size > 1.5 * target ? (int) Math.ceil(size / target) : 1;
            for (int part = 0; part < parts; part++) {
                int start = section.start() + (int) Math.round((double) size * part / parts);
                int end = section.start() + (int) Math.round((double) size * (part + 1) / parts) - 1;
                units.add(new Section(section.title(), start, end));
            }
        }

        int count = Math.min(chunkCount, units.size());
        List<Integer> boundaries = units.subList(0, units.size() - 1).stream().map(Section::end).toList();
        List<Integer> cuts = new ArrayList<>();
        for (int k = 1; k < count; k++) {
            int remaining = count - k;
            double wanted = firstNumber - 1 + totalSentences * (double) k / count;
            Integer best = null;
            for (int i = 0; i < boundaries.size(); i++) {
                int boundary = boundaries.get(i);
                boolean afterLastCut = cuts.isEmpty() || boundary > cuts.getLast();
                boolean leavesEnough = boundaries.size() - i >= remaining;
                if (afterLastCut && leavesEnough && (best == null || Math.abs(boundary - wanted) < Math.abs(best - wanted))) {
                    best = boundary;
                }
            }
            cuts.add(best);
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = firstNumber;
        List<Integer> ends = new ArrayList<>(cuts);
        ends.add(units.getLast().end());
        for (int end : ends) {
            int chunkStart = start;
            List<String> titles = new ArrayList<>(new LinkedHashSet<>(units.stream()
                    .filter(unit -> unit.start() >= chunkStart && unit.end() <= end)
                    .map(Section::title)
                    .toList()));
            chunks.add(new Chunk(chunkStart, end, titles));
            start = end + 1;
        }
        return chunks;
    }
}
