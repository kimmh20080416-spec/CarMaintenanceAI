"""
차량 소음 분류 모델 학습 스크립트 (2026년 기준, Colab 기본 환경 대응)

- 외부 라이브러리 설치가 필요 없습니다. Colab에 이미 깔려 있는 `tensorflow`만 씁니다.
  (tflite-model-maker처럼 유지보수 끊긴 패키지를 요구하지 않습니다.)
- 원시 파형(raw waveform)에 직접 Conv1D를 적용하는 구조라서, 모델 그래프 안에
  FFT/RFFT 같은 연산이 전혀 들어가지 않습니다. 그래서 TFLite로 변환할 때
  `TFLITE_BUILTINS`만 허용하도록 강제해도 문제없이 변환되고,
  안드로이드에서 Flex 연산자 없이 안정적으로 동작합니다.
- 변환 단계에서 혹시라도 Flex가 필요한 연산이 섞여 있으면, Colab에서 그 자리에서
  바로 에러로 알려줍니다. 안드로이드에서 나중에 조용히 NaN 나는 일이 없습니다.

[실행 환경]
  Google Colab (런타임 > 다시 시작 후 아무것도 pip install 하지 말고 바로 실행)
  또는 tensorflow가 설치된 아무 Python 환경

[데이터셋 준비]
  training/dataset/train/<클래스이름>/*.wav
  training/dataset/test/<클래스이름>/*.wav

  wav 파일은 16kHz, mono, 16bit PCM 형식을 권장합니다. 다른 형식이면 아래
  명령으로 변환하세요 (Colab에는 ffmpeg가 기본 설치되어 있습니다):
      ffmpeg -i input.mp3 -ar 16000 -ac 1 -sample_fmt s16 output.wav

[실행]
  python train_wave_cnn.py

[결과물]
  exported_model/sound_model.tflite
  exported_model/labels.txt
  이 두 파일을 app/src/main/assets/ 에 그대로 넣고 다시 빌드하면 끝입니다.
"""

import pathlib
import tensorflow as tf

DATA_DIR = pathlib.Path(__file__).parent / "dataset"
EXPORT_DIR = pathlib.Path(__file__).parent / "exported_model"

SAMPLE_RATE = 16000
SEQUENCE_LENGTH = SAMPLE_RATE * 2  # 2초 분량 = 32000 샘플
BATCH_SIZE = 16
EPOCHS = 30


def load_wav(file_path: str) -> tf.Tensor:
    """wav 파일을 읽어서 정확히 SEQUENCE_LENGTH 길이의 파형으로 맞춘다.
    tf.audio.decode_wav의 desired_samples가 부족하면 0으로 채우고,
    넘치면 앞부분만 잘라준다 (패딩/자르기가 자동으로 처리됨)."""
    audio_binary = tf.io.read_file(file_path)
    audio, _ = tf.audio.decode_wav(
        audio_binary, desired_channels=1, desired_samples=SEQUENCE_LENGTH
    )
    return tf.squeeze(audio, axis=-1)


def build_dataset(data_dir: pathlib.Path):
    class_names = sorted([d.name for d in data_dir.iterdir() if d.is_dir()])
    if not class_names:
        raise RuntimeError(f"{data_dir} 안에 클래스 폴더가 없습니다.")
    class_to_idx = {name: i for i, name in enumerate(class_names)}

    file_paths, labels = [], []
    for name in class_names:
        wavs = list((data_dir / name).glob("*.wav"))
        if not wavs:
            print(f"경고: '{name}' 폴더에 wav 파일이 없습니다.")
        for f in wavs:
            file_paths.append(str(f))
            labels.append(class_to_idx[name])

    if not file_paths:
        raise RuntimeError(f"{data_dir} 안에 wav 파일이 하나도 없습니다.")

    ds = tf.data.Dataset.from_tensor_slices((file_paths, labels))
    ds = ds.map(
        lambda path, label: (load_wav(path), label),
        num_parallel_calls=tf.data.AUTOTUNE,
    )
    return ds, class_names


def build_model(num_classes: int) -> tf.keras.Model:
    return tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(SEQUENCE_LENGTH,)),
            tf.keras.layers.Reshape((SEQUENCE_LENGTH, 1)),
            tf.keras.layers.Conv1D(16, 64, strides=4, activation="relu"),
            tf.keras.layers.MaxPooling1D(4),
            tf.keras.layers.Conv1D(32, 32, strides=2, activation="relu"),
            tf.keras.layers.MaxPooling1D(4),
            tf.keras.layers.Conv1D(64, 16, strides=2, activation="relu"),
            tf.keras.layers.GlobalAveragePooling1D(),
            tf.keras.layers.Dense(64, activation="relu"),
            tf.keras.layers.Dropout(0.3),
            tf.keras.layers.Dense(num_classes, activation="softmax"),
        ]
    )


def main():
    train_raw, class_names = build_dataset(DATA_DIR / "train")
    test_raw, test_class_names = build_dataset(DATA_DIR / "test")
    if class_names != test_class_names:
        print("경고: train/test 클래스 폴더 이름이 서로 다릅니다.", class_names, test_class_names)

    num_classes = len(class_names)
    print(f"클래스({num_classes}개): {class_names}")

    train_ds = train_raw.shuffle(1000).batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)
    test_ds = test_raw.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

    model = build_model(num_classes)
    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

    model.fit(train_ds, validation_data=test_ds, epochs=EPOCHS)

    loss, acc = model.evaluate(test_ds)
    print(f"테스트 정확도: {acc:.2%}")

    # TFLite 변환: builtin 연산만 허용 (Flex 연산이 필요하면 여기서 바로 에러가 남)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    tflite_model = converter.convert()

    EXPORT_DIR.mkdir(exist_ok=True)
    (EXPORT_DIR / "sound_model.tflite").write_bytes(tflite_model)
    (EXPORT_DIR / "labels.txt").write_text("\n".join(class_names) + "\n")

    print(f"완료! {EXPORT_DIR} 안의 sound_model.tflite, labels.txt를")
    print("app/src/main/assets/ 폴더에 넣고 다시 빌드하세요.")


if __name__ == "__main__":
    main()
