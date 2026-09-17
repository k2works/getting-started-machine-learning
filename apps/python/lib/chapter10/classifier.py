from dataclasses import dataclass
from typing import Protocol, Self

import pandas as pd

from lib.chapter01.kinoko_takenoko import accuracy
from lib.chapter02.iris_preprocessing import TrainTestSplit


class Classifier(Protocol):
    def fit(self, x: pd.DataFrame, t: pd.Series) -> Self: ...

    def predict(self, x: pd.DataFrame) -> list[str]: ...


@dataclass(frozen=True)
class Score:
    train: float
    test: float


def evaluate(model: Classifier, split: TrainTestSplit) -> Score:
    model.fit(split.x_train, split.t_train)
    return Score(
        train=accuracy(model.predict(split.x_train), split.t_train.to_list()),
        test=accuracy(model.predict(split.x_test), split.t_test.to_list()),
    )
