import pandas as pd

from lib.chapter09.feature_engineering import (
    Standardizer,
    iqr_outliers,
    join_weather,
    load_bike,
    load_weather,
    mean_count_by_weather,
    polynomial_features,
    prepare_boston,
    remove_target_outliers,
    score_feature_set,
)
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0
COLUMNS = ["RM", "LSTAT", "PTRATIO"]
SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"]
FEATURE_SETS = {
    "元の特徴量": COLUMNS,
    "2 乗の項を追加": COLUMNS + SQUARES,
    "交互作用の項も追加": list(
        polynomial_features(pd.DataFrame(columns=COLUMNS), COLUMNS).columns
    ),
}


def format_scores(scores: tuple[float, float]) -> str:
    train_score, test_score = scores
    return f"訓練 {train_score:.4f}, テスト {test_score:.4f}"


def main() -> None:
    split = prepare_boston(data_dir() / "Boston.csv", test_size=TEST_SIZE, seed=SEED)
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")
    print(f"特徴量の列: {', '.join(split.x_train.columns)}")

    standardized = Standardizer.fit(split.x_train).transform(split.x_train)
    # 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 に丸める
    mean = round(float(standardized["RM"].mean()), 6) or 0.0
    std = float(standardized["RM"].std(ddof=0))
    print(f"標準化した訓練データの RM: 平均 {mean:.2f}, 標準偏差 {std:.2f}")

    print("決定係数:")
    for name, terms in FEATURE_SETS.items():
        scores = score_feature_set(split, COLUMNS, terms)
        print(f"  {name}（{len(terms)} 列）: {format_scores(scores)}")

    outliers = int(iqr_outliers(split.t_train).sum())
    print(f"訓練データの PRICE の外れ値: {outliers} 件")
    scores = score_feature_set(
        remove_target_outliers(split), COLUMNS, COLUMNS + SQUARES
    )
    print(f"  外れ値を除いて 2 乗の項を追加: {format_scores(scores)}")

    joined = join_weather(
        load_bike(data_dir() / "bike.tsv"), load_weather(data_dir() / "weather.csv")
    )
    means = mean_count_by_weather(joined)
    print(
        "天気ごとの平均利用者数: "
        + ", ".join(f"{weather}={count:.1f}" for weather, count in means.items())
    )


if __name__ == "__main__":
    main()
