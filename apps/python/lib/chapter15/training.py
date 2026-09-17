from pathlib import Path

from lib.chapter02.iris_preprocessing import split_train_test
from lib.chapter07.cinema_regression import fit_linear_regression, prepare_cinema
from lib.chapter08.survived_classifier import (
    build_pipeline,
    load_survived,
    split_features_and_target,
)
from lib.chapter15.infrastructure import JoblibModelStore

TEST_SIZE = 0.2
SEED = 0
MAX_DEPTH = 5


def train_and_save_models(data_directory: Path, store: JoblibModelStore) -> None:
    cinema = prepare_cinema(
        data_directory / "cinema.csv", test_size=TEST_SIZE, seed=SEED
    )
    store.save_sales_model(fit_linear_regression(cinema.x_train, cinema.t_train))

    x, t = split_features_and_target(load_survived(data_directory / "Survived.csv"))
    survived = split_train_test(x, t, test_size=TEST_SIZE, seed=SEED)
    pipeline = build_pipeline(max_depth=MAX_DEPTH, class_weight="balanced")
    store.save_survival_model(pipeline.fit(survived.x_train, survived.t_train))
