# frozen_string_literal: true

require "test_helper"
require "tmpdir"

class Chapter14SpendingTest < Minitest::Test
  C = GettingStartedMl::Chapter14

  # Fresh と Milk を持つ特徴量を作る。
  def row(fresh, milk)
    GettingStartedMl::Chapter02::Features.new(columns: %w[Fresh Milk], values: [fresh, milk])
  end

  def with_csv(text)
    Dir.mktmpdir do |dir|
      path = File.join(dir, "spending.csv")
      File.write(path, text)
      yield path
    end
  end

  def test_区分の列を除いた支出額を読み込む
    with_csv("Channel,Region,Fresh,Milk\n2,3,100,200\n1,3,300,400\n") do |path|
      x = C.load_spending(path)

      assert_equal %w[Fresh Milk], x.first.columns
      assert_equal [[100.0, 200.0], [300.0, 400.0]], x.map(&:values)
    end
  end

  def test_支出額に欠損値があれば失敗にする
    with_csv("Channel,Region,Fresh,Milk\n2,3,,200\n") do |path|
      error = assert_raises(ArgumentError) { C.load_spending(path) }

      assert_equal "欠損値があります: Fresh", error.message
    end
  end

  def test_標準化した点は一件を一行とする行列になる
    points = C.standardize_spending([row(1.0, 10.0), row(3.0, 30.0)])

    assert_equal [[-1.0, -1.0], [1.0, 1.0]], points.to_a
  end

  def test_クラスタごとの件数と平均を件数の多い順に並べる
    summaries = C.summarize_clusters([row(1.0, 10.0), row(3.0, 30.0), row(100.0, 200.0)], [1, 1, 0])

    assert_equal [C::ClusterSummary.new(cluster: 1, count: 2, means: [2.0, 20.0]),
                  C::ClusterSummary.new(cluster: 0, count: 1, means: [100.0, 200.0])], summaries
  end

  def test_要約はタブで区切った一行になる
    summary = C::ClusterSummary.new(cluster: 2, count: 7, means: [1234.5, 6.4])

    assert_equal "2\t7\t1234\t6", C.format_summary(summary)
  end
end
