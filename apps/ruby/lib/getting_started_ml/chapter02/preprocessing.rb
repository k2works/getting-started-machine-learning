# frozen_string_literal: true

module GettingStartedMl
  # 第 2 章の前処理（欠損値の補完と分割）。
  module Chapter02
    # アヤメのデータの正解ラベルの列。
    TARGET = "種類"

    # 欠損値を持たない特徴量。列名と値を同じ順で持つ。
    Features = Data.define(:columns, :values) do
      def initialize(columns:, values:)
        raise ArgumentError, "件数が違います: #{columns.size} と #{values.size}" unless columns.size == values.size

        super
      end

      # 列名で値を読む。
      def value(column)
        index = columns.index(column)
        raise KeyError, "列がありません: #{column}" if index.nil?

        values[index]
      end
    end

    # 訓練データとテストデータ。
    TrainTestSplit = Data.define(:x_train, :x_test, :t_train, :t_test)

    module_function

    # 欠損値を除いて、列ごとの平均値を求める。
    def column_means(rows, columns)
      columns.to_h do |column|
        values = rows.filter_map { |row| row.number(column) }
        raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

        [column, values.sum / values.size]
      end
    end

    # 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。
    def fill_missing(rows, columns, fill_values)
      rows.map do |row|
        values = columns.map do |column|
          row.number(column) || fill_values.fetch(column) { raise KeyError, "補完する値がありません: #{column}" }
        end

        Features.new(columns:, values:)
      end
    end

    # 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
    def split_features_and_target(table, target)
      [table.columns - [target], table.rows, table.rows.map { |row| row.text(target) }]
    end

    # シードを使って並べ替える。元の配列は変えない。
    def shuffle(items, seed)
      items.shuffle(random: Random.new(seed))
    end

    # 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
    def split_train_test(x, t, test_size:, seed:)
      raise ArgumentError, "件数が違います: #{x.size} と #{t.size}" unless x.size == t.size

      shuffled = shuffle(x.zip(t), seed)
      train = shuffled.first(shuffled.size - (shuffled.size * test_size).ceil)

      to_split(train, shuffled.drop(train.size))
    end

    # （特徴量, 正解ラベル）の組の並びを、訓練データとテストデータに組み直す。
    def to_split(train, test)
      x_train, t_train = train.transpose
      x_test, t_test = test.transpose

      TrainTestSplit.new(x_train: x_train || [], x_test: x_test || [], t_train: t_train || [], t_test: t_test || [])
    end

    # iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
    def prepare_iris(csv_file, test_size:, seed:)
      table = Table.load(csv_file)
      columns, rows, labels = split_features_and_target(table, TARGET)
      split = split_train_test(rows, labels, test_size:, seed:)
      means = column_means(split.x_train, columns)

      split.with(x_train: fill_missing(split.x_train, columns, means),
                 x_test: fill_missing(split.x_test, columns, means))
    end
  end
end
