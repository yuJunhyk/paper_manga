# 고해상도 이미지 합성: 잠재 확산 모델로 배우는 혁신
모델: `exaone3.5:7.8b`

## 1쪽 — 1쪽: 이미지 합성의 새로운 지평을 열다
참고 범위: [1~63]
남은 문제: 1번 컷의 대사에 문장 번호나 페이지 표시가 들어 있다. 대사에서 빼고 evidence에만 넣어라. / 2번 컷의 대사에 문장 번호나 페이지 표시가 들어 있다. 대사에서 빼고 evidence에만 넣어라. / 3번 컷의 대사에 문장 번호나 페이지 표시가 들어 있다. 대사에서 빼고 evidence에만 넣어라. / 6번 컷의 대사에 문장 번호나 페이지 표시가 들어 있다. 대사에서 빼고 evidence에만 넣어라.

**컷 1** — 누리가 실험실 책상 앞에 앉아 컴퓨터 모니터를 응시하고 있다. 모니터에는 복잡한 이미지 합성 과정을 보여주는 그래프와 코드가 표시되어 있다. 박사님이 옆에서 그녀를 바라보며 설명을 준비하고 있다.
- **박사님**: 누리야, 이미지 합성 분야에서 최근 혁신적인 발전이 있었어. 특히 '확산 모델'이라는 기술이 주목받고 있단다.
- **해설**: 확산 모델은 이미지를 단계적으로 정제하면서 합성하는 방식으로, 뛰어난 합성 결과를 보여주고 있어.
- **누리**: 와, 정말 놀랍네요! 어떻게 이렇게 정교한 결과를 얻을 수 있는 건가요?
  - 근거 [1] (p.1) By decomposing the image formation process into a sequential application of denoising autoencoders, diffusion models (DMs) achieve state-of-the-art synthesis results on image data and beyond.

**컷 2** — 박사님이 컴퓨터 화면을 가리키며, 누리는 눈을 크게 뜨고 흥미롭게 관찰하고 있다. 화면에는 기존 모델과 새로운 Latent Diffusion Models (LDMs)의 성능 비교 그래프가 보인다.
- **박사님**: 기존 모델들은 엄청난 컴퓨팅 자원을 필요로 했지만, LDMs는 잠재 공간에서 훈련함으로써 효율성을 크게 향상시켰어. 이로 인해 복잡한 이미지 합성도 훨씬 효율적으로 가능해졌단다.
- **해설**: 잠재 공간에서의 훈련은 이미지의 중요한 세부 정보를 유지하면서 데이터 차원을 줄여 효율성을 극대화해.
- **누리**: 그럼 성능은 그대로 유지되면서도 비용이 크게 줄어드는 거네요! 대단해요!
  - 근거 [4] (p.1) To enable DM training on limited computational resources while retaining their quality and flexibility, we apply them in the latent space of powerful pretrained autoencoders.

**컷 3** — 누리가 흥미로운 표정으로 컴퓨터 화면의 다양한 조건에 따른 합성 이미지 예시들을 살펴보고 있다. 박사님은 그녀 옆에서 추가 설명을 하고 있다.
- **박사님**: LDMs는 텍스트나 바운딩 박스 같은 다양한 조건에 따라 이미지를 생성할 수 있어. 이를 통해 텍스트를 이미지로 변환하거나 인페인팅, 초해상도 합성 등 다양한 작업에서 뛰어난 성능을 발휘할 수 있단다.
- **누리**: 와, 정말 다양한 작업에 적용 가능하다니요! 미래의 이미지 생성 기술에 큰 변화가 올 것 같아요.
- **해설**: LDMs는 유연성과 효율성을 동시에 제공하여 다양한 생성 작업에 최적화되어 있습니다.
  - 근거 [6] (p.1) By introducing cross-attention layers into the model architecture, we turn diffusion models into powerful and flexible generators for general conditioning inputs such as text or bounding boxes and high-resolution synthesis becomes possible in a convolutional manner.

**컷 4 (비유·연출)** — 박사님과 누리가 실험실에서 웃으며 이야기를 나누고 있다. 배경에는 복잡한 과학 도표와 함께 미래 지향적인 이미지 합성 기술의 가능성을 상징하는 그래픽 요소들이 보인다. 누리는 노트를 펼치며 필기하고 있다.
- **박사님**: 이런 기술의 발전은 이미지 합성 분야에 새로운 지평을 열어주고 있어. 앞으로 더 많은 혁신이 기대된단다, 누리야.
- **누리**: 네, 박사님! 정말 기대되네요. 함께 만들어가는 미래가 멋지겠어요!
- **해설**: 이미지 합성 기술의 발전은 창의적 작업과 기술적 응용 분야 모두에서 혁신을 이끌 것입니다.

**컷 5 (비유·연출)** — 누리가 창문 너머로 바라보며, 박사님은 그녀 옆에서 미래의 기술 발전을 향한 희망찬 눈빛으로 함께 바라보고 있다. 실험실은 밝은 조명 아래 미래 지향적인 분위기로 가득 차 있다.
- **박사님**: 우리의 연구가 더 많은 사람들에게 이 혁신적인 기술을 제공할 수 있기를 바라.
- **누리**: 네, 박사님! 함께 노력하면 가능할 거예요!
- **해설**: 이러한 혁신은 과학과 기술의 경계를 넓혀나갈 것이며, 더 많은 사람들에게 혜택을 줄 것입니다.

**컷 6** — 누리가 실험실 책상 위에 놓인 여러 실험 장비와 노트를 살펴보며 생각에 잠겨 있다. 박사님은 그녀 옆에서 조용히 지원하고 있다. 배경은 실험의 성공적인 결과를 상징하는 그래픽 요소들이 보인다.
- **박사님**: LDMs는 다양한 이미지 생성 작업에서 뛰어난 성능을 보여주며, 특히 고해상도 이미지 합성에서도 뛰어난 결과를 달성하고 있어. 이는 컴퓨팅 자원을 크게 줄이면서도 품질을 유지하는 데 성공한 사례야.
- **누리**: 와, 정말 대단해요! 이렇게 효율적이면서도 품질이 뛰어나다니요.
- **해설**: LDMs는 고해상도 이미지 합성에서 뛰어난 성능을 보여주며, 기존 모델들에 비해 훨씬 효율적인 컴퓨팅 자원 사용을 가능하게 합니다.
  - 근거 [7] (p.1) Our latent diffusion models (LDMs) achieve new state-of-the-art scores for image inpainting and class-conditional image synthesis and highly competitive performance on various tasks, including text-to-image synthesis, unconditional image generation and super-resolution, while significantly reducing computational requirements compared to pixel-based DMs.

## 2쪽 — 2쪽: 효율성과 품질의 균형점 찾기
참고 범위: [64~125]
남은 문제: 비유·연출 컷(analogy가 true)이 4개다. 2개 이하로 줄이고, 논문 내용을 말하는 컷은 analogy를 false로 하고 근거 번호를 넣어라.

**컷 1** — 실험실 책상 위, 여러 실험 장비와 노트가 놓여 있다.
- **누리**: 박사님, 이렇게 효율성과 품질 사이에서 균형을 맞추는 방법이 정말 신기해요! 어떻게 하는지 자세히 알려주시겠어요?
- **박사님**: 누리야, 맞아. 우리는 압축과 생성 과정을 분리하는 방식을 사용해. 이렇게 하면 모델이 효율적이면서도 품질을 유지할 수 있어.
  - 근거 [70] (p.3) Our work prevents such tradeoffs, as our proposed LDMs scale more gently to higher dimensional latent spaces due to their convolutional backbone.

**컷 2 (비유·연출)** — 박사님이 실험 장비를 조작하며 설명하는 모습
- **박사님**: 예를 들어, 이미지를 더 낮은 차원의 공간으로 압축하면 생성 모델이 중요한 정보에 집중할 수 있어. 마치 큰 숲에서 핵심적인 나무들만 골라내는 것 같아.
- **누리**: 그럼 세부 사항들이 덜 손실되는 건가요? 어떻게 그렇게 할 수 있죠?

**컷 3 (비유·연출)** — 박사님이 노트에 그림을 그리며 설명하는 모습
- **박사님**: 우리 모델은 UNet 구조를 활용해 이미지의 공간적 구조를 이해하고, 이로 인해 압축률을 낮추면서도 높은 품질의 이미지를 복원할 수 있어. 마치 복잡한 미로를 간단한 지도로 안내하는 것 같지.
- **누리**: UNet이 이미지 구조를 잘 이해하는 특별한 네트워크라는 거네요! 정말 멋지네요.

**컷 4 (비유·연출)** — 누리가 눈을 크게 뜨고 감탄하는 모습
- **누리**: 와, 텍스트 프롬프트 같은 조건을 이미지에 적용할 수 있다니! 정말 다양한 응용이 가능하겠어요.

**컷 5** — 박사님이 컴퓨터 모니터를 보며 데이터 분석 중
- **박사님**: 실험 결과를 보니, VQ 정규화된 공간에서 학습한 모델들이 때때로 더 높은 품질의 샘플을 생성하기도 해. 하지만 초기 압축 단계의 성능은 약간 떨어질 수 있어. (p. 3, 122)
- **누리**: 그럼 어떤 상황에서 어떤 모델을 선택해야 할까요?
  - 근거 [122] (p.5) Interestingly, we find that LDMs trained in VQregularized latent spaces sometimes achieve better sample quality, even though the reconstruction capabilities of VQregularized first stage models slightly fall behind those of their continuous counterparts, cf .

**컷 6 (비유·연출)** — 두 사람이 함께 결과 차트를 살펴보며 토론하는 모습
- **박사님**: 품질과 효율성의 균형은 구체적인 응용 사례에 따라 달라져. 하지만 우리 모델은 다양한 해상도에서도 우수한 성능을 보여줘. 마치 다양한 크기의 그릇에 물을 담아도 넘치지 않게 조절하는 것 같아.
- **누리**: 정말 대단해요! 이런 기술이 앞으로 어떻게 발전할지 기대됩니다.

## 3쪽 — 3쪽: 실험과 결과: 실제 적용 사례
참고 범위: [126~196]
남은 문제: 비유·연출 컷(analogy가 true)이 4개다. 2개 이하로 줄이고, 논문 내용을 말하는 컷은 analogy를 false로 하고 근거 번호를 넣어라.

**컷 1 (비유·연출)** — 누리와 박사님이 실험실 테이블 앞에 앉아 있으며, 큰 모니터 화면에 다양한 이미지 품질 비교 차트가 표시되어 있다. 누리는 눈을 크게 뜨고 흥미로운 표정으로 차트를 살펴보고 있다.
- **누리**: 와아, 정말 놀랍네요! LDM-4와 LDM-8이 픽셀 기반 모델보다 훨씬 더 높은 품질을 유지하면서도 빠르게 이미지를 생성하는 거네요!
- **박사님**: 그렇죠, 누리야. 이 모델들은 효율성과 품질 사이의 균형을 잘 맞추고 있어요. 특히 FID 점수 차이가 38점이나 되는 걸 보니, 실제 적용에서 얼마나 유용한지 알 수 있죠.

**컷 2** — 박사님이 차트 옆에 있는 노트북을 터치하며 설명하고 있다. 누리는 주의 깊게 듣고 있다.
- **박사님**: 이 차트는 CelebA-HQ와 ImageNet 데이터셋에서 모델 성능을 비교한 거예요. LDM-4와 -8이 다른 모델들보다 훨씬 낮은 FID 점수를 보여주며, 동시에 샘플링 속도도 빠르다는 걸 확인할 수 있어요.
- **누리**: 그럼 이건 실제 사진 생성 앱이나 게임에서도 이런 성능 차이가 크게 느껴지겠네요!
  - 근거 [126] (p.5) This section analyzes the behavior of our LDMs with different downsampling factors f ∈ {1, 2, 4, 8, 16, 32} (abbreviated as LDM-f , where LDM-1 corresponds to pixel-based DMs).
  - 근거 [128] (p.5) Tab. 8 shows hyperparameters and reconstruction performance of the first stage models used for the LDMs com-pared in this section.
  - 근거 [132] (p.5) LDM-{4-16} strike a good balance between efficiency and perceptually faithful results, which manifests in a significant FID [29] gap of 38 between pixel-based diffusion (LDM-1) and LDM-8 after 2M training steps.

**컷 3 (비유·연출)** — 누리가 흥미로운 표정으로 박사님의 노트북 화면을 가리키며 질문한다. 화면에는 다양한 이미지와 FID 점수가 비교되어 있다.
- **누리**: 그런데 이런 모델들이 복잡한 데이터셋에서도 잘 작동하는지 궁금해요. 예를 들어 ImageNet 같은 큰 데이터셋에서는 어떻게 될까요?
- **박사님**: 맞아요, 누리야. 실제로 ImageNet에서도 LDM-4와 -8이 효과적으로 작동해요. 특히 압축률을 적절히 조절함으로써 품질 저하를 최소화할 수 있어요.
  - 근거 [136] (p.5) Complex datasets such as ImageNet require reduced compression rates to avoid reducing quality.
  - 근거 [160] (p.6) Moreover, LDMs consistently improve upon GAN-based methods in Precision and Recall, thus confirming the advantages of their mode-covering likelihood-based training objective over adversarial approaches.

**컷 4** — 박사님이 차트 옆에 있는 펜으로 특정 부분을 강조하며 설명한다. 누리는 고개를 끄덕이며 이해하려고 노력한다.
- **박사님**: 그리고 이건 텍스트 조건 기반 이미지 생성 사례예요. BERT 토큰화를 이용해 언어 프롬프트를 모델에 입력하면, 다양한 텍스트에 따른 이미지 생성이 가능해져요.
- **누리**: 와, 텍스트로 이미지를 만드는 거네요! 정말 신기해요. 예를 들어 '하늘 아래 푸른 바다' 같은 문장으로 이미지를 만들 수 있겠네요!
  - 근거 [163] (p.7) For textto-image image modeling, we train a 1.45B parameter KL-regularized LDM conditioned on language prompts on LAION-400M [78].
  - 근거 [164] (p.7) We employ the BERT-tokenizer [14] and implement τ θ as a transformer [97] to infer a latent code which is mapped into the UNet via (multi-head) crossattention (Sec.
  - 근거 [166] (p.7) This combination of domain specific experts for learning a language representation and visual synthesis results in a powerful model, which generalizes well to complex, user-defined text prompts, cf .

**컷 5 (비유·연출)** — 누리가 흥분한 표정으로 박사님을 바라보며 추가 질문을 한다. 박사님은 친절하게 답변한다.
- **누리**: 그럼 이런 모델들이 실제 앱이나 웹사이트에 어떻게 적용될 수 있을까요? 예를 들어, 사용자가 텍스트를 입력하면 바로 이미지를 생성해주는 서비스 같은 거요?
- **박사님**: 정확히 그렇습니다, 누리야! 이런 기술은 AI 기반 이미지 생성 앱이나 웹사이트에서 사용자 친화적인 인터페이스로 구현될 수 있어요. 텍스트 입력만으로 즉시 고해상도 이미지를 생성할 수 있으니까요.

**컷 6 (비유·연출)** — 두 사람이 함께 미소 지으며 미래의 가능성을 바라보고 있다. 배경에는 다양한 이미지 생성 예시가 보이는 모니터가 있다.
- **박사님**: 이런 발전은 정말 무궁무진해요. 앞으로 더 많은 분야에서 이 기술이 활용될 거예요. 예를 들어, 디자인, 마케팅, 심지어 교육 분야까지도!
- **누리**: 정말 멋지네요! 과학이 이렇게 재미있고 유용한 거였다니까요! 앞으로 더 많은 걸 배우고 싶어졌어요.

## 4쪽 — 4쪽: 미래를 향한 한계와 가능성
참고 범위: [197~260]
남은 문제: 비유·연출 컷(analogy가 true)이 4개다. 2개 이하로 줄이고, 논문 내용을 말하는 컷은 analogy를 false로 하고 근거 번호를 넣어라.

**컷 1 (비유·연출)** — 누리와 박사님은 미래의 연구실을 배경으로 서 있다. 연구실 벽에는 다양한 이미지 생성 예시가 모니터에 띄워져 있다. 누리는 눈을 반짝이며 박사님의 설명을 듣고 있다.
- **누리**: 박사님, 이런 기술이 발전하면 앞으로 어떤 일들이 가능해질까요?
- **박사님**: 누리야, 정말 멋지지 않아? 미래에는 우리가 상상하는 것 이상으로 다양해질 거야. 예를 들어, 실시간으로 복잡한 이미지를 생성하거나, 손상된 이미지를 정교하게 복원할 수 있을 거야.

**컷 2** — 모니터에는 손상된 이미지와 복원된 이미지가 대비되어 보인다. 누리는 복원된 이미지를 자세히 살펴보고 감탄한다.
- **누리**: 와, 정말 놀랍네요! 손상된 부분이 이렇게 자연스럽게 복원될 수 있다니...
- **박사님**: 그렇지. 특히 inpainting 같은 작업에서 LDM-4 모델이 뛰어난 성능을 보여주는데, 사용자 연구 결과에서도 우리 모델이 선호되는 것으로 나타났어.
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.
  - 근거 [244] (p.9) 40-50% masked All samples Method FID ↓ LPIPS ↓ FID ↓ LPIPS ↓ LDM-4 (ours, big, w/ ft) 9.39 0.246± 0.042 1.50 0.137± 0.080 LDM-4 (ours, big, w/o ft) 12.89 0.257± 0.047 2.40 0.142± 0.085 LDM-4 (ours, w/ attn) 11.87 0.257± 0.042 2.15 0.144± 0.084 LDM-4 (ours, w/o attn) 12.60 0.259± 0.041 2.37 0.145± 0.084 LaMa [88] † 12.31 0.243± 0.038 2.23 0.134± 0.080 LaMa [88] 12.0 0.24 2.21 0.14 CoModGAN [107] 10.4 0.26 1.82 0.15 RegionWise [52] 21.3 0.27 4.75 0.15 DeepFill v2 [104] 22.1 0.28 5.20 0.16 EdgeConnect [58] 30.5 0.28 8.37 0.16

**컷 3** — 박사님이 모니터 옆에서 그래프와 표를 가리키며 설명한다. 누리는 주의 깊게 그래프를 관찰하고 있다.
- **박사님**: 이 그래프를 보면, 우리 모델이 FID 점수에서 SR3보다 우수하고, 사용자 선호도에서도 더 높은 점수를 받았어. 하지만 여전히 GAN 기반 모델들에 비해 샘플링 속도는 느려.
- **누리**: 그럼, 더 빠르게 이미지를 생성하려면 어떻게 해야 할까요?
  - 근거 [249] (p.9) enable various creative applications, and in particular approaches like ours that reduce the cost of training and inference have the potential to facilitate access to this technology and democratize its exploration.
  - 근거 [256] (p.9) GAN-based approaches, the extent to which our two-stage approach that combines adversarial training and a likelihood-based objective misrepresents the data remains an important research question.

**컷 4 (비유·연출)** — 박사님이 미소를 지으며 누리를 바라본다. 연구실의 배경은 미래적인 기술 장비로 가득 차 있다.
- **박사님**: 현재로선 GAN 기반 모델들에 비해 순차적 샘플링이 느리다는 한계가 있지만, 계속 연구를 통해 개선할 수 있을 거야. 그리고 이 기술이 사회에 미치는 영향도 중요하단다.
- **누리**: 사회적 영향이라니요? 어떤 영향을 말하는 건가요?

**컷 5 (비유·연출)** — 누리는 고개를 끄덕이며 박사님의 말을 경청한다. 배경에는 다양한 이미지 생성 예시와 함께 '딥 페이크'와 편향 문제에 대한 간단한 그래픽이 보인다.
- **박사님**: generative 모델은 창의적인 애플리케이션을 가능하게 하지만, 동시에 조작된 데이터나 가짜 정보를 쉽게 만들 수 있게 해서 문제도 생길 수 있어. 특히 딥 페이크 같은 문제는 심각해.
- **누리**: 그렇군요... 이런 기술을 안전하게 사용하려면 어떻게 해야 할까요?
  - 근거 [251] (p.9) In particular, the deliberate manipulation of images ("deep fakes") is a common problem in this context, and women in particular are disproportionately affected by it [13,24].
  - 근거 [254] (p.9) Finally, deep learning modules tend to reproduce or exacerbate biases that are already present in the data [22,38,91].

**컷 6 (비유·연출)** — 두 사람은 미래의 연구실에서 함께 미래를 향해 손을 맞잡고 있다. 배경은 끊임없이 변화하는 디지털 이미지로 가득 차 있다.
- **박사님**: 기술의 발전은 무궁무진해. 하지만 우리가 그 기술을 어떻게 사용할지에 대한 책임감도 중요해.
- **누리**: 그렇죠! 앞으로 더 많은 연구와 윤리적 고려가 필요하겠네요.
