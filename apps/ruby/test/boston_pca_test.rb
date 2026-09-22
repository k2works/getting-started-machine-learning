# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Boston.csv）で主成分分析をするテスト。学習データが無ければスキップする。
class BostonPcaTest < Minitest::Test
  C = GettingStartedMl::Chapter13

  def x
    path = File.join(GettingStartedMl::Dataset.dir, "Boston.csv")
    skip "学習データ Boston.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    C.to_matrix(C.load_boston(path))
  end

  # 主成分は長さ 1 で互いに直交する。値を書き写さずに確かめられる性質
  def test_主成分は長さ一で互いに直交する
    components = C.fit(x, 15).components
    products = components.dot(components.transpose)

    assert_in_delta 0.0, (products - Numo::DFloat.eye(15)).abs.max, 1e-9
  end

  def test_実行すると寄与率と主成分の意味とRumaleとの差を表示する
    x
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      データ件数: 100, 列数: 15
      寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
      累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
      第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
      第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
      Rumale（既定: max_iter 100, tol 0.0001）との差: 寄与率 4.4e-04, 主成分 1.3e+00
      Rumale（厳しめ: max_iter 10000, tol 1.0e-12）との差: 寄与率 1.3e-11, 主成分 6.3e-05
    TEXT
  end
end
