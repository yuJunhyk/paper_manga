package com.readmanga.backend.comic.plan;

import org.junit.jupiter.api.Test;

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
    void 시험_단계_Python과_같은_12조각으로_나눈다() {
        List<Chunk> chunks = ChunkSplitter.split(ChunkSplitter.bodySections(PaperFixture.sentences()), 12);

        // spikes/llm-spike/run_chunk_spike.py --dry-run 결과와 같아야 한다
        assertThat(chunks).extracting(chunk -> chunk.start() + "~" + chunk.end()).containsExactly(
                "1~18", "19~48", "49~63", "64~91", "92~105", "106~125",
                "126~161", "162~175", "176~196", "197~217", "218~238", "239~260");
        assertThat(chunks.getLast().sections()).containsExactly("Limitations & Societal Impact", "Conclusion");
    }
}
