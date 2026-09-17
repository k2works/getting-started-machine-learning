from pathlib import Path

import pandas as pd

from lib.chapter02.iris_preprocessing import split_train_test
from lib.chapter08.survived_classifier import (
    FEATURES,
    MODEL_FILE,
    build_pipeline,
    evaluate,
    load_model,
    load_survived,
    save_model,
    split_features_and_target,
)
from lib.dataset import data_dir

TEST_SIZE = 0.2
SEED = 0
MAX_DEPTH = 5

NEW_PASSENGERS = pd.DataFrame(
    [
        (1, "female", None, 0, 0, 50.0, "C"),
        (3, "male", None, 0, 0, 8.0, "S"),
    ],
    columns=FEATURES,
)


def main(model_file: Path = MODEL_FILE) -> None:
    df = load_survived(data_dir() / "Survived.csv")
    x, t = split_features_and_target(df)
    split = split_train_test(x, t, test_size=TEST_SIZE, seed=SEED)
    counts = t.value_counts()
    print(f"データ件数: {len(df)}（生存 {counts[1]}, 死亡 {counts[0]}）")
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")

    pipelines = {}
    for class_weight in [None, "balanced"]:
        pipeline = build_pipeline(max_depth=MAX_DEPTH, class_weight=class_weight)
        pipelines[class_weight] = pipeline.fit(split.x_train, split.t_train)
        result = evaluate(pipeline, split)
        print(
            f"class_weight={class_weight}: "
            f"訓練 {result.train_accuracy:.3f}, テスト {result.test_accuracy:.3f}, "
            f"生存者 {result.survivors} 人中 {result.found_survivors} 人を発見"
        )

    save_model(pipelines["balanced"], model_file)
    predictions = load_model(model_file).predict(NEW_PASSENGERS)
    print(f"保存したモデル: {model_file.name}")
    print(f"架空の乗客の予測: {predictions.tolist()}")


if __name__ == "__main__":
    main()
