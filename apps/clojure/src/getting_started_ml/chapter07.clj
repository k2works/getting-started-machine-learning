(ns getting-started-ml.chapter07
  "第 7 章: 線形回帰による数値予測。正規方程式を自作してから Tribuo と突き合わせる。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.dataset :as dataset]))

(def feature-columns
  "映画のデータの特徴量の列。cinema_id は映画を区別する番号なので使わない。"
  [:SNS1 :SNS2 :actor :original])

(def target
  "映画のデータの正解ラベルの列。"
  :sales)

(def ^:private outlier-sns2
  "SNS2 がこの値を超え、かつ興行収入が outlier-sales 未満の映画を外れ値とする。"
  1000.0)

(def ^:private outlier-sales 8500.0)

;; モデルは {:intercept 切片 :coefficients [[列名 係数] ...]} で表す。
;; 係数をベクタの組で持つので列の順がそのまま残り、= で値として比べられる。

(defn model
  "列名と、同じ順に並んだ係数からモデルを作る。"
  [intercept columns coefficients]
  (when-not (= (count columns) (count coefficients))
    (throw (IllegalArgumentException.
            (str "列名と係数の数が違います: " (count columns) " と " (count coefficients)))))
  {:intercept intercept :coefficients (mapv vector columns coefficients)})

(defn coefficient
  "列名で係数を読む。無ければ失敗する。"
  [model column]
  (if-let [pair (first (filter #(= column (first %)) (:coefficients model)))]
    (second pair)
    (throw (IllegalArgumentException. (str "係数がありません: " (name column))))))

(defn predict-one
  "1 行分の特徴量の予測値。係数は列名で対応させるので、列の並び順は問わない。"
  [model features]
  (reduce (fn [sum [column value]] (+ sum (* value (get features column))))
          (:intercept model)
          (:coefficients model)))

(defn predict
  "行ごとの予測値。"
  [model x]
  (mapv #(predict-one model %) x))

(defn design-matrix
  "先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。"
  [x columns]
  (mapv (fn [features] (into [1.0] (map #(get features %)) columns)) x))

(defn fit
  "(Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。"
  [x t columns]
  (when (empty? x)
    (throw (IllegalArgumentException. "訓練データが空です")))
  (when-not (= (count x) (count t))
    (throw (IllegalArgumentException.
            (str "特徴量と実測値の件数が違います: " (count x) " と " (count t)))))
  (let [design (design-matrix x columns)
        transposed (matrix/transpose design)
        weights (matrix/column
                 (matrix/solve (matrix/multiply transposed design)
                               (matrix/multiply transposed (matrix/column-vector t)))
                 0)]
    (model (first weights) columns (vec (rest weights)))))

(defn- outlier?
  "外れ値の行かどうかを返す。"
  [row]
  (and (> (chapter02/number row :SNS2) outlier-sns2)
       (< (chapter02/number row target) outlier-sales)))

(defn remove-outliers
  "外れ値の行を除いた表を返す。"
  [table]
  (update table :rows #(vec (remove outlier? %))))

(defn prepare-cinema
  "cinema.csv を読み込み、外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。"
  [csv-file test-size seed]
  (let [table (remove-outliers (chapter02/load-table csv-file))
        t (mapv #(chapter02/number % target) (:rows table))
        split (chapter02/split-train-test (:rows table) t test-size seed)
        means (chapter02/column-means (:x-train split) feature-columns)]
    (assoc split
           :x-train (chapter02/fill-missing (:x-train split) feature-columns means)
           :x-test (chapter02/fill-missing (:x-test split) feature-columns means))))

(def ^:private test-size 0.2)
(def ^:private seed 0)

(defn run
  "映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。"
  []
  (let [csv-file (str (dataset/dir) "/cinema.csv")
        table (chapter02/load-table csv-file)
        split (prepare-cinema csv-file test-size seed)
        model (fit (:x-train split) (:t-train split) feature-columns)
        y (predict model (:x-test split))
        t (:t-test split)]
    (println (str "データ件数: " (count (:rows table))))
    (println (str "外れ値を除いた件数: " (count (:rows (remove-outliers table)))))
    (println (str "訓練データ: " (count (:x-train split)) " 件, "
                  "テストデータ: " (count (:x-test split)) " 件"))
    (println (format "切片: %.2f" (:intercept model)))
    (println (str "係数: "
                  (str/join ", " (map (fn [[column value]] (format "%s=%.4f" (name column) value))
                                      (:coefficients model)))))
    (println (format "テストデータの評価: R2=%.4f, MAE=%.2f, RMSE=%.2f"
                     (metrics/r2-score t y)
                     (metrics/mean-absolute-error t y)
                     (metrics/root-mean-squared-error t y)))))
