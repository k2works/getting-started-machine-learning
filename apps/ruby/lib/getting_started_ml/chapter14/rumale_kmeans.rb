# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter14
    # Rumale の KMeans を呼ぶ。自作の K-means と突き合わせるために使う。
    module RumaleKMeans
      module_function

      # シードを 1 ずつずらして n_init 回学習し、SSE が最小の結果を返す。Rumale の KMeans は SSE を持たず、
      # 初期中心を何通りも試す仕組み（scikit-learn の n_init）も無いので、どちらもこちらで用意する。
      # 乱数で初期中心を選ぶので、シードは必ず渡す（省くと srand でプロセス全体の乱数の種が入れ替わる）。
      def fit(points, n_clusters, seed:, n_init: DEFAULT_N_INIT, **options)
        Array.new([n_init, 1].max) { |offset| fit_once(points, n_clusters, seed + offset, options) }
             .min_by.with_index { |result, index| [result.sse, index] }
      end

      # 1 回だけ学習し、中心と、自作と同じ式で測り直した SSE を返す。
      def fit_once(points, n_clusters, seed, options)
        model = Rumale::Clustering::KMeans.new(n_clusters:, random_seed: seed, **options).fit(points)
        centers = model.cluster_centers
        labels = Chapter14.assign_clusters(points, centers)

        KMeansResult.new(labels:, centers:, sse: Chapter14.sum_of_squared_errors(points, labels, centers))
      end
    end
  end
end
