# frozen_string_literal: true

require "test_helper"

class Chapter13PcaTest < Minitest::Test
  C = GettingStartedMl::Chapter13

  def matrix(rows)
    Numo::DFloat.cast(rows)
  end

  def symmetric
    matrix([[4.0, 1.0, 0.5], [1.0, 3.0, 0.2], [0.5, 0.2, 1.0]])
  end

  def test_分散共分散行列は件数から一を引いた数で割る
    # 1 列目の分散は ((-1)^2 + 0 + 1^2) / 2 = 1
    covariance = C.covariance_matrix(matrix([[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]]))

    assert_in_delta 1.0, covariance[0, 0], 1e-12
    assert_in_delta 2.0, covariance[0, 1], 1e-12
    assert_in_delta 4.0, covariance[1, 1], 1e-12
  end

  def test_データが一件だけなら分散を求められない
    error = assert_raises(ArgumentError) { C.covariance_matrix(matrix([[1.0, 2.0]])) }

    assert_equal "主成分分析には 2 件以上のデータが必要です（1 件）", error.message
  end

  def test_対角行列の固有値はそのまま対角に並ぶ
    values, vectors = C.jacobi_eigen(matrix([[3.0, 0.0], [0.0, 1.0]]))

    assert_equal [3.0, 1.0], values
    assert_equal [[1.0, 0.0], [0.0, 1.0]], vectors.to_a
  end

  def test_固有値と固有ベクトルは定義の式を満たす
    values, vectors = C.jacobi_eigen(symmetric)

    values.each_with_index do |value, index|
      v = vectors[true, index]

      assert_in_delta 0.0, (symmetric.dot(v) - (v * value)).abs.max, 1e-9
    end
  end

  def test_正方行列でなければ固有値分解できない
    error = assert_raises(ArgumentError) { C.jacobi_eigen(matrix([[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]])) }

    assert_equal "正方行列ではありません: 2 行 3 列", error.message
  end

  def test_決めた回数で収束しなければ失敗にする
    error = assert_raises(C::NotConvergedError) { C.jacobi_eigen(symmetric, max_sweeps: 1) }

    assert_equal "1 回繰り返しても固有値分解が収束しませんでした", error.message
  end

  def test_完全に相関する二列の第一主成分は四十五度の向きになる
    # 2 列目が 1 列目と同じ値なので、ばらつきはすべて (1, 1) の向きにある
    model = C.fit(matrix([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0], [4.0, 4.0]]), 2)
    root = 1.0 / Math.sqrt(2.0)

    assert_in_delta root, model.components[0, 0], 1e-9
    assert_in_delta root, model.components[0, 1], 1e-9
    assert_in_delta 1.0, model.explained_variance_ratio[0], 1e-9
    assert_in_delta 0.0, model.explained_variance_ratio[1], 1e-9
  end

  def test_寄与率は大きい順に並ぶ
    # 1 列目のばらつきが 2 列目より大きい
    model = C.fit(matrix([[-2.0, -0.1], [-1.0, 0.1], [1.0, -0.1], [2.0, 0.1]]), 2)

    assert_operator model.explained_variance_ratio[0], :>, model.explained_variance_ratio[1]
    assert_in_delta 1.0, model.explained_variance_ratio.sum, 1e-12
    assert_operator model.components[0, 0].abs, :>, 0.99
  end

  def test_主成分の数は列の数までしか選べない
    x = matrix([[1.0, 1.0], [2.0, 3.0]])
    error = assert_raises(ArgumentError) { C.fit(x, 3) }

    assert_equal "主成分の数は 1 以上 2 以下にしてください: 3", error.message
    assert_raises(ArgumentError) { C.fit(x, 0) }
  end

  def test_符号は絶対値が最大の要素が正になるようにそろえる
    normalized = C.normalize_signs(matrix([[-0.8, 0.6], [0.8, -0.6]]))

    assert_equal [[0.8, -0.6], [0.8, -0.6]], normalized.to_a
  end

  def test_射影した値は平均を引いてから主成分にかけた値になる
    x = matrix([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]])
    projected = C.transform(C.fit(x, 1), x)

    # 平均 (2, 2) の点は原点に移る
    assert_in_delta 0.0, projected[1, 0], 1e-9
    assert_in_delta 0.0, projected[0, 0] + projected[2, 0], 1e-9
  end

  def test_累積寄与率がしきい値に届くまでの数を返す
    ratios = [0.5, 0.3, 0.2]

    assert_equal 1, C.components_needed(ratios, 0.4)
    assert_equal 2, C.components_needed(ratios, 0.8)
    assert_equal 3, C.components_needed(ratios, 0.9)
    # しきい値に届かなければ、すべての主成分を使う
    assert_equal 3, C.components_needed(ratios, 1.5)
  end

  def test_係数の絶対値が大きい順に列名を返す
    loadings = C.top_loadings([0.1, -0.9, 0.5], %w[A B C], 2)

    assert_equal [C::Loading.new(column: "B", value: -0.9), C::Loading.new(column: "C", value: 0.5)], loadings
  end
end
