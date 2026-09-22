# frozen_string_literal: true

require "test_helper"

class Chapter03Test < Minitest::Test
  C = GettingStartedMl::Chapter03
  Features = GettingStartedMl::Chapter02::Features

  def column(value)
    Features.new(columns: ["花弁幅"], values: [value])
  end

  # 3 種類に分かれる小さなデータ。
  def three_species
    [[0.2, 0.3, 1.2, 1.4, 2.0, 2.2].map { |value| column(value) },
     %w[setosa setosa versicolor versicolor virginica virginica]]
  end

  def test_一種類だけならジニ不純度は零になる
    assert_in_delta 0.0, C.gini(%w[setosa setosa]), 1e-12
  end

  def test_二種類が半々ならジニ不純度は零点五になる
    assert_in_delta 0.5, C.gini(%w[setosa virginica]), 1e-12
  end

  def test_三種類が均等ならジニ不純度は三分の二になる
    assert_in_delta 2.0 / 3, C.gini(%w[setosa versicolor virginica]), 1e-12
  end

  def test_いちばん多いラベルを返す
    assert_equal "virginica", C.majority(%w[setosa virginica virginica])
  end

  def test_同数なら先に現れたラベルを返す
    assert_equal "virginica", C.majority(%w[virginica setosa])
  end

  def test_分けられないときは分割を返さない
    assert_nil C.best_split([column(0.2), column(0.3)], %w[setosa setosa])
  end

  def test_不純度がいちばん小さくなる分割を選ぶ
    split = C.best_split(*three_species)

    assert_equal "花弁幅", split.feature
    assert_in_delta 0.75, split.threshold, 1e-12
  end

  def test_深さを制限しなければ訓練データを全部当てる
    x, t = three_species

    assert_equal t, C::DecisionTree.new.fit(x, t).predict(x)
  end

  def test_深さ一なら二つの葉になる
    x, t = three_species

    tree = C::DecisionTree.new(max_depth: 1).fit(x, t).tree

    assert_instance_of C::Node, tree
    assert_instance_of C::Leaf, tree.left
    assert_instance_of C::Leaf, tree.right
  end

  def test_学習する前は予測できない
    error = assert_raises(RuntimeError) { C::DecisionTree.new.predict([column(0.2)]) }

    assert_equal "学習してから予測してください", error.message
  end

  def test_木を字下げ付きの文字列にする
    x, t = three_species

    assert_equal "花弁幅 <= 0.7500\n  setosa\n花弁幅 > 0.7500\n  versicolor\n",
                 C.format(C::DecisionTree.new(max_depth: 1).fit(x, t).tree)
  end

  def test_Rumale_に渡すラベルは番号にする
    assert_equal [[0, 1, 0], %w[setosa virginica]], C::RumaleTree.encode(%w[setosa virginica setosa])
  end
end
