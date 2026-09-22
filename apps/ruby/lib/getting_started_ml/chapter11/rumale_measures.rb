# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  # 第 11 章の評価指標と交差検証を、Rumale で同じようにして突き合わせる。
  module Chapter11
    # Rumale の評価指標から取り出したスコア。
    RumaleScores = Data.define(:accuracy, :precision, :recall, :f1_score)

    module_function

    # 文字列のラベルを、昇順に並べた位置の番号（Numo::Int32）にする。
    # Rumale の Precision・Recall・FScore（average: "binary"）は、正解に現れるラベルを昇順に並べて
    # **最後のもの**を正例にする。つまり大きいほうのラベルが正例になる。
    def encode_sorted(actual, predicted)
      classes = (actual + predicted).uniq.sort

      [actual, predicted].map { |labels| Numo::Int32.cast(labels.map { |label| classes.index(label) }) }
    end

    # Rumale の混同行列。正解を行、予測を列にして、ラベルの昇順に並べる。
    def rumale_confusion_matrix(actual, predicted)
      Rumale::EvaluationMeasure.confusion_matrix(*encode_sorted(actual, predicted)).to_a
    end

    # Rumale の Accuracy・Precision・Recall・FScore で求めたスコア。
    def rumale_scores(actual, predicted)
      truth, prediction = encode_sorted(actual, predicted)
      measures = [Rumale::EvaluationMeasure::Accuracy.new, Rumale::EvaluationMeasure::Precision.new,
                  Rumale::EvaluationMeasure::Recall.new, Rumale::EvaluationMeasure::FScore.new]

      RumaleScores.new(*measures.map { |measure| measure.score(truth, prediction) })
    end

    # Rumale の ROCAUC で AUC を求める。正解は正例なら 1、負例なら 0 の整数にして渡す。
    def rumale_auc(scores, labels)
      Rumale::EvaluationMeasure::ROCAUC.new.score(Numo::Int32.cast(labels.map { |label| label ? 1 : 0 }),
                                                  Numo::DFloat.cast(scores))
    end

    # Rumale の KFold。シードを渡せば行を並べ替えてから、渡さなければ先頭から順に分ける。
    # 並べ替えないときも random_seed を渡す（省くと srand でプロセス全体の乱数の種が入れ替わる）。
    def rumale_kfold(n_splits, seed)
      Rumale::ModelSelection::KFold.new(n_splits:, shuffle: !seed.nil?, random_seed: seed || 0)
    end

    # Rumale の KFold の分け方を、自作と同じ Fold の並びにする。
    def rumale_folds(n_samples, n_splits, seed: nil)
      rumale_kfold(n_splits, seed).split(Numo::DFloat.zeros(n_samples, 1)).map do |train, test|
        Fold.new(train:, test:)
      end
    end

    # Rumale の CrossValidation で、分割ごとの RMSE を求める。
    # Rumale の評価指標は平均二乗誤差（MSE）なので、分割ごとに平方根を取る。
    def rumale_cross_validate_rmse(x, t, n_splits, seed: nil)
      validation = Rumale::ModelSelection::CrossValidation.new(
        estimator: Rumale::LinearModel::LinearRegression.new(tol: Chapter07::RumaleRegression::TOLERANCE),
        splitter: rumale_kfold(n_splits, seed), evaluator: Rumale::EvaluationMeasure::MeanSquaredError.new
      )

      validation.perform(Chapter03::RumaleTree.matrix(x), Numo::DFloat.cast(t))[:test_score].map { |mse| Math.sqrt(mse) }
    end

    # Rumale のロジスティック回帰で、正例らしさの確率を求める。ROC 曲線に使う。
    # 正解を「正例なら 1、そうでなければ 0」にして学習するので、predict_proba の 2 列目が正例の確率になる。
    def positive_probabilities(x_train, t_train, x_test, positive)
      codes = Numo::Int32.cast(t_train.map { |label| label == positive ? 1 : 0 })
      model = Rumale::LinearModel::LogisticRegression.new.fit(Chapter03::RumaleTree.matrix(x_train), codes)

      model.predict_proba(Chapter03::RumaleTree.matrix(x_test))[true, 1].to_a
    end
  end
end
