import numpy as np
import pytest
from numpy.typing import NDArray
from sklearn.decomposition import PCA

from lib.chapter13.pca import (
    PcaModel,
    components_needed,
    covariance_matrix,
    fit_pca,
    normalize_signs,
    top_loadings,
    transform,
)


def random_dataset() -> NDArray[np.float64]:
    rng = np.random.default_rng(0)
    base = rng.normal(size=(40, 2))
    mixing = np.array([[2.0, 0.5], [0.3, 1.0], [1.0, -1.0], [0.0, 0.2]])
    noise = rng.normal(scale=0.1, size=(40, 4))
    x: NDArray[np.float64] = base @ mixing.T + noise
    return x


class TestCovarianceMatrix:
    def test_2列の分散と共分散を並べた行列を返す(self) -> None:
        x = np.array([[1.0, 2.0], [3.0, 6.0], [5.0, 10.0]])

        assert covariance_matrix(x).tolist() == [
            pytest.approx([4.0, 8.0]),
            pytest.approx([8.0, 16.0]),
        ]

    def test_3列でもnumpyのcovと同じ行列を返す(self) -> None:
        x = np.random.default_rng(0).normal(size=(20, 3))

        assert covariance_matrix(x) == pytest.approx(np.cov(x, rowvar=False))


class TestFitPca:
    def test_完全に相関する2列なら第1主成分だけで分散をすべて説明する(self) -> None:
        x = np.array([[1.0, 2.0], [3.0, 6.0], [5.0, 10.0]])

        model = fit_pca(x, n_components=2)

        assert model.components[0] == pytest.approx([1 / np.sqrt(5), 2 / np.sqrt(5)])
        assert model.explained_variance_ratio == pytest.approx([1.0, 0.0])

    def test_主成分は寄与率の大きい順に指定した数だけ並ぶ(self) -> None:
        x = random_dataset()

        model = fit_pca(x, n_components=3)

        ratios = model.explained_variance_ratio.tolist()
        assert len(ratios) == 3
        assert ratios == sorted(ratios, reverse=True)

    def test_scikit_learnのPCAと符号をそろえれば同じ主成分になる(self) -> None:
        x = random_dataset()

        model = fit_pca(x, n_components=3)
        expected = PCA(n_components=3).fit(x)

        assert model.components == pytest.approx(normalize_signs(expected.components_))
        assert model.explained_variance == pytest.approx(expected.explained_variance_)
        assert model.explained_variance_ratio == pytest.approx(
            expected.explained_variance_ratio_
        )


class TestTransform:
    def test_平均を引いてから主成分の向きに射影する(self) -> None:
        model = PcaModel(
            mean=np.array([1.0, 2.0]),
            components=np.array([[0.6, 0.8]]),
            explained_variance=np.array([1.0]),
            explained_variance_ratio=np.array([1.0]),
        )

        assert transform(model, np.array([[2.0, 3.0], [1.0, 2.0]])).tolist() == [
            pytest.approx([1.4]),
            pytest.approx([0.0]),
        ]


class TestComponentsNeeded:
    def test_累積寄与率がしきい値に届くまでの主成分の数を返す(self) -> None:
        assert components_needed(np.array([0.5, 0.25, 0.25]), threshold=0.75) == 2

    def test_しきい値を上げると必要な主成分の数が増える(self) -> None:
        assert components_needed(np.array([0.5, 0.25, 0.25]), threshold=0.8) == 3


class TestTopLoadings:
    def test_係数の絶対値が大きい順に列名と係数を返す(self) -> None:
        component = np.array([0.1, -0.7, 0.5])

        assert top_loadings(component, ["ZN", "DIS", "TAX"], k=2) == [
            ("DIS", -0.7),
            ("TAX", 0.5),
        ]


class TestNormalizeSigns:
    def test_絶対値が最大の要素が正になるように主成分の向きをそろえる(self) -> None:
        components = np.array([[0.6, -0.8], [-0.8, 0.6]])

        assert normalize_signs(components).tolist() == [
            pytest.approx([-0.6, 0.8]),
            pytest.approx([0.8, -0.6]),
        ]
