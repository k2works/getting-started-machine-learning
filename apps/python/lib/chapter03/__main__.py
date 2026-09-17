from lib.chapter01.kinoko_takenoko import accuracy
from lib.chapter02.iris_preprocessing import prepare_iris
from lib.chapter03.decision_tree import DecisionTree, format_tree
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0
MAX_DEPTHS = [1, 2, 3, 4, 5, None]
TREE_DEPTH_TO_SHOW = 2


def main() -> None:
    split = prepare_iris(data_dir() / "iris.csv", test_size=TEST_SIZE, seed=SEED)
    print("深さ\t訓練データ\tテストデータ")
    for max_depth in MAX_DEPTHS:
        model = DecisionTree(max_depth=max_depth).fit(split.x_train, split.t_train)
        train = accuracy(model.predict(split.x_train), split.t_train.to_list())
        test = accuracy(model.predict(split.x_test), split.t_test.to_list())
        depth = "制限なし" if max_depth is None else str(max_depth)
        print(f"{depth}\t{train:.4f}\t{test:.4f}")

    shallow = DecisionTree(max_depth=TREE_DEPTH_TO_SHOW)
    shallow.fit(split.x_train, split.t_train)
    if shallow.tree is not None:
        print(f"\n深さ {TREE_DEPTH_TO_SHOW} の決定木:")
        print(format_tree(shallow.tree))


if __name__ == "__main__":
    main()
