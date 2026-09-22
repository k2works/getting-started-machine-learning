# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 13 章の主成分分析。分散共分散行列をヤコビ法で固有値分解し、寄与率の大きい順に主成分を並べる。
  module Chapter13
    # 対角より外の成分が行列全体の大きさに対してこの割合より小さくなったら、固有値分解が終わったとみなす。
    TOLERANCE = 1e-12
    # 1 回の掃き出しで対角より外の成分をすべて回す、その繰り返しの上限。
    MAX_SWEEPS = 100

    # 決めた回数だけ繰り返しても固有値分解が収束しない。
    class NotConvergedError < StandardError
      def initialize(sweeps)
        super("#{sweeps} 回繰り返しても固有値分解が収束しませんでした")
      end
    end

    # 学習した主成分分析のモデル。components は主成分を 1 行に 1 つずつ、寄与率の大きい順に並べた行列。
    PcaModel = Data.define(:mean, :components, :explained_variance, :explained_variance_ratio)

    # 主成分の向きに対する 1 つの列の係数。
    Loading = Data.define(:column, :value)

    module_function

    # 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。
    def covariance_matrix(x)
      rows = x.shape[0]
      raise ArgumentError, "主成分分析には 2 件以上のデータが必要です（#{rows} 件）" if rows < 2

      centered = x - x.mean(axis: 0)
      centered.transpose.dot(centered) / (rows - 1)
    end

    # 対称行列をヤコビ法で固有値分解する。固有値の並びと、固有ベクトルを列ごとに並べた行列を返す。
    # Numo には固有値分解が無い（Numo::Linalg は LAPACK を要求する）ので、対称行列の場合だけを自作する。
    def jacobi_eigen(matrix, max_sweeps: MAX_SWEEPS)
      size, columns = matrix.shape
      raise ArgumentError, "正方行列ではありません: #{size} 行 #{columns} 列" unless size == columns

      work = matrix.dup
      vectors = Numo::DFloat.eye(size)
      # 行列の大きさに合わせた「これ以上は消せない」しきい値（Rust 版の教訓で、絶対値ではなく割合で比べる）
      limit = TOLERANCE * [frobenius_norm(matrix), Float::MIN].max

      max_sweeps.times do
        return [work.diagonal.to_a, vectors] if off_diagonal_norm(work) < limit

        sweep(work, vectors, limit)
      end

      raise NotConvergedError, max_sweeps
    end

    # すべての成分の 2 乗和の平方根。
    def frobenius_norm(matrix)
      Math.sqrt((matrix**2).sum)
    end

    # 対角より外の成分の 2 乗和の平方根。
    def off_diagonal_norm(matrix)
      Math.sqrt([(matrix**2).sum - (matrix.diagonal**2).sum, 0.0].max)
    end

    # 対角より外の成分を 1 つずつ回して 0 に近づける。
    def sweep(work, vectors, limit)
      size = work.shape[0]

      (0...size).to_a.combination(2).each do |pair|
        # すでに十分小さい成分は回さない。しきい値は収束の判定より細かくする
        next if work[*pair].abs < limit * Float::EPSILON

        rotate(work, vectors, pair)
      end
    end

    # (p, q) の成分が 0 になるように回転し、固有ベクトルにも同じ回転をかける。
    def rotate(work, vectors, pair)
      p, q = pair
      # tan(2θ) = 2 a_pq / (a_pp - a_qq) を解いて、cos と sin を求める
      theta = 0.5 * Math.atan2(2.0 * work[p, q], work[p, p] - work[q, q])
      cos = Math.cos(theta)
      sin = Math.sin(theta)

      rotate_lines(work, [true, p], [true, q], cos, sin)
      rotate_lines(work, [p, true], [q, true], cos, sin)
      rotate_lines(vectors, [true, p], [true, q], cos, sin)
    end

    # 2 つの列（または 2 つの行）を回転する。回転の前の値を取り出してから書き戻す。
    def rotate_lines(matrix, index_p, index_q, cos, sin)
      line_p = matrix[*index_p].dup
      line_q = matrix[*index_q].dup
      matrix[*index_p] = (line_p * cos) + (line_q * sin)
      matrix[*index_q] = (line_q * cos) - (line_p * sin)
    end

    # 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。
    def normalize_signs(components)
      Numo::DFloat.cast(components.to_a.map { |row| row.max_by(&:abs).negative? ? row.map(&:-@) : row })
    end

    # 分散共分散行列を固有値分解し、寄与率の大きい順に n_components 個の主成分を求める。
    def fit(x, n_components)
      values, vectors = jacobi_eigen(covariance_matrix(x))
      order = descending_order(values, n_components)
      variances = values.values_at(*order)
      total = values.sum

      PcaModel.new(mean: x.mean(axis: 0), components: normalize_signs(vectors[true, order].transpose),
                   explained_variance: variances, explained_variance_ratio: variances.map { |value| value / total })
    end

    # 固有値の大きい順に、先頭から n_components 個の番号を返す。値が同じときは元の順を保つ。
    def descending_order(values, n_components)
      unless n_components.between?(1, values.size)
        raise ArgumentError, "主成分の数は 1 以上 #{values.size} 以下にしてください: #{n_components}"
      end

      values.each_index.sort_by { |index| [-values[index], index] }.first(n_components)
    end

    # 平均を引いてから、データを主成分の向きに射影する。
    def transform(model, x)
      (x - model.mean).dot(model.components.transpose)
    end

    # 累積寄与率がしきい値に届くまでの主成分の数。届かなければ、すべての主成分を使う。
    def components_needed(ratios, threshold)
      cumulative = 0.0
      index = ratios.index { |ratio| (cumulative += ratio) >= threshold }

      index.nil? ? ratios.size : index + 1
    end

    # 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。絶対値が同じなら列の順を保つ。
    def top_loadings(component, columns, count)
      columns.zip(component.to_a).each_with_index
             .sort_by { |(_, value), index| [-value.abs, index] }
             .first(count).map { |(column, value), _| Loading.new(column:, value:) }
    end
  end
end
