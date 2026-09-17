import pandas as pd

from lib.chapter02.iris_preprocessing import (
    count_missing,
    load_iris,
    prepare_iris,
)
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0


def format_counts(counts: pd.Series) -> str:
    return ", ".join(f"{column}={count}" for column, count in counts.items())


def main() -> None:
    csv_file = data_dir() / "iris.csv"
    df = load_iris(csv_file)
    split = prepare_iris(csv_file, test_size=TEST_SIZE, seed=SEED)
    print(f"データ件数: {len(df)}")
    print(f"欠損値の数: {format_counts(count_missing(df))}")
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")
    missing_train = int(count_missing(split.x_train).sum())
    missing_test = int(count_missing(split.x_test).sum())
    print(
        f"補完後の欠損値の数: 訓練データ {missing_train}, テストデータ {missing_test}"
    )


if __name__ == "__main__":
    main()
