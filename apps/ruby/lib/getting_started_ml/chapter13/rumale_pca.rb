# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter13
    # Rumale の PCA の主成分と、その主成分から自作の式で求めた寄与率。
    RumalePcaResult = Data.define(:components, :explained_variance_ratio)

    # Rumale の PCA を呼ぶ。自作の主成分分析と突き合わせるために使う。
    module RumalePca
      module_function

      # Rumale の PCA で主成分を求める。Rumale は寄与率を持たないので、主成分の向きの分散
      # （c^T Σ c）をすべての列の分散の合計（Σ の対角の和）で割って求める。
      # 乱数で初期値を選ぶので、シードは必ず渡す（省くと srand でプロセス全体の乱数の種が入れ替わる）。
      def fit(x, n_components, seed:, max_iter: 100, tol: 1e-4)
        model = Rumale::Decomposition::PCA.new(n_components:, max_iter:, tol:, random_seed: seed).fit(x)
        components = Chapter13.normalize_signs(as_rows(model.components))

        RumalePcaResult.new(components:, explained_variance_ratio: ratios(Chapter13.covariance_matrix(x), components))
      end

      # 主成分が 1 つだと 1 次元で返るので、1 行の行列にそろえる。
      def as_rows(components)
        components.ndim == 1 ? components.expand_dims(0) : components
      end

      # 主成分ごとの寄与率。
      def ratios(covariance, components)
        (components.dot(covariance) * components).sum(axis: 1).to_a.map { |value| value / covariance.trace }
      end
    end
  end
end
