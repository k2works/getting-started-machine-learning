import pytest
from sklearn.linear_model import Ridge

from lib.chapter12.__main__ import main
from lib.chapter12.boston_features import prepare_boston
from lib.chapter12.regularization import fit_ridge
from lib.dataset import data_dir
from test.markers import requires_data


@requires_data("Boston.csv")
class TestBostonData:
    def test_外れ値を除いて訓練データと検証データとテストデータに分ける(self) -> None:
        dataset = prepare_boston(
            data_dir() / "Boston.csv", test_size=0.3, validation_size=0.3, seed=0
        )

        assert (len(dataset.t_train), len(dataset.t_valid), len(dataset.t_test)) == (
            47,
            21,
            30,
        )

    def test_実データでも自作のリッジ回帰はscikit_learnと同じ係数になる(self) -> None:
        dataset = prepare_boston(
            data_dir() / "Boston.csv", test_size=0.3, validation_size=0.3, seed=0
        )

        model = fit_ridge(dataset.x_train, dataset.t_train, alpha=10.0)
        expected = Ridge(alpha=10.0).fit(dataset.x_train, dataset.t_train)

        assert model.coef == pytest.approx(expected.coef_)
        assert model.intercept == pytest.approx(expected.intercept_)

    def test_実行すると正則化の実験結果を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 98（外れ値 2 件を除外）\n"
            "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件\n"
            "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, "
            "PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n"
            "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n"
            "  0.0  0.8933  0.5865  19.504\n"
            "  0.1  0.8933  0.5898  19.396\n"
            "  1.0  0.8923  0.6136  18.486\n"
            " 10.0  0.8567  0.6797  12.841\n"
            "100.0  0.6653  0.5711  4.990\n"
            "検証データで選んだ alpha: 10.0\n"
            "テストデータの決定係数: 線形回帰 0.5683, リッジ回帰 0.6317\n"
            "ラッソ回帰（alpha=0.5）で係数が 0 になった特徴量: RM, PTRATIO LSTAT\n"
        )
