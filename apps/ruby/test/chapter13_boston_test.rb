# frozen_string_literal: true

require "test_helper"

class Chapter13BostonTest < Minitest::Test
  C = GettingStartedMl::Chapter13
  Chapter02 = GettingStartedMl::Chapter02

  # CRIME・RM・PRICE を持つ表を作る。
  def table(rows)
    Chapter02::Table.new(columns: %w[CRIME RM PRICE],
                         rows: rows.map { |cells| Chapter02::Row.new(%w[CRIME RM PRICE].zip(cells).to_h) })
  end

  def three_rows
    table([%w[high 6.0 20.0], %w[low 5.0 10.0], %w[low 7.0 30.0]])
  end

  def test_カテゴリ値はダミー変数の列になり正解の列も残る
    features = C.standardize_boston(three_rows)

    # PRICE も主成分分析の対象にするので、列から外さない
    assert_equal %w[RM PRICE CRIME_low], features.first.columns
  end

  def test_欠損値は列の平均値で補う
    features = C.standardize_boston(table([%w[high 6.0 20.0], ["low", "", "10.0"], %w[low 8.0 30.0]]))

    # RM の平均は 7.0 なので、補った行は標準化すると 0 になる
    assert_in_delta 0.0, features[1].value("RM"), 1e-12
  end

  def test_特徴量は一件を一行とする行列になる
    assert_equal [3, 3], C.to_matrix(C.standardize_boston(three_rows)).shape
  end
end
