# frozen_string_literal: true

require_relative "chapter11/metrics"
require_relative "chapter11/roc"
require_relative "chapter11/cross_validation"
require_relative "chapter11/data"
require_relative "chapter11/rumale_measures"

module GettingStartedMl
  # 第 11 章: 評価指標と交差検証。
  module Chapter11
    # 分割の数。
    N_SPLITS = 5
    # 分割の乱数のシード。
    SEED = 0
    # 決定木の深さ。
    TREE_DEPTH = 2

    # Survived の評価指標。表示する順に並べる。
    def self.survived_metrics
      { "正解率" => ACCURACY,
        "適合率" => classification_metric(:precision, SURVIVED_POSITIVE),
        "再現率" => classification_metric(:recall, SURVIVED_POSITIVE),
        "F値" => classification_metric(:f1_score, SURVIVED_POSITIVE) }
    end

    # Survived と cinema を K 分割交差検証で評価し、Rumale と突き合わせた結果を表示する。
    def self.run(out = $stdout)
      out.puts survived_lines(*prepare_survived(Chapter02::Table.load(File.join(Dataset.dir, "Survived.csv"))))
      out.puts
      out.puts cinema_lines(*prepare_cinema(Chapter02::Table.load(File.join(Dataset.dir, "cinema.csv"))))
    end

    # Survived の決定木を交差検証にかけ、最初の分割で ROC 曲線と混同行列を求めた行。
    def self.survived_lines(x, t)
      folds = k_fold(x.size, N_SPLITS, SEED)
      make = -> { Chapter03::DecisionTree.new(max_depth: TREE_DEPTH) }
      averages = survived_metrics.map do |name, metric|
        "  #{name}（#{N_SPLITS} 分割の平均）: #{four(mean(cross_validate(make, x, t, folds, metric)))}"
      end

      ["Survived（決定木・深さ #{TREE_DEPTH}）", "  件数: #{x.size}", *averages,
       "  分け方（自作の k_fold と Rumale の KFold）: #{folds == rumale_folds(x.size, N_SPLITS, seed: SEED) ? '一致' : '不一致'}",
       *first_fold_lines(x, t, folds.first)]
    end

    # 1 つ目の分割で、ROC 曲線の AUC と決定木の混同行列を自作と Rumale で並べた行。
    # ROC 曲線には「正例らしさ」の連続値が要るので、確率を返すロジスティック回帰で採点する。
    def self.first_fold_lines(x, t, fold)
      x_train, t_train = [x, t].map { |values| values.values_at(*fold.train) }
      x_test, t_test = [x, t].map { |values| values.values_at(*fold.test) }
      [*roc_lines(positive_probabilities(x_train, t_train, x_test, SURVIVED_POSITIVE), t_test),
       *matrix_lines(t_test, Chapter03::DecisionTree.new(max_depth: TREE_DEPTH).fit(x_train, t_train).predict(x_test))]
    end

    # ROC 曲線の点の数と、AUC を自作と Rumale で並べた行。
    def self.roc_lines(probabilities, t_test)
      labels = t_test.map { |label| label == SURVIVED_POSITIVE }
      curve = roc_curve(probabilities, labels)

      ["  ROC 曲線の点の数: #{curve.size}", "  AUC（自作）: #{four(auc(curve))}",
       "  AUC（Rumale）: #{four(rumale_auc(probabilities, labels))}"]
    end

    # 混同行列と、適合率を自作と Rumale で並べた行。
    def self.matrix_lines(actual, predicted)
      matrix = ConfusionMatrix.of(actual, predicted, SURVIVED_POSITIVE)

      ["  混同行列（1 つ目の分割）: TP=#{matrix.true_positive} FP=#{matrix.false_positive} " \
       "FN=#{matrix.false_negative} TN=#{matrix.true_negative}",
       "  Rumale の混同行列: #{rumale_confusion_matrix(actual, predicted)}",
       "  適合率: 自作 #{four(matrix.precision)} / Rumale #{four(rumale_scores(actual, predicted).precision)}"]
    end

    # cinema の線形回帰を交差検証にかけ、RMSE を Rumale の CrossValidation と並べた行。
    def self.cinema_lines(x, t)
      folds = k_fold(x.size, N_SPLITS, SEED)
      averages = { "RMSE" => RMSE, "MAE" => MAE }.map do |name, metric|
        scores = cross_validate(-> { LinearRegressionModel.new }, x, t, folds, metric)

        "  #{name}（#{N_SPLITS} 分割の平均）: #{two(mean(scores))}"
      end

      ["cinema（線形回帰）", "  件数: #{x.size}", *averages,
       rmse_line("並べ替えあり", x, t, folds, SEED), rmse_line("並べ替えなし", x, t, k_fold_sequential(x.size, N_SPLITS), nil)]
    end

    # 同じ分け方の RMSE の平均を、自作と Rumale（seed が nil なら並べ替えない KFold）で並べた行。
    def self.rmse_line(label, x, t, folds, seed)
      own = mean(cross_validate(-> { LinearRegressionModel.new }, x, t, folds, RMSE))

      "  RMSE（#{label}）: 自作 #{two(own)} / Rumale #{two(mean(rumale_cross_validate_rmse(x, t, N_SPLITS, seed:)))}"
    end

    # 小数 4 桁の文字列にする。
    def self.four(value)
      Kernel.format("%.4f", value)
    end

    # 小数 2 桁の文字列にする。
    def self.two(value)
      Kernel.format("%.2f", value)
    end
  end
end
