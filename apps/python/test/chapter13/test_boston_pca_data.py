import pytest
from sklearn.decomposition import PCA

from lib.chapter13.__main__ import main
from lib.chapter13.boston_standardized import load_standardized_boston
from lib.chapter13.pca import fit_pca, normalize_signs
from lib.dataset import data_dir
from test.markers import requires_data


@requires_data("Boston.csv")
class TestBostonPcaData:
    def test_CRIMEをダミー変数にして15列の標準化済みデータにする(self) -> None:
        df = load_standardized_boston(data_dir() / "Boston.csv")

        assert df.shape == (100, 15)

    def test_実データでも自作のPCAはscikit_learnと同じ寄与率と主成分になる(
        self,
    ) -> None:
        x = load_standardized_boston(data_dir() / "Boston.csv").to_numpy()

        model = fit_pca(x, n_components=15)
        expected = PCA().fit(x)

        assert model.explained_variance_ratio == pytest.approx(
            expected.explained_variance_ratio_
        )
        assert model.components == pytest.approx(normalize_signs(expected.components_))

    def test_実行すると寄与率と主成分の解釈を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 100, 列数: 15\n"
            "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, "
            "PC5 0.0623, PC6 0.0581\n"
            "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）\n"
            "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328\n"
            "第 2 主成分で影響の大きい列: PRICE 0.444, low 0.423, RM 0.405\n"
        )
