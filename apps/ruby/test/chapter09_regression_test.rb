# frozen_string_literal: true

require "test_helper"

class Chapter09RegressionTest < Minitest::Test
  C = GettingStartedMl::Chapter09
  Chapter02 = GettingStartedMl::Chapter02

  def row(rooms, lstat)
    Chapter02::Features.new(columns: %w[RM LSTAT], values: [rooms, lstat])
  end

  # 外れ値

  def test_分位数を線形補間で求める
    values = [4.0, 1.0, 3.0, 2.0]

    # 位置 = 3 * 0.25 = 0.75 なので、1 と 2 の間を 0.75 の割合で補間する
    assert_in_delta 1.75, C.quantile(values, 0.25), 1e-12
    assert_in_delta 3.25, C.quantile(values, 0.75), 1e-12
  end

  def test_値が無ければ分位数を求められない
    error = assert_raises(ArgumentError) { C.quantile([], 0.5) }

    assert_equal "値が 1 件もありません", error.message
  end

  def test_四分位範囲から離れた値を外れ値とする
    assert_equal [false, false, false, false, true], C.iqr_outliers([1.0, 2.0, 3.0, 4.0, 100.0])
  end

  def test_外れ値は訓練データからだけ取り除く
    split = Chapter02::TrainTestSplit.new(
      x_train: [1.0, 2.0, 3.0, 4.0, 100.0].map { |value| row(value, 0.0) }, x_test: [row(100.0, 0.0)],
      t_train: [1.0, 2.0, 3.0, 4.0, 100.0], t_test: [100.0]
    )
    removed = C.remove_target_outliers(split)

    assert_equal [1.0, 2.0, 3.0, 4.0], removed.t_train
    assert_equal([1.0, 2.0, 3.0, 4.0], removed.x_train.map { |features| features.value("RM") })
    assert_equal [100.0], removed.t_test
  end

  # 線形回帰と決定係数

  def test_直線に乗る点を当てる
    rows = [[0.0], [1.0], [2.0]]
    t = [1.0, 3.0, 5.0]
    model = C::LinearModel.fit(rows, t)

    assert_in_delta 1.0, model.intercept, 1e-9
    assert_in_delta 2.0, model.weights.first, 1e-9
    assert_in_delta 1.0, C.r_squared(t, model.predict(rows)), 1e-9
  end

  def test_列が二つでも解ける
    rows = [[1.0, 0.0], [0.0, 1.0], [1.0, 1.0], [2.0, 3.0]]
    model = C::LinearModel.fit(rows, rows.map { |a, b| (3 * a) - b + 2 })

    assert_in_delta 2.0, model.intercept, 1e-9
    assert_in_delta 3.0, model.weights[0], 1e-9
    assert_in_delta(-1.0, model.weights[1], 1e-9)
  end

  def test_同じ値の列が二つあれば解けない
    error = assert_raises(C::SingularError) { C::LinearModel.fit([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]], [1.0, 2.0, 3.0]) }

    assert_equal "特徴量の列が互いに独立でないため、正規方程式を解けません", error.message
  end

  def test_平均を当てるだけなら決定係数は0になる
    assert_in_delta 0.0, C.r_squared([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12
  end

  # 特徴量の組の決定係数

  # 価格が 3 × RM² + 1 になる架空のデータ。
  def squared_split
    make = ->(rm) { Chapter02::Features.new(columns: %w[RM], values: [rm]) }
    price = ->(rm) { (3 * rm * rm) + 1 }
    train = [1.0, 2.0, 3.0, 4.0, 5.0]
    test = [6.0, 7.0]

    Chapter02::TrainTestSplit.new(x_train: train.map(&make), x_test: test.map(&make),
                                  t_train: train.map(&price), t_test: test.map(&price))
  end

  def test_二乗の項が無いと当てきれない
    scores = C.score_feature_set(squared_split, %w[RM], %w[RM])

    assert_operator scores.train, :<, 0.99
  end

  def test_二乗の項を加えると決定係数が一になる
    scores = C.score_feature_set(squared_split, %w[RM], ["RM", "RM^2"])

    assert_in_delta 1.0, scores.train, 1e-9
    assert_in_delta 1.0, scores.test, 1e-9
  end
end
