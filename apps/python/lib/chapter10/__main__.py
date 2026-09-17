from lib.chapter02.iris_preprocessing import prepare_iris
from lib.chapter03.decision_tree import DecisionTree
from lib.chapter10.classifier import Classifier, evaluate
from lib.chapter10.feature_importance import forest_importances
from lib.chapter10.logistic_regression import LogisticRegression
from lib.chapter10.random_forest import RandomForest
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0
N_ESTIMATORS = 100


def models() -> list[tuple[str, Classifier]]:
    return [
        ("決定木（深さ 2）", DecisionTree(max_depth=2)),
        ("ロジスティック回帰", LogisticRegression()),
        (
            f"ランダムフォレスト（{N_ESTIMATORS} 本）",
            RandomForest(n_estimators=N_ESTIMATORS, max_features=2, seed=SEED),
        ),
        (
            f"ランダムフォレスト（{N_ESTIMATORS} 本・深さ 2）",
            RandomForest(
                n_estimators=N_ESTIMATORS, max_features=2, max_depth=2, seed=SEED
            ),
        ),
    ]


def main() -> None:
    split = prepare_iris(data_dir() / "iris.csv", test_size=TEST_SIZE, seed=SEED)
    print("モデル\t訓練データ\tテストデータ")
    for name, model in models():
        score = evaluate(model, split)
        print(f"{name}\t{score.train:.4f}\t{score.test:.4f}")

    forest = RandomForest(n_estimators=N_ESTIMATORS, max_features=2, seed=SEED)
    forest.fit(split.x_train, split.t_train)
    print(f"\nランダムフォレスト（{N_ESTIMATORS} 本）の特徴量の重要度:")
    for feature, value in forest_importances(
        forest, split.x_train, split.t_train
    ).items():
        print(f"{feature}\t{value:.4f}")


if __name__ == "__main__":
    main()
