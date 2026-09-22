# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（cinema.csv）で線形回帰を確かめるテスト。学習データが無ければスキップする。
class CinemaRegressionTest < Minitest::Test
  C = GettingStartedMl::Chapter07

  def csv_file
    path = File.join(GettingStartedMl::Dataset.dir, "cinema.csv")
    skip "学習データ cinema.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def split
    C::Cinema.prepare(csv_file, test_size: 0.2, seed: 0)
  end

  def test_実データの外れ値は一件だけ除かれる
    table = GettingStartedMl::Chapter02::Table.load(csv_file)

    assert_equal 100, table.rows.size
    assert_equal 99, C::Cinema.remove_outliers(table).rows.size
  end

  def test_実データを七十九件と二十件に分ける
    data = split

    assert_equal [79, 20], [data.x_train.size, data.x_test.size]
  end

  def test_自作とRumaleの係数は実データでも一致する
    data = split
    ours = C.fit(data.x_train, data.t_train)
    theirs = C::RumaleRegression.fit(data.x_train, data.t_train)

    assert_in_delta ours.intercept, theirs.intercept, 1e-3
    C::Cinema::FEATURES.each do |column|
      assert_in_delta ours.coefficient(column), theirs.coefficient(column), 1e-3, column
    end
  end

  # 既定の tol（1e-4）では、切片がほぼ 0 のまま最適化が止まる
  def test_Rumale_の既定の許容誤差では実データの切片を求められない
    data = split
    model = Rumale::LinearModel::LinearRegression.new
    model.fit(Numo::DFloat.cast(data.x_train.map(&:values)), Numo::DFloat.cast(data.t_train))

    assert_operator model.bias_term.abs, :<, 1.0
    assert_in_delta 6035.99, C.fit(data.x_train, data.t_train).intercept, 0.01
  end

  def test_テストデータの決定係数は零点七一
    data = split
    y = C.fit(data.x_train, data.t_train).predict(data.x_test)

    assert_in_delta 0.7140223196242237, C.r2_score(data.t_test, y), 1e-9
  end

  def test_実行すると係数と評価指標を表示する
    csv_file
    out = StringIO.new
    C.run(out)

    # Ruby の Random の分け方がほかの言語版と違うので、係数も評価指標も一致しない
    assert_equal <<~TEXT, out.string
      データ件数: 100
      外れ値を除いた件数: 99
      訓練データ: 79 件, テストデータ: 20 件
      切片: 6035.99
      係数: SNS1=1.1733, SNS2=0.3771, actor=0.3097, original=272.5036
      Rumale の切片: 6035.99, 係数: SNS1=1.1733, SNS2=0.3771, actor=0.3097, original=272.5036
      テストデータの評価: R2=0.7140, MAE=339.21, RMSE=413.57
    TEXT
  end
end
