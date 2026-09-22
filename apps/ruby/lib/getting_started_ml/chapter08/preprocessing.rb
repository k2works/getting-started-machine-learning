# frozen_string_literal: true

module GettingStartedMl
  # 第 8 章の前処理の部品。欠損値の補完とダミー変数化は Rumale に無いので自作する。
  #
  # 前処理は「fit で学習済みの前処理を返す」「transform で表を変える」という 2 つのメソッドを持つだけで、
  # 共通の親クラスもインターフェースも宣言しない（ダックタイピング）。
  module Chapter08
    # 数値の列の欠損値を、同じグループ（by の列の値の組）の中央値で補完する。
    GroupMedian = Data.define(:column, :by) do
      # グループごとの中央値と全体の中央値を求める。グループは値の組の配列をそのまま Hash の鍵にする。
      def fit(x)
        present = x.rows.reject { |row| row.missing?(column) }
        medians = present.group_by { |row| Chapter08.group_of(row, by) }
                         .transform_values { |rows| Chapter08.column_median(rows, column) }

        FittedGroupMedian.new(column:, by:, medians:, overall: Chapter08.column_median(present, column))
      end
    end

    # 学習済みの GroupMedian。訓練データに無いグループは全体の中央値で埋める。
    FittedGroupMedian = Data.define(:column, :by, :medians, :overall) do
      def transform(x)
        Chapter08.fill(x, column) { |row| medians.fetch(Chapter08.group_of(row, by), overall).to_s }
      end
    end

    # 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する。
    MostFrequent = Data.define(:column) do
      # 同数なら先に現れた値を選ぶ。tally は現れた順を保ち、max_by は同点なら最初の要素を返す。
      def fit(x)
        values = x.rows.reject { |row| row.missing?(column) }.map { |row| row.text(column) }
        raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

        FittedMostFrequent.new(column:, most_frequent: values.tally.max_by { |_value, count| count }.first)
      end
    end

    # 学習済みの MostFrequent。
    FittedMostFrequent = Data.define(:column, :most_frequent) do
      def transform(x)
        Chapter08.fill(x, column) { most_frequent }
      end
    end

    # カテゴリ値の列を、並べ替えて最初のカテゴリを除いた 0 と 1 の列（ダミー変数）にする。
    Dummy = Data.define(:columns) do
      def fit(x)
        FittedDummy.new(dummies: columns.to_h { |column| [column, Chapter08.categories(x, column).drop(1)] })
      end
    end

    # 学習済みの Dummy。元の列を除き、ダミー変数の列を末尾に足す。
    FittedDummy = Data.define(:dummies) do
      def transform(x)
        x.with(columns: (x.columns - dummies.keys) + Chapter08.dummy_columns(dummies),
               rows: x.rows.map { |row| Chapter02::Row.new(row.cells.merge(Chapter08.flags(row, dummies))) })
      end
    end

    module_function

    # 行の属するグループ。by の列の値を並べた配列。
    def group_of(row, columns)
      columns.map { |column| row.text(column) }
    end

    # 行の列の値の中央値。
    def column_median(rows, column)
      median(rows.map { |row| row.number(column) }, column)
    end

    # 中央値。件数が偶数なら中央の 2 つの平均。値が無ければ ArgumentError を投げる。
    def median(values, column)
      raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

      sorted = values.sort
      middle = sorted.size / 2
      sorted.size.odd? ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0
    end

    # 欠損値を、行ごとにブロックが返す値で埋める。元の行は変えず、埋めた行だけ新しく作る。
    def fill(x, column)
      x.with(rows: x.rows.map do |row|
        row.missing?(column) ? Chapter02::Row.new(row.cells.merge(column => yield(row))) : row
      end)
    end

    # 列のカテゴリを、欠損値を除いて並べ替える。
    def categories(x, column)
      values = x.rows.reject { |row| row.missing?(column) }.map { |row| row.text(column) }.uniq.sort
      raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

      values
    end

    # ダミー変数の列名。「元の列名_カテゴリ」にする。
    def dummy_columns(dummies)
      dummies.flat_map { |column, categories| categories.map { |category| "#{column}_#{category}" } }
    end

    # 1 行分のダミー変数の列と値（"0" か "1"）。
    def flags(row, dummies)
      dummies.flat_map do |column, categories|
        categories.map { |category| ["#{column}_#{category}", row.text(column) == category ? "1" : "0"] }
      end.to_h
    end
  end
end
