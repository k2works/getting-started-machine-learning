# frozen_string_literal: true

require "test_helper"

# 第 12 章のリッジ回帰・ラッソ回帰を、Rumale の Ridge・Lasso と突き合わせるテスト。
class Chapter12RumaleTest < Minitest::Test
  C = GettingStartedMl::Chapter12

  # 直線に乗らない 8 件。列の平均は 0 でなく、正解の平均も 0 でない。
  def sample
    rows = [[1.0, 2.0], [2.0, 1.0], [3.0, 5.0], [4.0, 3.0], [5.0, 4.0], [6.0, 6.0], [7.0, 2.0], [8.0, 7.0]]
    noise = [0.5, -0.3, 0.8, -0.6, 0.2, -0.9, 0.4, 0.1]

    [%w[a b], rows, rows.zip(noise).map { |(a, b), e| (2.0 * a) + (3.0 * b) + 10.0 + e }]
  end

  def assert_same_model(own, library, delta)
    own.coefficients.zip(library.coefficients).each { |mine, theirs| assert_in_delta mine, theirs, delta }
    assert_in_delta own.intercept, library.intercept, delta
  end

  def test_件数で割ればRumaleのリッジ回帰は自作と一致する
    assert_same_model C.fit_ridge(*sample, 2.0), C.rumale_ridge(*sample, 2.0), 1e-6
  end

  def test_Rumaleのラッソ回帰は同じ罰則で自作と一致する
    assert_same_model C.fit_lasso(*sample, 5.0), C.rumale_lasso(*sample, 5.0), 1e-8
  end

  def test_中心化せずに渡すとRumaleは切片にも罰則をかける
    # 列を足して切片を学習させると、切片も罰則で 0 のほうへ引き寄せられる
    own = C.fit_lasso(*sample, 5.0)
    raw = C.rumale_lasso_raw(*sample, 5.0)

    assert_operator raw.intercept.abs, :<, own.intercept.abs
  end

  def test_中心化せずに渡すとRumaleのリッジ回帰も切片が縮む
    assert_operator C.rumale_ridge_raw(*sample, 20.0).intercept.abs, :<, C.fit_ridge(*sample, 20.0).intercept.abs
  end

  def test_リッジ回帰の罰則は件数で割って渡す
    assert_in_delta 0.25, C.rumale_ridge_reg_param(2.0, 8), 1e-12
  end
end
