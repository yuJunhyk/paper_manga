package com.readmanga.backend.comic.plan;

import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperSentence;

import java.util.ArrayList;
import java.util.List;

/** 테스트용 논문: 4번 작업(Latent Diffusion 논문)과 같은 섹션 구성. 문장 내용은 가짜다. */
final class PaperFixture {

    /** {섹션 이름, 문장 수} — DB의 4번 작업과 같은 순서와 개수 (본문 260문장 + 부록 일부) */
    static final Object[][] SECTIONS = {
            {"Abstract", 7}, {"Introduction", 11}, {"Democratizing High-Resolution Image Synthesis", 30},
            {"Generative Models for Image Synthesis", 15}, {"Two-Stage Image Synthesis", 10}, {"Method", 6},
            {"Perceptual Image Compression", 12}, {"Latent Diffusion Models", 8},
            {"Generative Modeling of Latent Representations", 6}, {"Conditioning Mechanisms", 7}, {"R d×d i , W", 7},
            {"Experiments", 6}, {"On Perceptual Compression Tradeoffs", 12}, {"Image Generation with Latent Diffusion", 24},
            {"Transformer Encoders for LDMs", 14}, {"Convolutional Sampling", 21},
            {"Super-Resolution with Latent Diffusion", 21}, {"Inpainting with Latent Diffusion", 21},
            {"Limitations & Societal Impact", 20}, {"Conclusion", 2},
            {"A. Changelog", 13}, {"B. Detailed Information on Denoising Diffusion Models", 27},
    };

    private PaperFixture() {
    }

    static List<PaperSentence> sentences() {
        PaperJob job = new PaperJob("fixture.pdf");
        List<PaperSentence> sentences = new ArrayList<>();
        int number = 1;
        for (Object[] section : SECTIONS) {
            for (int i = 0; i < (int) section[1]; i++, number++) {
                String text = number % 25 == 0
                        ? "256 × 256 4.2 ↓ 3.1"  // 가끔 표 조각을 섞는다
                        : number == 181
                        ? "Comparison of a class-conditional ImageNet LDM with recent state-of-the-art methods."
                        : number == 239
                        ? "LDMs reduce computational requirements, but their sequential sampling is still slower than that of GANs."
                        : "Sentence " + number + " explains an important finding of the paper.";
                sentences.add(new PaperSentence(job, number, (String) section[0], number / 30 + 1, text));
            }
        }
        return sentences;
    }
}
