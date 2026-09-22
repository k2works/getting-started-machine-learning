# frozen_string_literal: true

require "test_helper"

# 第 11 章の評価指標と交差検証を、Rumale の EvaluationMeasure・ModelSelection と突き合わせるテスト。
class Chapter11RumaleTest < Minitest::Test
  C = GettingStartedMl::Chapter11

  # 正解 [1 1 1 1 0 0]、予測 [1 1 0 0 0 1]。TP=2, FP=1, FN=2 で適合率と再現率が食い違う
  ACTUAL = %w[1 1 1 1 0 0].freeze
  PREDICTED = %w[1 1 0 0 0 1].freeze

  def column(value)
    GettingStartedMl::Chapter02::Features.new(columns: %w[x z], values: [value, (value * value) % 7])
  end

  def test_Rumaleの混同行列は正解を行に予測を列にしてラベルの昇順に並べる
    # ラベルの昇順は "0"・"1" なので、1 行目が負例（TN FP）、2 行目が正例（FN TP）になる
    assert_equal [[1, 1], [2, 2]], C.rumale_confusion_matrix(ACTUAL, PREDICTED)
  end

  def test_Rumaleの適合率と再現率とF値は自作と一致する
    matrix = C::ConfusionMatrix.of(ACTUAL, PREDICTED, "1")
    scores = C.rumale_scores(ACTUAL, PREDICTED)

    assert_in_delta matrix.accuracy, scores.accuracy, 1e-12
    assert_in_delta matrix.precision, scores.precision, 1e-12
    assert_in_delta matrix.recall, scores.recall, 1e-12
    assert_in_delta matrix.f1_score, scores.f1_score, 1e-12
  end

  def test_Rumaleは大きいほうのラベルを正例にする
    # "no" と "yes" なら "yes" が正例。"no" を正例にした自作の値とは合わない
    actual = %w[yes yes yes no no]
    predicted = %w[yes no no no yes]
    scores = C.rumale_scores(actual, predicted)

    assert_in_delta C::ConfusionMatrix.of(actual, predicted, "yes").precision, scores.precision, 1e-12
    refute_in_delta C::ConfusionMatrix.of(actual, predicted, "no").precision, scores.precision, 1e-12
  end

  def test_RumaleのAUCは自作と同じ値になる
    scores = [0.9, 0.6, 0.4, 0.1]
    labels = [true, false, true, false]

    assert_in_delta C.auc(C.roc_curve(scores, labels)), C.rumale_auc(scores, labels), 1e-12
  end

  def test_同じスコアが並んでもRumaleのAUCと一致する
    scores = [0.9, 0.5, 0.5, 0.5, 0.1]
    labels = [true, true, false, true, false]

    assert_in_delta C.auc(C.roc_curve(scores, labels)), C.rumale_auc(scores, labels), 1e-12
  end

  def test_並べ替えるKFoldは同じシードなら自作と行の分け方まで一致する
    assert_equal C.k_fold(11, 3, 7), C.rumale_folds(11, 3, seed: 7)
  end

  def test_並べ替えないKFoldは自作の並べ替えなしと一致する
    assert_equal C.k_fold_sequential(11, 3), C.rumale_folds(11, 3)
  end

  # 直線に乗らない 12 件。t = 3x - z + (x を 3 で割った余り)
  def regression_data
    x = (1..12).map { |value| column(value.to_f) }

    [x, x.map { |features| (3.0 * features.value("x")) - features.value("z") + (features.value("x") % 3) }]
  end

  def test_Rumaleの交差検証のRMSEは同じ分け方なら自作と一致する
    x, t = regression_data
    own = C.cross_validate(-> { C::LinearRegressionModel.new }, x, t, C.k_fold(12, 4, 0), C::RMSE)

    C.rumale_cross_validate_rmse(x, t, 4, seed: 0).zip(own).each do |library, mine|
      assert_in_delta mine, library, 1e-6
    end
  end

  def test_正例らしさの確率は正例のラベルの列から取る
    x = [0.1, 0.2, 0.3, 0.7, 0.8, 0.9].map { |value| column(value) }
    t = %w[死亡 死亡 死亡 生存 生存 生存]
    probabilities = C.positive_probabilities(x, t, x, "生存")

    assert_operator probabilities.last, :>, probabilities.first
    assert(probabilities.all? { |probability| probability.between?(0.0, 1.0) })
  end
end
