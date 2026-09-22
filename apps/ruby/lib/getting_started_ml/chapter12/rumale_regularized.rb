# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  # 第 12 章のリッジ回帰・ラッソ回帰を、Rumale の Ridge・Lasso で同じようにして突き合わせる。
  #
  # Rumale の 2 つの目的関数は、自作とも、互いどうしとも流儀が違う（Rumale 2.2 のソースと実測で確かめた）。
  # - Ridge（Numo::Linalg が無いときの L-BFGS）: ‖t - Xw - b‖² / n + reg_param (‖w‖² + b²)
  # - Lasso（座標降下法）: ½‖t - Xw - b‖² + reg_param (‖w‖₁ + |b|)
  # どちらも切片 b を「1 の列の係数」として学習するので、切片にも罰則がかかる。
  module Chapter12
    # Rumale の収束の判定。既定（1e-4）では自作と桁がそろわない。
    RUMALE_TOLERANCE = 1e-10

    module_function

    # 自作の alpha を Rumale の Ridge の reg_param に直す。Rumale は誤差の 2 乗の和を件数で割るので、
    # 両辺に n を掛けると ‖t - Xw‖² + n reg_param ‖w‖²。自作の alpha = n reg_param になる。
    def rumale_ridge_reg_param(alpha, n_samples)
      alpha.fdiv(n_samples)
    end

    # Rumale のリッジ回帰。特徴量と正解の平均をこちらで引き、切片を学習させずに（fit_bias: false）渡す。
    def rumale_ridge(columns, rows, t, alpha)
      fit_centered(ridge_estimator(alpha, t.size, fit_bias: false), columns, rows, t)
    end

    # Rumale のラッソ回帰。目的関数の尺度は自作と同じなので、alpha をそのまま渡す。
    def rumale_lasso(columns, rows, t, alpha)
      fit_centered(lasso_estimator(alpha, fit_bias: false), columns, rows, t)
    end

    # Rumale のリッジ回帰を、中心化せずに切片ごと学習させる（Rumale の既定の使い方）。
    def rumale_ridge_raw(columns, rows, t, alpha)
      fit_raw(ridge_estimator(alpha, t.size, fit_bias: true), columns, rows, t)
    end

    # Rumale のラッソ回帰を、中心化せずに切片ごと学習させる（Rumale の既定の使い方）。
    def rumale_lasso_raw(columns, rows, t, alpha)
      fit_raw(lasso_estimator(alpha, fit_bias: true), columns, rows, t)
    end

    # Rumale の Ridge。Numo::Linalg が無いので L-BFGS で解く（solver: "svd" を渡しても黙って L-BFGS になる）。
    def ridge_estimator(alpha, n_samples, fit_bias:)
      Rumale::LinearModel::Ridge.new(reg_param: rumale_ridge_reg_param(alpha, n_samples), fit_bias:,
                                     max_iter: MAX_ITERATIONS, tol: RUMALE_TOLERANCE)
    end

    # Rumale の Lasso。繰り返しの上限と収束の判定は自作と同じにする。
    def lasso_estimator(alpha, fit_bias:)
      Rumale::LinearModel::Lasso.new(reg_param: alpha, fit_bias:, max_iter: MAX_ITERATIONS, tol: RUMALE_TOLERANCE)
    end

    # 特徴量と正解をそのまま渡して学習させ、Rumale の係数と切片のモデルにする。
    def fit_raw(estimator, columns, rows, t)
      estimator.fit(Numo::DFloat.cast(rows), Numo::DFloat.cast(t))

      RegularizedModel.new(columns:, coefficients: estimator.weight_vec.to_a, intercept: estimator.bias_term)
    end

    # 平均を引いてから学習させ、係数と、平均から組み立て直した切片のモデルにする。
    def fit_centered(estimator, columns, rows, t)
      x, residuals, x_means, t_mean = center(rows, t)
      coefficients = estimator.fit(x, residuals).weight_vec.to_a

      RegularizedModel.new(columns:, coefficients:, intercept: intercept_from(coefficients, x_means, t_mean))
    end
  end
end
