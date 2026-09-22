# frozen_string_literal: true

require "test_helper"

# 第 11 章の交差検証に渡す特徴量と正解ラベルのテスト。
class Chapter11DataTest < Minitest::Test
  C = GettingStartedMl::Chapter11
  Row = GettingStartedMl::Chapter02::Row
  Table = GettingStartedMl::Chapter02::Table

  # 年齢が 1 件だけ空欄の架空の表。
  def survived_table
    rows = [%w[1 1 20 female], %w[0 3 40 male], ["0", "3", "", "male"]].map do |cells|
      Row.new(%w[Survived Pclass Age Sex].zip(cells).to_h)
    end

    Table.new(columns: %w[Survived Pclass Age Sex], rows:)
  end

  def test_性別は男性なら一になる
    x, = C.prepare_survived(survived_table)

    assert_equal([0.0, 1.0], x.first(2).map { |features| features.value("male") })
  end

  def test_年齢の欠損値は全体の平均値で補われる
    x, = C.prepare_survived(survived_table)

    assert_in_delta 30.0, x.last.value("Age"), 1e-12
  end

  def test_正解ラベルは文字列のまま取り出される
    _, t = C.prepare_survived(survived_table)

    assert_equal %w[1 0 0], t
  end

  def test_cinemaは四つの列を特徴量にして欠損値を平均で補う
    rows = [%w[1 10 100 1 500], ["3", "", "300", "0", "700"]].map do |cells|
      Row.new(%w[SNS1 SNS2 actor original sales].zip(cells).to_h)
    end
    x, t = C.prepare_cinema(Table.new(columns: %w[SNS1 SNS2 actor original sales], rows:))

    assert_equal [3.0, 10.0, 300.0, 0.0], x.last.values
    assert_equal [500.0, 700.0], t
  end
end
