# 차량 소음 분류 모델 학습 (외부 라이브러리 설치 없이)

`tflite-model-maker`는 유지보수가 끊긴 패키지라 최신 환경에서 의존성 충돌이
계속 나서 완전히 뺐습니다. 지금 방식은 **Colab에 기본으로 깔려 있는
`tensorflow`만 사용**하고, 추가 `pip install`이 필요 없습니다.

## 왜 이 방식이 안전한가

원시 오디오 파형에 Conv1D를 직접 적용하는 구조라서, 모델 안에 FFT/RFFT 같은
연산이 아예 없습니다. TFLite로 변환할 때 `TFLITE_BUILTINS`만 허용하도록
강제했기 때문에(`converter.target_spec.supported_ops`), Flex 연산이 필요한
상황이면 Colab에서 변환하는 순간 바로 에러가 납니다. 즉, "일단 변환은 되는데
안드로이드에서 조용히 NaN 나는" 상황 자체가 안 생깁니다.

## 1. 데이터셋 준비

`training/dataset/train/<클래스이름>/*.wav`, `training/dataset/test/<클래스이름>/*.wav`
구조는 그대로입니다.

wav 파일은 **16kHz, mono, 16bit PCM**을 권장합니다. 다른 포맷/샘플레이트면
변환해서 넣으세요 (Colab에는 ffmpeg가 기본 설치돼 있습니다):

```bash
ffmpeg -i input.mp3 -ar 16000 -ac 1 -sample_fmt s16 output.wav
```

## 2. Colab에서 실행

1. 이 프로젝트의 `training` 폴더를 Colab에 업로드 (또는 Google Drive 마운트)
2. 새 pip install 없이 바로 실행:

```python
!python train_wave_cnn.py
```

30 에폭 기준으로 데이터 양에 따라 몇 분~몇십 분 걸립니다.

## 3. 결과물 적용

`training/exported_model/sound_model.tflite`와 `labels.txt`를
`app/src/main/assets/`에 그대로 덮어쓰고 GitHub에 push하면 끝입니다.
코드 수정은 필요 없습니다 (입력 크기는 앱이 모델에서 자동으로 읽어옵니다).

## 참고

- 녹음 샘플레이트는 이미 16000Hz로 맞춰져 있습니다 (`AudioCapture.kt`).
- 이 모델은 Flex 연산자가 필요 없으므로, 화면에 "Flex 델리게이트=못 찾음"이라고
  떠도 정상입니다 (원래 안 써도 되는 모델입니다).
- 정확도가 낮게 나오면 대부분 데이터 부족입니다. 클래스당 wav를 더 많이
  (각 30개 이상), 다양한 환경/거리에서 녹음해서 넣어보세요.
