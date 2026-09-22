# frozen_string_literal: true

require "test_helper"

# 第 8 章のクラスの重みを付けた決定木のテスト。
class Chapter08ModelTest < Minitest::Test
  C = GettingStartedMl::Chapter08
  Features = GettingStartedMl::Chapter02::Features
  Leaf = GettingStartedMl::Chapter03::Leaf

  def fare(value)
    Features.new(columns: ["Fare"], values: [value])
  end

  # 運賃で生死が分かれる、偏ったデータ。
  def imbalanced
    [[5.0, 6.0, 7.0, 8.0, 80.0].map { |value| fare(value) }, [0, 0, 0, 0, 1]]
  end

  def test_重みが等しければ普通のジニ不純度になる
    assert_in_delta 0.5, C.weighted_gini([0, 1], [1.0, 1.0]), 1e-12
  end

  def test_重みが偏ると不純度も偏る
    assert_in_delta 0.375, C.weighted_gini([0, 1], [3.0, 1.0]), 1e-12
  end

  def test_少数派のクラスに大きな重みが付く
    weights = C.balanced_weights([0, 0, 0, 1])

    assert_in_delta 4.0 / 6, weights[0], 1e-12
    assert_in_delta 2.0, weights[3], 1e-12
  end

  def test_重みを付けなければすべて一になる
    assert_equal [1.0, 1.0], C.weights_of([0, 1], :none)
  end

  def test_知らない重みの付け方は使えない
    error = assert_raises(ArgumentError) { C.weights_of([0, 1], :heavy) }

    assert_equal "クラスの重みの付け方が違います: heavy", error.message
  end

  def test_深さ零なら重みの大きいラベルの葉になる
    x, t = imbalanced

    assert_equal Leaf.new(label: 0), C::DecisionTreeClassifier.new(max_depth: 0).fit(x, t).tree
  end

  # balanced では 1 件の生存（重み 2.5）が 4 件の死亡（重み 0.625 × 4）と同じ重みになり、
  # 先に現れた 0 が選ばれる。重みを 1 件だけ増やせば 1 が選ばれる
  def test_重みを付けると少数派を答える葉になる
    x, t = imbalanced

    assert_equal Leaf.new(label: 0), C::DecisionTreeClassifier.new(max_depth: 0, class_weight: :balanced).fit(x, t).tree
    assert_equal Leaf.new(label: 1), C.build(x, t, [0.5, 0.5, 0.5, 0.5, 2.5], 0)
  end

  def test_深さを制限しなければ訓練データを全部当てる
    x, t = imbalanced

    assert_equal t, C::DecisionTreeClassifier.new.fit(x, t).predict(x)
  end

  def test_特徴量と正解ラベルの件数が違えば学習できない
    error = assert_raises(ArgumentError) { C::DecisionTreeClassifier.new.fit([fare(1.0)], [0, 1]) }

    assert_equal "件数が違います: 1 と 2", error.message
  end

  def test_重みを付けなければ自作とRumaleの予測は一致する
    x = [5.0, 6.0, 70.0, 80.0].map { |value| fare(value) }
    t = [0, 0, 1, 1]
    ours = C::DecisionTreeClassifier.new(max_depth: 1).fit(x, t).predict(x)

    assert_equal t, ours
    assert_equal ours, GettingStartedMl::Chapter03::RumaleTree.predict(x, t, x, 1)
  end

  def test_全部当たれば正解率は一になる
    assert_in_delta 1.0, C.accuracy([1, 0], [1, 0]), 1e-12
  end
end
