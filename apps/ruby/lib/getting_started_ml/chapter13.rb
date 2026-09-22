# frozen_string_literal: true

require_relative "chapter13/pca"
require_relative "chapter13/boston"
require_relative "chapter13/rumale_pca"

module GettingStartedMl
  # 第 13 章: 主成分分析による次元削減。
  module Chapter13
    # 累積寄与率のしきい値。
    THRESHOLD = 0.8
    # 主成分ごとに表示する列の数。
    TOP_K = 3
    # 意味を調べる主成分の数。
    COMPONENTS_TO_EXPLAIN = 2
    # Rumale の初期値の乱数のシード。
    SEED = 0

    # 寄与率・必要な主成分の数・主成分への影響が大きい列を表示し、Rumale と突き合わせる。
    def self.run(out = $stdout)
      features = load_boston(File.join(Dataset.dir, "Boston.csv"))
      columns = features.first.columns
      x = to_matrix(features)
      model = fit(x, columns.size)

      out.puts "データ件数: #{x.shape[0]}, 列数: #{columns.size}", summary_lines(model, columns),
               rumale_lines(x, model)
    end

    # 寄与率・必要な主成分の数・影響の大きい列の行。
    def self.summary_lines(model, columns)
      ratios = model.explained_variance_ratio
      needed = components_needed(ratios, THRESHOLD)

      ["寄与率: #{format_ratios(ratios, needed)}",
       "累積寄与率が #{THRESHOLD} に届く主成分の数: #{needed}（累積寄与率 #{format('%.4f', ratios.first(needed).sum)}）",
       *Array.new(COMPONENTS_TO_EXPLAIN) { |index| loading_line(model, columns, index) }]
    end

    # 1 つの主成分で影響の大きい列の行。
    def self.loading_line(model, columns, index)
      loadings = top_loadings(model.components[index, true], columns, TOP_K)

      "第 #{index + 1} 主成分で影響の大きい列: #{format_loadings(loadings)}"
    end

    # 寄与率を先頭から count 個だけ 1 行にまとめる。
    def self.format_ratios(ratios, count)
      ratios.first(count).each_with_index.map { |ratio, index| "PC#{index + 1} #{format('%.4f', ratio)}" }.join(", ")
    end

    # 列名と係数を 1 行にまとめる。
    def self.format_loadings(loadings)
      loadings.map { |loading| "#{loading.column} #{format('%.3f', loading.value)}" }.join(", ")
    end

    # Rumale の固定小数点法の設定。名前と、繰り返しの上限・収束の判定の組。
    RUMALE_SETTINGS = { "既定" => { max_iter: 100, tol: 1e-4 },
                        "厳しめ" => { max_iter: 10_000, tol: 1e-12 } }.freeze

    # Rumale の主成分と寄与率が、自作とどれだけ違うかの行。設定ごとに 1 行。
    def self.rumale_lines(x, model)
      RUMALE_SETTINGS.map do |name, settings|
        theirs = RumalePca.fit(x, model.explained_variance.size, seed: SEED, **settings)
        gaps = [ratio_gap(model, theirs), (model.components - theirs.components).abs.max]

        "Rumale（#{name}: max_iter #{settings[:max_iter]}, tol #{settings[:tol]}）との差: " \
          "寄与率 #{format('%.1e', gaps[0])}, 主成分 #{format('%.1e', gaps[1])}"
      end
    end

    # 寄与率の差の絶対値の最大。
    def self.ratio_gap(model, theirs)
      model.explained_variance_ratio.zip(theirs.explained_variance_ratio).map { |mine, other| (mine - other).abs }.max
    end
  end
end
