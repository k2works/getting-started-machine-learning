# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 14 章の K-means。点の集まりは 1 件を 1 行とする行列（Numo::DFloat）で表す。
  module Chapter14
    # 更新の回数の既定の上限（scikit-learn の KMeans と同じ）。
    DEFAULT_MAX_ITERATIONS = 300
    # 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。
    DEFAULT_N_INIT = 10

    # K-means の結果。点ごとのクラスタ番号、中心を 1 行に 1 つずつ並べた行列、誤差平方和。
    KMeansResult = Data.define(:labels, :centers, :sse)

    module_function

    # 2 点間の距離の 2 乗。
    def squared_distance(left, right)
      ((left - right)**2).sum
    end

    # 各点を、最も近い中心のクラスタ番号に割り当てる。距離が同じなら番号の小さいクラスタにする。
    # 点と中心の組ごとの距離の 2 乗を、ブロードキャストで「点の数 × 中心の数」の行列にまとめて求める。
    def assign_clusters(points, centers)
      distances = ((points.expand_dims(1) - centers.expand_dims(0))**2).sum(axis: 2)

      distances.to_a.map { |row| row.each_index.min_by { |cluster| [row[cluster], cluster] } }
    end

    # クラスタごとに、割り当てられた点の平均を新しい中心にする。
    # 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま使う。
    def update_centers(points, labels, previous)
      rows = Array.new(previous.shape[0]) do |cluster|
        members = labels.each_index.select { |index| labels[index] == cluster }
        members.empty? ? previous[cluster, true] : points[members, true].mean(axis: 0)
      end

      Numo::DFloat.cast(rows.map(&:to_a))
    end

    # 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
    def sum_of_squared_errors(points, labels, centers)
      ((points - centers[labels, true])**2).sum
    end

    # 中心が変わらなくなるか、更新の回数が max_iterations に達するまで、割り当てと中心の更新を繰り返す。
    def fit(points, initial_centers, max_iterations: DEFAULT_MAX_ITERATIONS)
      centers = initial_centers
      max_iterations.times do
        updated = update_centers(points, assign_clusters(points, centers), centers)
        break if updated == centers

        centers = updated
      end

      labels = assign_clusters(points, centers)
      KMeansResult.new(labels:, centers:, sse: sum_of_squared_errors(points, labels, centers))
    end

    # シード付きの乱数で点の番号を並べ替え、先頭から n_clusters 個の点を初期中心にする。
    def choose_initial_centers(points, n_clusters, seed)
      count = points.shape[0]
      raise ArgumentError, "クラスタ数は 1 以上 #{count} 以下にしてください: #{n_clusters}" unless n_clusters.between?(1, count)

      # 第 2 章の shuffle（分割と同じ乱数の使い方）を再利用する。sample に替えると選ばれる点が変わる
      order = Chapter02.shuffle((0...count).to_a, seed)
      points[order.first(n_clusters), true].dup
    end

    # シードを 1 ずつずらして初期中心を n_init 通り選び、SSE が最小の結果を返す。同じなら先の結果を選ぶ。
    def fit_with_restarts(points, n_clusters, seed:, n_init: DEFAULT_N_INIT)
      Array.new([n_init, 1].max) { |offset| fit(points, choose_initial_centers(points, n_clusters, seed + offset)) }
           .min_by.with_index { |result, index| [result.sse, index] }
    end

    # クラスタ数ごとに、初期中心を n_init 通り試した最小の SSE。
    def sse_by_cluster_count(points, cluster_counts, seed:, n_init: DEFAULT_N_INIT)
      cluster_counts.map { |n_clusters| [n_clusters, fit_with_restarts(points, n_clusters, seed:, n_init:).sse] }
    end
  end
end
