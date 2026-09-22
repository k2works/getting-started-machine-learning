# frozen_string_literal: true

require_relative "chapter09/dummies"
require_relative "chapter09/standardizer"
require_relative "chapter09/rumale_scaler"
require_relative "chapter09/polynomial"
require_relative "chapter09/outliers"
require_relative "chapter09/linear"
require_relative "chapter09/bike_weather"
require_relative "chapter09/boston"

module GettingStartedMl
  # 第 9 章: 特徴量エンジニアリング。ダミー変数・標準化・多項式特徴量・外れ値・表の結合。
  module Chapter09
    # テストデータの割合。
    TEST_SIZE = 0.3
    # 分割の乱数のシード。
    SEED = 0
    # 多項式特徴量を作る元の列。
    COLUMNS = %w[RM LSTAT PTRATIO].freeze
    # 2 乗の項。
    SQUARES = %w[RM^2 LSTAT^2 PTRATIO^2].freeze

    # 特徴量の組の名前と、使う項。
    def self.feature_sets
      { "元の特徴量" => COLUMNS,
        "2 乗の項を追加" => COLUMNS + SQUARES,
        "交互作用の項も追加" => COLUMNS + pairs_with_replacement(COLUMNS).map(&:name) }
    end

    # 特徴量の組ごとの決定係数と、天気ごとの平均利用者数を表示する。
    def self.run(out = $stdout)
      split = prepare_boston(File.join(Dataset.dir, "Boston.csv"), test_size: TEST_SIZE, seed: SEED)

      out.puts split_lines(split), standardized_rm(split.x_train), scores_lines(split), weather_line
    end

    # 分割の件数と特徴量の列の行。
    def self.split_lines(split)
      ["訓練データ: #{split.x_train.size} 件, テストデータ: #{split.x_test.size} 件",
       "特徴量の列: #{split.x_train.first.columns.join(', ')}"]
    end

    # 標準化した訓練データの RM の平均と標準偏差を、自作と Rumale で並べる。
    # 標準化した値にもう一度自作の Standardizer を当てて、平均と（件数で割る）標準偏差を読む。
    def self.standardized_rm(x_train)
      ours = Standardizer.fit(Standardizer.fit(x_train).transform(x_train))
      theirs = rumale_rm(x_train)

      ["標準化した訓練データの RM: 平均 #{two(ours.mean('RM'))}, 標準偏差 #{two(ours.std('RM'))}",
       "Rumale で標準化した RM: 平均 #{two(theirs.mean('RM'))}, 標準偏差 #{Kernel.format('%.4f', theirs.std('RM'))}"]
    end

    # 決定係数の行。特徴量の組ごとと、外れ値を除いたとき。
    def self.scores_lines(split)
      outlier_count = iqr_outliers(split.t_train).count(true)
      removed = score_feature_set(remove_target_outliers(split), COLUMNS, COLUMNS + SQUARES)

      ["決定係数:",
       *feature_sets.map { |name, terms| "  #{name}（#{terms.size} 列）: #{scores_text(split, terms)}" },
       "訓練データの PRICE の外れ値: #{outlier_count} 件",
       "  外れ値を除いて 2 乗の項を追加: #{format_scores(removed)}"]
    end

    # 項の組の決定係数を 1 行にする。
    def self.scores_text(split, terms)
      format_scores(score_feature_set(split, COLUMNS, terms))
    end

    # 天気ごとの平均利用者数の行。
    def self.weather_line
      joined = join_weather(load_bike(File.join(Dataset.dir, "bike.tsv")),
                            load_weather(File.join(Dataset.dir, "weather.csv")))
      means = mean_count_by_weather(joined).map { |weather, mean| "#{weather}=#{Kernel.format('%.1f', mean)}" }

      "天気ごとの平均利用者数: #{means.join(', ')}"
    end

    # Rumale で標準化した RM に、自作の Standardizer を当てる。
    def self.rumale_rm(x_train)
      rm = x_train.map { |features| features.value("RM") }
      scaled = RumaleScaler.standardize(rm, rm).map { |value| Chapter02::Features.new(columns: %w[RM], values: [value]) }

      Standardizer.fit(scaled)
    end

    # 決定係数を 1 行にまとめる。
    def self.format_scores(scores)
      "訓練 #{Kernel.format('%.4f', scores.train)}, テスト #{Kernel.format('%.4f', scores.test)}"
    end

    # 小数 2 桁にする。浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
    def self.two(value)
      Kernel.format("%.2f", value.abs < 1e-9 ? 0.0 : value)
    end
  end
end
