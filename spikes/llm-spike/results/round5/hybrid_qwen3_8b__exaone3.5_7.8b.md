# 고해상도 이미지 생성의 혁명: 잠재 공간 확산 모델
구조도·사실 고르기: `qwen3:8b` / 대사 쓰기: `exaone3.5:7.8b`

## 구조도

- 1쪽 · **왜 고해상도 이미지 생성이 필요했는가?** [1~7] — 현대 컴퓨터 비전의 핵심 기술인 이미지 생성은 높은 계산 자원을 요구하며, 이를 해결하기 위한 새로운 접근법이 필요했다.
- 1쪽 · **이미지 생성의 현재 문제는 무엇인가?** [8~18] — 기존 확산 모델은 이미지 생성에 최적화되어 있지만, 계산 비용이 너무 높아 대규모 적용이 어려웠다.
- 1쪽 · **고해상도 이미지 생성을 어떻게 개선할 수 있을까?** [19~48] — 잠재 공간을 활용해 확산 모델의 계산 비용을 줄이고, 이미지 품질을 유지할 수 있는 새로운 방법을 제안한다.
- 1쪽 · **확산 모델의 기본 원리와 한계** [49~63] — 확산 모델은 이미지 생성에 효과적이지만, 고해상도 이미지 생성에는 한계가 있었다.
- 2쪽 · **다단계 접근법으로 고해상도 생성 개선** [64~73] — 확산 모델과 다른 방법을 결합해 고해상도 이미지 생성을 효율적으로 수행할 수 있게 되었다.
- 2쪽 · **잠재 공간에서의 확산 모델 구현** [74~79] — 이미지 생성을 잠재 공간에서 수행해 계산 비용을 줄이고, 품질을 높일 수 있는 새로운 접근법을 소개한다.
- 2쪽 · **잠재 공간의 이미지 압축 기술** [80~91] — 감각 기반 압축 모델을 통해 이미지의 중요한 정보만을 유지하면서도, 계산 자원을 절약할 수 있다.
- 2쪽 · **확산 모델의 조건부 생성 기능** [92~99] — 텍스트나 바운딩 박스 등 다양한 조건을 활용해 이미지를 생성할 수 있는 새로운 기능을 소개한다.
- 2쪽 · **조건부 생성을 위한 트랜스포머 인코더** [100~105] — 트랜스포머 인코더를 도입해 다양한 조건을 처리할 수 있는 유연한 생성 모델을 개발했다.
- 3쪽 · **고해상도 이미지 생성 실험 결과 (1/3)** [106~161] — LDM을 활용해 고해상도 이미지 생성을 성공적으로 수행했으며, 다양한 실험 결과를 확인했다.
- 3쪽 · **고해상도 이미지 생성 실험 결과 (2/3)** [162~217] — LDM을 활용해 고해상도 이미지 생성을 성공적으로 수행했으며, 다양한 실험 결과를 확인했다.
- 4쪽 · **고해상도 이미지 생성 실험 결과 (3/3)** [218~260] — LDM을 활용해 고해상도 이미지 생성을 성공적으로 수행했으며, 다양한 실험 결과를 확인했다.

## 1쪽 — 왜 고해상도 이미지 생성이 필요했는가?, 이미지 생성의 현재 문제는 무엇인가?, 고해상도 이미지 생성을 어떻게 개선할 수 있을까?, 확산 모델의 기본 원리와 한계

**컷 1** — 누리가 컴퓨터 모니터 앞에 앉아 있는 실험실 장면. 모니터에는 복잡한 수식과 이미지 생성 과정이 표시되어 있다. 누리는 눈을 크게 뜨고 흥미진진하게 모니터를 바라본다.
- **누리**: 박사님, 고해상도 이미지 생성이 왜 이렇게 중요한 거죠? 요즘 사진이나 그래픽 작업에서 정말 중요한 것 같아요.
- **해설**: 고해상도 이미지는 마치 우리가 보는 세상을 더욱 세밀하고 정확하게 표현할 수 있게 해줍니다. 예를 들어, 의학 영상에서 미세한 세포 구조까지 볼 수 있게 되면 진단 정확도가 크게 향상되죠.
  - 사실: 이 연구는 고해상도 이미지 생성을 위해 잠재 확산 모델을 사용하였다.
  - 근거 [3] (p.1) However, since these models typically operate directly in pixel space, optimization of powerful DMs often consumes hundreds of GPU days and inference is expensive due to sequential evaluations.
  - 근거 [6] (p.1) By introducing cross-attention layers into the model architecture, we turn diffusion models into powerful and flexible generators for general conditioning inputs such as text or bounding boxes and high-resolution synthesis becomes possible in a convolutional manner.

**컷 2** — 박사님이 실험 장비 옆에서 누리를 바라보며 설명한다. 배경에는 다양한 이미지 생성 모델의 도표가 보인다.
- **박사님**: 맞아, 누리야. 고해상도 이미지 생성이 필요한 이유는 여러 분야에서 세밀한 디테일이 중요해졌기 때문이야. 예를 들어, 게임이나 영화에서 실제 같은 환경을 만들려면 고해상도가 필수지.
- **누리**: 그런데 현재 사용하는 모델들은 계산 비용이 너무 높아서 실용성이 떨어진다는 거죠?
- **해설**: 정확히 그렇습니다. 기존 확산 모델들은 이미지 생성에 최적화되어 있지만, 엄청난 계산 리소스를 필요로 해서 실제 적용이 어렵습니다.
  - 사실: 이 연구는 기존 확산 모델이 이미지 생성에 최적화되어 있지만 계산 비용이 너무 높아 대규모 적용이 어려웠다고 말하고 있다.
  - 근거 [8] (p.1) Image synthesis is one of the computer vision fields with the most spectacular recent development, but also among those with the greatest computational demands.

**컷 3** — 누리가 고개를 끄덕이며 생각에 잠겨 있고, 박사님은 노트를 펼치며 아이디어를 적는다. 실험실은 조용하고 집중된 분위기다.
- **박사님**: 그렇지, 누리야. 그래서 우리는 잠재 확산 모델을 사용해 보았어. 이 모델은 이미지 생성 과정에서 계산 비용을 크게 줄일 수 있단다.
- **누리**: 잠재 공간에서 학습한다니, 어떻게 그게 가능한 거죠?
- **해설**: 잠재 확산 모델은 이미지를 저차원의 잠재 공간으로 압축해 학습하므로, 계산 효율성이 향상되고 더 적은 자원으로도 고해상도 이미지를 생성할 수 있어요.
  - 사실: 잠재 확산 모델은 이미지 생성 시 계산 비용을 줄이고자 잠재 공간에서 학습한다.
  - 근거 [31] (p.2) Importantly, and in contrast to previous work [23,66], we do not need to rely on excessive spatial compression, as we train DMs in the learned latent space, which exhibits better scaling properties with respect to the spatial dimensionality.

**컷 4** — 박사님이 컴퓨터 화면을 가리키며 설명하고, 누리는 눈을 반짝이며 듣고 있다. 배경에는 복잡한 그래프와 모델 구조도가 보인다.
- **박사님**: 이 모델은 잠재 공간에서 학습하니까, 단일 네트워크 통과로도 효율적인 이미지 생성이 가능해. 마치 복잡한 레시피를 한 번에 완성하는 것 같지?
- **누리**: 와, 정말 놀랍네요! 그럼 품질은 유지하면서 비용도 줄일 수 있다는 거죠?
- **해설**: 정확히 그렇습니다. 품질을 유지하면서도 계산 비용을 크게 줄일 수 있어 실용성이 훨씬 높아집니다.
  - 사실: 잠재 확산 모델은 이미지 생성 시 단일 네트워크 통과로 효율적인 이미지 생성이 가능하다.
  - 근거 [32] (p.2) The reduced complexity also provides efficient image generation from the latent space with a single network pass.

**컷 5** — 누리가 흥분한 표정으로 박사님을 바라보며 질문한다. 박사님은 미소를 지으며 설명한다. 실험실은 밝고 활기찬 분위기다.
- **누리**: 그런데 여전히 고해상도 이미지 생성에서 한계가 있을 수 있잖아요? 어떻게 해결했나요?
- **박사님**: 맞아, 누리야. 기존 확산 모델의 한계를 잠재 확산 모델로 극복했어. 고해상도 이미지에서도 효율적인 학습과 생성이 가능하게 했지.
- **해설**: 잠재 확산 모델은 고해상도 이미지 생성의 복잡성을 줄이면서도 품질을 유지하는 데 성공했어요.
  - 사실: 잠재 확산 모델은 이미지 생성 시 품질을 유지하면서도 계산 비용을 줄일 수 있다.
  - 근거 [38] (p.2) (i) In contrast to purely transformer-based approaches [23,66], our method scales more graceful to higher dimensional data and can thus (a) work on a compression level which provides more faithful and detailed reconstructions than previous work (see Fig. 1) and (b) can be efficiently We propose latent diffusion models (LDMs) as an effective generative model and a separate mild compression stage that only eliminates imperceptible details.

**컷 6 (연출)** — 장면 전환: 실험실에서 밖으로 나가는 컷. 누리와 박사님이 창문을 통해 바깥 풍경을 바라보며 대화를 이어간다.
- **누리**: 이런 기술이 발전하면 실제 세계에서도 얼마나 많은 변화가 일어날까요?
- **박사님**: 응, 상상만 해도 설레네요. 더 정확한 의료 진단부터 실감나는 가상 현실까지!

**컷 7** — 박사님이 실험 결과를 보여주는 화면을 가리키며 마무리한다. 누리는 흥미로운 표정으로 화면을 응시한다.
- **박사님**: 이렇게 해서 우리는 고해상도 이미지 생성의 효율성과 품질을 동시에 향상시킬 수 있었어. 앞으로 더 많은 분야에서 활용될 수 있을 거야.
- **누리**: 와, 정말 대단하네요! 앞으로 어떤 변화가 있을지 기대됩니다.
- **해설**: 이 연구는 고해상도 이미지 생성 분야에 새로운 지평을 열어주었습니다.
  - 사실: 잠재 확산 모델은 고해상도 이미지 생성에 한계가 있는 확산 모델의 문제를 해결하였다.
  - 근거 [60] (p.3) Evaluating and optimizing these models in pixel space, however, has the downside of low inference speed and very high training costs.
  - 근거 [61] (p.3) While the former can be partially adressed by advanced sampling strategies [47,75,84] and hierarchical approaches [31,93], training on high-resolution image data always requires to calculate expensive gradients.
  - 근거 [62] (p.3) We adress both drawbacks with our proposed LDMs, which work on a compressed latent space of lower dimensionality.

## 2쪽 — 다단계 접근법으로 고해상도 생성 개선, 잠재 공간에서의 확산 모델 구현, 잠재 공간의 이미지 압축 기술, 확산 모델의 조건부 생성 기능, 조건부 생성을 위한 트랜스포머 인코더
남은 문제: 1번 컷의 대사에 번호 표시가 들어 있다: "물론이죠, 누리야. 생각해보자면, 마치 요리할 때 여러 재료를 적절히 섞어 맛을 극대화하는 것과 비슷해. 각" / 2번 컷의 대사에 번호 표시가 들어 있다: "쉽게 말해, 이미지를 매우 큰 퍼즐 조각들로 나누어 생각해봐. 각 조각이 복잡한 정보를 담고 있지만, 전체적" / 3번 컷의 대사에 번호 표시가 들어 있다: "그 비결은 감각 기반 손실이야. 마치 사진을 찍을 때 중요한 부분은 선명하게 유지하고 불필요한 부분은 약간 " / 4번 컷의 대사에 번호 표시가 들어 있다: "다운샘플링 비율을 적절히 조절하면 이미지의 구조를 유지하면서도 효율적인 압축이 가능해. 예를 들어, 사진을 " / 5번 컷의 대사에 번호 표시가 들어 있다: "맞아, 이건 조건부 생성의 힘이야. 마치 그림을 그릴 때 특정 부분을 강조하거나 수정하고 싶을 때 가이드라인" / 6번 컷의 대사에 번호 표시가 들어 있다: "트랜스포머 인코더는 이미지의 복잡한 특성을 이해하고 유연하게 처리하는 데 탁월해. 마치 음악에서 다양한 악기"

**컷 1** — 누리가 컴퓨터 화면을 집중해서 바라보고 있는 실험실 장면. 벽에는 다양한 과학 실험 장비들이 보이고, 박사님은 옆에서 누리를 바라보며 설명하고 있다.
- **누리**: 박사님, 이 연구가 왜 다단계 접근법을 사용하게 되었는지 좀 더 자세히 설명해 주실 수 있을까요?
- **박사님**: 물론이죠, 누리야. 생각해보자면, 마치 요리할 때 여러 재료를 적절히 섞어 맛을 극대화하는 것과 비슷해. 각각의 방법이 가지고 있는 장점을 결합하면 더 맛있고 완성도 높은 요리를 만들 수 있잖아? () 마찬가지로, 고해상도 이미지 생성에서도 여러 기법을 결합해 효율성을 극대화하는 거야.
- **해설**: 다단계 접근법은 여러 기술의 강점을 결합하여 이미지 생성 과정을 최적화하고, 결과적으로 더 빠르고 품질 높은 이미지를 만들어낼 수 있게 돕습니다.
  - 사실: 이 연구는 다단계 접근법을 통해 확산 모델과 다른 방법을 결합해 고해상도 이미지 생성을 효율적으로 수행할 수 있게 되었다.
  - 근거 [64] (p.3) To mitigate the shortcomings of individual generative approaches, a lot of research [11,23,67,70,101,103] has gone into combining the strengths of different methods into more efficient and performant models via a two stage approach.

**컷 2 (연출)** — 장면 전환: 실험실에서 밖으로 나와 공원으로 이동. 누리와 박사님이 벤치에 앉아 하늘을 바라보며 이야기를 나눈다.
- **박사님**: 이런 연구들이 결국 우리 일상에서 어떻게 활용될 수 있을까 생각해보면 정말 멋지지 않나?
- **누리**: 아, 그럼 미래에는 더 정교하고 조건에 맞는 이미지들이 많이 활용될 수 있겠네요! 정말 흥미롭네요!

**컷 3** — 화면이 바뀌어 잠재 공간에서의 확산 모델 작동 원리를 시각화한 그래픽이 나타난다. 박사님은 누리에게 손가락으로 그래픽을 가리키며 설명한다.
- **누리**: 잠재 공간에서 이미지 생성을 한다는 건 어떤 의미인가요? 이해하기 어렵네요.
- **박사님**: 쉽게 말해, 이미지를 매우 큰 퍼즐 조각들로 나누어 생각해봐. 각 조각이 복잡한 정보를 담고 있지만, 전체적으로는 큰 그림을 만드는 거야. () 이렇게 하면 계산 비용을 줄이면서도 품질을 높일 수 있어. 마치 큰 그림을 작은 부분으로 나누어 그리는 것처럼 효율적이지.
- **해설**: 잠재 공간에서의 이미지 생성은 계산 효율성을 향상시키고, 중요한 시각적 특징을 유지하면서 이미지 품질을 높이는 방법입니다.
  - 사실: 이 연구는 이미지 생성을 잠재 공간에서 수행해 계산 비용을 줄이고 품질을 높이는 방법을 제안한다.
  - 근거 [74] (p.3) To lower the computational demands of training diffusion models towards high-resolution image synthesis, we observe that although diffusion models allow to ignore perceptually irrelevant details by undersampling the corresponding loss terms [30], they still require costly function evaluations in pixel space, which causes huge demands in computation time and energy resources.
  - 근거 [75] (p.3) We propose to circumvent this drawback by introducing an explicit separation of the compressive from the generative learning phase (see Fig. 2).
  - 근거 [76] (p.3) To achieve this, we utilize an autoencoding model which learns a space that is perceptually equivalent to the image space, but offers significantly reduced computational complexity.

**컷 4** — 화면에는 이미지 압축 과정을 보여주는 그래픽이 나타나고, 박사님은 압축 과정을 설명하며 누리에게 미소를 지으며 말한다.
- **누리**: 이미지 압축하면서도 중요한 정보는 어떻게 유지할 수 있나요?
- **박사님**: 그 비결은 감각 기반 손실이야. 마치 사진을 찍을 때 중요한 부분은 선명하게 유지하고 불필요한 부분은 약간 흐리게 처리하는 것과 같아. () 이렇게 하면 중요한 디테일은 보존되고 흐릿함을 피할 수 있어.
- **해설**: 감각 기반 손실은 이미지의 중요한 구조를 유지하면서 불필요한 노이즈를 제거해 선명도를 보장합니다.
  - 사실: 이 연구는 감각 기반 손실을 사용해 이미지의 중요한 정보를 유지하면서도 흐릿해지는 것을 방지한다.
  - 근거 [81] (p.3) This ensures that the reconstructions are confined to the image manifold by enforcing local realism and avoids bluriness introduced by relying solely on pixel-space losses such as L 2 or L 1 objectives.

**컷 5** — 화면에는 이미지 다운샘플링 과정을 보여주는 그래픽이 나타나고, 박사님은 손가락으로 다운샘플링 비율을 가리키며 설명한다.
- **누리**: 다운샘플링 비율을 조절하면 어떤 효과가 있나요?
- **박사님**: 다운샘플링 비율을 적절히 조절하면 이미지의 구조를 유지하면서도 효율적인 압축이 가능해. 예를 들어, 사진을 축소할 때 중요한 특징을 잃지 않도록 적절한 비율로 자르는 것과 비슷해. () 여기서는 다양한 다운샘플링 비율을 실험해 최적의 결과를 찾고 있어.
- **해설**: 다운샘플링 비율 조절은 압축 효율성과 이미지 품질 사이의 균형을 맞추는 데 중요합니다.
  - 사실: 이 연구는 이미지를 압축할 때 이미지의 구조를 유지하기 위해 다운샘플링 비율을 조절한다.
  - 근거 [83] (p.4) Importantly, the encoder downsamples the image by a factor f = H/h = W/w, and we investigate different downsampling factors f = 2 m , with m ∈ N.

**컷 6** — 화면에는 텍스트와 바운딩 박스가 포함된 이미지 생성 예시가 나타나고, 박사님은 이를 가리키며 설명한다. 누리는 눈을 크게 뜨고 감탄한다.
- **누리**: 텍스트나 바운딩 박스를 사용해서 이미지를 생성할 수 있다니 정말 신기하네요!
- **박사님**: 맞아, 이건 조건부 생성의 힘이야. 마치 그림을 그릴 때 특정 부분을 강조하거나 수정하고 싶을 때 가이드라인을 주는 것과 같아. () 이렇게 하면 원하는 조건에 맞는 이미지를 더 정확하게 만들 수 있어.
- **해설**: 조건부 생성은 특정 조건을 반영해 이미지를 생성하는 능력을 향상시킵니다.
  - 사실: 잠재 확산 모델은 텍스트나 바운딩 박스 같은 조건을 활용해 이미지를 생성할 수 있다.
  - 근거 [92] (p.4) Diffusion Models [82] are probabilistic models designed to learn a data distribution p(x) by gradually denoising a normally distributed variable, which corresponds to learning the reverse process of a fixed Markov Chain of length T .

**컷 7** — 화면에는 트랜스포머 인코더 구조를 시각화한 그래픽이 나타나고, 박사님은 누리에게 트랜스포머의 중요성을 설명하며 미소 짓는다.
- **누리**: 트랜스포머 인코더가 어떻게 이미지 생성에 도움이 되는지 궁금해요.
- **박사님**: 트랜스포머 인코더는 이미지의 복잡한 특성을 이해하고 유연하게 처리하는 데 탁월해. 마치 음악에서 다양한 악기의 소리를 조화롭게 섞는 것처럼, 이미지의 다양한 요소를 효과적으로 결합해 () 더 정교한 이미지를 생성할 수 있게 돕는 거야.
- **해설**: 트랜스포머 인코더는 이미지 특성에 맞는 유연한 생성 모델을 구축하는 데 핵심적인 역할을 합니다.
  - 사실: 이 연구는 이미지 특성에 맞는 유연한 생성 모델을 만들기 위해 트랜스포머 인코더를 도입했다.
  - 근거 [104] (p.4) The neural backbone θ (•, t) of our model is realized as a time-conditional UNet [71].

## 3쪽 — 고해상도 이미지 생성 실험 결과 (1/3), 고해상도 이미지 생성 실험 결과 (2/3)

**컷 1** — 누리와 박사님이 실험실의 대형 모니터 앞에 앉아 있다. 모니터에는 고해상도 이미지 생성 과정의 시뮬레이션 결과가 보인다. 박사님은 흥미로운 표정으로 누리를 바라본다. 누리는 눈을 크게 뜨고 있다.
- **누리**: 박사님, LDM을 활용해서 고해상도 이미지를 만들어내셨군요! 정말 놀랍네요! 어떻게 이렇게 선명하게 만들 수 있었나요?
- **박사님**: 응, 누리야! LDM은 마치 큰 그림을 작은 조각들로 나누어 천천히 복원하는 것 같아. 예를 들어, 큰 사진을 여러 단계로 축소해서 각 단계마다 정보를 추가하는 거야. 그렇게 하면 나중에 다시 원래 크기로 돌릴 때 훨씬 정교한 결과를 얻을 수 있어.
  - 사실: 이 연구는 LDM을 활용해 고해상도 이미지 생성을 성공적으로 수행했다.
  - 근거 [126] (p.5) This section analyzes the behavior of our LDMs with different downsampling factors f ∈ {1, 2, 4, 8, 16, 32} (abbreviated as LDM-f , where LDM-1 corresponds to pixel-based DMs).

**컷 2 (연출)** — 실험실 조명이 밝아지며, 누리가 흥분한 표정으로 박사님을 바라보는 장면으로 전환된다. 박사님은 그녀의 열정에 미소를 지으며 고개를 끄덕인다.
- **해설**: 이 연구는 고해상도 이미지 생성 분야에서 획기적인 발전을 이루었고, 앞으로 더 많은 혁신이 기대된다!

**컷 3** — 실험실 배경이 조금 어두워지며, 모니터 옆에는 다양한 실험 결과 그래프들이 보인다. 누리는 그래프를 주의 깊게 관찰하며 감탄한다.
- **누리**: 다양한 다운샘플링 요인을 사용하셨군요! 각 요인별로 어떤 차이가 있는지 자세히 알려주시겠어요?
- **박사님**: 물론이지! 다운샘플링 요인을 바꾸면 복원 과정이 달라져. 예를 들어, 큰 그림을 작은 종이에 그린 후 다시 확대하는 것처럼, 요인이 클수록 복원 과정이 더 정교해지지만 계산량도 늘어나지. 그래서 적절한 균형이 필요해.
  - 사실: 이 연구는 다양한 실험 결과를 통해 LDM의 효과를 확인했다.
  - 근거 [120] (p.5) LDMs provide means to flexible and computationally tractable diffusion based image synthesis of various image modalities, which we empirically show in the following.

**컷 4** — 모니터 옆에는 다른 모델들과 성능 비교 그래프가 보인다. 누리는 눈을 반짝이며 그래프를 바라본다. 박사님은 그녀의 반응을 보며 미소 짓는다.
- **누리**: 와, LDM이 다른 모델들보다 훨씬 우수한 성능을 보이네요! 특히 CelebA-HQ에서의 FID 점수가 정말 놀랍습니다!
- **박사님**: 그렇지! LDM은 다른 모델들보다 세밀한 표현력을 제공해. 마치 여러 요리사가 같은 레시피로 요리를 하지만, LDM은 그 중에서도 가장 정교하고 맛있는 요리를 만드는 요리사 같아.
  - 사실: 이 연구는 LDM의 성능을 다른 모델과 비교해 고해상도 이미지 생성에서 우수한 결과를 보였다.
  - 근거 [140] (p.5) On CelebA-HQ, we report a new state-of-the-art FID of 5.11, outperforming previous likelihood-based models as well as GANs.

**컷 5** — 화면이 텍스트 프롬프트 기반 이미지 생성 과정으로 전환된다. 누리는 화면을 주의 깊게 관찰하며 흥미롭게 반응한다. 박사님은 그녀에게 설명을 시작한다.
- **누리**: 텍스트 프롬프트를 사용해서 이미지를 생성하신다니 정말 신기해요! 언어가 어떻게 이미지를 만들어내는지 궁금해요.
- **박사님**: 음, 생각해봐. 언어는 그림을 그리는 데 필요한 모든 아이디어와 세부 사항을 담고 있는 지도 같아. 그 지도를 읽고 LDM이 그에 따라 정교한 이미지를 그려내는 거지.
  - 사실: 잠재 확산 모델은 언어 프롬프트를 기반으로 고해상도 이미지를 생성할 수 있다.
  - 근거 [163] (p.7) For textto-image image modeling, we train a 1.45B parameter KL-regularized LDM conditioned on language prompts on LAION-400M [78].

**컷 6** — 실험실의 분위기가 활기차게 바뀌며, 박사님은 컴퓨터 화면에 나타난 이미지를 보여주고 있다. 누리는 화면에 집중하며 감탄한다.
- **누리**: LDM-KL-8-G 모델이 다른 최신 모델들과 비슷한 성능을 내면서도 파라미터 수가 적다니 정말 효율적이네요!
- **박사님**: 그렇지! 이건 마치 작은 도구로 큰 작업을 해내는 장인 같아. 적은 자원으로도 최고의 결과를 내는 거지.
  - 사실: LDM-KL-8-G는 최근의 텍스트-이미지 생성 모델과 비슷한 성능을 보이면서 파라미터 수는 적다.
  - 근거 [169] (p.7) Tab. 2. We note that applying classifier-free diffusion guidance [32] greatly boosts sample quality, such that the guided LDM-KL-8-G is on par with the recent state-of-the-art AR [26] and diffusion models [59] for text-to-image synthesis, while substantially reducing parameter count.

**컷 7** — 누리는 텍스트-이미지 생성 실험 결과를 보여주는 그래프를 보고 있다. 박사님은 그녀 옆에서 격려하며 미소 짓는다.
- **누리**: 텍스트 프롬프트 기반 생성에서도 LDM이 다른 방법들보다 훨씬 뛰어나네요! 정말 놀라운 성과네요!
- **박사님**: 응, 누리야! LDM은 마치 이야기를 듣고 그림을 그리는 능력이 훨씬 향상된 예술가 같아. 텍스트의 의미를 정확히 이해하고 그에 맞는 이미지를 만들어내는 거지.
  - 사실: 이 연구는 텍스트-이미지 생성에서 다른 방법들보다 성능이 더 좋다.
  - 근거 [168] (p.7) For quantitative analysis, we follow prior work and evaluate text-to-image generation on the MS-COCO [51] validation set, where our model improves upon powerful AR [17,66] and GAN-based [109] methods, cf .

## 4쪽 — 고해상도 이미지 생성 실험 결과 (3/3)
버린 사실(근거가 틀리거나 겹침): LDM-1과 LDM-4는 KL과 VQ 정규화를 사용해 비교했다. / LDM-4는 디코딩 시 GPU 메모리 사용량을 줄였다.

**컷 1** — 누리는 컴퓨터 화면을 집중해서 바라보며, 그래프 위에 표시된 고해상도 이미지들이 눈에 띄게 선명해지는 모습을 확인하고 있다. 주변은 조용한 실험실 분위기로, 책장에는 과학 서적들이 꽂혀 있다.
- **누리**: 박사님, 이 결과를 보니 정말 놀랍네요! 고해상도 이미지 생성이 이렇게 잘 되다니요.
- **박사님**: 그렇지, 누리야. 마치 우리가 사진 속으로 들어간 것 같지 않니? (사진 속 세상이 더 선명해지는 것처럼 보이는 비유)
- **해설**: 이 그래프는 우리가 개발한 잠재 확산 모델이 고해상도 이미지 생성에서 얼마나 성공적이었는지 보여줍니다.
  - 사실: 잠재 확산 모델은 고해상도 이미지 생성을 성공적으로 수행했다.
  - 근거 [224] (p.8) In particular, we compare the inpainting efficiency of LDM-1 (i.e. a pixel-based conditional DM) with LDM-4, for both KL and VQ regularizations, as well as VQ-LDM-4 without any attention in the first stage (see Tab. 8), where the latter reduces GPU memory for decoding at high resolutions.

**컷 2** — 두 사람은 실험 데이터 테이블 옆에 앉아 있다. 테이블 위에는 다양한 모델 파라미터 설정을 나타내는 종이와 노트가 보인다. 박사님은 노트를 가리키며 설명하고 있다.
- **박사님**: 모든 모델이 같은 조건에서 테스트되었다는 걸 알고 있니? (노트를 가리키며) 이렇게 설정하면 공정하게 비교할 수 있단다.
- **누리**: 그렇군요! 그럼 모델 간 차이는 결국 성능 차이만으로 판단할 수 있겠네요.
- **해설**: 모든 모델의 파라미터 수를 동일하게 설정함으로써, 성능 차이만으로 결과를 분석할 수 있게 되었습니다.
  - 사실: 모든 모델의 파라미터 수는 동일하게 설정했다.
  - 근거 [225] (p.8) For comparability, we fix the number of parameters for all models.

**컷 3** — 실험실의 한쪽 벽에 큰 모니터가 설치되어 있고, 누리는 모니터 화면을 응시하며 속도 향상에 대해 놀라워하고 있다. 박사님은 옆에서 미소를 지으며 설명하고 있다.
- **누리**: 와, 256×256 해상도에서 생성 속도가 2.7배나 빨라졌다니 정말 대단해요!
- **박사님**: 그렇지, 마치 자전거를 타는 것처럼 처음에는 천천히 시작하지만, 기술이 쌓이면 훨씬 빠르게 달릴 수 있다는 거야 (자전거 타는 비유).
- **해설**: 256×256 해상도에서의 생성 속도가 2.7배나 빨라져 효율성이 크게 향상되었습니다.
  - 사실: 256×256 해상도에서 생성 속도는 2.7배 빨라졌다.
  - 근거 [227] (p.8) 6 reports the training and sampling throughput at resolution 256 2 and 512 2 , the total training time in hours per epoch and the FID score on the validation split after six epochs.

**컷 4 (연출)** — 실험실의 창문 너머로 햇살이 들어오며, 두 사람의 얼굴을 부드럽게 비추고 있다. 누리는 손을 흔들며 흥분을 표현하고, 박사님은 그녀의 어깨를 가볍게 두드리며 격려한다.
- **해설**: 이런 성과는 끊임없는 실험과 창의적인 접근법의 결과입니다.

**컷 5** — 누리는 눈을 크게 뜨고, 박사님의 설명을 경청하며 주의 기능이 포함된 모델의 성능 향상에 감탄하고 있다. 실험실은 약간 어두워지며, 조명 아래에서 두 사람의 표정이 강조된다.
- **누리**: 주의 기능이 들어간 모델이 FID 점수를 1.6배나 개선했다니, 정말 놀랍네요!
- **박사님**: 그렇지, 이건 마치 음악 연주에서 오케스트라의 각 악기가 조화롭게 연주되어 더 아름다운 음악을 만드는 것과 같아 (음악 연주 비유).
- **해설**: 주의 기능이 포함된 모델은 이미지의 질을 크게 향상시켜 FID 점수를 최소 1.6배 개선했습니다.
  - 사실: 주의 기능이 포함된 모델은 FID 점수를 1.6배 개선했다.
  - 근거 [228] (p.8) Overall, we observe a speed-up of at least 2.7× between pixel-and latent-based diffusion models while improving FID scores by a factor of at least 1.6×.
