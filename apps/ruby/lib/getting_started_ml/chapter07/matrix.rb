# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 7 章の連立方程式。行列そのものは Numo の DFloat を使い、解く手続きだけを自作する。
  module Chapter07
    # ピボットがこれより小さければ、解が 1 つに定まらないとみなす。
    SINGULAR = 1e-12

    module_function

    # 部分ピボット選択つきのガウス・ジョルダンの掃き出し法で matrix w = rhs を解く。元の行列と右辺は変えない。
    # numo-narray-alt には連立方程式を解くメソッドが無い（numo-linalg-alt は LAPACK を要求する）ので自作する。
    def solve(matrix, rhs)
      size = matrix.shape[0]
      check_size(size, matrix.shape[1])
      check_size(size, rhs.size)

      # 係数行列と右辺を並べた拡大係数行列にしてから掃き出す。hstack は新しい行列を作る
      work = Numo::DFloat.hstack([matrix, rhs.reshape(size, 1)])
      size.times { |pivot| eliminate(work, pivot) }
      work[true, size].dup
    end

    # 件数が違えば ArgumentError を投げる。
    def check_size(expected, actual)
      raise ArgumentError, "件数が違います: #{expected} と #{actual}" unless expected == actual
    end

    # ピボットの行を選んで入れ替え、ほかの行のピボット列を 0 にする。
    def eliminate(work, pivot)
      row = pivot_row(work, pivot)
      raise ArgumentError, "解けない連立方程式です" if work[row, pivot].abs < SINGULAR

      swap_rows(work, pivot, row)
      sweep(work, pivot)
    end

    # 2 つの行を入れ替える。添字の配列で 2 行をまとめて取り出し、逆の順で書き戻す。
    # 右辺はビューなので、dup で写してから書き戻さないと、読みながら書き換えてしまう。
    def swap_rows(work, left, right)
      work[[left, right], true] = work[[right, left], true].dup
    end

    # ピボットの行を 1 に正規化してから、ほかの行のピボット列を 0 にする。
    def sweep(work, pivot)
      work[pivot, true] /= work[pivot, pivot]
      pivot_line = work[pivot, true].dup

      work.shape[0].times do |index|
        work[index, true] -= work[index, pivot] * pivot_line unless index == pivot
      end
    end

    # ピボットの列で絶対値が最大の行を、対角より下から選ぶ。
    def pivot_row(work, pivot)
      pivot + work[pivot..-1, pivot].abs.max_index
    end
  end
end
