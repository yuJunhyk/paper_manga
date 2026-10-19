package com.readmanga.backend.comic.plan;

import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperSentence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSplitterTest {

    @Test
    void 부록_전까지만_본문으로_본다() {
        List<ChunkSplitter.Section> sections = ChunkSplitter.bodySections(PaperFixture.sentences());

        assertThat(sections.getLast().title()).isEqualTo("Conclusion");
        assertThat(sections.getLast().end()).isEqualTo(260);
    }

    @Test
    void 결론_뒤에_번호_없이_붙은_그림_부록은_본문에서_뺀다() {
        // 187번(Attention 논문): 결론 뒤 'Attention Visualizations'와 그림 제목이 섹션 이름이 된 'Input-Input Layer5'
        List<ChunkSplitter.Section> sections = ChunkSplitter.bodySections(sentences(
                "Introduction", "Conclusion", "Attention Visualizations", "Input-Input Layer5"));

        assertThat(sections).extracting(ChunkSplitter.Section::title).containsExactly("Introduction", "Conclusion");
    }

    @Test
    void 결론_뒤의_한계_섹션은_본문에_남긴다() {
        List<ChunkSplitter.Section> sections = ChunkSplitter.bodySections(sentences(
                "Introduction", "Concluding remarks", "Limitations", "Acknowledgments"));

        assertThat(sections).extracting(ChunkSplitter.Section::title).containsExactly("Introduction", "Concluding remarks", "Limitations");
    }

    /** 섹션마다 문장 3개씩 */
    private static List<PaperSentence> sentences(String... sectionTitles) {
        PaperJob job = new PaperJob("test.pdf");
        List<PaperSentence> sentences = new ArrayList<>();
        int number = 1;
        for (String title : sectionTitles) {
            for (int i = 0; i < 3; i++, number++) {
                sentences.add(new PaperSentence(job, number, title, 1, "Sentence " + number + " explains a finding."));
            }
        }
        return sentences;
    }

    @Test
    void 시험_단계_Python과_같은_12조각으로_나눈다() {
        List<Chunk> chunks = ChunkSplitter.split(ChunkSplitter.bodySections(PaperFixture.sentences()), 12);

        // spikes/llm-spike/run_chunk_spike.py --dry-run 결과와 같아야 한다
        assertThat(chunks).extracting(chunk -> chunk.start() + "~" + chunk.end()).containsExactly(
                "1~18", "19~48", "49~63", "64~91", "92~105", "106~125",
                "126~161", "162~175", "176~196", "197~217", "218~238", "239~260");
        assertThat(chunks.getLast().sections()).containsExactly("Limitations & Societal Impact", "Conclusion");
    }
}
