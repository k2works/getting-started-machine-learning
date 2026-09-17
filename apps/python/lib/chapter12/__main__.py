import pandas as pd
from sklearn.linear_model import Lasso
from sklearn.metrics import r2_score

from lib.chapter12.boston_features import (
    FEATURES,
    OUTLIER_THRESHOLD,
    TARGET,
    prepare_boston,
    remove_outliers,
)
from lib.chapter12.regularization import (
    best_experiment,
    fit_ridge,
    predict,
    run_ridge_experiments,
    zero_coefficient_names,
)
from lib.dataset import data_dir

TEST_SIZE = 0.3
VALIDATION_SIZE = 0.3
SEED = 0
ALPHAS = [0.0, 0.1, 1.0, 10.0, 100.0]
LASSO_ALPHA = 0.5


def main() -> None:
    csv_file = data_dir() / "Boston.csv"
    df = pd.read_csv(csv_file)
    kept = remove_outliers(df, FEATURES + [TARGET], OUTLIER_THRESHOLD)
    dataset = prepare_boston(csv_file, TEST_SIZE, VALIDATION_SIZE, SEED)
    print(f"データ件数: {len(kept)}（外れ値 {len(df) - len(kept)} 件を除外）")
    print(
        f"訓練データ: {len(dataset.t_train)} 件, "
        f"検証データ: {len(dataset.t_valid)} 件, "
        f"テストデータ: {len(dataset.t_test)} 件"
    )
    print(f"特徴量: {', '.join(dataset.feature_names)}")

    experiments = run_ridge_experiments(
        dataset.x_train, dataset.t_train, dataset.x_valid, dataset.t_valid, ALPHAS
    )
    print("alpha  訓練 R²  検証 R²  係数の絶対値の合計")
    for e in experiments:
        print(
            f"{e.alpha:>5}  {e.train_score:.4f}  {e.validation_score:.4f}  "
            f"{e.coef_abs_sum:.3f}"
        )
    best = best_experiment(experiments)
    print(f"検証データで選んだ alpha: {best.alpha}")

    linear = fit_ridge(dataset.x_train, dataset.t_train, alpha=0.0)
    ridge = fit_ridge(dataset.x_train, dataset.t_train, alpha=best.alpha)
    linear_score = r2_score(dataset.t_test, predict(linear, dataset.x_test))
    ridge_score = r2_score(dataset.t_test, predict(ridge, dataset.x_test))
    print(
        f"テストデータの決定係数: 線形回帰 {linear_score:.4f}, "
        f"リッジ回帰 {ridge_score:.4f}"
    )

    lasso = Lasso(alpha=LASSO_ALPHA).fit(dataset.x_train, dataset.t_train)
    zeros = ", ".join(zero_coefficient_names(lasso.coef_, dataset.feature_names))
    print(f"ラッソ回帰（alpha={LASSO_ALPHA}）で係数が 0 になった特徴量: {zeros}")


if __name__ == "__main__":
    main()
