# frozen_string_literal: true

require "test_helper"

class Chapter10RumaleTest < Minitest::Test
  C = GettingStartedMl::Chapter10

  def row(width, length)
    GettingStartedMl::Chapter02::Features.new(columns: %w[花弁幅 花弁長さ], values: [width, length])
  end

  def three_species
    [[row(0.2, 1.4), row(0.3, 1.5), row(1.2, 4.0), row(1.4, 4.4), row(2.3, 5.5), row(2.5, 5.7)],
     %w[setosa setosa versicolor versicolor virginica virginica]]
  end

  def test_Rumale_のロジスティック回帰に文字列のラベルで学習させる
    x, t = three_species

    assert_equal t, C::RumaleLogisticRegression.new(reg_param: 1e-8).fit(x, t).predict(x)
  end

  def test_Rumale_のランダムフォレストは同じシードなら同じ重要度になる
    x, t = three_species
    first = C::RumaleRandomForest.new(n_estimators: 10, max_features: 1, seed: 3).fit(x, t)
    second = C::RumaleRandomForest.new(n_estimators: 10, max_features: 1, seed: 3).fit(x, t)

    assert_equal first.feature_importances, second.feature_importances
    assert_equal t, first.predict(x)
  end

  def test_Rumale_のモデルも学習する前は予測できない
    error = assert_raises(RuntimeError) { C::RumaleLogisticRegression.new.predict([row(0.2, 1.4)]) }

    assert_equal "学習してから予測してください", error.message
  end
end
