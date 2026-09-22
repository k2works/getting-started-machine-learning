# frozen_string_literal: true

require_relative "chapter14/kmeans"
require_relative "chapter14/spending"
require_relative "chapter14/rumale_kmeans"

module GettingStartedMl
  # 第 14 章: K-means によるクラスタリング。
  module Chapter14
    # 乱数のシード。
    SEED = 0
    # エルボー法で試すクラスタ数の上限。
    MAX_CLUSTERS = 10
    # 特徴を調べるクラスタ数。
    N_CLUSTERS = 5

    # クラスタ数ごとの SSE を自作と Rumale で並べ、クラスタごとの件数・平均支出額を表示する。
    def self.run(out = $stdout)
      x = load_spending(File.join(Dataset.dir, "Wholesale.csv"))
      points = standardize_spending(x)

      out.puts "データ件数: #{x.size}（支出額 #{x.first.columns.size} 列）", elbow_lines(points), "",
               summary_lines(x, points)
    end

    # クラスタ数ごとの SSE の行。
    def self.elbow_lines(points)
      counts = (1..MAX_CLUSTERS).to_a
      theirs = counts.map { |n_clusters| RumaleKMeans.fit(points, n_clusters, seed: SEED).sse }

      ["クラスタ数ごとの SSE（初期中心 #{DEFAULT_N_INIT} 通りの最小値）:", "クラスタ数\t自作\tRumale（k-means++）",
       *sse_by_cluster_count(points, counts, seed: SEED).zip(theirs).map do |(n_clusters, sse), other|
         "#{n_clusters}\t#{format('%.2f', sse)}\t#{format('%.2f', other)}"
       end]
    end

    # 自作の K-means で N_CLUSTERS 個に分けたときの、クラスタごとの件数と平均支出額の行。
    def self.summary_lines(x, points)
      result = fit_with_restarts(points, N_CLUSTERS, seed: SEED)

      ["クラスタ数 #{N_CLUSTERS} のクラスタごとの件数と平均支出額:", "クラスタ\t件数\t#{x.first.columns.join("\t")}",
       *summarize_clusters(x, result.labels).map { |summary| format_summary(summary) }]
    end
  end
end
