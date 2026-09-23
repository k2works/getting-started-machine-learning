(ns getting-started-ml.chapter07.metrics-test
  "回帰の評価指標（MAE・RMSE・R²）のテスト。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07.metrics :as metrics]))

(defn- near? [a b] (< (abs (- a b)) 1e-12))

(deftest MAEは誤差の絶対値の平均になる
  (is (near? 1.0 (metrics/mean-absolute-error [3.0 1.0 4.0] [2.0 2.0 5.0]))))

(deftest MAEは予測が大きく外れるほど大きくなる
  (is (< (metrics/mean-absolute-error [3.0 1.0] [2.0 2.0])
         (metrics/mean-absolute-error [3.0 1.0] [0.0 5.0]))))

(deftest 実測値と予測値の件数が違えば失敗する
  (is (thrown? IllegalArgumentException (metrics/mean-absolute-error [1.0] [1.0 2.0]))))

(deftest RMSEは誤差の二乗の平均の平方根になる
  ;; 誤差 3 と 4 → √((9 + 16) / 2) = √12.5
  (is (near? (Math/sqrt 12.5) (metrics/root-mean-squared-error [0.0 0.0] [3.0 -4.0]))))

(deftest R2は予測がすべて正解なら一になる
  (is (near? 1.0 (metrics/r2-score [1.0 2.0 3.0] [1.0 2.0 3.0]))))

(deftest R2は平均値を予測し続けるモデルなら零になる
  (is (near? 0.0 (metrics/r2-score [1.0 2.0 3.0] [2.0 2.0 2.0]))))
