import pandas as pd
import pytest

from lib.appendix_a.__main__ import main
from lib.appendix_a.bank_exercise import (
    DepthResult,
    Scores,
    ThreeWaySplit,
    build_pipeline,
    fit_final,
    prepare_bank,
    score,
    select_best,
    split_three_way,
    tune_max_depth,
)
from lib.dataset import data_dir
from test.markers import requires_data


def numbered_dataset(size: int) -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame({"x": range(size)})
    t = pd.Series([i % 2 for i in range(size)])
    return x, t


class TestSplitThreeWay:
    def test_テスト10パーセントと残りの検証20パーセントに分ける(self) -> None:
        x, t = numbered_dataset(100)

        split = split_three_way(x, t, test_size=0.1, validation_size=0.2, seed=0)

        assert (len(split.x_train), len(split.x_valid), len(split.x_test)) == (
            72,
            18,
            10,
        )

    def test_3つのデータに重複なくすべての行を分ける(self) -> None:
        x, t = numbered_dataset(100)

        split = split_three_way(x, t, test_size=0.1, validation_size=0.2, seed=0)

        rows = [
            set(split.x_train["x"]),
            set(split.x_valid["x"]),
            set(split.x_test["x"]),
        ]
        assert set().union(*rows) == set(range(100))
        assert sum(len(r) for r in rows) == 100


DEFAULT_CUSTOMER: dict[str, object] = {
    "age": 40,
    "job": "a",
    "marital": "single",
    "education": "primary",
    "default": "no",
    "amount": 100.0,
    "housing": "no",
    "loan": "no",
    "contact": "c",
    "month": "jan",
    "duration": 300.0,
    "campaign": 1,
    "previous": 0,
}


def customers(*overrides: dict[str, object]) -> pd.DataFrame:
    return pd.DataFrame([{**DEFAULT_CUSTOMER, **override} for override in overrides])


class TestBuildPipeline:
    def test_欠損値と訓練データに無いカテゴリがあっても予測できる(self) -> None:
        x = customers(
            {"job": "a", "housing": "yes", "duration": 200.0},
            {"job": "b", "duration": None},
            {"job": "a", "housing": "yes", "loan": "yes", "duration": 400.0},
            {"job": "b", "loan": "yes", "duration": 500.0},
        )
        t = pd.Series([0, 0, 1, 1])
        unseen = customers(
            {"job": "z", "marital": "divorced", "month": "dec", "duration": None}
        )

        pipeline = build_pipeline(max_depth=2).fit(x, t)

        assert len(pipeline.predict(unseen)) == 1


class TestScore:
    def test_正解率_適合率_再現率_F値をまとめて求める(self) -> None:
        scores = score(actual=[1, 1, 0, 0], predicted=[1, 0, 1, 0])

        assert scores == Scores(accuracy=0.5, precision=0.5, recall=0.5, f1=0.5)


def result(max_depth: int, f1: float) -> DepthResult:
    scores = Scores(accuracy=0.0, precision=0.0, recall=0.0, f1=f1)
    return DepthResult(max_depth=max_depth, train=scores, valid=scores)


class TestSelectBest:
    def test_検証データのF値が最も大きい深さを選ぶ(self) -> None:
        results = [result(1, 0.6), result(2, 0.8), result(3, 0.7)]

        assert select_best(results).max_depth == 2

    def test_F値が同じなら浅い木を選ぶ(self) -> None:
        results = [result(3, 0.8), result(2, 0.8), result(4, 0.7)]

        assert select_best(results).max_depth == 2


def small_split() -> ThreeWaySplit:
    x = customers(
        *[
            {
                "age": 30 + i,
                "job": ["a", "b"][i % 2],
                "housing": ["yes", "no", "no"][i % 3],
                "duration": 100.0 + 50 * i,
            }
            for i in range(12)
        ]
    )
    t = pd.Series([0, 1] * 6)
    return split_three_way(x, t, test_size=0.25, validation_size=0.25, seed=0)


class TestTuneMaxDepth:
    def test_深さごとに訓練データと検証データの指標を求める(self) -> None:
        results = tune_max_depth(small_split(), max_depths=[1, 2, 3])

        assert [r.max_depth for r in results] == [1, 2, 3]


class TestFitFinal:
    def test_訓練データと検証データを合わせて学習する(self) -> None:
        split = small_split()

        pipeline = fit_final(split, max_depth=2)

        tree = pipeline.named_steps["model"].tree_
        assert tree.n_node_samples[0] == len(split.x_train) + len(split.x_valid)


@requires_data("Bank.csv")
class TestBankData:
    def test_実データを訓練_検証_テストデータに分ける(self) -> None:
        split = prepare_bank(data_dir() / "Bank.csv", seed=0)

        assert (len(split.x_train), len(split.x_valid), len(split.x_test)) == (
            19532,
            4883,
            2713,
        )
        assert "id" not in split.x_train.columns
        assert "day" not in split.x_train.columns

    def test_実行すると深さの選択とテストデータの評価を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        lines = capsys.readouterr().out.splitlines()
        assert lines[-2:] == [
            "選んだ深さ: 11",
            "テストデータ: 正解率 0.8293, 適合率 0.7088, 再現率 0.8259, F値 0.7629",
        ]
