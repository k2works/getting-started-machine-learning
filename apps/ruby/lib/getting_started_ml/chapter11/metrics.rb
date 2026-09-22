# frozen_string_literal: true

module GettingStartedMl
  # 第 11 章の評価指標。
  module Chapter11
    # 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
    ConfusionMatrix = Data.define(:true_positive, :false_positive, :false_negative, :true_negative) do
      # 正解と予測から数える。件数が違えば ArgumentError を投げる（短いほうに合わせて黙って切り詰めない）。
      def self.of(actual, predicted, positive)
        Chapter11.require_same_size(actual, predicted)
        # 「実際が正例か」「予測が正例か」の組ごとに数える。4 通りのどれも無ければ 0 件
        counts = actual.zip(predicted).map { |truth, prediction| [truth == positive, prediction == positive] }.tally

        new(true_positive: counts.fetch([true, true], 0), false_positive: counts.fetch([false, true], 0),
            false_negative: counts.fetch([true, false], 0), true_negative: counts.fetch([false, false], 0))
      end

      # 正解率。
      def accuracy
        Chapter11.ratio(true_positive + true_negative, to_h.values.sum)
      end

      # 適合率。正例と予測したうち、本当に正例だった割合。
      def precision
        Chapter11.ratio(true_positive, true_positive + false_positive)
      end

      # 再現率。本当の正例のうち、正例と予測できた割合。
      def recall
        Chapter11.ratio(true_positive, true_positive + false_negative)
      end

      # F 値。適合率と再現率の調和平均。
      def f1_score
        Chapter11.ratio(2.0 * precision * recall, precision + recall)
      end
    end

    module_function

    # 件数が同じでなければ ArgumentError を投げる。
    def require_same_size(actual, predicted)
      raise ArgumentError, "件数が違います: #{actual.size} と #{predicted.size}" unless actual.size == predicted.size
    end

    # 分母が 0 なら 0 を返す割り算。
    def ratio(numerator, denominator)
      denominator.zero? ? 0.0 : numerator.to_f / denominator
    end

    # 混同行列から求める指標を、正例を決めて評価関数（正解と予測を受け取る lambda）に変える。
    def classification_metric(score, positive)
      ->(actual, predicted) { ConfusionMatrix.of(actual, predicted, positive).public_send(score) }
    end

    # 正解率の評価関数。第 1 章の accuracy は（予測, 正解）の順に受け取るので、順を入れ替えて包む。
    ACCURACY = ->(actual, predicted) { Chapter01.accuracy(predicted, actual) }
    # 第 7 章の RMSE と MAE は（実測, 予測）の順なので、Method オブジェクトをそのまま評価関数にできる。
    RMSE = Chapter07.method(:root_mean_squared_error)
    MAE = Chapter07.method(:mean_absolute_error)
  end
end
