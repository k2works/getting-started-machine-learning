# frozen_string_literal: true

require "test_helper"

# 第 12 章のリッジ回帰・ラッソ回帰と、係数を持つモデルのテスト。
class Chapter12RegularizationTest < Minitest::Test
  C = GettingStartedMl::Chapter12

  # t = 2a + 3b + 1 ちょうどの架空のデータ。c は正解に関係しない列。
  def sample
    rows = [[1.0, 2.0, 0.3], [2.0, 1.0, -0.2], [3.0, 5.0, 0.1], [4.0, 3.0, -0.3], [5.0, 4.0, 0.2], [6.0, 6.0, -0.1]]

    [%w[a b c], rows, rows.map { |a, b, _| (2.0 * a) + (3.0 * b) + 1.0 }]
  end

  def test_罰則が零なら最小二乗法と同じ解になる
    model = C.fit_ridge(*sample, 0.0)

    assert_in_delta 2.0, model.coefficients[0], 1e-9
    assert_in_delta 3.0, model.coefficients[1], 1e-9
    assert_in_delta 0.0, model.coefficients[2], 1e-9
    assert_in_delta 1.0, model.intercept, 1e-9
  end

  def test_罰則を強くすると係数が小さくなる
    weak = C.fit_ridge(*sample, 1.0)
    strong = C.fit_ridge(*sample, 100.0)

    assert_operator strong.coefficient_abs_sum, :<, weak.coefficient_abs_sum
  end

  def test_罰則を強くしても切片は正解の平均に近いまま
    _, _, t = sample

    assert_in_delta t.sum / t.size, C.fit_ridge(*sample, 1e9).intercept, 1e-6
  end

  def test_負の罰則は受け付けない
    error = assert_raises(ArgumentError) { C.fit_ridge(*sample, -1.0) }

    assert_equal "正則化の強さは 0 以上にしてください: -1.0", error.message
  end

  def test_行と正解の件数が違えば失敗する
    columns, rows, t = sample

    assert_raises(ArgumentError) { C.fit_ridge(columns, rows, t.drop(1), 1.0) }
    assert_raises(ArgumentError) { C.fit_lasso(columns, rows, t.drop(1), 1.0) }
  end

  def test_軟しきい値作用素は零に寄せる
    assert_in_delta 2.0, C.soft_threshold(3.0, 1.0), 1e-12
    assert_in_delta(-2.0, C.soft_threshold(-3.0, 1.0), 1e-12)
    assert_equal 0.0, C.soft_threshold(0.5, 1.0)
  end

  def test_罰則が零ならラッソ回帰も最小二乗法と同じ解になる
    model = C.fit_lasso(*sample, 0.0)

    assert_in_delta 2.0, model.coefficients[0], 1e-6
    assert_in_delta 3.0, model.coefficients[1], 1e-6
    assert_in_delta 1.0, model.intercept, 1e-6
  end

  def test_正解に効かない列が先に零になる
    model = C.fit_lasso(*sample, 1.0)

    assert_equal ["c"], model.zero_columns
  end

  def test_罰則を強くすると係数がちょうど零になる
    columns, = sample

    assert_equal columns, C.fit_lasso(*sample, 1000.0).zero_columns
  end

  def test_ラッソ回帰も負の罰則は受け付けない
    assert_raises(ArgumentError) { C.fit_lasso(*sample, -1.0) }
  end

  def test_モデルは列名と係数の数がそろっていなければ作れない
    error = assert_raises(ArgumentError) { C::RegularizedModel.new(columns: %w[a b], coefficients: [1.0], intercept: 0.0) }

    assert_equal "件数が違います: 2 と 1", error.message
  end

  def test_モデルは係数と切片で予測する
    model = C::RegularizedModel.new(columns: %w[a b], coefficients: [2.0, -1.0], intercept: 0.5)

    assert_equal [1.5, 4.5], model.predict([[1.0, 1.0], [3.0, 2.0]])
    assert_in_delta 3.0, model.coefficient_abs_sum, 1e-12
    assert_raises(ArgumentError) { model.predict([[1.0]]) }
  end
end
