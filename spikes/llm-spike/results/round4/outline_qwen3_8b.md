# 고해상도 이미지 생성의 마법을 만든 '잠재공간 확산 모델'
모델: `qwen3:8b`

## 구조도

- 1쪽 · **왜 이 연구가 필요한가?** [1~7] — 이전의 확산 모델은 계산 비용이 너무 많아 실용성이 낮았지만, 이 연구는 이를 해결할 새로운 방법을 제시한다.
- 1쪽 · **문제는 무엇인가?** [8~73] — 고해상도 이미지 생성은 계산 자원이 많아야 가능했지만, 이 연구는 이를 더 효율적으로 해결할 수 있다고 주장한다.
- 2쪽 · **어떻게 해결했는가?** [74~91] — 확산 모델을 잠재공간에서 실행함으로써 계산 비용을 줄이고도 이미지 품질을 유지할 수 있게 되었다.
- 2쪽 · **이해하기 쉬운 마법의 원리** [92~119] — 확산 모델이 어떻게 잠재공간에서 작동하는지, 그리고 이 과정에서 어떤 기술이 사용되는지 설명한다.
- 3쪽 · **실험 결과는 어땠는가?** [120~238] — 이미지 생성, 텍스트 기반 생성, 초해상도 복원 등 다양한 작업에서 최고 수준의 성능을 보여주었다.
- 4쪽 · **왜 중요한가? 한계는 무엇인가?** [239~260] — 이 연구는 생성 모델의 효율성과 유연성을 균형 있게 개선했지만, 여전히 한계가 존재한다.

## 1쪽 — 왜 이 연구가 필요한가?, 문제는 무엇인가?

**컷 1** — 도서관 책장 위에 놓인 컴퓨터와 화면. 누리는 화면을 응시하며 고민하는 표정. 박사님은 책상 위에 앉아 누리에게 손을 흔든다.
- **누리**: 박사님, 왜 이 연구가 필요한 거죠?
- **박사님**: 좋은 질문이야. 지금까지 사람들은 이미지 만들기 위해 컴퓨터에 엄청난 자원을 써야 했어.
  - 사실: 이전의 확산 모델은 픽셀 공간에서 작동하기 때문에 계산 비용이 매우 많고 추론도 비싸다.
  - 근거 [3] (p.1) However, since these models typically operate directly in pixel space, optimization of powerful DMs often consumes hundreds of GPU days and inference is expensive due to sequential evaluations.

**컷 2** — 컴퓨터 화면에 떠 있는 고해상도 이미지가 보인다. 누리는 화면을 바라보며 감탄하며 눈을 뜬다. 박사님은 설명을 하며 손을 가리킨다.
- **박사님**: 이렇게 세부적인 이미지를 만들려면 컴퓨터가 엄청나게 빠르고 강력해야 해.
- **누리**: 아, 그래서 컴퓨터가 너무 느려서 그런 거예요?
  - 사실: 고해상도 이미지 생성은 계산 자원이 많아야 가능하다.
  - 근거 [8] (p.1) Image synthesis is one of the computer vision fields with the most spectacular recent development, but also among those with the greatest computational demands.

**컷 3** — 책상 위에 놓인 노트와 수첩. 누리는 수첩을 잡고 흥미롭게 읽고 있다. 박사님은 책에서 정보를 읽으며 설명한다.
- **박사님**: 지금까지는 확률 기반 모델을 확장해서 이런 이미지를 만들고 있어.
- **누리**: 확률 기반 모델이란 뭐예요?
  - 사실: 현재 고해상도 이미지 생성은 확률 기반 모델을 확장해 처리한다.
  - 근거 [9] (p.1) Especially high-resolution synthesis of complex, natural scenes is presently dominated by scaling up likelihood-based models, potentially containing billions of parameters in autoregressive (AR) transformers [66,67].

**컷 4** — 화면에 GAN이 생성한 이미지가 보인다. 누리는 화면을 바라보며 의문을 품는 표정. 박사님은 설명을 하며 손을 가리킨다.
- **박사님**: GAN은 복잡한 데이터를 만들기에는 한계가 있어.
- **누리**: 그래서 이 연구가 필요했어요?
  - 사실: GAN은 복잡한 데이터를 생성하는 데 한계가 있다.
  - 근거 [10] (p.1) In contrast, the promising results of GANs [3,27,40] have been revealed to be mostly confined to data with comparably limited variability as their adversarial learning procedure does not easily scale to modeling complex, multi-modal distributions.

**컷 5** — 화면에 확산 모델이 생성한 이미지가 보인다. 누리는 감탄하며 화면을 응시하고 있다. 박사님은 화면을 가리키며 설명한다.
- **박사님**: 확산 모델은 이런 이미지를 만들기에는 효과적이야.
- **누리**: 그래서 이 연구는 확산 모델을 개선한 거예요?
  - 사실: 확산 모델은 고해상도 이미지 생성에 효과적이다.
  - 근거 [11] (p.1) Recently, diffusion models [82], which are built from a hierarchy of denoising autoencoders, have shown to achieve impressive Figure 1.

**컷 6** — 화면에 떠 있는 고해상도 이미지와 함께 계산 과정이 보인다. 누리는 화면을 바라보며 의문을 품는 표정. 박사님은 설명을 하며 손을 가리킨다.
- **박사님**: 하지만 확산 모델도 고해상도 이미지를 만들려면 엄청난 자원이 필요해.
- **누리**: 그래서 이 연구는 이 문제를 해결하려는 거예요?
  - 사실: 확산 모델은 고해상도 이미지 생성에 많은 계산 자원이 필요하다.
  - 근거 [21] (p.1) As an example, training the most powerful DMs often takes hundreds of GPU days (e.g. 150 -1000 V100 days in [15]) and repeated evaluations on a noisy version of the input space render also inference expensive, so that producing 50k samples takes approximately 5 days [15] on a single A100 GPU.

## 2쪽 — 어떻게 해결했는가?, 이해하기 쉬운 마법의 원리

**컷 1** — 박사님은 화면에 떠 있는 이미지 위로 손을 올리며, 누리는 화면을 바라보며 고개를 숙인다. 화면에는 저차원 공간에서 실행되는 확산 모델의 시각화가 보인다. 작은 점들이 연결되어 하나의 이미지로 변하는 과정이 보인다.
- **박사님**: 이거야, 누리야. 이 확산 모델은 고차원 이미지 공간 대신, 더 작은 잠재공간에서 실행되는데, 그래서 계산도 더 빠르지.
  - 사실: 확산 모델을 잠재공간에서 실행함으로써 계산 비용을 줄일 수 있다.
  - 근거 [77] (p.3) Such an approach offers several advantages: (i) By leaving the high-dimensional image space, we obtain DMs which are computationally much more efficient because sampling is performed on a low-dimensional space.

**컷 2** — 박사님은 화면을 가리키며, 누리는 화면에서 생성된 이미지에 눈을 뜨며 감탄한다. 이미지의 세부 사항이 잘 보이는 것을 확인한다. 화면에는 고해상도 이미지가 떠 있다.
- **누리**: 박사님! 이 이미지 정말 세부가 잘 나와요. 원본처럼 생겼어요!
  - 사실: 잠재공간에서 실행하는 확산 모델은 이미지 품질을 유지할 수 있다.
  - 근거 [90] (p.4) Hence, our compression model preserves details of x better (see Tab. 8).

**컷 3** — 화면에 노이즈가 섞인 이미지가 보인다. 박사님은 그 이미지 위로 손을 뻗으며, 누리는 화면을 주의 깊게 본다. 노이즈가 제거되는 과정이 보인다.
- **박사님**: 이 확산 모델은 데이터가 어떻게 생겼는지 배우려고 해. 노이즈를 제거하면서 데이터의 본질을 알아내는 거야.
  - 사실: 확산 모델은 데이터 분포 p(x)를 배우기 위해 일반적으로 분포된 변수를 점차적으로 노이즈를 제거하는 방식으로 설계되었다.
  - 근거 [92] (p.4) Diffusion Models [82] are probabilistic models designed to learn a data distribution p(x) by gradually denoising a normally distributed variable, which corresponds to learning the reverse process of a fixed Markov Chain of length T .

**컷 4** — 화면에는 이미지 생성을 위한 확산 모델의 구조가 보인다. 박사님은 그 구조를 가리키며, 누리는 화면을 주의 깊게 본다. 이미지 생성에 성공적인 모델이 보인다.
- **박사님**: 이미지 생성에서는 p(x)를 더 잘 배우기 위해, 이 모델이 특별하게 설계됐어.
  - 사실: 이미지 생성에서는 p(x)의 변분 하한을 재가중한 방식을 사용하는 모델이 가장 성공적이었다.
  - 근거 [93] (p.4) For image synthesis, the most successful models [15,30,72] rely on a reweighted variant of the variational lower bound on p(x), which mirrors denoising score-matching [85].

**컷 5** — 화면에 노이즈가 섞인 입력 x_t가 보인다. 박사님은 그 이미지 위로 손을 올리며, 누리는 화면을 주의 깊게 본다. 이미지가 점차 정리되는 과정이 보인다.
- **누리**: 박사님, 이 모델은 노이즈를 제거하면서 이미지를 정리하는 거예요?
  - 사실: 확산 모델은 입력 x_t의 노이즈 버전을 정리한 형태를 예측하는 denoising autoencoder 시퀀스로 해석된다.
  - 근거 [95] (p.4) T , which are trained to predict a denoised variant of their input x t , where x t is a noisy version of the input x.

**컷 6** — 화면에는 저차원 잠재공간에서 생성된 이미지가 보인다. 박사님은 화면을 가리키며, 누리는 화면을 감탄하며 본다. 이미지가 정리된 모습이 보인다.
- **박사님**: 이렇게 저차원 공간에서 훈련시키면, 이미지 생성도 더 효율적이지.
  - 사실: 확산 모델은 고차원 픽셀 공간 대신 저차원이고 계산 효율적인 잠재공간에서 생성 모델을 훈련시킬 수 있다.
  - 근거 [101] (p.4) Compared to the high-dimensional pixel space, this space is more suitable for likelihood-based generative models, as they can now (i) focus on the important, semantic bits of the data and (ii) train in a lower dimensional, computationally much more efficient space.

## 3쪽 — 실험 결과는 어땠는가?

**컷 1** — 화면에는 다양한 이미지가 채워진 화면이 보인다. 누리는 화면을 멍하니 바라보고 있다. 박사님은 누리의 어깨 위로 몸을 숙여 화면을 가리키며 이야기한다.
- **박사님**: 누리야, 이 이미지들이 얼마나 잘 만들어졌는지 알아? 이 모델은 지금까지의 기술을 모두 뛰어넘었어.
  - 사실: LDM-4와 LDM-8는 이미지 생성에서 최고 수준의 성능을 보여주었다.
  - 근거 [140] (p.5) On CelebA-HQ, we report a new state-of-the-art FID of 5.11, outperforming previous likelihood-based models as well as GANs.
  - 근거 [144] (p.6) CelebA-HQ 256 × 256 FFHQ 256 × 256 Method FID ↓ Prec.
  - 근거 [149] (p.6) ↑ Recall ↑ DDPM [30] 7.89 --ImageBART [21] 5.51 --ImageBART [21] 7.32 --DDPM [30] 4.9 --PGGAN [39] 6.42 --UDM [43] 4.57 --StyleGAN [41] 4.21 --StyleGAN [41] 2.35 0.59 0.48 StyleGAN2 [42] 3.86 --ADM [15] 1.90 0.66 0.51 ProjectedGAN [76] 1.59 0.61 0.44 ProjectedGAN [76] 1.52 0.61 0.34 LDM-8 * (ours, 200-s) 4.02 0.64 0.52 LDM-4 (ours, 200-s) 2.95 0.66 0.48 Table 1.

**컷 2** — 화면은 FID 점수를 표시하는 표로 바뀐다. 누리는 표를 응시하며 눈을 동그랗게 뜬다. 박사님은 표를 가리키며 설명한다.
- **박사님**: 이 표를 보면 LDM-4는 기존 모델보다 훨씬 좋은 성능을 보여. FID 점수가 낮아서 이미지가 더 자연스럽다.
  - 사실: LDM-4는 FID 점수에서 기존 모델보다 우수한 성능을 보여주었다.
  - 근거 [140] (p.5) On CelebA-HQ, we report a new state-of-the-art FID of 5.11, outperforming previous likelihood-based models as well as GANs.
  - 근거 [144] (p.6) CelebA-HQ 256 × 256 FFHQ 256 × 256 Method FID ↓ Prec.
  - 근거 [149] (p.6) ↑ Recall ↑ DDPM [30] 7.89 --ImageBART [21] 5.51 --ImageBART [21] 7.32 --DDPM [30] 4.9 --PGGAN [39] 6.42 --UDM [43] 4.57 --StyleGAN [41] 4.21 --StyleGAN [41] 2.35 0.59 0.48 StyleGAN2 [42] 3.86 --ADM [15] 1.90 0.66 0.51 ProjectedGAN [76] 1.59 0.61 0.44 ProjectedGAN [76] 1.52 0.61 0.34 LDM-8 * (ours, 200-s) 4.02 0.64 0.52 LDM-4 (ours, 200-s) 2.95 0.66 0.48 Table 1.

**컷 3** — 화면에는 텍스트를 입력하면 이미지가 생성되는 장면이 보인다. 누리는 텍스트를 입력하는 모습을 보며 감탄한다. 박사님은 화면을 가리키며 설명한다.
- **박사님**: 이 모델은 텍스트를 입력하면 그대로 이미지를 만들어내. 텍스트 기반 생성에서는 지금까지의 기술을 넘어섰어.
  - 사실: LDM-4는 텍스트 기반 생성 작업에서 최고 수준의 성능을 보여주었다.
  - 근거 [158] (p.6) Table 2. Evaluation of text-conditional image synthesis on the 256 × 256-sized MS-COCO [51] dataset: with 250 DDIM [84] steps our model is on par with the most recent diffusion [59] and autoregressive [26] methods despite using significantly less parameters.
  - 근거 [168] (p.7) For quantitative analysis, we follow prior work and evaluate text-to-image generation on the MS-COCO [51] validation set, where our model improves upon powerful AR [17,66] and GAN-based [109] methods, cf .
  - 근거 [169] (p.7) Tab. 2. We note that applying classifier-free diffusion guidance [32] greatly boosts sample quality, such that the guided LDM-KL-8-G is on par with the recent state-of-the-art AR [26] and diffusion models [59] for text-to-image synthesis, while substantially reducing parameter count.

**컷 4** — 화면에는 초해상도 이미지가 생성되는 장면이 보인다. 누리는 이미지의 세부적인 부분을 자세히 보고 있다. 박사님은 이미지를 가리키며 이야기한다.
- **박사님**: 이 모델은 초해상도 이미지도 자연스럽게 만들어내. 세부적인 부분도 정확하게 표현해내는 거야.
  - 사실: LDM-4는 초해상도 복원 작업에서 우수한 성능을 보여주었다.
  - 근거 [189] (p.7) We train on an input resolution of 256 2 (crops from 384 2 ) but find that our model generalizes to larger resolutions and can generate images up to the megapixel regime when evaluated in a convolutional manner (see Fig. 9).
  - 근거 [192] (p.7) 4.5 to generate large images between 512 2 and 1024 2 .
  - 근거 [199] (p.7) In a first experiment, we follow SR3 LDM-SR has advantages at rendering realistic textures but SR3 can synthesize more coherent fine structures.

**컷 5** — 화면에는 텍스트 기반 생성 결과를 비교하는 표가 보인다. 누리는 표를 응시하며 눈을 뜬다. 박사님은 표를 가리키며 설명한다.
- **박사님**: 이 표를 보면 LDM-4는 기존 모델보다 훨씬 더 자연스러운 이미지를 만들어내. 텍스트 기반 생성에서는 최고 수준이야.
  - 사실: LDM-4는 텍스트 기반 생성에서 기존 모델보다 우수한 성능을 보여주었다.
  - 근거 [158] (p.6) Table 2. Evaluation of text-conditional image synthesis on the 256 × 256-sized MS-COCO [51] dataset: with 250 DDIM [84] steps our model is on par with the most recent diffusion [59] and autoregressive [26] methods despite using significantly less parameters.
  - 근거 [168] (p.7) For quantitative analysis, we follow prior work and evaluate text-to-image generation on the MS-COCO [51] validation set, where our model improves upon powerful AR [17,66] and GAN-based [109] methods, cf .
  - 근거 [169] (p.7) Tab. 2. We note that applying classifier-free diffusion guidance [32] greatly boosts sample quality, such that the guided LDM-KL-8-G is on par with the recent state-of-the-art AR [26] and diffusion models [59] for text-to-image synthesis, while substantially reducing parameter count.

**컷 6** — 화면에는 FID 점수를 비교하는 표가 보인다. 누리는 표를 응시하며 감탄한다. 박사님은 표를 가리키며 이야기한다.
- **박사님**: 이 표를 보면 LDM-4는 FID 점수에서 기존 모델보다 훨씬 우수한 성능을 보여. 이미지가 더 정확하게 생성되는 거야.
  - 사실: LDM-4는 FID 점수에서 기존 모델보다 우수한 성능을 보여주었다.
  - 근거 [140] (p.5) On CelebA-HQ, we report a new state-of-the-art FID of 5.11, outperforming previous likelihood-based models as well as GANs.
  - 근거 [144] (p.6) CelebA-HQ 256 × 256 FFHQ 256 × 256 Method FID ↓ Prec.
  - 근거 [149] (p.6) ↑ Recall ↑ DDPM [30] 7.89 --ImageBART [21] 5.51 --ImageBART [21] 7.32 --DDPM [30] 4.9 --PGGAN [39] 6.42 --UDM [43] 4.57 --StyleGAN [41] 4.21 --StyleGAN [41] 2.35 0.59 0.48 StyleGAN2 [42] 3.86 --ADM [15] 1.90 0.66 0.51 ProjectedGAN [76] 1.59 0.61 0.44 ProjectedGAN [76] 1.52 0.61 0.34 LDM-8 * (ours, 200-s) 4.02 0.64 0.52 LDM-4 (ours, 200-s) 2.95 0.66 0.48 Table 1.

## 4쪽 — 왜 중요한가? 한계는 무엇인가?

**컷 1** — 화면에는 누리가 박사님과 함께 연구실의 책상 위에 놓인 컴퓨터 모니터를 바라보고 있다. 모니터에는 생성된 이미지들이 빠르게 스와이프하며 보인다. 누리는 화면을 응시하며 감탄하고 있다.
- **누리**: 박사님, 이걸로 인공지능이 사람처럼 그릴 수 있나요?
  - 사실: 학습 만화는 생성 모델의 효율성과 유연성을 개선했다.
  - 근거 [259] (p.9) We have presented latent diffusion models, a simple and efficient way to significantly improve both the training and sampling efficiency of denoising diffusion models without degrading their quality.

**컷 2** — 화면에는 박사님이 컴퓨터 키보드를 두드리는 모습이 보인다. 그 뒤에 누리가 박사님의 말을 듣고 고개를 끄덕이는 장면이 이어진다. 화면에는 생성 모델의 품질과 효율성 비교 표가 나타난다.
- **박사님**: 아, 이건 학습 만화가 말하는 거야. 인공지능이 이미지 만들 때, 품질은 떨어지지 않고, 만들 시간도 짧아졌어.
  - 사실: 학습 만화는 생성 모델의 품질을 저하시키지 않고 효율성을 높였다.
  - 근거 [259] (p.9) We have presented latent diffusion models, a simple and efficient way to significantly improve both the training and sampling efficiency of denoising diffusion models without degrading their quality.

**컷 3** — 화면에는 누리가 고개를 숙이며 박사님을 바라보며 걱정하는 표정을 짓고 있다. 박사님은 테이블 위에 놓인 이미지가 보이는 화면을 가리키며 설명하고 있다.
- **박사님**: 하지만, 너무 정밀한 작업에서는 한계가 있어. 예를 들어, 미세한 텍스처나 색상 차이를 정확히 복원하는 건 조금 어려워.
  - 사실: 학습 만화는 특정 작업에 필요한 세부적인 정확도를 제공하지 못할 수 있다.
  - 근거 [240] (p.9) Moreover, the use of LDMs can be questionable when high precision is required: although the loss of image quality is very small in our f = 4 autoencoding models (see Fig. 1), their reconstruction capability can become a bottleneck for tasks that require fine-grained accuracy in pixel space.

**컷 4** — 화면에는 박사님이 컴퓨터 화면을 가리키며 설명하는 모습이 보인다. 누리는 화면을 응시하며 이해하려는 표정을 짓고 있다. 화면에는 이미지 품질 손실이 작은데 복원 능력이 한계가 있음을 보여주는 그래프가 나타난다.
- **누리**: 그래도 이미지 품질은 거의 안 떨어지는데요, 박사님?
  - 사실: 학습 만화는 이미지의 품질 손실이 거의 없지만, 복원 능력이 한계가 있다.
  - 근거 [240] (p.9) Moreover, the use of LDMs can be questionable when high precision is required: although the loss of image quality is very small in our f = 4 autoencoding models (see Fig. 1), their reconstruction capability can become a bottleneck for tasks that require fine-grained accuracy in pixel space.

**컷 5** — 화면에는 누리가 박사님의 말을 듣고 고개를 끄덕이며, 박사님이 컴퓨터 화면을 가리키며 설명하는 장면이 이어진다. 화면에는 샘플링 과정이 GAN보다 느리다는 그래프가 보인다.
- **박사님**: 아, 그런데 샘플링 과정은 GAN보다 느리다는 점도 있어. 더 빠른 방법이 필요해.
  - 사실: 학습 만화는 샘플링 과정이 GAN보다 느리다.
  - 근거 [239] (p.9) Limitations While LDMs significantly reduce computational requirements compared to pixel-based approaches, their sequential sampling process is still slower than that of GANs.

**컷 6** — 화면에는 누리가 박사님을 바라보며 의심스러운 표정을 짓고 있다. 박사님이 테이블 위에 놓인 이미지가 보이는 화면을 가리키며 설명하고 있다.
- **박사님**: 그래서, 고정밀도 작업에서는 이 방법이 의심받을 수도 있어. 완벽한 복원이 필요할 때는 다른 방법을 고려해야 해.
  - 사실: 학습 만화는 고정밀도 작업에서는 사용이 의심받을 수 있다.
  - 근거 [240] (p.9) Moreover, the use of LDMs can be questionable when high precision is required: although the loss of image quality is very small in our f = 4 autoencoding models (see Fig. 1), their reconstruction capability can become a bottleneck for tasks that require fine-grained accuracy in pixel space.
