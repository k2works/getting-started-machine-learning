# frozen_string_literal: true

module GettingStartedMl
  # 第 11 章の ROC 曲線と AUC。
  module Chapter11
    # ROC 曲線の 1 点。threshold 以上を正例と予測したときの偽陽性率と真陽性率。
    RocPoint = Data.define(:threshold, :false_positive_rate, :true_positive_rate)

    module_function

    # スコア（正例らしさ）と正解（正例なら true）から ROC 曲線を求める。
    # スコアの高いほうから閾値を下げていき、同じスコアは 1 つの点にまとめる。
    def roc_curve(scores, labels)
      require_same_size(scores, labels)
      positives = labels.count(true)
      negatives = labels.size - positives
      raise ArgumentError, "正例と負例が両方ないと ROC 曲線を描けません" if positives.zero? || negatives.zero?

      [RocPoint.new(threshold: Float::INFINITY, false_positive_rate: 0.0, true_positive_rate: 0.0),
       *roc_points(scores, labels, positives, negatives)]
    end

    # 閾値ごとの点。スコアの高いほうから、同じスコアのかたまりごとに正例と負例の数を足し込む。
    def roc_points(scores, labels, positives, negatives)
      true_positive = 0
      false_positive = 0

      score_groups(scores, labels).map do |threshold, group|
        true_positive += group.count(true)
        false_positive += group.count(false)

        RocPoint.new(threshold:, false_positive_rate: false_positive.fdiv(negatives),
                     true_positive_rate: true_positive.fdiv(positives))
      end
    end

    # スコアごとにラベルをまとめ、スコアの降順に並べる。
    def score_groups(scores, labels)
      scores.zip(labels).group_by(&:first).sort_by { |score, _| -score }
            .map { |score, pairs| [score, pairs.map(&:last)] }
    end

    # ROC 曲線の下の面積（AUC）を台形則で求める。
    def auc(curve)
      curve.each_cons(2).sum do |left, right|
        (right.false_positive_rate - left.false_positive_rate) *
          (left.true_positive_rate + right.true_positive_rate) / 2.0
      end
    end
  end
end
