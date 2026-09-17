import pandas as pd

from lib.chapter03.decision_tree import Leaf, Node, gini
from lib.chapter10.random_forest import RandomForest


def normalize(totals: dict[str, float]) -> dict[str, float]:
    total = sum(totals.values())
    if total == 0.0:
        return totals
    return {feature: value / total for feature, value in totals.items()}


def impurity_decreases(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series, totals: dict[str, float]
) -> None:
    if isinstance(tree, Leaf):
        return
    split = tree.split
    totals[split.feature] += len(t) * (gini(t.to_list()) - split.impurity)
    goes_left = x[split.feature] <= split.threshold
    impurity_decreases(tree.left, x[goes_left], t[goes_left], totals)
    impurity_decreases(tree.right, x[~goes_left], t[~goes_left], totals)


def tree_importances(
    tree: Leaf | Node, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    impurity_decreases(tree, x, t, totals)
    return normalize(totals)


def forest_importances(
    forest: RandomForest, x: pd.DataFrame, t: pd.Series
) -> dict[str, float]:
    totals = {str(column): 0.0 for column in x.columns}
    for (columns, model), rows in zip(forest.trees, forest.bootstrap_rows, strict=True):
        if model.tree is not None:
            sample_x, sample_t = x.iloc[rows][columns], t.iloc[rows]
            for feature, value in tree_importances(
                model.tree, sample_x, sample_t
            ).items():
                totals[feature] += value / len(forest.trees)
    return normalize(totals)
