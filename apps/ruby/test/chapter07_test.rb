# frozen_string_literal: true

require "test_helper"

class Chapter07Test < Minitest::Test
  C = GettingStartedMl::Chapter07

  def test_二元一次の連立方程式を解く
    w = C.solve(Numo::DFloat[[2, 1], [1, 3]], Numo::DFloat[5, 10])

    assert_in_delta 1.0, w[0], 1e-12
    assert_in_delta 3.0, w[1], 1e-12
  end

  def test_先頭のピボットが零でも入れ替えて解ける
    w = C.solve(Numo::DFloat[[0, 1], [1, 0]], Numo::DFloat[2, 3])

    assert_in_delta 3.0, w[0], 1e-12
    assert_in_delta 2.0, w[1], 1e-12
  end

  def test_解が定まらなければ失敗する
    error = assert_raises(ArgumentError) { C.solve(Numo::DFloat[[1, 2], [2, 4]], Numo::DFloat[3, 6]) }

    assert_equal "解けない連立方程式です", error.message
  end

  def test_正方でない行列は解けない
    error = assert_raises(ArgumentError) { C.solve(Numo::DFloat[[1, 2, 3], [4, 5, 6]], Numo::DFloat[1, 2]) }

    assert_equal "件数が違います: 2 と 3", error.message
  end

  def test_右辺の件数が合わなければ解けない
    error = assert_raises(ArgumentError) { C.solve(Numo::DFloat[[2, 1], [1, 3]], Numo::DFloat[5]) }

    assert_equal "件数が違います: 2 と 1", error.message
  end

  def test_解いても元の行列と右辺は変わらない
    a = Numo::DFloat[[0, 1], [1, 0]]
    b = Numo::DFloat[2, 3]
    C.solve(a, b)

    assert_equal [[0.0, 1.0], [1.0, 0.0]], a.to_a
    assert_equal [2.0, 3.0], b.to_a
  end

  def test_平均絶対誤差は誤差の絶対値の平均になる
    assert_in_delta 1.5, C.mean_absolute_error([1.0, 2.0], [2.0, 4.0]), 1e-12
  end

  def test_二乗平均平方根誤差は大きい誤差を重く見る
    assert_in_delta Math.sqrt(5.0 / 2), C.root_mean_squared_error([1.0, 2.0], [2.0, 4.0]), 1e-12
  end

  def test_完全に当たれば決定係数は一になる
    assert_in_delta 1.0, C.r2_score([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1e-12
  end

  def test_平均を答え続けると決定係数は零になる
    assert_in_delta 0.0, C.r2_score([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12
  end

  def test_実測値と予測値の件数が違えば評価できない
    error = assert_raises(ArgumentError) { C.r2_score([1.0], [1.0, 2.0]) }

    assert_equal "件数が違います: 1 と 2", error.message
  end
end
