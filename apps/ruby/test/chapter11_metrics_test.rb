# frozen_string_literal: true

require "test_helper"

# 第 11 章の混同行列と、そこから求める評価指標のテスト。
class Chapter11MetricsTest < Minitest::Test
  C = GettingStartedMl::Chapter11

  # 正解 [1 1 1 0 0]、予測 [1 1 0 0 1] の混同行列。TP=2, FN=1, TN=1, FP=1。
  def sample
    C::ConfusionMatrix.of(%w[1 1 1 0 0], %w[1 1 0 0 1], "1")
  end

  def counts(matrix)
    [matrix.true_positive, matrix.false_negative, matrix.false_positive, matrix.true_negative]
  end

  def test_混同行列は四つの数を数える
    assert_equal [2, 1, 1, 1], counts(sample)
  end

  def test_正例のラベルを入れ替えると四つの数も入れ替わる
    assert_equal [1, 1, 1, 2], counts(C::ConfusionMatrix.of(%w[1 1 1 0 0], %w[1 1 0 0 1], "0"))
  end

  def test_件数が違えば失敗する
    error = assert_raises(ArgumentError) { C::ConfusionMatrix.of(%w[1], %w[1 0], "1") }

    assert_equal "件数が違います: 1 と 2", error.message
  end

  def test_適合率と再現率とF値を求める
    # 正解 [1 1 1 1 0 0]、予測 [1 1 0 0 0 1]。TP=2, FP=1, FN=2 で適合率と再現率が食い違う
    matrix = C::ConfusionMatrix.of(%w[1 1 1 1 0 0], %w[1 1 0 0 0 1], "1")

    assert_in_delta 2.0 / 3, matrix.precision, 1e-12
    assert_in_delta 0.5, matrix.recall, 1e-12
    assert_in_delta 4.0 / 7, matrix.f1_score, 1e-12
    assert_in_delta 0.5, matrix.accuracy, 1e-12
  end

  def test_正例と予測した件数が零なら適合率は零になる
    matrix = C::ConfusionMatrix.of(%w[1 0], %w[0 0], "1")

    assert_in_delta 0.0, matrix.precision, 1e-12
    assert_in_delta 0.0, matrix.f1_score, 1e-12
  end

  def test_評価関数にすると正解と予測から直に採点できる
    metric = C.classification_metric(:precision, "1")

    assert_in_delta 2.0 / 3, metric.call(%w[1 1 1 0 0], %w[1 1 0 0 1]), 1e-12
  end

  def test_正解率と回帰の評価指標も同じ形で呼べる
    assert_in_delta 0.6, C::ACCURACY.call(%w[1 1 1 0 0], %w[1 1 0 0 1]), 1e-12
    assert_in_delta 1.0, C::RMSE.call([1.0, 2.0], [2.0, 3.0]), 1e-12
    assert_in_delta 1.0, C::MAE.call([1.0, 2.0], [2.0, 1.0]), 1e-12
  end
end
