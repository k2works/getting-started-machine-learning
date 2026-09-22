# frozen_string_literal: true

require "test_helper"

class Chapter10LogisticTest < Minitest::Test
  C = GettingStartedMl::Chapter10

  def column(value)
    GettingStartedMl::Chapter02::Features.new(columns: ["花弁幅"], values: [value])
  end

  def test_値がすべて同じなら確率は均等になる
    C.softmax([1.0, 1.0, 1.0]).each { |probability| assert_in_delta 1.0 / 3, probability, 1e-12 }
  end

  def test_値の差が指数の比になる
    probabilities = C.softmax([0.0, 1.0])

    assert_in_delta Math::E, probabilities[1] / probabilities[0], 1e-12
    assert_in_delta 1.0, probabilities.sum, 1e-12
  end

  def test_大きな値でもあふれない
    # 定義どおり exp(1000) を求めると Infinity になり、Infinity / Infinity が NaN になる
    assert_predicate Math.exp(1000), :infinite?

    probabilities = C.softmax([1000.0, 1001.0])

    assert probabilities.all?(&:finite?), probabilities.inspect
    assert_in_delta 1.0, probabilities.sum, 1e-12
  end

  def test_正解の確率が1なら交差エントロピーは0になる
    assert_in_delta 0.0, C.cross_entropy([[1.0, 0.0]], [0]), 1e-9
    assert_operator C.cross_entropy([[0.5, 0.5]], [0]), :>, 0.69
  end

  def test_二種類のラベルを予測する
    x = [column(0.2), column(0.3), column(2.3), column(2.5)]
    t = %w[setosa setosa virginica virginica]

    assert_equal t, C::LogisticRegression.new.fit(x, t).predict(x)
  end

  def test_三種類のラベルを予測する
    x = [0.2, 0.3, 1.2, 1.4, 2.3, 2.5].map { |value| column(value) }
    t = %w[setosa setosa versicolor versicolor virginica virginica]

    assert_equal t, C::LogisticRegression.new.fit(x, t).predict(x)
  end

  def test_学習を繰り返すと損失が小さくなる
    x = [column(0.2), column(0.3), column(2.3), column(2.5)]
    losses = C::LogisticRegression.new.fit(x, %w[setosa setosa virginica virginica]).losses

    assert_equal 5000, losses.size
    assert_operator losses.first, :>, losses.last
  end

  def test_学習する前は予測できない
    error = assert_raises(RuntimeError) { C::LogisticRegression.new.predict([column(0.2)]) }

    assert_equal "学習してから予測してください", error.message
  end
end
