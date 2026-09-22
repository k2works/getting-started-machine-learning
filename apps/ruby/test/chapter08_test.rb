# frozen_string_literal: true

require "test_helper"

# 第 8 章の前処理（補完とダミー変数化）のテスト。
class Chapter08Test < Minitest::Test
  C = GettingStartedMl::Chapter08
  Row = GettingStartedMl::Chapter02::Row
  Table = GettingStartedMl::Chapter02::Table

  # 列名と値の組から表を作る。
  def table(columns, *values)
    Table.new(columns:, rows: values.map { |row| Row.new(columns.zip(row).to_h) })
  end

  # 客室等級・性別・年齢を持つ表を作る。
  def passengers(*values)
    table(%w[Pclass Sex Age], *values)
  end

  def age_step(group_columns = [])
    C::GroupMedian.new(column: "Age", by: group_columns)
  end

  def test_欠損した年齢を全体の中央値で埋める
    x = passengers(%w[1 female 10], %w[1 female 20], ["1", "female", ""])

    assert_in_delta 15.0, age_step.fit(x).transform(x).rows[2].number("Age"), 1e-12
  end

  def test_グループごとに違う中央値で埋める
    x = passengers(%w[1 female 10], %w[1 female 20], %w[3 male 40], %w[3 male 60],
                   ["3", "male", ""], ["1", "female", ""])
    filled = age_step(%w[Pclass Sex]).fit(x).transform(x)

    assert_equal([50.0, 15.0], filled.rows.last(2).map { |row| row.number("Age") })
  end

  def test_訓練データで求めた中央値を別のデータに使う
    train = passengers(%w[1 female 10], %w[1 female 20])
    test = passengers(["1", "female", ""])

    assert_in_delta 15.0, age_step(%w[Pclass Sex]).fit(train).transform(test).rows[0].number("Age"), 1e-12
  end

  def test_訓練データに無いグループは全体の中央値で埋める
    train = passengers(%w[1 female 10], %w[1 female 30])
    test = passengers(["3", "male", ""])

    assert_in_delta 20.0, age_step(%w[Pclass Sex]).fit(train).transform(test).rows[0].number("Age"), 1e-12
  end

  def test_値がすべて欠けていれば中央値を求められない
    error = assert_raises(ArgumentError) { age_step.fit(passengers(["1", "female", ""])) }

    assert_equal "値がすべて空欄です: Age", error.message
  end

  def test_補完しても元の表は変わらない
    x = passengers(%w[1 female 10], ["1", "female", ""])
    age_step.fit(x).transform(x)

    assert x.rows[1].missing?("Age")
  end

  def test_欠損した港を最頻値で埋める
    x = table(["Embarked"], ["S"], ["C"], ["S"], [""])

    assert_equal "S", C::MostFrequent.new(column: "Embarked").fit(x).transform(x).rows[3].text("Embarked")
  end

  def test_最頻値が同数なら先に現れた値を選ぶ
    x = table(["Embarked"], ["C"], ["S"])

    assert_equal "C", C::MostFrequent.new(column: "Embarked").fit(x).most_frequent
  end

  def test_カテゴリ値を最初のカテゴリを除いたダミー変数にする
    x = table(%w[Sex Age], %w[male 20], %w[female 30])
    encoded = C::Dummy.new(columns: ["Sex"]).fit(x).transform(x)

    assert_equal %w[Age Sex_male], encoded.columns
    assert_equal([1.0, 0.0], encoded.rows.map { |row| row.number("Sex_male") })
  end

  def test_別のデータにも訓練データと同じダミー変数の列を作る
    train = table(["Embarked"], ["C"], ["Q"], ["S"])
    encoded = C::Dummy.new(columns: ["Embarked"]).fit(train).transform(table(["Embarked"], ["S"]))

    assert_equal %w[Embarked_Q Embarked_S], encoded.columns
    assert_equal([0.0, 1.0], encoded.columns.map { |column| encoded.rows[0].number(column) })
  end
end
