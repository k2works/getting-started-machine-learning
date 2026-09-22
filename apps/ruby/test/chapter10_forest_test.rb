# frozen_string_literal: true

require "test_helper"

class Chapter10ForestTest < Minitest::Test
  C = GettingStartedMl::Chapter10
  Chapter03 = GettingStartedMl::Chapter03

  def row(width, length)
    GettingStartedMl::Chapter02::Features.new(columns: %w[花弁幅 花弁長さ], values: [width, length])
  end

  # 花弁幅だけで分けられる小さなデータ。花弁長さはすべて同じ値。
  def separable
    [[row(0.2, 1.0), row(0.3, 1.0), row(2.3, 1.0), row(2.5, 1.0)], %w[setosa setosa virginica virginica]]
  end

  def test_多数決で予測を一つに決める
    assert_equal %w[a c], C.majority_vote([%w[a b], %w[a c], %w[b c]])
  end

  def test_同数なら先に現れた予測を選ぶ
    assert_equal %w[b], C.majority_vote([%w[b], %w[a]])
  end

  def test_ブートストラップ標本は同じ件数で重複を許す
    rows = C.bootstrap_sample(100, Random.new(0))

    assert_equal 100, rows.size
    assert(rows.all? { |row| (0...100).cover?(row) })
    assert_operator rows.uniq.size, :<, 100
  end

  def test_特徴量の部分集合は元の列の順を保つ
    columns = %w[a b c d e]
    chosen = C.choose(columns, 3, Random.new(0))

    assert_equal 3, chosen.size
    assert_equal columns & chosen, chosen
  end

  def test_指定した列だけを取り出す
    selected = C.select_columns([row(0.2, 1.4)], %w[花弁長さ]).first

    assert_equal %w[花弁長さ], selected.columns
    assert_equal [1.4], selected.values
  end

  def test_第三章の決定木を束ねて予測する
    x = [row(0.2, 1.4), row(0.3, 1.5), row(2.3, 5.5), row(2.5, 5.7)]
    t = %w[setosa setosa virginica virginica]
    forest = C::RandomForest.new(n_estimators: 10, max_features: 1, seed: 0).fit(x, t)

    assert_equal 10, forest.trees.size
    assert(forest.trees.all? { |tree| tree.model.is_a?(Chapter03::DecisionTree) })
    assert_equal t, forest.predict(x)
  end

  def test_同じシードなら同じ森になる
    x, t = separable
    first = C::RandomForest.new(n_estimators: 5, max_features: 1, seed: 7).fit(x, t)
    second = C::RandomForest.new(n_estimators: 5, max_features: 1, seed: 7).fit(x, t)

    assert_equal first.trees.map(&:rows), second.trees.map(&:rows)
  end

  def test_森を学習する前は予測できない
    error = assert_raises(RuntimeError) { C::RandomForest.new(n_estimators: 1, max_features: 1).predict([row(0.2, 1.0)]) }

    assert_equal "学習してから予測してください", error.message
  end

  def test_使った特徴量だけが重要度を持つ
    x, t = separable
    tree = Chapter03::DecisionTree.new(max_depth: 1).fit(x, t).tree

    assert_equal [["花弁幅", 1.0], ["花弁長さ", 0.0]], C.tree_importances(tree, x, t)
  end

  def test_葉だけの木は重要度を持たない
    x = [row(0.2, 1.0), row(0.3, 1.0)]
    t = %w[setosa setosa]
    tree = Chapter03::DecisionTree.new.fit(x, t).tree

    assert_equal [["花弁幅", 0.0], ["花弁長さ", 0.0]], C.tree_importances(tree, x, t)
  end

  def test_森の重要度の合計は一になる
    x, t = separable
    forest = C::RandomForest.new(n_estimators: 10, max_features: 2, seed: 0).fit(x, t)

    assert_in_delta 1.0, C.forest_importances(forest, x, t).sum(&:last), 1e-12
  end

  def test_どのモデルも同じ関数で評価する
    x, t = separable
    split = GettingStartedMl::Chapter02::TrainTestSplit.new(x_train: x, x_test: x.first(2), t_train: t,
                                                            t_test: t.first(2))

    [Chapter03::DecisionTree.new(max_depth: 1), C::LogisticRegression.new,
     C::RandomForest.new(n_estimators: 5, max_features: 2)].each do |model|
      assert_equal C::Score.new(train: 1.0, test: 1.0), C::Score.evaluate(model, split), model.class.name
    end
  end
end
