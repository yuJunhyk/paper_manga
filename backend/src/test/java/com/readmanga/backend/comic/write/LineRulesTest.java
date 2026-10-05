package com.readmanga.backend.comic.write;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 단계에서 실제로 나온 대사 실수들을 잡는지 확인한다. */
class LineRulesTest {

    private static List<String> problemsOf(String speaker, String text) {
        return LineRules.problems("연구실 칠판 앞에서 박사님이 설명한다", List.of(new LineRules.Line(speaker, text)), Set.of());
    }

    @Test
    void 박사님_존댓말을_문장마다_잡는다() {
        assertThat(problemsOf("박사님", "좋은 질문이야. 이건 정말 중요해요.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("박사님", "좋은 질문이야, 누리야. 레고처럼 조립하는 거야.")).isEmpty();
    }

    @Test
    void 해설이_인물_말투로_끝나거나_누리에게_말을_걸면_잡는다() {
        assertThat(problemsOf("해설", "조건부 생성으로 빠르게 만들 수 있게 되는 거야.")).anyMatch(p -> p.contains("인물 말투"));
        assertThat(problemsOf("해설", "누리야, 잠재 공간은 작은 공간이다.")).anyMatch(p -> p.contains("말을 걸었다"));
        assertThat(problemsOf("해설", "누리는 프로젝트를 완성했다.")).isEmpty();
    }

    @Test
    void 번호_표시_긴_대사_섞인_단어_짧은_장면을_잡는다() {
        assertThat(problemsOf("박사님", "처리 시간이 [23]에서 보듯이 줄었어.")).anyMatch(p -> p.contains("번호 표시"));
        assertThat(problemsOf("박사님", "가".repeat(120))).anyMatch(p -> p.contains("너무 길다"));
        assertThat(problemsOf("누리", "마asking 영역이 뭐예요?")).anyMatch(p -> p.contains("섞여"));
        assertThat(LineRules.problems("0", List.of(new LineRules.Line("누리", "와!")), Set.of()))
                .anyMatch(p -> p.contains("장면 설명이 너무 짧다"));
    }

    @Test
    void 같은_쪽에서_긴_대사를_반복하면_잡고_짧은_감탄사는_봐준다() {
        Set<String> seen = Set.of("이미지 생성을 더 빠르게 할 수 있어.", "와!");
        String scene = "연구실 칠판 앞에서 박사님이 설명한다";
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("박사님", "이미지 생성을 더 빠르게 할 수 있어.")), seen))
                .anyMatch(p -> p.contains("같은 대사"));
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("누리", "와!")), seen)).isEmpty();
    }

    @Test
    void 정리할_때_해설_괄호를_따로_빼고_번호와_따옴표와_줄바꿈을_지운다() {
        List<LineRules.Line> tidied = LineRules.tidy(List.of(
                new LineRules.Line("박사님", "\"마치 레고 같아. (해설: 잠재 공간은 작은 공간이다.) (F5)\n\n그래서 빨라.\""),
                new LineRules.Line("누리", "“정말요?”")));

        assertThat(tidied).containsExactly(
                new LineRules.Line("박사님", "마치 레고 같아. 그래서 빨라."),
                new LineRules.Line("해설", "잠재 공간은 작은 공간이다."),
                new LineRules.Line("누리", "정말요?"));
    }

    // ---------- 실제 실행 1차(13번 작업)에서 나온 실수 ----------

    private static final List<String> SCENES_WITH_INTRUSION = List.of(
            "누리는 눈을 크게 뜨며 박사님의 설명을 집중해서 듣고 있다. 박사님은 누리를 향해 친근하게 미소 지으며 간결하게 설명한다. **박사님**:",
            "누리는 눈을 크게 뜨고 감탄하며 그래프를 자세히 들여다보고 있다. **대사:** - **누리:**",
            "주변의 그래프는 이제 이해하기 쉬운 선으로 표시되어 있다. 누리: 박사님, 이렇게 선명한 이미지가 생성되다니 정말 놀랍네요! 해설: 이 연구는 …",
            "주변의 실험 장비들은 조용히 작동하고 있다. (참고: 이 설명은 주어진 사실에 근거한 해석이며, 직접적인 대사는 아님)");

    @Test
    void 장면에_섞인_대사와_꾸밈_기호를_잡고_정상_장면은_통과시킨다() {
        SCENES_WITH_INTRUSION.forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("누리", "와!")), Set.of()))
                .as(scene).anyMatch(p -> p.contains("장면 칸에 대사나 기호")));

        assertThat(LineRules.problems("박사님의 연구실, 컴퓨터 화면에는 잠재 확산 모델의 학습 과정 도식이 보인다. 박사님은 누리 옆에 서서 설명을 시작한다.",
                List.of(new LineRules.Line("누리", "와!")), Set.of())).isEmpty();
        assertThat(LineRules.problems("연구실 벽에는 'Latent Space'라는 문구가 걸려 있고, 화면에는 '계산 시간 초과' 메시지가 깜빡인다.",
                List.of(new LineRules.Line("누리", "와!")), Set.of())).isEmpty();
    }

    @Test
    void 정리할_때_장면은_섞인_대사_앞까지만_남긴다() {
        assertThat(SCENES_WITH_INTRUSION.stream().map(LineRules::tidyScene)).containsExactly(
                "누리는 눈을 크게 뜨며 박사님의 설명을 집중해서 듣고 있다. 박사님은 누리를 향해 친근하게 미소 지으며 간결하게 설명한다.",
                "누리는 눈을 크게 뜨고 감탄하며 그래프를 자세히 들여다보고 있다.",
                "주변의 그래프는 이제 이해하기 쉬운 선으로 표시되어 있다.",
                "주변의 실험 장비들은 조용히 작동하고 있다.");
        // 앞부분이 너무 짧게 남으면 꾸밈 기호만 지운다
        assertThat(LineRules.tidyScene("**장면**: 연구실에서 누리가 웃는다")).isEqualTo("장면 연구실에서 누리가 웃는다");
    }

    @Test
    void 대사_속_별표_행동_설명을_잡고_정리할_때_지운다() {
        String line = "맞아, 트랜스포머 인코더가 핵심이야. *박사님은 누리 옆에서 친근하게 미소 지으며 설명한다.*";

        assertThat(problemsOf("박사님", line)).anyMatch(p -> p.contains("꾸밈 기호"));
        assertThat(LineRules.tidy(List.of(new LineRules.Line("박사님", line))))
                .containsExactly(new LineRules.Line("박사님", "맞아, 트랜스포머 인코더가 핵심이야."));
    }

    @Test
    void 한계_컷에서_단점을_뒤집는_대사를_잡는다() {
        assertThat(LineRules.limitationProblems(List.of(
                new LineRules.Line("박사님", "맞아, 누리야. 처음엔 느릴 수 있지만, 결국엔 훨씬 빠르게 결과를 낼 수 있어."))))
                .anyMatch(p -> p.contains("단점을 뒤집는다"));

        // 단점을 그대로 인정하는 대사는 통과 (실제 실행의 240번, 250번 컷)
        assertThat(LineRules.limitationProblems(List.of(
                new LineRules.Line("누리", "박사님, 디테일이 조금 부족하지 않나요?"),
                new LineRules.Line("박사님", "그렇지, 누리야. 빠르지만 세밀한 부분은 아직 조금 부족할 수 있어."),
                new LineRules.Line("박사님", "하지만 기억해, 이런 혁신적인 도구도 잘못 사용하면 위험해.")))).isEmpty();
    }

    // ---------- 실제 실행 2차(23번 작업)에서 나온 실수 ----------

    @Test
    void 장면_끝의_중괄호와_칸_이름_메아리를_잡고_그_앞까지만_남긴다() {
        String echo = "주변의 그래프는 샘플링 효율성과 이미지 품질 간의 긍정적 상관관계를 보여준다. lines에는 대사만 포함한다. {";
        String brace = "누리는 머리를 긁적이며 고민스러운 표정을 짓고 있다. {";

        assertThat(LineRules.problems(echo, List.of(new LineRules.Line("누리", "와!")), Set.of())).anyMatch(p -> p.contains("장면 칸에 대사나 기호"));
        assertThat(LineRules.problems(brace, List.of(new LineRules.Line("누리", "와!")), Set.of())).anyMatch(p -> p.contains("장면 칸에 대사나 기호"));
        assertThat(LineRules.tidyScene(echo)).isEqualTo("주변의 그래프는 샘플링 효율성과 이미지 품질 간의 긍정적 상관관계를 보여준다.");
        assertThat(LineRules.tidyScene(brace)).isEqualTo("누리는 머리를 긁적이며 고민스러운 표정을 짓고 있다.");
    }

    @Test
    void 해설이_존댓말로_끝나면_잡는다() {
        assertThat(problemsOf("해설", "이 공간에서는 복잡한 데이터가 단순화되어 핵심 정보만을 담게 됩니다.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("해설", "잠재 공간 기술은 과학 프로젝트에 혁신을 가져올 수 있습니다.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("해설", "잠재 공간은 이미지의 핵심 특징만 담은 작은 공간이다.")).isEmpty();
    }

    @Test
    void 바로_앞_컷과_거의_같은_장면은_잡고_다른_장면은_통과시킨다() {
        String previous = "컴퓨터 과학 동아리 방의 책상 앞. 누리는 노트북 화면을 집중해서 바라보며 약간의 좌절감을 드러내고 있다. "
                + "책상 위에는 고해상도 우주 사진 이미지와 함께 시간 초과 경고 메시지가 표시되어 있다. 박사님은 조용히 방에 들어와 옆 의자에 앉는다.";
        String almostSame = "박사님의 연구실 책상 앞. 누리는 노트북 화면을 집중해서 바라보며 약간의 실망감을 드러내고 있다. "
                + "책상 위에는 고해상도 우주 사진 이미지와 시간 초과 경고 메시지가 표시되어 있다. 박사님은 조용히 옆 의자에 앉아 누리를 부드럽게 바라본다.";
        String different = "학교 과학 박람회 전시대 앞. 누리가 완성된 우주 사진을 걸고 관람객들에게 설명한다. 박사님은 뒤에서 응원한다.";

        assertThat(LineRules.problems(almostSame, List.of(new LineRules.Line("누리", "와!")), Set.of(), previous)).anyMatch(p -> p.contains("장면이 거의 같다"));
        assertThat(LineRules.problems(different, List.of(new LineRules.Line("누리", "와!")), Set.of(), previous)).isEmpty();
        assertThat(LineRules.problems(different, List.of(new LineRules.Line("누리", "와!")), Set.of(), null)).isEmpty();
    }

    // ---------- 실제 실행 3차(81번 다시 시도)에서 나온 실수 ----------

    @Test
    void 장면_칸의_따옴표_대사와_쌍점과_br_찌꺼기를_잡고_끝난_문장까지만_남긴다() {
        String quoted = "누리의 연구실 책상 앞에는 과학 도표가 걸려 있다. 박사님은 편안한 미소로 누리 옆 의자에 앉아 조용히 기다리고 있다. "
                + "누리는 눈빛을 집중시키며 질문한다: \"박사님, GAN이 고해상도 이미지를 잘 만드는데 왜 완벽하지 않다고 하죠?\"";
        String br = "br/ 실험실 책상 위에서 누리는 컴퓨터 화면을 쳐다보며 고민하는 표정을 짓고 있다.br/";
        String colon = "누리는 눈을 크게 뜨고 설명에 귀 기울이고 있으며, 박사님은 손가락으로 핵심 부분을 가리키며 열정적으로 이야기한다. : \u3000";

        List.of(quoted, br, colon).forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("누리", "와!")), Set.of()))
                .as(scene).anyMatch(p -> p.contains("장면 칸에 대사나 기호")));
        assertThat(LineRules.tidyScene(quoted))
                .isEqualTo("누리의 연구실 책상 앞에는 과학 도표가 걸려 있다. 박사님은 편안한 미소로 누리 옆 의자에 앉아 조용히 기다리고 있다.");
        assertThat(LineRules.tidyScene(br)).isEqualTo("실험실 책상 위에서 누리는 컴퓨터 화면을 쳐다보며 고민하는 표정을 짓고 있다.");
        assertThat(LineRules.tidyScene(colon))
                .isEqualTo("누리는 눈을 크게 뜨고 설명에 귀 기울이고 있으며, 박사님은 손가락으로 핵심 부분을 가리키며 열정적으로 이야기한다.");
    }

    @Test
    void 실제_결과의_정상_장면은_통과시킨다() {
        List.of("실험실 책상 앞에 앉아 있는 누리와 박사님 주변이 조용하다. 모니터 화면에는 LDM-4와 LDM-8 모델의 성능 비교 그래프가 선명하게 표시되어 있다.",
                        "누리는 눈을 반짝이며 박사님의 설명에 귀 기울이고 있다. 모니터 화면에는 'Cross-Attention Based Conditioning' 문구가 선명하게 표시되어 있다.",
                        "노트에 필기하며 박사님의 설명을 경청하고 있는 누리. 모니터 화면에는 복잡한 그래프와 강조된 핵심 개념이 보인다.")
                .forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("누리", "와!")), Set.of())).as(scene).isEmpty());
    }

    @Test
    void 대사가_하나도_없으면_잡는다() {
        String scene = "연구실 칠판 앞에서 박사님이 설명한다";

        assertThat(LineRules.problems(scene, List.of(), Set.of())).anyMatch(p -> p.contains("대사가 하나도 없다"));
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("누리", "  ")), Set.of())).anyMatch(p -> p.contains("대사가 하나도 없다"));
    }

    @Test
    void 대사_속_괄호_행동_설명과_앞_기호와_끝_쉼표를_정리한다() {
        assertThat(problemsOf("누리", "(눈을 반짝이며) 와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"))
                .anyMatch(p -> p.contains("괄호로 행동 설명"));
        assertThat(problemsOf("박사님", "응, 그건 잠재 공간(latent space) 덕분이야.")).as("용어 풀이 괄호는 행동 설명이 아니다")
                .noneMatch(p -> p.contains("괄호로 행동 설명"));

        assertThat(LineRules.tidy(List.of(
                new LineRules.Line("누리", "(눈을 반짝이며) 와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"),
                new LineRules.Line("누리", "- 확산 모델이 이미지를 점점 흐리게 만드는데... 어떻게 고화질 이미지를 만들 수 있을까요?"),
                new LineRules.Line("박사님", "-- 맞아! 마치 흐린 사진을 단계별로 맑게 다듬듯이, 정교한 이미지로 만들어내는 거야.,"))))
                .containsExactly(
                        new LineRules.Line("누리", "와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"),
                        new LineRules.Line("누리", "확산 모델이 이미지를 점점 흐리게 만드는데... 어떻게 고화질 이미지를 만들 수 있을까요?"),
                        new LineRules.Line("박사님", "맞아! 마치 흐린 사진을 단계별로 맑게 다듬듯이, 정교한 이미지로 만들어내는 거야."));
    }
}
