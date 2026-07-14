"""
YAMNet 기반 전이학습으로 차량 소음 분류 모델을 학습하는 스크립트입니다.
본인 PC(Python 3.9~3.10 권장)에서 실행하세요. 이 프로젝트(안드로이드 앱) 안에서는 실행되지 않습니다.

[설치]
    pip install tflite-model-maker
    pip install tensorflow

[데이터셋 준비]
    training/dataset/train/<클래스이름>/*.wav
    training/dataset/test/<클래스이름>/*.wav

    예시 클래스: engine_normal(정상 엔진음), belt_squeal(벨트 마찰음),
                brake_squeal(브레이크 스퀼음) — 폴더 이름은 자유롭게 정하세요.
    클래스당 wav 파일을 최소 20~30개 이상 넣으면 좋습니다 (많을수록 정확도↑).
    wav는 mono/16kHz가 이상적이지만, 다른 포맷이어도 라이브러리가 자동으로 리샘플링합니다.

[실행]
    python train_yamnet_model.py

[결과물]
    exported_model/sound_model.tflite
    exported_model/labels.txt

    이 두 파일을 안드로이드 프로젝트의
    app/src/main/assets/ 폴더에 그대로 넣으면 앱이 자동으로 인식합니다.
"""

import os
from tflite_model_maker import audio_classifier
from tflite_model_maker.config import ExportFormat

DATA_DIR = os.path.join(os.path.dirname(__file__), "dataset")
EXPORT_DIR = os.path.join(os.path.dirname(__file__), "exported_model")


def main():
    # YAMNet을 백본으로 사용하는 스펙. Flex 연산자 없이 표준 TFLite 연산만으로
    # 동작하는 모델을 만들어줍니다 (안드로이드 온디바이스 호환성이 검증된 방식).
    spec = audio_classifier.YamNetSpec(
        keep_yamnet_and_custom_heads=True,
        frame_step=3 * audio_classifier.YamNetSpec.EXPECTED_WAVEFORM_LENGTH,
        frame_length=6 * audio_classifier.YamNetSpec.EXPECTED_WAVEFORM_LENGTH,
    )

    train_data = audio_classifier.DataLoader.from_folder(
        spec, os.path.join(DATA_DIR, "train")
    )
    train_data, validation_data = train_data.split(0.8)
    test_data = audio_classifier.DataLoader.from_folder(
        spec, os.path.join(DATA_DIR, "test")
    )

    print(f"클래스 목록: {train_data.index_to_label}")

    model = audio_classifier.create(
        train_data,
        spec,
        validation_data=validation_data,
        batch_size=32,
        epochs=100,
    )

    loss, acc = model.evaluate(test_data)
    print(f"테스트 정확도: {acc:.2%}")

    os.makedirs(EXPORT_DIR, exist_ok=True)
    model.export(EXPORT_DIR, tflite_filename="sound_model.tflite")
    model.export(EXPORT_DIR, export_format=[ExportFormat.LABEL], label_filename="labels.txt")

    print(f"완료! {EXPORT_DIR} 안의 sound_model.tflite, labels.txt를")
    print("app/src/main/assets/ 폴더에 넣고 다시 빌드하세요.")


if __name__ == "__main__":
    main()
