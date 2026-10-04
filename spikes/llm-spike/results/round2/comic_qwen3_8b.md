# 고해상도 이미지 생성을 위한 잠재 공간 확산 모델
모델: `qwen3:8b`

## 1쪽 — 왜 이 연구가 필요했는가?
참고 범위: [1~7]

**컷 1** — 도서관 책장 앞. 누리는 책을 들고 놀라운 표정으로 박사님을 바라보고 있다. 박사님은 책을 가리키며 말한다.
- **박사님**: 누리야, 이 책은 이미지 생성을 더 빠르고 정확하게 만들기 위한 연구를 다룬 거야.
  - 근거 [1] (p.1) By decomposing the image formation process into a sequential application of denoising autoencoders, diffusion models (DMs) achieve state-of-the-art synthesis results on image data and beyond.
  - 근거 [2] (p.1) Additionally, their formulation allows for a guiding mechanism to control the image generation process without retraining.
  - 근거 [3] (p.1) However, since these models typically operate directly in pixel space, optimization of powerful DMs often consumes hundreds of GPU days and inference is expensive due to sequential evaluations.

**컷 2** — 책 페이지를 넘기는 누리와 박사님. 누리는 책을 읽으며 감탄한다.
- **누리**: 아! 이 연구는 사진을 만들 때 컴퓨터가 너무 느려요. 그걸 고쳤대요!
  - 근거 [3] (p.1) However, since these models typically operate directly in pixel space, optimization of powerful DMs often consumes hundreds of GPU days and inference is expensive due to sequential evaluations.

**컷 3** — 컴퓨터 화면을 보는 누리와 박사님. 누리는 화면을 바라보며 의문을 품는다.
- **누리**: 그럼 왜 이 연구가 필요했어요?

**컷 4** — 박사님은 책을 열고 설명을 시작한다. 누리는 집중해서 듣고 있다.
- **박사님**: 이전에는 사진을 만들 때 컴퓨터가 너무 느려서, 많은 시간과 자원이 필요했어.
  - 근거 [3] (p.1) However, since these models typically operate directly in pixel space, optimization of powerful DMs often consumes hundreds of GPU days and inference is expensive due to sequential evaluations.

**컷 5** — 박사님은 화면을 가리키며 설명을 이어간다. 누리는 화면을 바라보며 이해한다.
- **박사님**: 이 연구는 그런 문제를 해결하기 위해, 컴퓨터가 더 빠르게 사진을 만들 수 있도록 만들었어.
  - 근거 [4] (p.1) To enable DM training on limited computational resources while retaining their quality and flexibility, we apply them in the latent space of powerful pretrained autoencoders.
  - 근거 [5] (p.1) In contrast to previous work, training diffusion models on such a representation allows for the first time to reach a near-optimal point between complexity reduction and detail preservation, greatly boosting visual fidelity.

**컷 6** — 누리가 책을 열고 화면을 바라보며 감탄한다. 박사님은 미소 지으며 대답한다.
- **누리**: 정말 멋진 방법이에요! 이 연구는 정말 중요했어요!
  - 근거 [7] (p.1) Our latent diffusion models (LDMs) achieve new state-of-the-art scores for image inpainting and class-conditional image synthesis and highly competitive performance on various tasks, including text-to-image synthesis, unconditional image generation and super-resolution, while significantly reducing computational requirements compared to pixel-based DMs.

## 2쪽 — 문제와 해결 방법
참고 범위: [8~48]

**컷 1** — 누리가 책을 열고 화면을 바라보며 감탄한다. 박사님은 미소 지으며 대답한다.
- **누리**: 정말 멋진 방법이에요! 이 연구는 정말 중요했어요!
- **박사님**: 그렇죠! 이제 이 연구가 왜 필요했는지 설명해드릴게요.
  - 근거 [8] (p.1) Image synthesis is one of the computer vision fields with the most spectacular recent development, but also among those with the greatest computational demands.
  - 근거 [9] (p.1) Especially high-resolution synthesis of complex, natural scenes is presently dominated by scaling up likelihood-based models, potentially containing billions of parameters in autoregressive (AR) transformers [66,67].
  - 근거 [10] (p.1) In contrast, the promising results of GANs [3,27,40] have been revealed to be mostly confined to data with comparably limited variability as their adversarial learning procedure does not easily scale to modeling complex, multi-modal distributions.

**컷 2** — 박사님은 책을 가리키며 설명한다. 누리는 눈을 반짝이며 듣고 있다.
- **박사님**: 이 연구는 컴퓨터가 사진이나 그림을 만들 때 더 빠르고 효율적으로 만들기 위해 시작되었어요.
- **해설**: 이 연구는 이미지 생성 기술에서 문제가 되는 '계산량'을 줄이는 것이 목표였어요.
  - 근거 [11] (p.1) Recently, diffusion models [82], which are built from a hierarchy of denoising autoencoders, have shown to achieve impressive Figure 1.
  - 근거 [12] (p.1) Boosting the upper bound on achievable quality with less agressive downsampling.
  - 근거 [13] (p.1) Since diffusion models offer excellent inductive biases for spatial data, we do not need the heavy spatial downsampling of related generative models in latent space, but can still greatly reduce the dimensionality of the data via suitable autoencoding models, see Sec. 3. Images are from the DIV2K [1] validation set, evaluated at 512 2 px.

**컷 3** — 박사님은 화면에 보이는 복잡한 그림을 가리키며 설명한다. 누리는 고개를 끄덕이며 듣고 있다.
- **박사님**: 이전에는 사진을 만들기 위해 너무 많은 계산이 필요했어요. 그래서 많은 컴퓨터 자원이 필요했고, 환경에도 부담이 있었어요.
- **누리**: 아! 그래서 이 연구는 그 문제를 해결하려는 거예요?
  - 근거 [22] (p.2) This has two consequences for the research community and users in general: Firstly, training such a model requires massive computational resources only available to a small fraction of the field, and leaves a huge carbon footprint [65,86].
  - 근거 [23] (p.2) Secondly, evaluating an already trained model is also expensive in time and memory, since the same model architecture must run sequentially for a large number of steps (e.g. 25 -1000 steps in [15]).
  - 근거 [24] (p.2) To increase the accessibility of this powerful model class and at the same time reduce its significant resource consumption, a method is needed that reduces the computational complexity for both training and sampling.

**컷 4** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝인다.
- **박사님**: 이 연구는 이미지 생성을 더 쉽게 만들기 위해 '잠재 공간'이라는 새로운 방법을 사용했어요.
- **해설**: 잠재 공간은 데이터를 더 효율적으로 표현할 수 있는 공간으로, 계산량을 줄일 수 있어요.
  - 근거 [26] (p.2) Departure to Latent Space Our approach starts with the analysis of already trained diffusion models in pixel space: Fig. 2 shows the rate-distortion trade-off of a trained model.
  - 근거 [27] (p.2) As with any likelihood-based model, learning can be roughly divided into two stages: First is a perceptual compression stage which removes high-frequency details but still learns little semantic variation.
  - 근거 [28] (p.2) In the second stage, the actual generative model learns the semantic and conceptual composition of the data (semantic compression).

**컷 5** — 박사님은 화면에 보이는 그림을 가리키며 설명한다. 누리는 화면을 바라보며 흥미진진하게 듣고 있다.
- **박사님**: 이 방법은 이미지 생성을 위해 먼저 데이터를 압축하고, 그 압축된 데이터를 이용해 더 빠르게 만들 수 있어요.
- **누리**: 아! 그래서 이 연구는 계산이 더 적게 필요해졌어요?
  - 근거 [30] (p.2) Following common practice [11,23,66,67,96], we separate training into two distinct phases: First, we train an autoencoder which provides a lower-dimensional (and thereby efficient) representational space which is perceptually equivalent to the data space.
  - 근거 [31] (p.2) Importantly, and in contrast to previous work [23,66], we do not need to rely on excessive spatial compression, as we train DMs in the learned latent space, which exhibits better scaling properties with respect to the spatial dimensionality.
  - 근거 [32] (p.2) The reduced complexity also provides efficient image generation from the latent space with a single network pass.

**컷 6** — 박사님은 책을 펼치며 설명한다. 누리는 책을 읽으며 고개를 끄덕인다.
- **박사님**: 이 연구는 이미지 생성을 위한 새로운 방법을 제안했고, 그 방법은 이미지 생성뿐만 아니라 다른 작업에도 사용할 수 있어요.
- **해설**: 이 방법은 다양한 작업에 적용할 수 있어, 연구의 범위가 넓었어요.
  - 근거 [36] (p.2) For the latter, we design an architecture that connects transformers to the DM's UNet backbone [71] and enables arbitrary types of token-based conditioning mechanisms, see Sec. 3.3.
  - 근거 [37] (p.2) In sum, our work makes the following contributions:
  - 근거 [38] (p.2) (i) In contrast to purely transformer-based approaches [23,66], our method scales more graceful to higher dimensional data and can thus (a) work on a compression level which provides more faithful and detailed reconstructions than previous work (see Fig. 1) and (b) can be efficiently We propose latent diffusion models (LDMs) as an effective generative model and a separate mild compression stage that only eliminates imperceptible details.

## 3쪽 — 실험과 결과
참고 범위: [49~217]

**컷 1** — 박사님은 책을 펼치며 설명한다. 누리는 책을 읽으며 고개를 끄덕인다. 책의 페이지가 열려 있고, 그 위에 빈 공백이 보인다.
- **박사님**: 이 연구는 이미지 생성을 위한 새로운 방법을 제안했고, 그 방법은 이미지 생성뿐만 아니라 다른 작업에도 사용할 수 있어요.
  - 근거 [49] (p.2) The high dimensional nature of images presents distinct challenges to generative modeling.
  - 근거 [50] (p.2) Generative Adversarial Networks (GAN) [27] allow for efficient sampling of high resolution images with good perceptual quality [3,42], but are diffi-cult to optimize [2,28,54] and struggle to capture the full data distribution [55].
  - 근거 [51] (p.3) In contrast, likelihood-based methods emphasize good density estimation which renders optimization more well-behaved.

**컷 2** — 박사님은 책을 넘기며 설명한다. 누리는 책을 따라 읽으며 눈을 반짝인다. 책의 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다.
- **박사님**: 우리가 실험을 해봤어요. 이미지 생성을 더 빠르고 효율적으로 할 수 있게 했어요.
  - 근거 [63] (p.3) This renders training computationally cheaper and speeds up inference with almost no reduction in synthesis quality (see Fig. 1).
  - 근거 [101] (p.4) Compared to the high-dimensional pixel space, this space is more suitable for likelihood-based generative models, as they can now (i) focus on the important, semantic bits of the data and (ii) train in a lower dimensional, computationally much more efficient space.
  - 근거 [132] (p.5) LDM-{4-16} strike a good balance between efficiency and perceptually faithful results, which manifests in a significant FID [29] gap of 38 between pixel-based diffusion (LDM-1) and LDM-8 after 2M training steps.

**컷 3** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝이며 웃는다. 책의 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다.
- **박사님**: 이 실험 결과는, 우리가 이미지 생성을 더 빠르게 할 수 있다는 걸 보여줘요.
  - 근거 [63] (p.3) This renders training computationally cheaper and speeds up inference with almost no reduction in synthesis quality (see Fig. 1).
  - 근거 [101] (p.4) Compared to the high-dimensional pixel space, this space is more suitable for likelihood-based generative models, as they can now (i) focus on the important, semantic bits of the data and (ii) train in a lower dimensional, computationally much more efficient space.
  - 근거 [132] (p.5) LDM-{4-16} strike a good balance between efficiency and perceptually faithful results, which manifests in a significant FID [29] gap of 38 between pixel-based diffusion (LDM-1) and LDM-8 after 2M training steps.

**컷 4** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝이며 웃는다. 책의 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다.
- **박사님**: 이 방법은 이미지 생성뿐만 아니라 다른 작업에도 적용할 수 있어요. 정말 멋지죠?
  - 근거 [54] (p.3) Because pixel based representations of images contain barely perceptible, high-frequency details [16,73], maximum-likelihood training spends a disproportionate amount of capacity on modeling them, resulting in long training times.
  - 근거 [60] (p.3) Evaluating and optimizing these models in pixel space, however, has the downside of low inference speed and very high training costs.
  - 근거 [70] (p.3) Our work prevents such tradeoffs, as our proposed LDMs scale more gently to higher dimensional latent spaces due to their convolutional backbone.

**컷 5** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝이며 웃는다. 책의 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다.
- **박사님**: 이 실험은 이미지 생성을 더 빠르게 할 수 있다는 걸 보여줬어요. 정말 흥미롭죠?
  - 근거 [63] (p.3) This renders training computationally cheaper and speeds up inference with almost no reduction in synthesis quality (see Fig. 1).
  - 근거 [101] (p.4) Compared to the high-dimensional pixel space, this space is more suitable for likelihood-based generative models, as they can now (i) focus on the important, semantic bits of the data and (ii) train in a lower dimensional, computationally much more efficient space.
  - 근거 [132] (p.5) LDM-{4-16} strike a good balance between efficiency and perceptually faithful results, which manifests in a significant FID [29] gap of 38 between pixel-based diffusion (LDM-1) and LDM-8 after 2M training steps.

**컷 6** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝이며 웃는다. 책의 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다.
- **박사님**: 이 실험은 우리가 이미지 생성을 더 빠르게 할 수 있다는 걸 보여줬어요. 정말 멋지죠?
  - 근거 [63] (p.3) This renders training computationally cheaper and speeds up inference with almost no reduction in synthesis quality (see Fig. 1).
  - 근거 [101] (p.4) Compared to the high-dimensional pixel space, this space is more suitable for likelihood-based generative models, as they can now (i) focus on the important, semantic bits of the data and (ii) train in a lower dimensional, computationally much more efficient space.
  - 근거 [132] (p.5) LDM-{4-16} strike a good balance between efficiency and perceptually faithful results, which manifests in a significant FID [29] gap of 38 between pixel-based diffusion (LDM-1) and LDM-8 after 2M training steps.

## 4쪽 — 의미와 한계
참고 범위: [218~260]

**컷 1** — 도서관 책장 위에 책이 쌓여 있고, 누리는 책을 들고 있다. 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 눈을 반짝이며 웃는다.
- **박사님**: 이 실험은 우리가 이미지 생성을 더 빠르게 할 수 있다는 걸 보여줬어요. 정말 멋지죠?
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.

**컷 2** — 책 페이지가 열려 있고, 그 위에 실험 결과가 그려져 있다. 누리는 책을 읽으며 감탄한다.
- **누리**: 이렇게 빨리 만들 수 있다니요? 진짜 놀랍네요!
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.

**컷 3** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 고개를 끄덕인다. 책 페이지에는 '이미지 생성 속도 향상'이라는 제목이 보인다.
- **박사님**: 이 실험은 이미지 생성을 빠르게 할 수 있다는 걸 보여줬어요. 이전 방식보다 최소 2.7배 빠르게 만들 수 있죠.
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.

**컷 4** — 책 페이지가 열려 있고, 그 위에 FID 점수 비교 표가 그려져 있다. 누리는 표를 보며 눈을 반짝인다.
- **누리**: 이렇게 점수도 좋아요? 정말 잘 했어요!
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.
  - 근거 [230] (p.8) 7 shows that our model with attention improves the overall image quality as measured by FID over that of [88].

**컷 5** — 박사님은 책을 넘기며 설명한다. 누리는 책을 읽으며 고개를 끄덕인다. 책 페이지에는 '한계'라는 제목이 보인다.
- **박사님**: 하지만 이 방법도 한계가 있어요. 이미지가 너무 정밀하게 필요한 작업에서는 여전히 GAN 방식이 더 빠르고 정확해요.
  - 근거 [240] (p.9) Moreover, the use of LDMs can be questionable when high precision is required: although the loss of image quality is very small in our f = 4 autoencoding models (see Fig. 1), their reconstruction capability can become a bottleneck for tasks that require fine-grained accuracy in pixel space.

**컷 6** — 책 페이지가 열려 있고, 그 위에 '사회적 영향'이라는 제목과 함께 텍스트가 보인다. 누리는 책을 읽으며 고개를 끄덕인다.
- **박사님**: 이 기술은 창의적인 작업에 도움이 되지만, 동시에 허위 정보를 퍼뜨리는 위험도 있어요. 연구자들이 이 점을 잘 고려해야 해요.
  - 근거 [249] (p.9) enable various creative applications, and in particular approaches like ours that reduce the cost of training and inference have the potential to facilitate access to this technology and democratize its exploration.
  - 근거 [250] (p.9) On the other hand, it also means that it becomes easier to create and disseminate manipulated data or spread misinformation and spam.
