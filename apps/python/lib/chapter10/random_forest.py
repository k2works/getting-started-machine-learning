from collections import Counter

import numpy as np
import pandas as pd
from numpy.typing import NDArray

from lib.chapter03.decision_tree import DecisionTree


def majority_vote(votes: list[list[str]]) -> list[str]:
    return [Counter(sample).most_common(1)[0][0] for sample in zip(*votes, strict=True)]


def bootstrap_sample(size: int, rng: np.random.Generator) -> NDArray[np.int64]:
    return rng.integers(0, size, size=size)


class RandomForest:
    def __init__(
        self,
        n_estimators: int = 10,
        max_features: int = 2,
        max_depth: int | None = None,
        seed: int = 0,
    ) -> None:
        self.n_estimators = n_estimators
        self.max_features = max_features
        self.max_depth = max_depth
        self.seed = seed
        self.trees: list[tuple[list[str], DecisionTree]] = []
        self.bootstrap_rows: list[NDArray[np.int64]] = []

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "RandomForest":
        rng = np.random.default_rng(self.seed)
        self.trees = []
        self.bootstrap_rows = []
        for _ in range(self.n_estimators):
            rows = bootstrap_sample(len(x), rng)
            self.bootstrap_rows.append(rows)
            chosen = rng.choice(len(x.columns), size=self.max_features, replace=False)
            columns = [str(x.columns[i]) for i in sorted(chosen)]
            tree = DecisionTree(max_depth=self.max_depth)
            tree.fit(x.iloc[rows][columns], t.iloc[rows])
            self.trees.append((columns, tree))
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if not self.trees:
            raise ValueError("fit で学習してから predict を呼んでください")
        return majority_vote([tree.predict(x[columns]) for columns, tree in self.trees])
