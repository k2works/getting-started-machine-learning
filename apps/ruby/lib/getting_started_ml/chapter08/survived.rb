# frozen_string_literal: true

module GettingStartedMl
  module Chapter08
    # Survived.csv の特徴量の列と正解ラベルの列。
    module Survived
      # モデルに渡す特徴量の列。
      FEATURES = %w[Pclass Sex Age SibSp Parch Fare Embarked].freeze
      # 正解ラベルの列（1 が生存、0 が死亡）。
      TARGET = "Survived"
      # 生存を表す値。
      SURVIVED = 1

      module_function

      # 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。
      def features(rows)
        Chapter02::Table.new(columns: FEATURES, rows:)
      end

      # 行の Survived 列を、整数の正解ラベルにする。数値として読めなければ ArgumentError を投げる。
      def target(rows)
        rows.map do |row|
          cell = row.text(TARGET)
          Integer(cell, 10)
        rescue ArgumentError
          raise ArgumentError, "#{TARGET} を数値として読めません: #{cell}"
        end
      end

      # 特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。
      def passenger(values)
        Chapter07.check_size(FEATURES.size, values.size)

        Chapter02::Row.new(FEATURES.zip(values).to_h)
      end
    end
  end
end
