from pathlib import Path

import pytest

from lib.chapter01.__main__ import main
from lib.chapter01.kinoko_takenoko import (
    Features,
    Person,
    accuracy,
    load_people,
    predict_by_rule,
    split_features_and_labels,
)
from lib.dataset import data_dir

KVST_CSV = data_dir() / "KvsT.csv"
HEADER = "身長,体重,年代,派閥\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "kvst.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8-sig")
    return csv_file


class TestLoadPeople:
    def test_BOM付きCSVを読み込んで人物のリストを返す(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "165,58,30,きのこ\n")

        people = load_people(csv_file)

        assert people == [Person(height=165, weight=58, age_group=30, faction="きのこ")]

    def test_複数行のCSVを読み込んで行の順に人物のリストを返す(
        self, tmp_path: Path
    ) -> None:
        csv_file = write_csv(tmp_path, "161,52,20,きのこ\n183,74,50,たけのこ\n")

        people = load_people(csv_file)

        assert people == [
            Person(height=161, weight=52, age_group=20, faction="きのこ"),
            Person(height=183, weight=74, age_group=50, faction="たけのこ"),
        ]


class TestSplitFeaturesAndLabels:
    def test_人物のリストを特徴量と正解ラベルに分ける(self) -> None:
        people = [
            Person(height=161, weight=52, age_group=20, faction="きのこ"),
            Person(height=183, weight=74, age_group=50, faction="たけのこ"),
        ]

        features, labels = split_features_and_labels(people)

        assert features == [
            Features(height=161, weight=52, age_group=20),
            Features(height=183, weight=74, age_group=50),
        ]
        assert labels == ["きのこ", "たけのこ"]


class TestPredictByRule:
    def test_20代ならきのこ派と判定する(self) -> None:
        features = Features(height=161, weight=52, age_group=20)

        assert predict_by_rule(features) == "きのこ"

    def test_20代以外ならたけのこ派と判定する(self) -> None:
        features = Features(height=183, weight=74, age_group=50)

        assert predict_by_rule(features) == "たけのこ"


class TestAccuracy:
    def test_すべての予測が正解なら正解率は1(self) -> None:
        assert accuracy(["きのこ", "たけのこ"], ["きのこ", "たけのこ"]) == 1.0

    def test_4件中3件の予測が正解なら正解率は075(self) -> None:
        predictions = ["きのこ", "きのこ", "たけのこ", "たけのこ"]
        labels = ["きのこ", "たけのこ", "たけのこ", "たけのこ"]

        assert accuracy(predictions, labels) == 0.75


@pytest.mark.skipif(
    not KVST_CSV.exists(),
    reason="学習データ KvsT.csv が配置されていない（gulp data:setup）",
)
class TestKvsTData:
    def test_実データから19人分を読み込む(self) -> None:
        assert len(load_people(KVST_CSV)) == 19

    def test_ルールによる判定の正解率を実データで計算する(self) -> None:
        features, labels = split_features_and_labels(load_people(KVST_CSV))

        predictions = [predict_by_rule(f) for f in features]

        assert accuracy(predictions, labels) == pytest.approx(14 / 19)

    def test_実行するとデータ件数と正解率を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 19\nルールによる判定の正解率: 0.7368\n"
        )
