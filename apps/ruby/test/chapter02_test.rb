# frozen_string_literal: true

require "test_helper"

class Chapter02Test < Minitest::Test
  C = GettingStartedMl::Chapter02

  def row(cells)
    C::Row.new(cells)
  end

  def test_数値の列を読む
    assert_in_delta 5.1, row("がく片長さ" => "5.1").number("がく片長さ")
  end

  def test_空欄の列は欠損値になる
    cells = row("がく片長さ" => "")

    assert_nil cells.number("がく片長さ")
    assert cells.missing?("がく片長さ")
  end

  def test_数値として読めない列は失敗する
    error = assert_raises(ArgumentError) { row("がく片長さ" => "たくさん").number("がく片長さ") }

    assert_equal "がく片長さ を数値として読めません: たくさん", error.message
  end

  def test_列が無ければ失敗する
    error = assert_raises(KeyError) { row("がく片長さ" => "5.1").text("花弁幅") }

    assert_equal "列がありません: 花弁幅", error.message
  end

  def test_文字列の列を読む
    assert_equal "Iris-setosa", row("種類" => "Iris-setosa").text("種類")
  end

  def test_列ごとに欠損値を数える
    table = C::Table.new(
      columns: %w[がく片長さ 種類],
      rows: [
        row("がく片長さ" => "5.1", "種類" => "Iris-setosa"),
        row("がく片長さ" => "", "種類" => "Iris-setosa"),
        row("がく片長さ" => "", "種類" => "Iris-virginica")
      ]
    )

    assert_equal({ "がく片長さ" => 2, "種類" => 0 }, table.count_missing)
  end

  def test_欠損値を除いて列ごとの平均値を求める
    rows = [row("がく片長さ" => "1.0"), row("がく片長さ" => ""), row("がく片長さ" => "3.0")]

    assert_equal({ "がく片長さ" => 2.0 }, C.column_means(rows, ["がく片長さ"]))
  end

  def test_値がすべて空欄なら平均値を求められない
    error = assert_raises(ArgumentError) { C.column_means([row("がく片長さ" => "")], ["がく片長さ"]) }

    assert_equal "値がすべて空欄です: がく片長さ", error.message
  end

  def test_欠損値を平均値で補完する
    rows = [row("がく片長さ" => "1.0"), row("がく片長さ" => "")]

    filled = C.fill_missing(rows, ["がく片長さ"], { "がく片長さ" => 2.0 })

    assert_equal [C::Features.new(columns: ["がく片長さ"], values: [1.0]),
                  C::Features.new(columns: ["がく片長さ"], values: [2.0])], filled
  end

  def test_補完する値が無ければ失敗する
    error = assert_raises(KeyError) { C.fill_missing([row("がく片長さ" => "")], ["がく片長さ"], {}) }

    assert_equal "補完する値がありません: がく片長さ", error.message
  end

  def test_列名で特徴量の値を読む
    features = C::Features.new(columns: %w[がく片長さ 花弁幅], values: [5.1, 0.2])

    assert_in_delta 0.2, features.value("花弁幅")
    error = assert_raises(KeyError) { features.value("種類") }
    assert_equal "列がありません: 種類", error.message
  end

  def test_列名と値の数が合わなければ特徴量を作れない
    error = assert_raises(ArgumentError) { C::Features.new(columns: ["がく片長さ"], values: [5.1, 0.2]) }

    assert_equal "件数が違います: 1 と 2", error.message
  end

  def test_同じシードなら同じ並びになる
    items = (0..9).to_a

    assert_equal C.shuffle(items, 0), C.shuffle(items, 0)
    refute_equal C.shuffle(items, 0), C.shuffle(items, 1)
  end

  def test_並べ替えの並びはほかの言語版と違う
    items = (0..9).to_a

    # Rust 版は [9 3 6 4 8 1 5 2 0 7]、Java 版は [4 8 9 6 3 5 2 1 7 0]、Go 版は [6 8 2 3 7 5 9 1 0 4]
    assert_equal [2, 8, 4, 9, 1, 6, 7, 3, 0, 5], C.shuffle(items, 0)
  end

  def test_並べ替えても要素は変わらず元の配列も変わらない
    items = (0..9).to_a

    assert_equal items, C.shuffle(items, 0).sort
    assert_equal (0..9).to_a, items
  end

  def test_テストデータの割合で分ける
    x = (0..9).to_a

    split = C.split_train_test(x, x.map(&:to_s), test_size: 0.3, seed: 0)

    assert_equal [7, 3, 7, 3], [split.x_train.size, split.x_test.size, split.t_train.size, split.t_test.size]
  end

  def test_分けても特徴量と正解ラベルの対応は崩れない
    x = (0..9).to_a

    split = C.split_train_test(x, x.map(&:to_s), test_size: 0.3, seed: 0)

    assert_equal split.x_train.map(&:to_s), split.t_train
    assert_equal split.x_test.map(&:to_s), split.t_test
  end

  def test_特徴量と正解ラベルの件数が違えば分けられない
    error = assert_raises(ArgumentError) { C.split_train_test([1, 2], [1], test_size: 0.3, seed: 0) }

    assert_equal "件数が違います: 2 と 1", error.message
  end

  def test_正解ラベルの列を取り出して残りを特徴量の列にする
    table = C::Table.new(
      columns: %w[がく片長さ 種類],
      rows: [row("がく片長さ" => "5.1", "種類" => "Iris-setosa"), row("がく片長さ" => "6.0", "種類" => "Iris-virginica")]
    )

    columns, rows, labels = C.split_features_and_target(table, C::TARGET)

    assert_equal ["がく片長さ"], columns
    assert_equal 2, rows.size
    assert_equal %w[Iris-setosa Iris-virginica], labels
  end
end
