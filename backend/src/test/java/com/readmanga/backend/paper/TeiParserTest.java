package com.readmanga.backend.paper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeiParserTest {

    @Test
    void 문장_앞에_붙은_문단_소제목은_뗀다() {
        // 204번 2쪽 1컷: 'Departure to Latent Space'를 '잠재 공간으로 이동한다'는 내용으로 읽었다
        assertThat(TeiParser.withoutRunInHeading("Departure to Latent Space Our approach starts with the analysis of already trained diffusion models."))
                .isEqualTo("Our approach starts with the analysis of already trained diffusion models.");
        assertThat(TeiParser.withoutRunInHeading("Limitations While LDMs significantly reduce computational requirements."))
                .isEqualTo("While LDMs significantly reduce computational requirements.");
        // 보통 문장은 그대로 (Latent Diffusion 논문 383문장 중 2개, 다른 두 논문은 0개만 걸렸다)
        assertThat(TeiParser.withoutRunInHeading("In Fig. 2 we show the rate-distortion trade-off.")).isEqualTo("In Fig. 2 we show the rate-distortion trade-off.");
        assertThat(TeiParser.withoutRunInHeading("LDMs achieve new state-of-the-art scores.")).isEqualTo("LDMs achieve new state-of-the-art scores.");
    }
}
