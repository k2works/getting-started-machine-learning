from pathlib import Path

import pandas as pd
import pytest

from lib.chapter02.iris_preprocessing import TrainTestSplit, split_train_test
from lib.chapter08.__main__ import main
from lib.chapter08.survived_classifier import (
    FEATURES,
    DummyEncoder,
    Evaluation,
    GroupMedianImputer,
    MostFrequentImputer,
    build_pipeline,
    evaluate,
    load_model,
    load_survived,
    save_model,
    split_features_and_target,
)
from lib.dataset import data_dir
from test.markers import requires_data

HEADER = "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "survived.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8-sig")
    return csv_file


class TestLoadSurvived:
    def test_BOM付きCSVを読み込み空欄を欠損値にする(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "1,0,3,male,,0,0,X-1,8.5,,S\n")

        df = load_survived(csv_file)

        assert df.loc[0, "Sex"] == "male"
        assert pd.isna(df.loc[0, "Age"])
        assert pd.isna(df.loc[0, "Cabin"])


class TestGroupMedianImputer:
    def test_同じグループの中央値で欠損値を補完する(self) -> None:
        df = pd.DataFrame(
            {
                "Pclass": [1, 1, 1, 1],
                "Sex": ["female", "female", "female", "female"],
                "Age": [20.0, 30.0, 70.0, None],
            }
        )
        imputer = GroupMedianImputer(column="Age", by=("Pclass", "Sex"))

        filled = imputer.fit(df).transform(df)

        assert filled["Age"].to_list() == [20.0, 30.0, 70.0, 30.0]

    def test_グループごとに異なる中央値で補完する(self) -> None:
        df = pd.DataFrame(
            {
                "Pclass": [1, 1, 1, 3, 3, 3],
                "Sex": ["female", "female", "female", "male", "male", "male"],
                "Age": [40.0, 50.0, None, 10.0, 20.0, None],
            }
        )
        imputer = GroupMedianImputer(column="Age", by=("Pclass", "Sex"))

        filled = imputer.fit(df).transform(df)

        assert filled["Age"].to_list() == [40.0, 50.0, 45.0, 10.0, 20.0, 15.0]

    def test_訓練データで求めた中央値を別のデータの補完に使う(self) -> None:
        train = pd.DataFrame(
            {"Pclass": [2, 2], "Sex": ["male", "male"], "Age": [30.0, 34.0]}
        )
        other = pd.DataFrame({"Pclass": [2], "Sex": ["male"], "Age": [None]})
        imputer = GroupMedianImputer(column="Age", by=("Pclass", "Sex"))

        filled = imputer.fit(train).transform(other)

        assert filled["Age"].to_list() == [32.0]

    def test_訓練データに無いグループは全体の中央値で補完する(self) -> None:
        train = pd.DataFrame(
            {
                "Pclass": [1, 1, 3],
                "Sex": ["female", "female", "male"],
                "Age": [30.0, 40.0, 20.0],
            }
        )
        other = pd.DataFrame({"Pclass": [2], "Sex": ["female"], "Age": [None]})
        imputer = GroupMedianImputer(column="Age", by=("Pclass", "Sex"))

        filled = imputer.fit(train).transform(other)

        assert filled["Age"].to_list() == [30.0]

    def test_元のデータフレームは変更しない(self) -> None:
        df = pd.DataFrame(
            {"Pclass": [1, 1], "Sex": ["male", "male"], "Age": [30.0, None]}
        )
        imputer = GroupMedianImputer(column="Age", by=("Pclass", "Sex"))

        imputer.fit(df).transform(df)

        assert df["Age"].isna().sum() == 1


class TestMostFrequentImputer:
    def test_訓練データで最も多い値で欠損値を補完する(self) -> None:
        train = pd.DataFrame({"Embarked": ["S", "C", "S", None]})
        other = pd.DataFrame({"Embarked": [None, "Q"]})
        imputer = MostFrequentImputer(column="Embarked")

        filled = imputer.fit(train).transform(other)

        assert filled["Embarked"].to_list() == ["S", "Q"]


class TestGetDummies:
    def test_get_dummiesはデータに含まれるカテゴリの列しか作らない(self) -> None:
        train = pd.DataFrame({"Embarked": ["C", "Q", "S"]})
        other = pd.DataFrame({"Embarked": ["S"]})

        assert list(pd.get_dummies(train).columns) == [
            "Embarked_C",
            "Embarked_Q",
            "Embarked_S",
        ]
        assert list(pd.get_dummies(other).columns) == ["Embarked_S"]


class TestDummyEncoder:
    def test_2値のカテゴリを最初のカテゴリを除いた0と1の列にする(self) -> None:
        df = pd.DataFrame({"Pclass": [1, 3, 2], "Sex": ["female", "male", "male"]})
        encoder = DummyEncoder(columns=("Sex",))

        encoded = encoder.fit(df).transform(df)

        assert encoded.to_dict(orient="list") == {
            "Pclass": [1, 3, 2],
            "Sex_male": [0, 1, 1],
        }

    def test_別のデータにも訓練データと同じ列を作る(self) -> None:
        train = pd.DataFrame({"Embarked": ["C", "Q", "S"]})
        other = pd.DataFrame({"Embarked": ["S", "S"]})
        encoder = DummyEncoder(columns=("Embarked",))

        encoded = encoder.fit(train).transform(other)

        assert encoded.to_dict(orient="list") == {
            "Embarked_Q": [0, 0],
            "Embarked_S": [1, 1],
        }


def passengers(rows: list[tuple[object, ...]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=FEATURES)


class TestSplitFeaturesAndTarget:
    def test_特徴量の列とSurvived列に分ける(self) -> None:
        df = pd.DataFrame(
            {
                "PassengerId": [1],
                "Survived": [1],
                "Pclass": [2],
                "Sex": ["female"],
                "Age": [28.0],
                "SibSp": [0],
                "Parch": [1],
                "Ticket": ["X-2"],
                "Fare": [15.0],
                "Cabin": [None],
                "Embarked": ["C"],
            }
        )

        x, t = split_features_and_target(df)

        assert list(x.columns) == [
            "Pclass",
            "Sex",
            "Age",
            "SibSp",
            "Parch",
            "Fare",
            "Embarked",
        ]
        assert t.to_list() == [1]


def training_data() -> tuple[pd.DataFrame, pd.Series]:
    x = passengers(
        [
            (1, "female", 30.0, 0, 0, 80.0, "C"),
            (2, "female", None, 1, 0, 20.0, "S"),
            (3, "female", 22.0, 0, 1, 9.0, None),
            (3, "female", 18.0, 0, 0, 8.0, "Q"),
            (1, "male", 45.0, 0, 0, 60.0, "S"),
            (2, "male", None, 0, 0, 13.0, "S"),
            (3, "male", 25.0, 1, 0, 7.0, "S"),
            (3, "male", 33.0, 0, 0, 8.0, None),
        ]
    )
    t = pd.Series([1, 1, 1, 1, 0, 0, 0, 0])
    return x, t


def new_passengers() -> pd.DataFrame:
    return passengers(
        [
            (2, "female", None, 0, 0, 12.0, None),
            (1, "male", None, 1, 1, 70.0, "C"),
        ]
    )


class TestBuildPipeline:
    def test_欠損値を含むデータで学習して予測できる(self) -> None:
        x, t = training_data()
        pipeline = build_pipeline(max_depth=3, class_weight=None)

        pipeline.fit(x, t)

        assert pipeline.predict(new_passengers()).tolist() == [1, 0]

    def test_class_weightをモデルに渡す(self) -> None:
        pipeline = build_pipeline(max_depth=5, class_weight="balanced")

        assert pipeline.named_steps["model"].class_weight == "balanced"
        assert pipeline.named_steps["model"].max_depth == 5


class TestEvaluate:
    def test_正解率と見つけた生存者の数を求める(self) -> None:
        x, t = training_data()
        split = TrainTestSplit(
            x_train=x, x_test=new_passengers(), t_train=t, t_test=pd.Series([1, 1])
        )
        pipeline = build_pipeline(max_depth=3, class_weight=None).fit(x, t)

        evaluation = evaluate(pipeline, split)

        assert evaluation == Evaluation(
            train_accuracy=1.0, test_accuracy=0.5, found_survivors=1, survivors=2
        )


class TestSaveAndLoadModel:
    def test_保存したパイプラインを読み込むと同じ予測をする(
        self, tmp_path: Path
    ) -> None:
        x, t = training_data()
        pipeline = build_pipeline(max_depth=3, class_weight=None).fit(x, t)
        model_file = tmp_path / "model" / "survived.joblib"

        save_model(pipeline, model_file)
        loaded = load_model(model_file)

        assert loaded.predict(new_passengers()).tolist() == [1, 0]


@requires_data("Survived.csv")
class TestSurvivedData:
    def test_実データの件数と欠損値の数を確認する(self) -> None:
        df = load_survived(data_dir() / "Survived.csv")

        assert len(df) == 891
        assert df[["Age", "Cabin", "Embarked"]].isna().sum().to_dict() == {
            "Age": 177,
            "Cabin": 687,
            "Embarked": 2,
        }

    def test_class_weightをbalancedにすると見つけられる生存者が増える(self) -> None:
        x, t = split_features_and_target(load_survived(data_dir() / "Survived.csv"))
        split = split_train_test(x, t, test_size=0.2, seed=0)

        results = {
            class_weight: evaluate(
                build_pipeline(max_depth=5, class_weight=class_weight).fit(
                    split.x_train, split.t_train
                ),
                split,
            )
            for class_weight in [None, "balanced"]
        }

        assert results[None].found_survivors == 45
        assert results["balanced"].found_survivors == 51
        assert results["balanced"].test_accuracy == pytest.approx(0.838, abs=1e-3)

    def test_実行すると評価結果を表示してモデルを保存する(
        self, tmp_path: Path, capsys: pytest.CaptureFixture[str]
    ) -> None:
        model_file = tmp_path / "survived.joblib"

        main(model_file)

        assert model_file.exists()
        assert capsys.readouterr().out == (
            "データ件数: 891（生存 342, 死亡 549）\n"
            "訓練データ: 712 件, テストデータ: 179 件\n"
            "class_weight=None: 訓練 0.850, テスト 0.827, 生存者 69 人中 45 人を発見\n"
            "class_weight=balanced: 訓練 0.840, テスト 0.838, 生存者 69 人中 51 人を発見\n"
            "保存したモデル: survived.joblib\n"
            "架空の乗客の予測: [1, 0]\n"
        )
