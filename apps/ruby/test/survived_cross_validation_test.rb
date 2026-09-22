# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Survived.csv・cinema.csv）で交差検証をするテスト。学習データが無ければスキップする。
class SurvivedCrossValidationTest < Minitest::Test
  C = GettingStartedMl::Chapter11

  def table(name)
    path = File.join(GettingStartedMl::Dataset.dir, name)
    skip "学習データ #{name} が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    GettingStartedMl::Chapter02::Table.load(path)
  end

  def test_実データの交差検証で適合率と再現率が食い違う
    x, t = C.prepare_survived(table("Survived.csv"))
    folds = C.k_fold(x.size, 5, 0)
    make = -> { GettingStartedMl::Chapter03::DecisionTree.new(max_depth: 2) }
    precision, recall = %i[precision recall].map do |score|
      C.mean(C.cross_validate(make, x, t, folds, C.classification_metric(score, "1")))
    end

    # 深さ 2 の決定木は「生存」と言い切るのに慎重で、適合率は高いが再現率は低い
    assert_in_delta 0.8239, precision, 1e-4
    assert_in_delta 0.5640, recall, 1e-4
  end

  def test_実データの交差検証のRMSEは分割ごとにRumaleと一致する
    x, t = C.prepare_cinema(table("cinema.csv"))
    own = C.cross_validate(-> { C::LinearRegressionModel.new }, x, t, C.k_fold(x.size, 5, 0), C::RMSE)

    C.rumale_cross_validate_rmse(x, t, 5, seed: 0).zip(own).each do |library, mine|
      assert_in_delta mine, library, 1e-3
    end
  end

  def test_実行すると交差検証の結果を表示する
    table("Survived.csv")
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      Survived（決定木・深さ 2）
        件数: 891
        正解率（5 分割の平均）: 0.7677
        適合率（5 分割の平均）: 0.8239
        再現率（5 分割の平均）: 0.5640
        F値（5 分割の平均）: 0.6384
        分け方（自作の k_fold と Rumale の KFold）: 一致
        ROC 曲線の点の数: 117
        AUC（自作）: 0.8376
        AUC（Rumale）: 0.8376
        混同行列（1 つ目の分割）: TP=52 FP=22 FN=17 TN=88
        Rumale の混同行列: [[88, 22], [17, 52]]
        適合率: 自作 0.7027 / Rumale 0.7027

      cinema（線形回帰）
        件数: 100
        RMSE（5 分割の平均）: 406.49
        MAE（5 分割の平均）: 321.31
        RMSE（並べ替えあり）: 自作 406.49 / Rumale 406.49
        RMSE（並べ替えなし）: 自作 410.42 / Rumale 410.42
    TEXT
  end
end
