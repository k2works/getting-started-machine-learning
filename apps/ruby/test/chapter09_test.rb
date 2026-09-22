# frozen_string_literal: true

require "test_helper"

class Chapter09Test < Minitest::Test
  C = GettingStartedMl::Chapter09
  Chapter02 = GettingStartedMl::Chapter02

  def row(rooms, lstat)
    Chapter02::Features.new(columns: %w[RM LSTAT], values: [rooms, lstat])
  end

  def crime_table(crimes)
    rows = crimes.each_with_index.map { |crime, index| Chapter02::Row.new("RM" => (index + 5).to_s, "CRIME" => crime) }

    Chapter02::Table.new(columns: %w[RM CRIME], rows:)
  end

  # ダミー変数

  def test_先頭を除いたカテゴリを辞書順に返す
    assert_equal %w[low very_low], C.categories(%w[low high very_low low])
  end

  def test_欠損値はカテゴリに数えない
    assert_equal %w[low], C.categories(["low", "", "high"])
  end

  def test_カテゴリごとに0と1の列を作り元の列を取り除く
    encoded = C.encode(crime_table(%w[low high very_low]), "CRIME", %w[low very_low])

    assert_equal %w[RM CRIME_low CRIME_very_low], encoded.columns
    assert_equal(%w[1 0 0], encoded.rows.map { |row| row.text("CRIME_low") })
    assert_equal(%w[0 0 1], encoded.rows.map { |row| row.text("CRIME_very_low") })
    assert_raises(KeyError) { encoded.rows.first.text("CRIME") }
  end

  def test_ダミー変数にしても元の表は変わらない
    table = crime_table(%w[low])
    C.encode(table, "CRIME", %w[low])

    assert_equal %w[RM CRIME], table.columns
    assert_equal "low", table.rows.first.text("CRIME")
  end

  # 標準化

  def test_訓練データから列ごとの平均と標準偏差を求める
    standardizer = C::Standardizer.fit([row(1.0, 10.0), row(2.0, 10.0), row(3.0, 40.0)])

    assert_in_delta 2.0, standardizer.mean("RM"), 1e-12
    assert_in_delta 20.0, standardizer.mean("LSTAT"), 1e-12
    # 件数（3）で割る標準偏差（母標準偏差）。scikit-learn の StandardScaler と同じ定義
    assert_in_delta Math.sqrt(2.0 / 3), standardizer.std("RM"), 1e-12
    assert_in_delta Math.sqrt(200.0), standardizer.std("LSTAT"), 1e-12
  end

  def test_訓練データの平均と標準偏差で別のデータを標準化する
    standardizer = C::Standardizer.fit([row(1.0, 10.0), row(3.0, 30.0)])
    scaled = standardizer.transform([row(5.0, 20.0)]).first

    assert_equal %w[RM LSTAT], scaled.columns
    assert_in_delta 3.0, scaled.value("RM"), 1e-12
    assert_in_delta 0.0, scaled.value("LSTAT"), 1e-12
  end

  def test_すべて同じ値の列は標準化すると0になる
    standardizer = C::Standardizer.fit([row(1.0, 5.0), row(3.0, 5.0)])

    assert_in_delta 1.0, standardizer.std("LSTAT"), 1e-12
    assert_in_delta 0.0, standardizer.transform_one(row(2.0, 5.0)).value("LSTAT"), 1e-12
  end

  def test_特徴量が無ければ標準化できない
    error = assert_raises(ArgumentError) { C::Standardizer.fit([]) }

    assert_equal "特徴量が 1 件もありません", error.message
  end

  def test_Rumale_の標準化は標本標準偏差で割る
    # 平均 2.5、標本標準偏差 sqrt(5/3)。母標準偏差なら sqrt(1.25) になる
    scaled = C::RumaleScaler.standardize([1.0, 2.0, 3.0, 4.0], [1.0])

    assert_in_delta((1.0 - 2.5) / Math.sqrt(5.0 / 3), scaled.first, 1e-12)
  end

  # 多項式特徴量

  def test_重複を許して二つの列を選ぶ組を並べる
    names = C.pairs_with_replacement(%w[RM LSTAT PTRATIO]).map(&:name)

    assert_equal ["RM^2", "RM LSTAT", "RM PTRATIO", "LSTAT^2", "LSTAT PTRATIO", "PTRATIO^2"], names
  end

  def test_二乗の項と交互作用の項を加える
    expanded = C.expand([row(2.0, 3.0)], %w[RM LSTAT]).first

    assert_equal ["RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"], expanded.columns
    assert_equal [2.0, 3.0, 4.0, 6.0, 9.0], expanded.values
  end

  def test_使う項だけを選ぶ
    selected = C.select(C.expand([row(2.0, 3.0)], %w[RM LSTAT]), ["LSTAT", "RM^2"]).first

    assert_equal ["LSTAT", "RM^2"], selected.columns
    assert_equal [3.0, 4.0], selected.values
  end

  def test_無い列は選べない
    error = assert_raises(KeyError) { C.select([row(2.0, 3.0)], ["RM^2"]) }

    assert_equal "列がありません: RM^2", error.message
  end
end
