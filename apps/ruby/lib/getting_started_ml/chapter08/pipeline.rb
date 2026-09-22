# frozen_string_literal: true

module GettingStartedMl
  # 第 8 章の前処理とモデルをつなぐパイプライン。
  module Chapter08
    # 学習前のパイプライン。前処理を順に fit・transform してから、モデルを学習する。
    Pipeline = Data.define(:steps, :model) do
      # Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。
      def self.build(max_depth:, class_weight:)
        new(steps: [GroupMedian.new(column: "Age", by: %w[Pclass Sex]),
                    MostFrequent.new(column: "Embarked"),
                    Dummy.new(columns: %w[Sex Embarked])],
            model: DecisionTreeClassifier.new(max_depth:, class_weight:))
      end

      # 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。
      # Data は属性を差し替えられないだけで、中の分類器は書き換えられる。学び直しで前の結果を
      # 壊さないように、分類器を複製してから学習する。
      def fit(x, t)
        prepared = x
        fitted = steps.map do |step|
          step.fit(prepared).tap { |fitted_step| prepared = fitted_step.transform(prepared) }
        end

        FittedPipeline.new(steps: fitted, model: model.dup.fit(Chapter08.to_features(prepared), t))
      end
    end

    # 学習済みのパイプライン。予測するときは前処理の transform だけを使う。
    FittedPipeline = Data.define(:steps, :model) do
      # 学習済みの前処理を順に適用する。
      def transform(x)
        steps.reduce(x) { |prepared, step| step.transform(prepared) }
      end

      # 前処理をして、モデルに渡す特徴量にする。
      def features(x)
        Chapter08.to_features(transform(x))
      end

      # 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。
      def predict(x)
        model.predict(features(x))
      end
    end

    module_function

    # 前処理の済んだ表を特徴量にする。欠損値が残っていれば ArgumentError を投げる。
    def to_features(x)
      x.rows.map do |row|
        values = x.columns.map { |column| row.number(column) || raise(ArgumentError, "値が空欄です: #{column}") }
        Chapter02::Features.new(columns: x.columns, values:)
      end
    end
  end
end
