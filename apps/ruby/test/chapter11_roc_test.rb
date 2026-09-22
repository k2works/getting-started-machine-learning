# frozen_string_literal: true

require "test_helper"

# 第 11 章の ROC 曲線と AUC のテスト。
class Chapter11RocTest < Minitest::Test
  C = GettingStartedMl::Chapter11

  def test_完全に分けられればAUCは一になる
    curve = C.roc_curve([0.9, 0.8, 0.2, 0.1], [true, true, false, false])

    assert_in_delta 1.0, C.auc(curve), 1e-12
  end

  def test_順が逆ならAUCは零になる
    curve = C.roc_curve([0.1, 0.2, 0.8, 0.9], [true, true, false, false])

    assert_in_delta 0.0, C.auc(curve), 1e-12
  end

  def test_混ざっているとAUCは中間の値になる
    # 正例のスコア 0.9・0.4、負例のスコア 0.6・0.1。組は 4 つで、正例が上なのは 3 つ
    curve = C.roc_curve([0.9, 0.6, 0.4, 0.1], [true, false, true, false])

    assert_in_delta 0.75, C.auc(curve), 1e-12
  end

  def test_曲線は原点から始まり右上で終わる
    curve = C.roc_curve([0.9, 0.6, 0.4, 0.1], [true, false, true, false])

    assert_equal [0.0, 0.0], [curve.first.false_positive_rate, curve.first.true_positive_rate]
    assert_equal [1.0, 1.0], [curve.last.false_positive_rate, curve.last.true_positive_rate]
    assert_equal 5, curve.size
  end

  def test_同じスコアは一つの点にまとめる
    # 0.5 が正例と負例で 1 件ずつ。閾値 0.5 では両方が同時に正例になる
    curve = C.roc_curve([0.9, 0.5, 0.5, 0.1], [true, true, false, false])

    assert_equal 4, curve.size
    assert_in_delta 0.875, C.auc(curve), 1e-12
  end

  def test_正例か負例が片方しか無ければ失敗する
    error = assert_raises(ArgumentError) { C.roc_curve([0.9, 0.1], [true, true]) }

    assert_equal "正例と負例が両方ないと ROC 曲線を描けません", error.message
  end

  def test_スコアとラベルの件数が違えば失敗する
    assert_raises(ArgumentError) { C.roc_curve([0.9], [true, false]) }
  end
end
