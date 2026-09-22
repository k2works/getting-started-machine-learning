# frozen_string_literal: true

require "test_helper"

class Chapter14KMeansTest < Minitest::Test
  C = GettingStartedMl::Chapter14

  def matrix(rows)
    Numo::DFloat.cast(rows)
  end

  # 原点のまわりと (10, 10) のまわりに分かれた 4 点。
  def points
    matrix([[0.0, 0.0], [1.0, 1.0], [10.0, 10.0], [11.0, 11.0]])
  end

  # 1 次元に 3 組並べた点。3 つに分けるなら SSE は 0.5 × 3 = 1.5 が最小。
  def three_pairs
    matrix([[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]])
  end

  def test_距離の二乗は各次元の差の二乗の和
    assert_in_delta 25.0, C.squared_distance(Numo::DFloat[0.0, 0.0], Numo::DFloat[3.0, 4.0]), 1e-12
  end

  def test_各点は最も近い中心に割り当てられる
    assert_equal [0, 0, 1, 1], C.assign_clusters(points, matrix([[0.0, 0.0], [10.0, 10.0]]))
  end

  def test_距離が同じなら番号の小さいクラスタに割り当てる
    assert_equal [0], C.assign_clusters(matrix([[0.0, 0.0]]), matrix([[1.0, 0.0], [-1.0, 0.0]]))
  end

  def test_中心は割り当てられた点の平均になる
    centers = C.update_centers(points, [0, 0, 1, 1], matrix([[0.0, 0.0], [0.0, 0.0]]))

    assert_equal [[0.5, 0.5], [10.5, 10.5]], centers.to_a
  end

  def test_点が割り当てられなかったクラスタは前の中心を保つ
    centers = C.update_centers(points, [0, 0, 0, 0], matrix([[0.0, 0.0], [99.0, 99.0]]))

    assert_equal [99.0, 99.0], centers[1, true].to_a
  end

  def test_誤差平方和は中心からの距離の二乗の合計
    # 4 点それぞれ 0.5^2 + 0.5^2 = 0.5
    assert_in_delta 2.0, C.sum_of_squared_errors(points, [0, 0, 1, 1], matrix([[0.5, 0.5], [10.5, 10.5]])), 1e-12
  end

  def test_中心が変わらなくなるまで繰り返す
    result = C.fit(points, matrix([[0.0, 0.0], [11.0, 11.0]]))

    assert_equal [0, 0, 1, 1], result.labels
    assert_equal [[0.5, 0.5], [10.5, 10.5]], result.centers.to_a
    assert_in_delta 2.0, result.sse, 1e-12
  end

  def test_繰り返しの上限で止める
    # 1 次元の 3 組を 2 つに分けると、1 回目の更新では中心がまだ動く
    once = C.fit(three_pairs, matrix([[0.0], [1.0]]), max_iterations: 1)
    converged = C.fit(three_pairs, matrix([[0.0], [1.0]]))

    refute_equal converged.centers.to_a, once.centers.to_a
  end

  def test_初期中心は点の中から選ぶ
    centers = C.choose_initial_centers(points, 2, 0)

    centers.to_a.each { |center| assert_includes points.to_a, center }
  end

  def test_クラスタ数は点の数までしか選べない
    error = assert_raises(ArgumentError) { C.choose_initial_centers(points, 5, 0) }

    assert_equal "クラスタ数は 1 以上 4 以下にしてください: 5", error.message
    assert_raises(ArgumentError) { C.choose_initial_centers(points, 0, 0) }
  end

  def test_初期中心によっては局所解に止まる
    # 左の組から 2 点・中央の組から 1 点を初期中心にすると、右の 4 点が 1 つにまとまる
    stuck = C.fit(three_pairs, matrix([[0.0], [1.0], [10.0]]))
    best = C.fit(three_pairs, matrix([[0.0], [10.0], [20.0]]))

    assert_in_delta 1.5, best.sse, 1e-12
    assert_operator stuck.sse, :>, best.sse
  end

  def test_何通りか試せば局所解から抜け出せる
    assert_in_delta 1.5, C.fit_with_restarts(three_pairs, 3, seed: 0, n_init: 10).sse, 1e-12
  end

  def test_クラスタ数を増やすと誤差平方和は小さくなる
    sse = C.sse_by_cluster_count(points, [1, 2, 4], seed: 0, n_init: 10).map(&:last)

    assert_operator sse[0], :>, sse[1]
    assert_operator sse[1], :>, sse[2]
    # 点の数だけクラスタを作れば、中心は点そのものになる
    assert_in_delta 0.0, sse[2], 1e-12
  end
end
