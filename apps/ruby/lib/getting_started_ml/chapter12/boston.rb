# frozen_string_literal: true

module GettingStartedMl
  # 第 12 章の前処理。ボストンの住宅価格（Boston.csv）を、外れ値を除いて訓練・検証・テストの 3 つに分ける。
  module Chapter12
    # 特徴量の列。
    FEATURES = %w[RM PTRATIO LSTAT].freeze
    # 正解の列。
    TARGET = "PRICE"
    # z スコアの絶対値がこの値を超える行を外れ値とする。
    OUTLIER_THRESHOLD = 3.0

    # 標準化と多項式特徴量をつなげた変換。訓練データで fit し、同じ平均と標準偏差で
    # 訓練・検証・テストの 3 つを transform する。第 9 章の Standardizer と expand をそのまま使う。
    PolynomialScaler = Data.define(:standardizer) do
      # 訓練データの平均と標準偏差を覚える。
      def self.fit(x)
        new(standardizer: Chapter09::Standardizer.fit(x))
      end

      # 変換後の列名。元の列、2 乗の列（"RM^2"）、積の列（"RM LSTAT"）の順。
      def feature_names
        standardizer.columns + Chapter09.pairs_with_replacement(standardizer.columns).map(&:name)
      end

      # 標準化してから 2 次の項を足し、値だけの配列にする。
      def transform(x)
        Chapter09.expand(standardizer.transform(x), standardizer.columns).map(&:values)
      end
    end

    # 訓練・検証・テストの特徴量（値だけの配列）と正解。kept と removed は外れ値を除いたあとの件数と除いた件数。
    BostonSplit = Data.define(:x_train, :t_train, :x_valid, :t_valid, :x_test, :t_test, :feature_names, :kept, :removed)

    module_function

    # 欠損値を許さずに数値の列を読む。
    def required_number(row, column)
      row.number(column) || raise(ArgumentError, "値が空欄です: #{column}")
    end

    # 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が threshold を超える値を
    # 1 つでも持つ行を除く。元の表は変えない。
    def remove_outliers(table, columns, threshold)
      stats = columns.to_h do |column|
        [column, mean_and_sample_std(table.rows.map { |row| required_number(row, column) })]
      end

      table.with(rows: table.rows.reject do |row|
        stats.any? { |column, (mean, std)| ((required_number(row, column) - mean) / std).abs > threshold }
      end)
    end

    # 平均と、件数 n - 1 で割る標準偏差の組。
    def mean_and_sample_std(values)
      mean = values.sum / values.size

      [mean, Math.sqrt(values.sum { |value| (value - mean)**2 } / (values.size - 1))]
    end

    # 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
    # 標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。
    def prepare(csv_file, test_size:, validation_size:, seed:)
      table = Chapter02::Table.load(csv_file)
      kept = remove_outliers(table, [*FEATURES, TARGET], OUTLIER_THRESHOLD).rows
      outer = split_rows(kept, test_size, seed)
      inner = Chapter02.split_train_test(outer.x_train, outer.t_train, test_size: validation_size, seed:)

      scale(inner, outer, kept: kept.size, removed: table.rows.size - kept.size)
    end

    # 訓練データで標準化の平均と標準偏差を求め、訓練・検証・テストの 3 つを同じ値で変換する。
    # inner は訓練データと検証データ、outer はテストデータを持つ第 2 章の分割。
    def scale(inner, outer, kept:, removed:)
      scaler = PolynomialScaler.fit(inner.x_train)
      x_train, x_valid, x_test = [inner.x_train, inner.x_test, outer.x_test].map { |x| scaler.transform(x) }

      BostonSplit.new(x_train:, t_train: inner.t_train, x_valid:, t_valid: inner.t_test, x_test:, t_test: outer.t_test,
                      feature_names: scaler.feature_names, kept:, removed:)
    end

    # 行を特徴量と正解にしてから、第 2 章の関数で訓練用とテスト用に分ける。
    def split_rows(rows, test_size, seed)
      x = rows.map { |row| Chapter02::Features.new(columns: FEATURES, values: FEATURES.map { |c| required_number(row, c) }) }

      Chapter02.split_train_test(x, rows.map { |row| required_number(row, TARGET) }, test_size:, seed:)
    end
  end
end
