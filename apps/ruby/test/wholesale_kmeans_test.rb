# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Wholesale.csv）でクラスタリングするテスト。学習データが無ければスキップする。
class WholesaleKMeansTest < Minitest::Test
  C = GettingStartedMl::Chapter14

  def points
    path = File.join(GettingStartedMl::Dataset.dir, "Wholesale.csv")
    skip "学習データ Wholesale.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    C.standardize_spending(C.load_spending(path))
  end

  # 母標準偏差で標準化した列は分散が 1 なので、クラスタが 1 つなら SSE は「件数 × 列数」になる
  def test_クラスタが一つなら誤差平方和は件数と列数の積になる
    assert_in_delta 440 * 6, C.fit_with_restarts(points, 1, seed: 0, n_init: 1).sse, 1e-9
  end

  # 初期化の方式が違うので、厳密な一致ではなく、アルゴリズムとして守られるべき性質を確かめる
  def test_自作とRumaleの誤差平方和は同じ桁に収まる
    data = points
    ours = C.fit_with_restarts(data, 5, seed: 0).sse
    theirs = C::RumaleKMeans.fit(data, 5, seed: 0).sse

    assert_in_delta 1.0, ours / theirs, 0.1
  end

  def test_実行するとクラスタ数ごとのSSEとクラスタごとの特徴を表示する
    points
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      データ件数: 440（支出額 6 列）
      クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
      クラスタ数\t自作\tRumale（k-means++）
      1\t2640.00\t2640.00
      2\t1954.18\t1954.18
      3\t1614.00\t1608.43
      4\t1345.54\t1324.85
      5\t1085.74\t1058.77
      6\t990.29\t916.89
      7\t902.93\t833.63
      8\t750.94\t738.68
      9\t724.76\t675.76
      10\t705.72\t609.86

      クラスタ数 5 のクラスタごとの件数と平均支出額:
      クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen
      3\t257\t8219\t3040\t3909\t2209\t1047\t972
      1\t91\t5342\t10613\t16833\t1382\t7404\t1606
      4\t78\t29666\t4222\t5165\t6517\t839\t2141
      0\t10\t15965\t34708\t48537\t3055\t24875\t2943
      2\t4\t52022\t31696\t18491\t29826\t2699\t19656
    TEXT
  end
end
