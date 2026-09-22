# frozen_string_literal: true

require "test_helper"

# 実データ（iris.csv）を使うテスト。学習データが無ければスキップする。
class IrisDataTest < Minitest::Test
  C = GettingStartedMl::Chapter02

  def csv_file
    path = File.join(GettingStartedMl::Dataset.dir, "iris.csv")
    skip "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def test_実データの列ごとの欠損値の数を数える
    table = C::Table.load(csv_file)

    assert_equal 150, table.rows.size
    assert_equal({ "がく片長さ" => 2, "がく片幅" => 1, "花弁長さ" => 2, "花弁幅" => 2, "種類" => 0 }, table.count_missing)
  end

  def test_実データを百五件と四十五件に分けて欠損値を補完する
    split = C.prepare_iris(csv_file, test_size: 0.3, seed: 0)

    assert_equal [105, 45, 105, 45], [split.x_train.size, split.x_test.size, split.t_train.size, split.t_test.size]
    assert(split.x_train.all? { |features| features.values.none?(&:nil?) })
  end

  # Ruby の Random（MT19937）と Array#shuffle の並びは、Python 版・Rust 版などと違うので、
  # 分かれる行と平均値はほかの言語版と一致しない（件数だけ一致する）
  def test_訓練データの平均値は乱数の分け方で決まる
    columns, rows, labels = C.split_features_and_target(C::Table.load(csv_file), C::TARGET)
    split = C.split_train_test(rows, labels, test_size: 0.3, seed: 0)
    means = C.column_means(split.x_train, columns)

    { "がく片長さ" => 0.4202912621359223, "がく片幅" => 0.4314285714285714,
      "花弁長さ" => 0.49640776699029127, "花弁幅" => 0.45796116504854373 }.each do |column, want|
      assert_in_delta want, means[column], 1e-12, column
    end
  end
end
