# frozen_string_literal: true

require "test_helper"

# 第 12 章の前処理（外れ値・標準化と 2 次の項）とモデル選択のテスト。
class Chapter12SelectionTest < Minitest::Test
  C = GettingStartedMl::Chapter12
  Features = GettingStartedMl::Chapter02::Features

  def row(first, second)
    Features.new(columns: %w[a b], values: [first, second])
  end

  def train
    [row(1.0, 10.0), row(3.0, 20.0), row(5.0, 60.0)]
  end

  def experiment(alpha, validation_score)
    C::Experiment.new(alpha:, train_score: 0.9, validation_score:, coefficient_abs_sum: 1.0)
  end

  def test_列名は元の列と二次の項の順に並ぶ
    assert_equal ["a", "b", "a^2", "a b", "b^2"], C::PolynomialScaler.fit(train).feature_names
  end

  def test_検証データは訓練データの平均と標準偏差で変換される
    # 訓練データの a の平均は 3 なので、3 は標準化すると 0 になり、2 乗の項も 0 になる
    values = C::PolynomialScaler.fit(train).transform([row(3.0, 30.0)]).first

    assert_in_delta 0.0, values[0], 1e-12
    assert_in_delta 0.0, values[2], 1e-12
    assert_equal 5, values.size
  end

  def test_zスコアの絶対値が三を超える値を持つ行を除く
    rows = Array.new(11) { GettingStartedMl::Chapter02::Row.new("a" => "0", "b" => "1") } +
           [GettingStartedMl::Chapter02::Row.new("a" => "100", "b" => "1")]
    table = GettingStartedMl::Chapter02::Table.new(columns: %w[a b], rows:)

    assert_equal 11, C.remove_outliers(table, %w[a b], 3.0).rows.size
  end

  def test_検証データの決定係数が最も高い実験を選ぶ
    assert_in_delta 1.0, C.best_experiment([experiment(0.1, 0.7), experiment(1.0, 0.8), experiment(10.0, 0.6)]).alpha
  end

  def test_同じ値なら先の実験を選ぶ
    assert_in_delta 0.1, C.best_experiment([experiment(0.1, 0.8), experiment(1.0, 0.8)]).alpha
  end

  def test_実験が無ければ選べない
    error = assert_raises(ArgumentError) { C.best_experiment([]) }

    assert_equal "実験の結果が 1 件もありません", error.message
  end

  def test_alphaごとに訓練データと検証データの決定係数を記録する
    rows = [[1.0], [2.0], [3.0], [4.0]]
    t = [3.0, 5.0, 7.1, 8.9]
    split = C::BostonSplit.new(x_train: rows, t_train: t, x_valid: rows, t_valid: t, x_test: [], t_test: [],
                               feature_names: ["x"], kept: 4, removed: 0)
    experiments = C.run_ridge_experiments(split, [0.0, 10.0])

    assert_equal [0.0, 10.0], experiments.map(&:alpha)
    assert_in_delta experiments[0].train_score, experiments[0].validation_score, 1e-12
    assert_operator experiments[1].coefficient_abs_sum, :<, experiments[0].coefficient_abs_sum
  end
end
