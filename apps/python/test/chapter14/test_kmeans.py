from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler

from lib.chapter14.__main__ import main
from lib.chapter14.kmeans import (
    assign_clusters,
    best_kmeans,
    choose_initial_centers,
    kmeans,
    load_spending,
    sse_by_cluster_count,
    standardize,
    sum_of_squared_errors,
    summarize_clusters,
    update_centers,
)
from lib.dataset import data_dir
from test.markers import requires_data

HEADER = "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "wholesale.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8")
    return csv_file


class TestLoadSpending:
    def test_ChannelとRegionを除いた支出額の列を読み込む(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "1,2,100,200,300,400,500,600\n")

        df = load_spending(csv_file)

        assert list(df.columns) == [
            "Fresh",
            "Milk",
            "Grocery",
            "Frozen",
            "Detergents_Paper",
            "Delicassen",
        ]
        assert df.iloc[0].to_list() == [100, 200, 300, 400, 500, 600]


class TestStandardize:
    def test_列ごとに平均0標準偏差1に変換する(self) -> None:
        df = pd.DataFrame({"Fresh": [10.0, 20.0, 30.0], "Milk": [5.0, 5.0, 8.0]})

        standardized = standardize(df)

        assert standardized["Fresh"].mean() == pytest.approx(0.0)
        assert standardized["Fresh"].std(ddof=0) == pytest.approx(1.0)
        assert standardized["Milk"].mean() == pytest.approx(0.0)
        assert standardized["Milk"].std(ddof=0) == pytest.approx(1.0)

    def test_scikit_learnのStandardScalerと同じ値になる(self) -> None:
        df = pd.DataFrame({"Fresh": [10.0, 20.0, 60.0], "Milk": [5.0, 9.0, 8.0]})

        standardized = standardize(df)

        expected = StandardScaler().fit_transform(df)
        assert np.allclose(standardized.to_numpy(), expected)


class TestAssignClusters:
    def test_各点を最も近い中心のクラスタに割り当てる(self) -> None:
        points = np.array([[0.0], [1.0], [9.0], [10.0]])
        centers = np.array([[0.0], [10.0]])

        labels = assign_clusters(points, centers)

        assert labels.tolist() == [0, 0, 1, 1]

    def test_2次元の点をユークリッド距離で最も近い中心に割り当てる(self) -> None:
        points = np.array([[0.0, 0.0], [5.0, 4.0], [1.0, 0.0]])
        centers = np.array([[5.0, 5.0], [0.0, 0.0]])

        labels = assign_clusters(points, centers)

        assert labels.tolist() == [1, 0, 1]


class TestUpdateCenters:
    def test_クラスタごとに割り当てられた点の平均を新しい中心にする(self) -> None:
        points = np.array([[0.0, 0.0], [2.0, 0.0], [10.0, 10.0], [10.0, 12.0]])
        labels = np.array([0, 0, 1, 1])
        previous = np.array([[0.0, 0.0], [0.0, 0.0]])

        centers = update_centers(points, labels, previous)

        assert centers.tolist() == [[1.0, 0.0], [10.0, 11.0]]

    def test_点が1つも割り当てられなかったクラスタは中心を変えない(self) -> None:
        points = np.array([[0.0, 0.0], [2.0, 4.0]])
        labels = np.array([0, 0])
        previous = np.array([[0.0, 0.0], [99.0, 99.0]])

        centers = update_centers(points, labels, previous)

        assert centers.tolist() == [[1.0, 2.0], [99.0, 99.0]]


class TestSumOfSquaredErrors:
    def test_各点と所属するクラスタの中心との距離の2乗を合計する(self) -> None:
        points = np.array([[0.0, 0.0], [2.0, 0.0], [10.0, 10.0], [10.0, 12.0]])
        labels = np.array([0, 0, 1, 1])
        centers = np.array([[1.0, 0.0], [10.0, 11.0]])

        assert sum_of_squared_errors(points, labels, centers) == 4.0

    def test_中心から離れた点ほど誤差が大きくなる(self) -> None:
        points = np.array([[0.0], [4.0]])
        labels = np.array([0, 0])
        centers = np.array([[1.0]])

        assert sum_of_squared_errors(points, labels, centers) == 10.0


def two_groups() -> np.ndarray:
    return np.array([[0.0, 0.0], [0.0, 1.0], [10.0, 10.0], [10.0, 11.0]])


class TestKMeans:
    def test_割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す(self) -> None:
        initial_centers = np.array([[0.0, 0.0], [0.0, 1.0]])

        result = kmeans(two_groups(), initial_centers)

        assert result.labels.tolist() == [0, 0, 1, 1]
        assert result.centers.tolist() == [[0.0, 0.5], [10.0, 10.5]]
        assert result.sse == 1.0

    def test_最大反復回数に達したら収束していなくても打ち切る(self) -> None:
        initial_centers = np.array([[0.0, 0.0], [0.0, 1.0]])

        result = kmeans(two_groups(), initial_centers, max_iterations=1)

        assert result.centers == pytest.approx(np.array([[0.0, 0.0], [20 / 3, 22 / 3]]))
        assert result.labels.tolist() == [0, 0, 1, 1]


def numbered_points(size: int) -> np.ndarray:
    return np.array([[float(i), float(i * 2)] for i in range(size)])


class TestChooseInitialCenters:
    def test_データの中から重複なくクラスタ数だけ点を選ぶ(self) -> None:
        points = numbered_points(10)

        centers = choose_initial_centers(points, n_clusters=3, seed=0)

        chosen = {tuple(center) for center in centers.tolist()}
        assert len(chosen) == 3
        assert chosen <= {tuple(point) for point in points.tolist()}

    def test_同じシードなら同じ点を選ぶ(self) -> None:
        points = numbered_points(10)

        first = choose_initial_centers(points, n_clusters=3, seed=42)
        second = choose_initial_centers(points, n_clusters=3, seed=42)

        assert first.tolist() == second.tolist()

    def test_シードが違えば違う点を選ぶ(self) -> None:
        points = numbered_points(10)

        first = choose_initial_centers(points, n_clusters=3, seed=0)
        second = choose_initial_centers(points, n_clusters=3, seed=1)

        assert first.tolist() != second.tolist()


def three_pairs() -> np.ndarray:
    return np.array([[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]])


class TestBestKMeans:
    def test_初期中心によっては局所解に陥る(self) -> None:
        stuck = kmeans(three_pairs(), np.array([[0.0], [1.0], [10.0]]))

        assert stuck.sse == 101.0

    def test_複数の初期中心の候補のうちSSEが最小の結果を返す(self) -> None:
        candidates = [
            np.array([[0.0], [1.0], [10.0]]),
            np.array([[0.0], [10.0], [20.0]]),
        ]

        result = best_kmeans(three_pairs(), candidates)

        assert result.sse == 1.5
        assert result.centers.tolist() == [[0.5], [10.5], [20.5]]


class TestSseByClusterCount:
    def test_クラスタ数ごとにクラスタリングしたときのSSEを求める(self) -> None:
        sse = sse_by_cluster_count(two_groups(), cluster_counts=[1, 2], seed=0)

        assert sse == {1: 201.0, 2: 1.0}

    def test_初期中心を変えて繰り返し最小のSSEを使う(self) -> None:
        sse = sse_by_cluster_count(three_pairs(), cluster_counts=[3], seed=0, n_init=10)

        assert sse == {3: 1.5}


class TestSummarizeClusters:
    def test_クラスタごとの件数と平均を件数の多い順に並べる(self) -> None:
        df = pd.DataFrame({"Fresh": [100, 300, 1000], "Milk": [20, 40, 900]})
        labels = np.array([1, 1, 0])

        summary = summarize_clusters(df, labels)

        assert summary.index.to_list() == [1, 0]
        assert summary["件数"].to_list() == [2, 1]
        assert summary["Fresh"].to_list() == [200.0, 1000.0]
        assert summary["Milk"].to_list() == [30.0, 900.0]


@requires_data("Wholesale.csv")
class TestWholesaleData:
    def test_実データから440件の支出額6列を読み込む(self) -> None:
        df = load_spending(data_dir() / "Wholesale.csv")

        assert df.shape == (440, 6)

    def test_標準化したデータのクラスタ数1のSSEは件数と列数の積になる(self) -> None:
        points = standardize(load_spending(data_dir() / "Wholesale.csv")).to_numpy()

        sse = sse_by_cluster_count(points, cluster_counts=[1], seed=0)

        assert sse[1] == pytest.approx(440 * 6)

    def test_クラスタ数を増やすほどSSEが小さくなる(self) -> None:
        points = standardize(load_spending(data_dir() / "Wholesale.csv")).to_numpy()

        sse = sse_by_cluster_count(points, cluster_counts=list(range(1, 11)), seed=0)

        values = list(sse.values())
        assert all(a > b for a, b in zip(values, values[1:], strict=False))

    def test_実行するとSSEとクラスタごとの件数と平均支出額を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 440（支出額 6 列）\n"
            "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:\n"
            "   1: 2640.00\n"
            "   2: 1954.80\n"
            "   3: 1610.15\n"
            "   4: 1345.47\n"
            "   5: 1070.46\n"
            "   6: 934.77\n"
            "   7: 890.83\n"
            "   8: 798.85\n"
            "   9: 746.01\n"
            "  10: 617.64\n"
            "クラスタ数 5 のクラスタごとの件数と平均支出額:\n"
            "    件数  Fresh   Milk  Grocery  Frozen  Detergents_Paper  Delicassen\n"
            "1  277   9203   2969     3773    2594               967         983\n"
            "4   96   5509  10556    16478    1420              7199        1659\n"
            "2   54  36043   5007     6118    6736              1006        2593\n"
            "3   11  16911  34864    46126    3245             23008        4177\n"
            "0    2  34782  30367    16898   48702               756       26776\n"
        )


def three_blobs() -> np.ndarray:
    rng = np.random.default_rng(123)
    return np.vstack(
        [
            rng.normal(loc=center, scale=1.0, size=(20, 2))
            for center in ([0.0, 0.0], [6.0, 6.0], [0.0, 8.0])
        ]
    )


class TestCompareWithScikitLearn:
    def test_同じ初期中心を与えるとscikit_learnのKMeansと同じ結果になる(self) -> None:
        points = three_blobs()
        initial_centers = choose_initial_centers(points, n_clusters=3, seed=0)

        result = kmeans(points, initial_centers)
        model = KMeans(n_clusters=3, init=initial_centers, n_init=1, tol=0.0)
        model.fit(points)

        assert result.labels.tolist() == model.labels_.tolist()
        assert result.centers == pytest.approx(model.cluster_centers_)
        assert result.sse == pytest.approx(model.inertia_)
