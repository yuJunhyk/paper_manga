package com.readmanga.backend.comic;

import com.readmanga.backend.comic.ComicResponse.EvidenceResponse;
import com.readmanga.backend.comic.ComicResponse.LineResponse;
import com.readmanga.backend.comic.ComicResponse.PageResponse;
import com.readmanga.backend.comic.ComicResponse.PanelResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 대본 Markdown 내보내기. 한국어 대사는 206번 대본에서 가져왔고, 근거 원문은 지어낸 문장이다 */
class ComicScriptTest {

    private static final PanelResponse OPENING = new PanelResponse(1, "콩이가 책상 앞에서 그림 프로그램을 실행한다.", true, null,
            List.of(new LineResponse("콩이", "드디어 멋진 풍경화를 만들 수 있을 거예요!")), List.of());

    private static final PanelResponse FACT = new PanelResponse(2, "모니터에서 계산이 돌아간다.", false,
            "CelebA-HQ 데이터셋에서 이 연구는 새로운 최고 수준인 FID 5.11을 보고한다.",
            List.of(new LineResponse("냥박사", "맞아. FID 점수가 5.11로 나왔어."),
                    new LineResponse("해설", "CelebA-HQ 데이터셋에서 이 연구는 새로운 최고 수준인 FID 5.11을 보고한다.")),
            List.of(new EvidenceResponse(132, 6, "Image Generation with Latent Diffusion", "Sentence 132 reports an FID of 5.11.")));

    private static ComicResponse comic(PanelResponse... panels) {
        return new ComicResponse(206L, "잠재 공간의 마법!", "콩이가 그림을 만들려 한다.", "콩이가 원하는 그림을 얻었다.",
                List.of(new PageResponse(1, "기", "콩이는 계산이 오래 걸린다는 것을 알게 되었다.", List.of(panels))));
    }

    @Test
    void 쪽_컷_대사를_차례로_쓰고_근거_원문은_접어_둔다() {
        String markdown = ComicScript.markdown(comic(OPENING, FACT));

        assertThat(markdown).startsWith("""
                # 잠재 공간의 마법!

                - 설정: 콩이가 그림을 만들려 한다.
                - 결말: 콩이가 원하는 그림을 얻었다.

                ## 1쪽 (기) — 콩이는 계산이 오래 걸린다는 것을 알게 되었다.

                ### 1컷 (연출)

                장면: 콩이가 책상 앞에서 그림 프로그램을 실행한다.

                - **콩이**: 드디어 멋진 풍경화를 만들 수 있을 거예요!

                ### 2컷 (사실)
                """);
        assertThat(markdown).endsWith("""
                - **냥박사**: 맞아. FID 점수가 5.11로 나왔어.
                - **해설**: CelebA-HQ 데이터셋에서 이 연구는 새로운 최고 수준인 FID 5.11을 보고한다.

                <details>
                <summary>근거 원문 (6쪽 132번)</summary>

                > **6쪽 132번** · Image Generation with Latent Diffusion<br>Sentence 132 reports an FID of 5.11.

                </details>
                """);
    }

    @Test
    void 연출_컷에는_근거_접기가_없다() {
        assertThat(ComicScript.markdown(comic(OPENING))).doesNotContain("<details>");
    }

    @Test
    void 글_속_HTML은_실행되지_않게_바꾸고_줄바꿈으로_새_블록을_만들지_못한다() {
        PanelResponse panel = new PanelResponse(1, "장면", false, "사실",
                List.of(new LineResponse("냥박사", "x < y & <script>alert(1)</script>\n# 가짜 제목")),
                List.of(new EvidenceResponse(1, null, null, "a</details>\n\n<img src=x onerror=alert(1)>")));

        String markdown = ComicScript.markdown(comic(panel));

        assertThat(markdown).contains("- **냥박사**: x &lt; y &amp; &lt;script&gt;alert(1)&lt;/script&gt; # 가짜 제목")
                .contains("> **1번**<br>a&lt;/details&gt; &lt;img src=x onerror=alert(1)&gt;")
                .doesNotContain("<script>").doesNotContain("<img").doesNotContainPattern("(?m)^# 가짜 제목");
        assertThat(markdown.split("</details>", -1)).as("닫는 태그는 코드가 쓴 하나뿐").hasSize(2);
    }

    @Test
    void 파일_이름은_작업번호_논문제목이다() {
        assertThat(ComicScript.fileName(206L, "High-Resolution Image Synthesis with Latent Diffusion Models"))
                .isEqualTo("206_High-Resolution-Image-Synthesis-with-Latent-Diffusion-Models.md");
        assertThat(ComicScript.fileName(7L, "데이터 만화: 교육/사용자 연구?")).isEqualTo("7_데이터-만화-교육-사용자-연구.md");
        assertThat(ComicScript.fileName(8L, "../../etc/passwd")).isEqualTo("8_etc-passwd.md");
        assertThat(ComicScript.fileName(9L, null)).isEqualTo("9_comic-script.md");
        assertThat(ComicScript.fileName(10L, "a".repeat(300))).hasSize("10_".length() + 100 + ".md".length());
    }
}
