(ns getting-started-ml.chapter07.metrics
  "第 7 章: 回帰の評価指標。t は実測値、y は予測値。第 11・12 章でも使う。")

(defn- residuals
  "実測値と予測値の差。件数が違えば失敗する。"
  [t y]
  (when-not (= (count t) (count y))
    (throw (IllegalArgumentException.
            (str "実測値と予測値の件数が違います: " (count t) " と " (count y)))))
  (mapv - t y))

(defn- sum-of-squares
  "値の 2 乗の合計。"
  [values]
  (reduce + (map #(* % %) values)))

(defn mean-absolute-error
  "平均絶対誤差（MAE）。誤差の絶対値の平均。"
  [t y]
  (let [r (residuals t y)]
    (/ (reduce + (map abs r)) (count r))))

(defn root-mean-squared-error
  "平均二乗誤差の平方根（RMSE）。"
  [t y]
  (let [r (residuals t y)]
    (Math/sqrt (/ (sum-of-squares r) (count r)))))

(defn r2-score
  "決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。"
  [t y]
  (let [residual (sum-of-squares (residuals t y))
        mean (/ (reduce + t) (count t))]
    (- 1.0 (/ residual (sum-of-squares (map #(- % mean) t))))))
