from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass

import pandas as pd


@dataclass(frozen=True)
class Split:
    feature: str
    threshold: float
    impurity: float


def gini(labels: Sequence[str]) -> float:
    counts = Counter(labels)
    total = len(labels)
    return 1.0 - sum((count / total) ** 2 for count in counts.values())


def best_split(x: pd.DataFrame, t: pd.Series) -> Split | None:
    labels = t.to_list()
    if gini(labels) == 0.0:
        return None
    best: Split | None = None
    for feature in x.columns:
        pairs = sorted(zip(x[feature].to_list(), labels, strict=True))
        values = [value for value, _ in pairs]
        sorted_labels = [label for _, label in pairs]
        for i in range(1, len(pairs)):
            if values[i] == values[i - 1]:
                continue
            left, right = sorted_labels[:i], sorted_labels[i:]
            impurity = (len(left) * gini(left) + len(right) * gini(right)) / len(pairs)
            if best is None or impurity < best.impurity:
                threshold = (values[i - 1] + values[i]) / 2
                best = Split(
                    feature=str(feature), threshold=threshold, impurity=impurity
                )
    return best


@dataclass(frozen=True)
class Leaf:
    label: str


@dataclass(frozen=True)
class Node:
    split: Split
    left: "Leaf | Node"
    right: "Leaf | Node"


def build_tree(x: pd.DataFrame, t: pd.Series, max_depth: int | None) -> Leaf | Node:
    split = None if max_depth == 0 else best_split(x, t)
    if split is None:
        return Leaf(label=str(Counter(t.to_list()).most_common(1)[0][0]))
    goes_left = x[split.feature] <= split.threshold
    child_depth = None if max_depth is None else max_depth - 1
    return Node(
        split=split,
        left=build_tree(x[goes_left], t[goes_left], child_depth),
        right=build_tree(x[~goes_left], t[~goes_left], child_depth),
    )


def predict_one(tree: Leaf | Node, row: pd.Series) -> str:
    if isinstance(tree, Leaf):
        return tree.label
    if row[tree.split.feature] <= tree.split.threshold:
        return predict_one(tree.left, row)
    return predict_one(tree.right, row)


def format_tree(tree: Leaf | Node, indent: str = "") -> str:
    if isinstance(tree, Leaf):
        return f"{indent}{tree.label}"
    condition = f"{tree.split.feature} {{}} {tree.split.threshold:.4f}"
    return "\n".join(
        [
            indent + condition.format("<="),
            format_tree(tree.left, indent + "  "),
            indent + condition.format(">"),
            format_tree(tree.right, indent + "  "),
        ]
    )


class DecisionTree:
    def __init__(self, max_depth: int | None = None) -> None:
        self.max_depth = max_depth
        self.tree: Leaf | Node | None = None

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "DecisionTree":
        self.tree = build_tree(x, t, self.max_depth)
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if self.tree is None:
            raise ValueError("fit で学習してから predict を呼んでください")
        tree = self.tree
        return [predict_one(tree, row) for _, row in x.iterrows()]
