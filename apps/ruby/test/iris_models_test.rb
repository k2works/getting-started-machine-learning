# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（iris.csv）でロジスティック回帰とランダムフォレストを確かめるテスト。
# 学習データが無ければスキップする。
class IrisModelsTest < Minitest::Test
  C = GettingStartedMl::Chapter10

  def split
    path = File.join(GettingStartedMl::Dataset.dir, "iris.csv")
    skip "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    GettingStartedMl::Chapter02.prepare_iris(path, test_size: 0.3, seed: 0)
  end

  def predictions(model, data)
    model.fit(data.x_train, data.t_train).predict(data.x_test)
  end

  # Rumale の既定は L2 正則化（reg_param: 1.0）があり、収束の判定（tol: 1e-4）もゆるい。
  # 正則化を外し、tol を 1e-8 に厳しくすると、テストデータ 45 件の予測が自作と 1 件も違わない
  def test_正則化を外して収束を厳しくすると自作とRumaleの予測が一致する
    data = split

    assert_equal predictions(C::LogisticRegression.new, data),
                 predictions(C::RumaleLogisticRegression.new(reg_param: 0.0, tol: 1e-8), data)
  end

  def test_収束の判定が既定のままだと一件違う
    data = split
    ours = predictions(C::LogisticRegression.new, data)
    theirs = predictions(C::RumaleLogisticRegression.new(reg_param: 0.0), data)

    assert_equal(1, ours.zip(theirs).count { |mine, other| mine != other })
  end

  # 深く育てた森は訓練データを全部当てるが、テストデータでは第 3 章の深さ 2 の決定木を上回らない
  def test_森の木を深く育てても深さ二の決定木を上回らない
    data = split
    deep = C::Score.evaluate(C.forest, data)
    tree = C::Score.evaluate(GettingStartedMl::Chapter03::DecisionTree.new(max_depth: 2), data)

    assert_in_delta 1.0, deep.train, 1e-12
    assert_operator deep.test, :<=, tree.test
  end

  def test_実行するとモデルごとの正解率と重要度を表示する
    split
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      モデル\t訓練データ\tテストデータ
      決定木（深さ 2）\t0.9333\t0.9556
      ロジスティック回帰\t0.8952\t0.9556
      ランダムフォレスト（100 本）\t1.0000\t0.9333
      ランダムフォレスト（100 本・深さ 2）\t0.9238\t0.9333
      Rumale ロジスティック回帰（既定）\t0.8381\t0.8667
      Rumale ロジスティック回帰（正則化なし）\t0.8952\t0.9556
      Rumale ランダムフォレスト（100 本）\t1.0000\t0.9333

      ランダムフォレスト（100 本）の特徴量の重要度:
      特徴量\t自作\tRumale
      がく片長さ\t0.2128\t0.1756
      がく片幅\t0.1062\t0.0593
      花弁長さ\t0.2587\t0.1971
      花弁幅\t0.4223\t0.5681
    TEXT
  end
end
