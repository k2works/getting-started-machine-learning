# frozen_string_literal: true

require "test_helper"

# 第 11 章の K 分割と交差検証のテスト。
class Chapter11CrossValidationTest < Minitest::Test
  C = GettingStartedMl::Chapter11

  def column(value)
    GettingStartedMl::Chapter02::Features.new(columns: ["x"], values: [value])
  end

  def test_割り切れる件数は同じ大きさに分かれる
    folds = C.k_fold_sequential(6, 3)

    assert_equal [[0, 1], [2, 3], [4, 5]], folds.map(&:test)
    assert_equal [2, 3, 4, 5], folds.first.train
  end

  def test_余りは先頭の分割に一件ずつ配られる
    assert_equal([3, 2, 2], C.k_fold_sequential(7, 3).map { |fold| fold.test.size })
  end

  def test_テストデータは重ならず全体を覆う
    assert_equal (0...10).to_a, C.k_fold(10, 5, 0).flat_map(&:test).sort
  end

  def test_訓練データとテストデータは交わらない
    C.k_fold(10, 5, 0).each do |fold|
      assert_equal 8, fold.train.size
      assert_empty fold.train & fold.test
    end
  end

  def test_同じシードなら同じ分け方になる
    assert_equal C.k_fold(10, 5, 3), C.k_fold(10, 5, 3)
    refute_equal C.k_fold_sequential(10, 5), C.k_fold(10, 5, 3)
  end

  def test_分割の数が少なすぎると失敗する
    error = assert_raises(ArgumentError) { C.k_fold(10, 1, 0) }

    assert_equal "分割の数は 2 以上 10 以下にしてください: 1", error.message
  end

  def test_分割の数が件数より多いと失敗する
    assert_raises(ArgumentError) { C.k_fold_sequential(3, 4) }
  end

  def test_分割ごとに新しいモデルで学習して採点する
    # t = 2x + 1 に乗る 6 件。どの分割でも直線を当てられるので RMSE は 0
    x = (1..6).map { |value| column(value.to_f) }
    t = (1..6).map { |value| (2.0 * value) + 1.0 }
    scores = C.cross_validate(-> { C::LinearRegressionModel.new }, x, t, C.k_fold(6, 3, 0), C::RMSE)

    assert_equal 3, scores.size
    scores.each { |score| assert_in_delta 0.0, score, 1e-9 }
  end

  def test_決定木も同じ交差検証にかけられる
    # 0 と 1 が交互に並ぶように並べ替えておけば、どの分割の訓練データにも両方のラベルが入る
    x = [0.1, 0.7, 0.2, 0.8, 0.3, 0.9].map { |value| column(value) }
    t = %w[0 1 0 1 0 1]
    make = -> { GettingStartedMl::Chapter03::DecisionTree.new(max_depth: 1) }

    assert_equal [1.0, 1.0, 1.0], C.cross_validate(make, x, t, C.k_fold_sequential(6, 3), C::ACCURACY)
  end

  def test_学習する前に予測すると失敗する
    error = assert_raises(RuntimeError) { C::LinearRegressionModel.new.predict([column(1.0)]) }

    assert_equal "学習してから予測してください", error.message
  end

  def test_スコアの平均を求める
    assert_in_delta 2.0, C.mean([1.0, 2.0, 3.0]), 1e-12
    assert_raises(ArgumentError) { C.mean([]) }
  end
end
