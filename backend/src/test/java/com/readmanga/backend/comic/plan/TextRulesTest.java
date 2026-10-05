package com.readmanga.backend.comic.plan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 단계(Python)에서 정한 규칙과 기준값이 Java에서도 같게 나오는지 확인한다. */
class TextRulesTest {

    @Test
    void 한글_뒤에_영어가_붙은_단어만_섞인_단어로_잡는다() {
        assertThat(TextRules.mixedScriptWords("라tent Diffusion 모델")).isEqualTo("라tent");
        assertThat(TextRules.hasMixedScript("마asking 영역의 채우기")).isTrue();
        assertThat(TextRules.hasMixedScript("LDM은 GAN보다 느리고 UNet에 붙인다")).isFalse();
        assertThat(TextRules.hasMixedScript("1.45B 파라미터, f=8과 같은")).isFalse();
    }

    @Test
    void 수식_기호를_잡고_보통_괄호는_통과시킨다() {
        assertThat(TextRules.hasFormula("조건부 분포 p(z|y)를 모델링한다")).isTrue();
        assertThat(TextRules.hasFormula("데이터 분포 p(x)를 배운다")).isTrue();
        assertThat(TextRules.hasFormula("LDM(ours)은 빠르다")).isFalse();
        assertThat(TextRules.hasFormula("f=4 모델은 손실이 작다")).isFalse();
    }

    @Test
    void 숫자와_기호가_대부분인_표_조각을_잡는다() {
        assertThat(TextRules.isTableFragment("CelebA-HQ 256 × 256 FFHQ 256 × 256 Method FID ↓ Prec.")).isTrue();
        assertThat(TextRules.isTableFragment("Tab. 8.")).isTrue();
        assertThat(TextRules.isTableFragment("On CelebA-HQ, we report a new state-of-the-art FID of 5.11.")).isFalse();
    }

    @Test
    void 부록과_한계_결론_섹션을_알아본다() {
        assertThat(TextRules.isAppendixHeading("A. Changelog")).isTrue();
        assertThat(TextRules.isAppendixHeading("D.6. Super-Resolution")).isTrue();
        assertThat(TextRules.isAppendixHeading("R d×d i , W")).isFalse();
        assertThat(TextRules.isClosingSection("Limitations & Societal Impact")).isTrue();
        assertThat(TextRules.isClosingSection("Conclusion")).isTrue();
    }

    @Test
    void 비슷한_사실의_유사도가_시험_단계와_같다() {
        // 같은 근거, 다른 내용 → 0.35 미만이라 둘 다 남는다
        assertThat(TextRules.similarity("잠재 확산 모델은 픽셀 기반 모델에 비해 계산 요구가 적다.",
                "잠재 확산 모델은 GAN에 비해 샘플링 속도가 느리다.")).isCloseTo(0.24, org.assertj.core.data.Offset.offset(0.01));
        // 4차 FID 반복 → 0.55 이상이라 버린다
        assertThat(TextRules.similarity("LDM-4는 FID 점수에서 기존 모델보다 우수한 성능을 보여주었다.",
                "LDM-4는 텍스트 기반 생성에서 기존 모델보다 우수한 성능을 보여주었다.")).isCloseTo(0.61, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void 사실_문장의_번호를_지운다() {
        assertThat(TextRules.removeCitations("확산 모델은 뛰어나다 [11].")).isEqualTo("확산 모델은 뛰어나다.");
        assertThat(TextRules.removeCitations("효율을 높인다 [62, 63]")).isEqualTo("효율을 높인다");
    }

    @Test
    void 논문에_없는_영어_용어를_찾는다() {
        Set<String> paper = TextRules.englishWords(List.of(
                "We apply cross-attention based conditioning and convolutional sampling in the latent space of LDM."));

        assertThat(TextRules.unknownEnglishWords("트랜스포머와 Kernel Filtering을 결합한 Latent Space", paper))
                .containsExactly("Kernel", "Filtering");
        assertThat(TextRules.unknownEnglishWords("'Cross-Attention Based Conditioning' 문구와 LDM", paper)).isEmpty();
        assertThat(TextRules.unknownEnglishWords("LDMs와 latent spaces", paper)).as("복수형은 같은 낱말").isEmpty();
    }

    @Test
    void 표_그림_제목만_골라내고_표를_인용하는_본문_문장은_남긴다() {
        assertThat(TextRules.isCaption("Comparison of a class-conditional ImageNet LDM with recent state-of-the-art methods [12].")).isTrue();
        assertThat(TextRules.isCaption("Table 2. Evaluation of text-conditional image synthesis on the 256 × 256-sized MS-COCO dataset")).isTrue();
        assertThat(TextRules.isCaption("Tab. 2. We note that applying classifier-free diffusion guidance greatly boosts sample quality")).isFalse();
        assertThat(TextRules.isCaption("Tab. 8 shows hyperparameters and reconstruction performance of the first stage models")).isFalse();
        assertThat(TextRules.isCaption("Fig. 6 shows sample quality as a function of training progress")).isFalse();
    }

    @Test
    void 사실_끝의_괄호_근거_번호를_찾아_지운다() {
        assertThat(TextRules.hasCitation("잠재 확산 모델은 샘플링 속도가 느리다(239).")).isTrue();
        assertThat(TextRules.removeCitations("잠재 확산 모델은 샘플링 속도가 느리다(239, 240).")).isEqualTo("잠재 확산 모델은 샘플링 속도가 느리다.");
        assertThat(TextRules.removeCitations("FID가 1.6배(1.6x) 개선됐다 (239번)")).isEqualTo("FID가 1.6배(1.6x) 개선됐다");
    }
}
