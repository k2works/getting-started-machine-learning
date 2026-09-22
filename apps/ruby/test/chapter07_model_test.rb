# frozen_string_literal: true

require "test_helper"

class Chapter07ModelTest < Minitest::Test
  C = GettingStartedMl::Chapter07
  Features = GettingStartedMl::Chapter02::Features
  Row = GettingStartedMl::Chapter02::Row
  Table = GettingStartedMl::Chapter02::Table

  def features(columns, values)
    Features.new(columns:, values:)
  end

  # SNS2 と sales だけを持つ表を作る。
  def table(pairs)
    Table.new(columns: %w[SNS2 sales],
              rows: pairs.map { |sns2, sales| Row.new({ "SNS2" => sns2, "sales" => sales }) })
  end

  def test_計画行列の先頭は一の列になる
    x = [features(["SNS1"], [2.0]), features(["SNS1"], [3.0])]

    assert_equal [[1.0, 2.0], [1.0, 3.0]], C.design_matrix(x).to_a
  end

  def test_直線上の点から切片と係数を求める
    # y = 1 + 2x
    x = [0.0, 1.0, 2.0].map { |value| features(["SNS1"], [value]) }
    model = C.fit(x, [1.0, 3.0, 5.0])

    assert_in_delta 1.0, model.intercept, 1e-9
    assert_in_delta 2.0, model.coefficient("SNS1"), 1e-9
  end

  def test_二つの特徴量でも係数を求める
    # y = 3 + 2a - b
    x = [[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [1.0, 1.0]].map { |values| features(%w[a b], values) }
    model = C.fit(x, [3.0, 5.0, 2.0, 4.0])

    assert_in_delta 3.0, model.intercept, 1e-9
    assert_in_delta 2.0, model.coefficient("a"), 1e-9
    assert_in_delta(-1.0, model.coefficient("b"), 1e-9)
  end

  def test_列の並びが違っても同じ予測になる
    model = C::LinearModel.new(intercept: 1.0, columns: %w[a b], coefficients: [2.0, -1.0])

    assert_in_delta 2.0, model.predict_one(features(%w[b a], [1.0, 1.0])), 1e-9
  end

  def test_列名と係数の数が違えばモデルを作れない
    error = assert_raises(ArgumentError) do
      C::LinearModel.new(intercept: 0.0, columns: ["a"], coefficients: [1.0, 2.0])
    end

    assert_equal "件数が違います: 1 と 2", error.message
  end

  def test_知らない列の係数は読めない
    model = C::LinearModel.new(intercept: 0.0, columns: ["a"], coefficients: [1.0])
    error = assert_raises(KeyError) { model.coefficient("b") }

    assert_equal "列がありません: b", error.message
  end

  def test_特徴量と実測値の件数が違えば学習できない
    error = assert_raises(ArgumentError) { C.fit([features(["a"], [1.0])], [1.0, 2.0]) }

    assert_equal "件数が違います: 1 と 2", error.message
  end

  def test_特徴量が無ければ学習できない
    error = assert_raises(ArgumentError) { C.fit([], []) }

    assert_equal "特徴量がありません", error.message
  end

  def test_話題になったのに売れなかった映画を外れ値として除く
    cleaned = C::Cinema.remove_outliers(table([%w[1200 8000], %w[1200 9000], %w[800 8000]]))

    assert_equal(%w[9000 8000], cleaned.rows.map { |row| row.text("sales") })
  end

  def test_境界の値は外れ値にしない
    assert_equal 2, C::Cinema.remove_outliers(table([%w[1000 8000], %w[1200 8500]])).rows.size
  end

  def test_外れ値の判定に使う列が空欄なら失敗する
    error = assert_raises(ArgumentError) { C::Cinema.remove_outliers(table([["", "8000"]])) }

    assert_equal "値が空欄です: SNS2", error.message
  end

  def test_Rumale_の線形回帰も直線の切片と係数を求める
    x = [0.0, 1.0, 2.0].map { |value| features(["SNS1"], [value]) }
    model = C::RumaleRegression.fit(x, [1.0, 3.0, 5.0])

    assert_in_delta 1.0, model.intercept, 1e-5
    assert_in_delta 2.0, model.coefficient("SNS1"), 1e-5
  end

  def test_自作とRumaleの係数はほぼ一致する
    x = [[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [1.0, 2.0]].map { |values| features(%w[a b], values) }
    t = [3.0, 5.0, 2.2, 3.1]
    ours = C.fit(x, t)
    theirs = C::RumaleRegression.fit(x, t)

    assert_in_delta ours.intercept, theirs.intercept, 1e-5
    %w[a b].each { |column| assert_in_delta ours.coefficient(column), theirs.coefficient(column), 1e-5 }
  end

  # 既定の tol（1e-4）のままだと、L-BFGS が厳密な解の手前で止まる
  def test_Rumale_の既定の許容誤差では厳密な解に届かない
    x = Numo::DFloat[[0, 0], [1, 0], [0, 1], [1, 2]]
    model = Rumale::LinearModel::LinearRegression.new.fit(x, Numo::DFloat[3, 5, 2.2, 3.1])

    assert_equal "lbfgs", model.params[:solver]
    assert_operator (model.bias_term - 3.06).abs, :>, 1e-4
  end
end
