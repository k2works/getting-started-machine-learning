from lib.chapter01.kinoko_takenoko import (
    accuracy,
    load_people,
    predict_by_rule,
    split_features_and_labels,
)
from lib.dataset import data_dir


def main() -> None:
    people = load_people(data_dir() / "KvsT.csv")
    features, labels = split_features_and_labels(people)
    predictions = [predict_by_rule(f) for f in features]
    print(f"データ件数: {len(people)}")
    print(f"ルールによる判定の正解率: {accuracy(predictions, labels):.4f}")


if __name__ == "__main__":
    main()
