package com.readmanga.backend.comic.write;

import com.readmanga.backend.comic.plan.ComicPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 단계에서 실제로 나온 대사 실수들을 잡는지 확인한다. */
class LineRulesTest {

    private static List<String> problemsOf(String speaker, String text) {
        return LineRules.problems("연구실 칠판 앞에서 냥박사님이 설명한다", List.of(new LineRules.Line(speaker, text)), Set.of());
    }

    @Test
    void 냥박사님_존댓말을_문장마다_잡는다() {
        assertThat(problemsOf("냥박사", "좋은 질문이야. 이건 정말 중요해요.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("냥박사", "좋은 질문이야, 콩이야. 레고처럼 조립하는 거야.")).isEmpty();
    }

    @Test
    void 해설이_인물_말투로_끝나거나_콩이에게_말을_걸면_잡는다() {
        assertThat(problemsOf("해설", "조건부 생성으로 빠르게 만들 수 있게 되는 거야.")).anyMatch(p -> p.contains("인물 말투"));
        assertThat(problemsOf("해설", "콩이야, 잠재 공간은 작은 공간이다.")).anyMatch(p -> p.contains("말을 걸었다"));
        assertThat(problemsOf("해설", "콩이는 프로젝트를 완성했다.")).isEmpty();
    }

    @Test
    void 번호_표시_긴_대사_섞인_단어_짧은_장면을_잡는다() {
        assertThat(problemsOf("냥박사", "처리 시간이 [23]에서 보듯이 줄었어.")).anyMatch(p -> p.contains("번호 표시"));
        assertThat(problemsOf("냥박사", "가".repeat(150))).anyMatch(p -> p.contains("너무 길다"));
        assertThat(problemsOf("냥박사", "가".repeat(120))).as("한 대사 1~3문장은 된다").noneMatch(p -> p.contains("너무 길다"));
        assertThat(problemsOf("콩이", "마asking 영역이 뭐예요?")).anyMatch(p -> p.contains("섞여"));
        assertThat(LineRules.problems("0", List.of(new LineRules.Line("콩이", "와!")), Set.of()))
                .anyMatch(p -> p.contains("장면 설명이 너무 짧다"));
    }

    @Test
    void 같은_쪽에서_긴_대사를_반복하면_잡고_짧은_감탄사는_봐준다() {
        Set<String> seen = Set.of("이미지 생성을 더 빠르게 할 수 있어.", "와!");
        String scene = "연구실 칠판 앞에서 냥박사님이 설명한다";
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("냥박사", "이미지 생성을 더 빠르게 할 수 있어.")), seen))
                .anyMatch(p -> p.contains("같은 대사"));
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("콩이", "와!")), seen)).isEmpty();
    }

    @Test
    void 정리할_때_해설_괄호를_따로_빼고_번호와_따옴표와_줄바꿈을_지운다() {
        List<LineRules.Line> tidied = LineRules.tidy(List.of(
                new LineRules.Line("냥박사", "\"마치 레고 같아. (해설: 잠재 공간은 작은 공간이다.) (F5)\n\n그래서 빨라.\""),
                new LineRules.Line("콩이", "“정말요?”")));

        assertThat(tidied).containsExactly(
                new LineRules.Line("냥박사", "마치 레고 같아. 그래서 빨라."),
                new LineRules.Line("해설", "잠재 공간은 작은 공간이다."),
                new LineRules.Line("콩이", "정말요?"));
    }

    // ---------- 실제 실행 1차(13번 작업)에서 나온 실수 ----------

    private static final List<String> SCENES_WITH_INTRUSION = List.of(
            "콩이는 눈을 크게 뜨며 냥박사님의 설명을 집중해서 듣고 있다. 냥박사님은 콩이를 향해 친근하게 미소 지으며 간결하게 설명한다. **냥박사님**:",
            "콩이는 눈을 크게 뜨고 감탄하며 그래프를 자세히 들여다보고 있다. **대사:** - **콩이:**",
            "주변의 그래프는 이제 이해하기 쉬운 선으로 표시되어 있다. 콩이: 냥박사님, 이렇게 선명한 이미지가 생성되다니 정말 놀랍네요! 해설: 이 연구는 …",
            "주변의 실험 장비들은 조용히 작동하고 있다. (참고: 이 설명은 주어진 사실에 근거한 해석이며, 직접적인 대사는 아님)");

    @Test
    void 장면에_섞인_대사와_꾸밈_기호를_잡고_정상_장면은_통과시킨다() {
        SCENES_WITH_INTRUSION.forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("콩이", "와!")), Set.of()))
                .as(scene).anyMatch(p -> p.contains("장면 칸에 대사나 기호")));

        assertThat(LineRules.problems("냥박사님의 연구실, 컴퓨터 화면에는 잠재 확산 모델의 학습 과정 도식이 보인다. 냥박사님은 콩이 옆에 서서 설명을 시작한다.",
                List.of(new LineRules.Line("콩이", "와!")), Set.of())).isEmpty();
        assertThat(LineRules.problems("연구실 벽에는 'Latent Space'라는 문구가 걸려 있고, 화면에는 '계산 시간 초과' 메시지가 깜빡인다.",
                List.of(new LineRules.Line("콩이", "와!")), Set.of())).isEmpty();
    }

    @Test
    void 정리할_때_장면은_섞인_대사_앞까지만_남긴다() {
        assertThat(SCENES_WITH_INTRUSION.stream().map(LineRules::tidyScene)).containsExactly(
                "콩이는 눈을 크게 뜨며 냥박사님의 설명을 집중해서 듣고 있다. 냥박사님은 콩이를 향해 친근하게 미소 지으며 간결하게 설명한다.",
                "콩이는 눈을 크게 뜨고 감탄하며 그래프를 자세히 들여다보고 있다.",
                "주변의 그래프는 이제 이해하기 쉬운 선으로 표시되어 있다.",
                "주변의 실험 장비들은 조용히 작동하고 있다.");
        // 앞부분이 너무 짧게 남으면 꾸밈 기호만 지운다
        assertThat(LineRules.tidyScene("**장면**: 연구실에서 콩이가 웃는다")).isEqualTo("장면 연구실에서 콩이가 웃는다");
    }

    @Test
    void 대사_속_별표_행동_설명을_잡고_정리할_때_지운다() {
        String line = "맞아, 트랜스포머 인코더가 핵심이야. *냥박사님은 콩이 옆에서 친근하게 미소 지으며 설명한다.*";

        assertThat(problemsOf("냥박사", line)).anyMatch(p -> p.contains("꾸밈 기호"));
        assertThat(LineRules.tidy(List.of(new LineRules.Line("냥박사", line))))
                .containsExactly(new LineRules.Line("냥박사", "맞아, 트랜스포머 인코더가 핵심이야."));
    }

    @Test
    void 한계_컷에서_단점을_뒤집는_대사를_잡는다() {
        assertThat(LineRules.limitationProblems(List.of(
                new LineRules.Line("냥박사", "맞아, 콩이야. 처음엔 느릴 수 있지만, 결국엔 훨씬 빠르게 결과를 낼 수 있어."))))
                .anyMatch(p -> p.contains("단점을 뒤집는다"));

        // 단점을 그대로 인정하는 대사는 통과 (실제 실행의 240번, 250번 컷)
        assertThat(LineRules.limitationProblems(List.of(
                new LineRules.Line("콩이", "냥박사님, 디테일이 조금 부족하지 않나요?"),
                new LineRules.Line("냥박사", "그렇지, 콩이야. 빠르지만 세밀한 부분은 아직 조금 부족할 수 있어."),
                new LineRules.Line("냥박사", "하지만 기억해, 이런 혁신적인 도구도 잘못 사용하면 위험해.")))).isEmpty();
    }

    // ---------- 실제 실행 2차(23번 작업)에서 나온 실수 ----------

    @Test
    void 장면_끝의_중괄호와_칸_이름_메아리를_잡고_그_앞까지만_남긴다() {
        String echo = "주변의 그래프는 샘플링 효율성과 이미지 품질 간의 긍정적 상관관계를 보여준다. lines에는 대사만 포함한다. {";
        String brace = "콩이는 머리를 긁적이며 고민스러운 표정을 짓고 있다. {";

        assertThat(LineRules.problems(echo, List.of(new LineRules.Line("콩이", "와!")), Set.of())).anyMatch(p -> p.contains("장면 칸에 대사나 기호"));
        assertThat(LineRules.problems(brace, List.of(new LineRules.Line("콩이", "와!")), Set.of())).anyMatch(p -> p.contains("장면 칸에 대사나 기호"));
        assertThat(LineRules.tidyScene(echo)).isEqualTo("주변의 그래프는 샘플링 효율성과 이미지 품질 간의 긍정적 상관관계를 보여준다.");
        assertThat(LineRules.tidyScene(brace)).isEqualTo("콩이는 머리를 긁적이며 고민스러운 표정을 짓고 있다.");
    }

    @Test
    void 해설이_존댓말로_끝나면_잡는다() {
        assertThat(problemsOf("해설", "이 공간에서는 복잡한 데이터가 단순화되어 핵심 정보만을 담게 됩니다.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("해설", "잠재 공간 기술은 과학 프로젝트에 혁신을 가져올 수 있습니다.")).anyMatch(p -> p.contains("존댓말"));
        assertThat(problemsOf("해설", "잠재 공간은 이미지의 핵심 특징만 담은 작은 공간이다.")).isEmpty();
    }

    @Test
    void 바로_앞_컷과_거의_같은_장면은_잡고_다른_장면은_통과시킨다() {
        String previous = "컴퓨터 과학 동아리 방의 책상 앞. 콩이는 노트북 화면을 집중해서 바라보며 약간의 좌절감을 드러내고 있다. "
                + "책상 위에는 고해상도 우주 사진 이미지와 함께 시간 초과 경고 메시지가 표시되어 있다. 냥박사님은 조용히 방에 들어와 옆 의자에 앉는다.";
        String almostSame = "냥박사님의 연구실 책상 앞. 콩이는 노트북 화면을 집중해서 바라보며 약간의 실망감을 드러내고 있다. "
                + "책상 위에는 고해상도 우주 사진 이미지와 시간 초과 경고 메시지가 표시되어 있다. 냥박사님은 조용히 옆 의자에 앉아 콩이를 부드럽게 바라본다.";
        String different = "학교 과학 박람회 전시대 앞. 콩이가 완성된 우주 사진을 걸고 관람객들에게 설명한다. 냥박사님은 뒤에서 응원한다.";

        assertThat(LineRules.problems(almostSame, List.of(new LineRules.Line("콩이", "와!")), Set.of(), previous)).anyMatch(p -> p.contains("장면이 거의 같다"));
        assertThat(LineRules.problems(different, List.of(new LineRules.Line("콩이", "와!")), Set.of(), previous)).isEmpty();
        assertThat(LineRules.problems(different, List.of(new LineRules.Line("콩이", "와!")), Set.of(), null)).isEmpty();
    }

    // ---------- 실제 실행 3차(81번 다시 시도)에서 나온 실수 ----------

    @Test
    void 장면_칸의_따옴표_대사와_쌍점과_br_찌꺼기를_잡고_끝난_문장까지만_남긴다() {
        String quoted = "콩이의 연구실 책상 앞에는 과학 도표가 걸려 있다. 냥박사님은 편안한 미소로 콩이 옆 의자에 앉아 조용히 기다리고 있다. "
                + "콩이는 눈빛을 집중시키며 질문한다: \"냥박사님, GAN이 고해상도 이미지를 잘 만드는데 왜 완벽하지 않다고 하죠?\"";
        String br = "br/ 실험실 책상 위에서 콩이는 컴퓨터 화면을 쳐다보며 고민하는 표정을 짓고 있다.br/";
        String colon = "콩이는 눈을 크게 뜨고 설명에 귀 기울이고 있으며, 냥박사님은 손가락으로 핵심 부분을 가리키며 열정적으로 이야기한다. : \u3000";

        List.of(quoted, br, colon).forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("콩이", "와!")), Set.of()))
                .as(scene).anyMatch(p -> p.contains("장면 칸에 대사나 기호")));
        assertThat(LineRules.tidyScene(quoted))
                .isEqualTo("콩이의 연구실 책상 앞에는 과학 도표가 걸려 있다. 냥박사님은 편안한 미소로 콩이 옆 의자에 앉아 조용히 기다리고 있다.");
        assertThat(LineRules.tidyScene(br)).isEqualTo("실험실 책상 위에서 콩이는 컴퓨터 화면을 쳐다보며 고민하는 표정을 짓고 있다.");
        assertThat(LineRules.tidyScene(colon))
                .isEqualTo("콩이는 눈을 크게 뜨고 설명에 귀 기울이고 있으며, 냥박사님은 손가락으로 핵심 부분을 가리키며 열정적으로 이야기한다.");
    }

    @Test
    void 실제_결과의_정상_장면은_통과시킨다() {
        List.of("실험실 책상 앞에 앉아 있는 콩이와 냥박사님 주변이 조용하다. 모니터 화면에는 LDM-4와 LDM-8 모델의 성능 비교 그래프가 선명하게 표시되어 있다.",
                        "콩이는 눈을 반짝이며 냥박사님의 설명에 귀 기울이고 있다. 모니터 화면에는 'Cross-Attention Based Conditioning' 문구가 선명하게 표시되어 있다.",
                        "노트에 필기하며 냥박사님의 설명을 경청하고 있는 콩이. 모니터 화면에는 복잡한 그래프와 강조된 핵심 개념이 보인다.")
                .forEach(scene -> assertThat(LineRules.problems(scene, List.of(new LineRules.Line("콩이", "와!")), Set.of())).as(scene).isEmpty());
    }

    @Test
    void 대사가_하나도_없으면_잡는다() {
        String scene = "연구실 칠판 앞에서 냥박사님이 설명한다";

        assertThat(LineRules.problems(scene, List.of(), Set.of())).anyMatch(p -> p.contains("대사가 하나도 없다"));
        assertThat(LineRules.problems(scene, List.of(new LineRules.Line("콩이", "  ")), Set.of())).anyMatch(p -> p.contains("대사가 하나도 없다"));
    }

    @Test
    void 대사_속_괄호_행동_설명과_앞_기호와_끝_쉼표를_정리한다() {
        assertThat(problemsOf("콩이", "(눈을 반짝이며) 와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"))
                .anyMatch(p -> p.contains("괄호로 행동 설명"));
        assertThat(problemsOf("냥박사", "응, 그건 잠재 공간(latent space) 덕분이야.")).as("용어 풀이 괄호는 행동 설명이 아니다")
                .noneMatch(p -> p.contains("괄호로 행동 설명"));

        assertThat(LineRules.tidy(List.of(
                new LineRules.Line("콩이", "(눈을 반짝이며) 와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"),
                new LineRules.Line("콩이", "- 확산 모델이 이미지를 점점 흐리게 만드는데... 어떻게 고화질 이미지를 만들 수 있을까요?"),
                new LineRules.Line("냥박사", "-- 맞아! 마치 흐린 사진을 단계별로 맑게 다듬듯이, 정교한 이미지로 만들어내는 거야.,"))))
                .containsExactly(
                        new LineRules.Line("콩이", "와, 그렇군요! 그럼 풍경화 작업이 훨씬 빨라질 것 같네요!"),
                        new LineRules.Line("콩이", "확산 모델이 이미지를 점점 흐리게 만드는데... 어떻게 고화질 이미지를 만들 수 있을까요?"),
                        new LineRules.Line("냥박사", "맞아! 마치 흐린 사진을 단계별로 맑게 다듬듯이, 정교한 이미지로 만들어내는 거야."));
    }

    @Test
    void 학생이_박사를_부르며_묻는_대사로_시작하는_컷을_알아보고_연달아_나오면_다시_쓰게_한다() {
        List<LineRules.Line> ask = List.of(new LineRules.Line("콩이", "냥박사님, 이게 정말 빠른 건가요?"), new LineRules.Line("냥박사", "응, 그렇지."));
        List<LineRules.Line> wow = List.of(new LineRules.Line("콩이", "와, 직접 해 보니 정말 빨라요!"));

        assertThat(LineRules.isAddressedQuestion(ask)).isTrue();
        assertThat(LineRules.isAddressedQuestion(wow)).isFalse();
        assertThat(LineRules.repeatedQuestionProblems(ask, true)).hasSize(1);
        assertThat(LineRules.repeatedQuestionProblems(ask, false)).as("앞 컷이 질문이 아니면 괜찮다").isEmpty();
        assertThat(LineRules.repeatedQuestionProblems(wow, true)).isEmpty();
    }

    @Test
    void 여는_컷이_바로_뒤_첫_사실_컷의_장면이나_대사를_베끼면_다시_쓰게_한다() {
        // 189번 1쪽 1컷(여는 컷)과 2컷(첫 사실 컷)
        String nextScene = "책상 앞에 앉아 노트북 화면을 응시하는 콩이. 표정은 답답해 보이며, 손가락으로 키보드를 두드리고 있다.";
        List<LineRules.Line> nextLines = List.of(new LineRules.Line("콩이", "냥박사님, 이 모델로 번역을 시도했는데... 너무 느려요."),
                new LineRules.Line("냥박사", "왜 그렇게 답답해 보여? 뭐가 문제야?"));

        List<String> copied = LineRules.nextPanelProblems(
                "콩이가 책상 앞에 앉아 노트북 화면을 응시하고 있다. 콩이의 표정은 답답해 보이며, 손가락으로 키보드를 두드리고 있다.",
                List.of(new LineRules.Line("콩이", "냥박사님, 이 방식으로 번역을 시도했는데... 너무 시간이 오래 걸려요.")), nextScene, nextLines);
        List<String> different = LineRules.nextPanelProblems("콩이의 방, 산더미 같은 숙제 종이 사이에서 콩이가 머리를 감싸 쥔다.",
                List.of(new LineRules.Line("콩이", "오늘 안에 이걸 다 번역할 수 있을까?")), nextScene, nextLines);

        assertThat(copied).hasSize(2).anyMatch(problem -> problem.contains("바로 뒤 첫 사실 컷과 장면이 거의 같다"))
                .anyMatch(problem -> problem.contains("바로 뒤 첫 사실 컷의 대사"));
        assertThat(different).isEmpty();
    }

    @Test
    void 바로_앞_대사를_되풀이하면_다시_쓰게_한다() {
        // 190번: 1쪽 마지막 대사를 2쪽 첫 사실 컷이 그대로 옮겨 썼다
        List<LineRules.Line> before = List.of(new LineRules.Line("콩이", "다른 방법으로는 이 문장을 어떻게 이해할 수 있을까요?"));

        assertThat(LineRules.previousLineProblems(List.of(new LineRules.Line("콩이", "다른 방법으로는 이 문장을 어떻게 이해할 수 있을까요?"),
                new LineRules.Line("냥박사", "주의 집중 장치는 일종의 '질문'과 여러 정보 묶음으로 작동하는 거란다.")), before))
                .containsExactly("바로 앞 대사 \"다른 방법으로는 이 문장을 어떻게 이해할 수 있을까요?\"와 거의 같은 말이다: \"다른 방법으로는 이 문장을 어떻게 이해할 수 있을까요?\". 같은 말을 되풀이하지 말고 그 말에 대한 반응이나 다음 이야기를 쓴다.");
        assertThat(LineRules.previousLineProblems(List.of(new LineRules.Line("냥박사", "좋은 질문이야. 단어끼리 서로 얼마나 관련 있는지 먼저 보는 거야.")), before))
                .isEmpty();
    }

    @Test
    void 사실_컷의_냥박사_대사에는_사실의_숫자나_용어가_있어야_한다() {
        // 198번: 사실 '최소 2.7배의 속도 향상'에 냥박사 "응. 계산 과정 자체가 훨씬 효율적으로 바뀌었거든."
        String fact = "이 연구는 인페인팅에서 최소 2.7배의 속도 향상을 관찰했으며 FID 점수는 최소 1.6배 개선되었다.";
        List<ComicPlan.Term> terms = List.of(new ComicPlan.Term("latent space", "잠재 공간", List.of()));

        assertThat(LineRules.doctorContentProblems(fact, List.of(new LineRules.Line("콩이", "속도가 정말 빨라졌네요!"),
                new LineRules.Line("냥박사", "응. 계산 과정 자체가 훨씬 효율적으로 바뀌었거든.")), terms))
                .singleElement().asString().contains("냥박사 대사에 이 컷의 사실 내용이 없다", "2.7, 1.6");
        assertThat(LineRules.doctorContentProblems(fact, List.of(new LineRules.Line("냥박사", "구멍 채우기에서 2.7배나 빨라졌어.")), terms)).isEmpty();
        assertThat(LineRules.doctorContentProblems(fact, List.of(new LineRules.Line("콩이", "2.7배요?")), terms))
                .singleElement().asString().contains("냥박사 대사가 없다");
        assertThat(LineRules.doctorContentProblems("이 연구는 잠재 공간(latent space)에서 학습한다.",
                List.of(new LineRules.Line("냥박사", "그림을 작게 줄인 잠재 공간에서 배우는 거야.")), terms)).isEmpty();
        assertThat(LineRules.doctorContentProblems("두 단계로 나누어 학습한다.", List.of(new LineRules.Line("냥박사", "맞아.")), terms))
                .as("숫자도 용어도 없는 사실은 검사하지 않는다").isEmpty();
        // 201번: '4.4절'을 넣으라고 해서 "4.4절에 적용하고 있거든"
        assertThat(LineRules.factKeys("이 연구는 이미지 채우기 모델을 4.4절에 적용한다. 표 12와 그림 3.2에 있다.", terms)).isEmpty();
    }

    @Test
    void 같은_쪽_앞_컷에서_한_말을_되풀이하면_다시_쓰게_하고_다시_써도_바로_앞_대사를_되풀이한_대사는_뺀다() {
        // 199번 1쪽 5·6컷: 냥박사가 거의 같은 말을 두 번 했다 (유사도 0.64)
        List<LineRules.Line> before = List.of(new LineRules.Line("냥박사", "잠재 공간에서 확산 모델을 훈련시키면, 복잡도는 줄이고 디테일은 살리는 최적점을 찾을 수 있거든."));
        List<LineRules.Line> lines = List.of(
                new LineRules.Line("냥박사", "확산 모델을 잠재 공간에서 훈련시키면, 복잡도는 줄이면서도 디테일을 살리는 최적의 지점을 찾을 수 있거든."),
                new LineRules.Line("콩이", "그럼 그 공간은 어떻게 만들어요?"),
                new LineRules.Line("해설", "잠재 공간에서 학습하면 복잡도는 줄이고 디테일은 살린다."));

        assertThat(LineRules.earlierLineProblems(lines, before)).singleElement().asString().contains("이번 쪽 앞 컷 대사");
        assertThat(LineRules.withoutRepeats(lines, before)).extracting(LineRules.Line::speaker).containsExactly("콩이", "해설");
    }

    @Test
    void 냥박사_답이_없는_콩이_질문과_묻기만_하는_콩이를_잡고_다시_써도_남은_답_없는_질문은_뺀다() {
        // 202번 1쪽 4컷: 냥박사 설명 뒤 콩이 질문으로 끝나 답이 없었다. 콩이 대사 20개 중 18개가 물음표
        List<LineRules.Line> hanging = List.of(new LineRules.Line("냥박사", "그 모델로 5만 장을 만들면 약 5일이 걸려."),
                new LineRules.Line("콩이", "와... 그만큼 계산 자원을 많이 써야 한다는 뜻인가요?"),
                new LineRules.Line("해설", "5만 장을 만드는 데 약 5일이 걸린다."));
        List<LineRules.Line> answered = List.of(new LineRules.Line("콩이", "그만큼 오래 걸려요?"),
                new LineRules.Line("냥박사", "응. 5일이나 걸려."), new LineRules.Line("콩이", "그럼 저 같은 학생은 시도하기 어렵겠네요."));

        assertThat(LineRules.unansweredProblems(hanging)).singleElement().asString().contains("질문", "뒤에 냥박사의 답이 없다");
        assertThat(LineRules.unansweredProblems(answered)).isEmpty();
        assertThat(LineRules.studentOnlyAsksProblems(hanging)).singleElement().asString().contains("묻기만 한다");
        assertThat(LineRules.studentOnlyAsksProblems(answered)).isEmpty();
        assertThat(LineRules.withoutUnansweredQuestions(hanging)).extracting(LineRules.Line::speaker).containsExactly("냥박사", "해설");
        assertThat(LineRules.withoutUnansweredQuestions(answered)).isEqualTo(answered);
        // 203번 1쪽 5컷: 냥박사 답이 빠져 콩이 질문 두 개가 이어졌다
        List<LineRules.Line> twoQuestions = List.of(new LineRules.Line("콩이", "반복 평가도 똑같이 느린 건가요?"),
                new LineRules.Line("콩이", "5만 장에 약 5일이나 걸린다는 건가요?"), new LineRules.Line("냥박사", "그렇지. 반복 평가 자체가 무거워."));
        assertThat(LineRules.unansweredProblems(twoQuestions)).singleElement().asString().contains("반복 평가도 똑같이 느린 건가요?");
        assertThat(LineRules.withoutUnansweredQuestions(twoQuestions)).extracting(LineRules.Line::text)
                .containsExactly("5만 장에 약 5일이나 걸린다는 건가요?", "그렇지. 반복 평가 자체가 무거워.");
    }

    @Test
    void 냥박사가_저자처럼_우리라고_말하면_다시_쓰게_하고_사실과_다른_내용만_말한_냥박사_대사는_뺀다() {
        // 204번 2쪽 3컷: 다른 연구(VQ-VAE)의 방법을 냥박사가 "우리는 자기회귀 모델을 써서 …"
        assertThat(LineRules.authorVoiceProblems(List.of(new LineRules.Line("냥박사", "그게 핵심이야. 우리는 자기회귀 모델을 써서 사전 분포를 학습시키거든."))))
                .singleElement().asString().contains("저자처럼");
        assertThat(LineRules.authorVoiceProblems(List.of(new LineRules.Line("냥박사", "우리가 보는 그림에는 작은 점이 아주 많아.")))).isEmpty();
        assertThat(LineRules.authorVoiceProblems(List.of(new LineRules.Line("콩이", "우리는 모델을 훈련시켜 볼까요?")))).as("콩이는 보지 않는다").isEmpty();
        // 205번: 좁은 검사를 빠져나간 저자 말투
        List<LineRules.Line> author = List.of(new LineRules.Line("냥박사", "우리가 사용한 모델은 LAION-400M이라는 데이터셋에서 훈련했어."),
                new LineRules.Line("냥박사", "CelebA-HQ 데이터셋에서 우리가 새로운 최고 수준인 FID 점수 5.11을 기록했거든."),
                new LineRules.Line("냥박사", "우리는 압축 수준을 선택할 자유가 있어."),
                new LineRules.Line("냥박사", "교차 주의층 덕분에 우리가 상상하지 못했던 방식으로 그림을 만들 수 있어."));
        assertThat(LineRules.authorVoiceProblems(author)).hasSize(3);
        assertThat(LineRules.withoutAuthorVoice(author)).extracting(LineRules.Line::text).containsExactly(
                "연구자들이 사용한 모델은 LAION-400M이라는 데이터셋에서 훈련했어.",
                "CelebA-HQ 데이터셋에서 연구자들이 새로운 최고 수준인 FID 점수 5.11을 기록했거든.",
                "연구자들은 압축 수준을 선택할 자유가 있어.",
                "교차 주의층 덕분에 우리가 상상하지 못했던 방식으로 그림을 만들 수 있어.");
        // 205번 4쪽 3컷: 냥박사 대사가 빠지자 콩이가 같은 말을 연달아 했다
        assertThat(LineRules.withoutInnerRepeats(List.of(
                new LineRules.Line("콩이", "그렇다면, 고정밀도가 정말 중요할 때 잠재 공간 확산 모델을 쓰는 건 의문스러울 수 있겠네요."),
                new LineRules.Line("콩이", "즉, 아주 높은 정밀도를 요구하는 작업에서는 잠재 공간 확산 모델의 사용 자체가 의문시될 수 있다는 뜻이군요."),
                new LineRules.Line("해설", "고정밀도가 필요할 때 사용은 의문스러울 수 있다.")))).extracting(LineRules.Line::speaker).containsExactly("콩이", "해설");

        // 204번 1쪽 6컷: 사실은 '5만 장에 약 5일'(추론)인데 냥박사는 같은 문장의 '수백 GPU 날'(학습)을 말했다
        String fact = "단일 A100 GPU에서 50k 샘플을 생성하는 데는 약 5일이 걸린다.";
        List<LineRules.Line> lines = List.of(new LineRules.Line("콩이", "그럼 그림을 만드는 것도 느린가요?"),
                new LineRules.Line("냥박사", "가장 강력한 확산 모델들을 훈련하는 데는 수백 개의 GPU 날이 걸리기도 해."),
                new LineRules.Line("콩이", "정말 오래 걸리네요."));
        assertThat(LineRules.withoutOffFactDoctorLines(lines, fact, List.of())).extracting(LineRules.Line::speaker).containsExactly("콩이", "콩이");
        assertThat(LineRules.withoutUnansweredQuestions(LineRules.withoutOffFactDoctorLines(lines, fact, List.of())))
                .as("답이 빠진 질문도 뺀다").extracting(LineRules.Line::text).containsExactly("정말 오래 걸리네요.");
        List<LineRules.Line> onFact = List.of(new LineRules.Line("냥박사", "A100 한 대로 50k 장을 만들면 5일쯤 걸려."));
        assertThat(LineRules.withoutOffFactDoctorLines(onFact, fact, List.of())).isEqualTo(onFact);
    }
}
