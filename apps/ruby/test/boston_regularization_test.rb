# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Boston.csv）で正則化とモデル選択をするテスト。学習データが無ければスキップする。
class BostonRegularizationTest < Minitest::Test
  C = GettingStartedMl::Chapter12

  def data
    path = File.join(GettingStartedMl::Dataset.dir, "Boston.csv")
    skip "学習データ Boston.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    C.prepare(path, test_size: 0.3, validation_size: 0.3, seed: 0)
  end

  def test_正則化を強くすると係数の絶対値の合計は単調に減る
    split = data
    sums = C.run_ridge_experiments(split, C::ALPHAS).map(&:coefficient_abs_sum)

    assert_equal sums.sort.reverse, sums
  end

  def test_実データでもラッソ回帰はRumaleと同じ係数になる
    split = data
    own = C.fit_lasso(split.feature_names, split.x_train, split.t_train, 10.0)
    library = C.rumale_lasso(split.feature_names, split.x_train, split.t_train, 10.0)

    assert_equal ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"], own.zero_columns
    assert_equal own.coefficients, library.coefficients
  end

  def test_実行すると正則化とモデル選択の結果を表示する
    data
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      データ件数: 98（外れ値 2 件を除外）
      訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
      特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
      alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計
      0.0\t0.8605\t0.8252\t14.215
      0.1\t0.8605\t0.8264\t13.946
      1.0\t0.8597\t0.8309\t12.991
      10.0\t0.8450\t0.8097\t10.409
      100.0\t0.6460\t0.5722\t6.013
      検証データで選んだ alpha: 1.0
      テストデータの決定係数: 線形回帰 0.3357, リッジ回帰 0.4384
      リッジ回帰の係数の最大の差（自作と Rumale）: 1.38e-05
      ラッソ回帰（alpha ごとに 0 になった特徴量）
        alpha=1.0: 自作 [] / Rumale []
        alpha=10.0: 自作 ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"]
        alpha=50.0: 自作 ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
        alpha=100.0: 自作 ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
      ラッソ回帰の係数の最大の差（自作と Rumale）: 0.00e+00
      中心化せずに Rumale に渡したとき（切片にも罰則がかかる）
        リッジ回帰（alpha=1.0）の切片: 自作 22.17 / Rumale 20.61
        ラッソ回帰（alpha=10.0）で 0 になった特徴量: 自作 ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
    TEXT
  end
end
