# frozen_string_literal: true

module GettingStartedMl
  # 第 8 章の評価。
  module Chapter08
    # 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。
    Evaluation = Data.define(:train_accuracy, :test_accuracy, :found_survivors, :survivors)

    module_function

    # 予測が正解ラベルと一致した割合。第 1 章の accuracy をそのまま使う。
    def accuracy(predictions, labels)
      Chapter01.accuracy(predictions, labels)
    end

    # 学習済みのパイプラインを、訓練データとテストデータで評価する。
    def evaluate(pipeline, split)
      predictions = pipeline.predict(Survived.features(split.x_test))

      Evaluation.new(
        train_accuracy: accuracy(pipeline.predict(Survived.features(split.x_train)), split.t_train),
        test_accuracy: accuracy(predictions, split.t_test),
        found_survivors: found_survivors(predictions, split.t_test),
        survivors: split.t_test.count(Survived::SURVIVED)
      )
    end

    # 生存者のうち、生存と予測できた人数。
    def found_survivors(predictions, labels)
      predictions.zip(labels).count { |prediction, label| prediction == Survived::SURVIVED && label == prediction }
    end
  end
end
