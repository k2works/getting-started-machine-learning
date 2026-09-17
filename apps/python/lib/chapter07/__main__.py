from lib.chapter07.cinema_regression import (
    fit_linear_regression,
    load_cinema,
    mean_absolute_error,
    prepare_cinema,
    r2_score,
    remove_outliers,
    root_mean_squared_error,
)
from lib.dataset import data_dir

TEST_SIZE = 0.2
SEED = 0


def main() -> None:
    csv_file = data_dir() / "cinema.csv"
    df = load_cinema(csv_file)
    split = prepare_cinema(csv_file, test_size=TEST_SIZE, seed=SEED)
    model = fit_linear_regression(split.x_train, split.t_train)
    y = model.predict(split.x_test)
    coefficients = ", ".join(f"{k}={v:.4f}" for k, v in model.coefficients.items())
    print(f"データ件数: {len(df)}")
    print(f"外れ値を除いた件数: {len(remove_outliers(df))}")
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")
    print(f"切片: {model.intercept:.2f}")
    print(f"係数: {coefficients}")
    print(
        f"テストデータの評価: R2={r2_score(split.t_test, y):.4f}, "
        f"MAE={mean_absolute_error(split.t_test, y):.2f}, "
        f"RMSE={root_mean_squared_error(split.t_test, y):.2f}"
    )


if __name__ == "__main__":
    main()
