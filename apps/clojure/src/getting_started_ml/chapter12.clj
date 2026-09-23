(ns getting-started-ml.chapter12
  "第 12 章: 正則化とモデル選択。リッジ回帰を自作し、検証データで正則化の強さを選び、
   Tribuo の ElasticNetCDTrainer のラッソ回帰・リッジ回帰と突き合わせる。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.chapter07.tribuo :as tribuo]
            [getting-started-ml.dataset :as dataset])
  (:import [java.util.logging Level Logger]
           [org.tribuo.math.la SparseVector]
           [org.tribuo.regression.slm ElasticNetCDTrainer SparseLinearModel]))

;; 行列は第 7 章と同じ「ベクタのベクタ」。正則化したモデルは
;; {:coefficients [列の順に並んだ係数] :intercept 切片} のマップで表す。

;; ## 第 7 章の行列に足りない演算

(defn matrix-plus
  "同じ大きさの行列の和。"
  [a b]
  (when-not (and (= (matrix/row-count a) (matrix/row-count b))
                 (= (matrix/column-count a) (matrix/column-count b)))
    (throw (IllegalArgumentException.
            (str (matrix/row-count a) " 行 " (matrix/column-count a) " 列の行列と "
                 (matrix/row-count b) " 行 " (matrix/column-count b) " 列の行列は足せません"))))
  (mapv #(mapv + %1 %2) a b))

(defn matrix-scale
  "すべての成分を scalar 倍した行列。"
  [scalar m]
  (mapv (fn [row] (mapv #(* scalar %) row)) m))

(defn identity-matrix
  "size 行 size 列の単位行列。"
  [size]
  (mapv (fn [i] (mapv #(if (= i %) 1.0 0.0) (range size))) (range size)))

;; ## リッジ回帰

(defn- column-means
  "列ごとの平均。"
  [m]
  (mapv (fn [j] (/ (reduce + (matrix/column m j)) (matrix/row-count m)))
        (range (matrix/column-count m))))

(defn- center
  "列ごとに平均を引いた行列。"
  [m means]
  (mapv (fn [row] (mapv - row means)) m))

(defn ridge-fit
  "特徴量と正解から平均を引いてから (Xᵀ X + alpha I) w = Xᵀ t を解いて係数を求め、切片は平均値から求める。
   平均を引くのは、切片に罰則をかけないため。alpha が 0 なら最小二乗法と同じ解になる。"
  [x t alpha]
  (when-not (= (matrix/row-count x) (count t))
    (throw (IllegalArgumentException.
            (str "特徴量と正解の件数が違います: " (matrix/row-count x) " と " (count t)))))
  (let [x-means (column-means x)
        t-mean (/ (reduce + t) (count t))
        xc (center x x-means)
        tc (matrix/column-vector (mapv #(- % t-mean) t))
        xct (matrix/transpose xc)
        penalized (matrix-plus (matrix/multiply xct xc)
                               (matrix-scale alpha (identity-matrix (count x-means))))
        coefficients (matrix/column (matrix/solve penalized (matrix/multiply xct tc)) 0)]
    {:coefficients coefficients
     :intercept (- t-mean (reduce + (map * x-means coefficients)))}))

(defn predict
  "行ごとの予測値。行列の列数は係数の数と同じでなければならない。"
  [{:keys [coefficients intercept]} x]
  (when-not (= (matrix/column-count x) (count coefficients))
    (throw (IllegalArgumentException.
            (str "特徴量の列数 " (matrix/column-count x) " と係数の数 " (count coefficients) " が違います"))))
  (mapv (fn [row] (reduce + intercept (map * row coefficients))) x))

(defn coefficient-abs-sum
  "係数の絶対値の合計。正則化が強いほど小さくなる。"
  [{:keys [coefficients]}]
  (reduce + (map abs coefficients)))

;; ## 実験の記録とモデル選択

(defn run-ridge-experiments
  "alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。"
  [x-train t-train x-valid t-valid alphas]
  (mapv (fn [alpha]
          (let [model (ridge-fit x-train t-train alpha)]
            {:alpha alpha
             :train-score (metrics/r2-score t-train (predict model x-train))
             :validation-score (metrics/r2-score t-valid (predict model x-valid))
             :coefficient-abs-sum (coefficient-abs-sum model)}))
        alphas))

(defn best-experiment
  "検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。
   max-key は同点なら後ろを返すので、厳密な不等号で畳む。"
  [experiments]
  (when (empty? experiments)
    (throw (IllegalArgumentException. "実験結果が 1 件もありません")))
  (reduce (fn [best experiment]
            (if (> (:validation-score experiment) (:validation-score best)) experiment best))
          experiments))

(defn zero-coefficient-names
  "係数がちょうど 0 になった特徴量の名前を、列の順に返す。"
  [coefficients feature-names]
  (when-not (= (count coefficients) (count feature-names))
    (throw (IllegalArgumentException. "係数と特徴量名の数が違います")))
  (vec (keep (fn [[value name*]] (when (zero? value) name*))
             (map vector coefficients feature-names))))

;; ## 標準化して多項式特徴量にする

(defn scaler-fit
  "訓練データの列ごとの平均と、件数 n で割る標準偏差を求める。"
  [x columns]
  (let [stats (mapv (fn [column]
                      (let [values (mapv #(get % column) x)
                            mean (/ (reduce + values) (count values))]
                        [mean (Math/sqrt (/ (reduce + (map #(let [d (- % mean)] (* d d)) values))
                                            (count values)))]))
                    columns)]
    {:columns (vec columns) :means (mapv first stats) :stds (mapv second stats)}))

(defn- pairs
  "2 次の項を作る列の組（i <= j）。"
  [n]
  (vec (for [i (range n) j (range i n)] [i j])))

(defn feature-names
  "変換後の列名。元の列、2 乗の列（\"RM^2\"）、積の列（\"RM LSTAT\"）の順。"
  [{:keys [columns]}]
  (into (mapv name columns)
        (map (fn [[i j]]
               (if (= i j)
                 (str (name (columns i)) "^2")
                 (str (name (columns i)) " " (name (columns j))))))
        (pairs (count columns))))

(defn scaler-transform
  "標準化した値と、その 2 次の項を並べた行列にする。"
  [{:keys [columns means stds]} x]
  (let [ps (pairs (count columns))]
    (mapv (fn [features]
            (let [z (mapv (fn [column mean std] (/ (- (get features column) mean) std))
                          columns means stds)]
              (into z (map (fn [[i j]] (* (z i) (z j)))) ps)))
          x)))

;; ## 外れ値を除く

(def outlier-threshold
  "z スコアの絶対値がこの値を超える値を持つ行を外れ値とする。"
  3.0)

(defn remove-outliers
  "列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が
   threshold を超える値を 1 つでも持つ行を除く。"
  [table columns threshold]
  (let [stats (mapv (fn [column]
                      (let [values (mapv #(chapter02/number % column) (:rows table))
                            mean (/ (reduce + values) (count values))]
                        [column mean
                         (Math/sqrt (/ (reduce + (map #(let [d (- % mean)] (* d d)) values))
                                       (dec (count values))))]))
                    columns)]
    (update table :rows
            (fn [rows]
              (vec (remove (fn [row]
                             (some (fn [[column mean std]]
                                     (> (abs (/ (- (chapter02/number row column) mean) std))
                                        threshold))
                                   stats))
                           rows))))))

;; ## Tribuo の ElasticNetCDTrainer

(def ^:private min-l1-ratio
  "ElasticNetCDTrainer は l1Ratio が 0（純粋なリッジ回帰）を受け付けないので、その代わりに使う値。"
  1e-12)

(def ^:private tolerance 1e-10)
(def ^:private max-iterations 100000)

(defn- to-features
  "行列を、列名を付けた特徴量のマップにする。"
  [x tribuo-columns]
  (mapv (fn [row] (zipmap tribuo-columns row)) x))

(defn tribuo-columns
  "Tribuo に渡すときの列名。Tribuo は列名の順に特徴量を並べ替えるので、桁をそろえた名前にする。"
  [n]
  (mapv #(keyword (format "x%02d" %)) (range n)))

(defn fit-elastic-net
  "ElasticNetCDTrainer で学習し、係数と切片を取り出す。
   Tribuo は特徴量の平均を引いてから学習するので、切片は特徴量と正解の平均値から求める。"
  [x t alpha l1-ratio]
  (let [columns (tribuo-columns (matrix/column-count x))
        trainer (ElasticNetCDTrainer. (double alpha) (double l1-ratio) (double tolerance)
                                      (int max-iterations) false 0)
        ^SparseLinearModel model (tribuo/train trainer (to-features x columns) t columns)
        ^SparseVector weights (first (vals (.getWeights model)))
        coefficients (mapv (fn [column]
                             (.get weights (.getID (.get (.getFeatureIDMap model) (name column)))))
                           columns)
        x-means (column-means x)]
    {:coefficients coefficients
     :intercept (- (/ (reduce + t) (count t)) (reduce + (map * x-means coefficients)))}))

(defn fit-lasso
  "l1Ratio を 1 にしたラッソ回帰。"
  [x t alpha]
  (fit-elastic-net x t alpha 1.0))

(defn fit-ridge
  "自作のリッジ回帰と同じ alpha の尺度で、ElasticNetCDTrainer にリッジ回帰を学習させる。
   ElasticNetCDTrainer は誤差の 2 乗の合計を 2n で割った値に罰則を足すので、alpha を件数 n で割って渡す。"
  [x t alpha]
  (fit-elastic-net x t (/ alpha (count t)) min-l1-ratio))

;; ## ボストンの住宅価格

(def feature-columns
  "特徴量の列。"
  [:RM :PTRATIO :LSTAT])

(def target
  "正解の列。"
  :PRICE)

(defn prepare-boston
  "外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
   標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。"
  [csv-file test-size validation-size seed]
  (let [table (remove-outliers (chapter02/load-table csv-file)
                               (conj feature-columns target) outlier-threshold)
        x (mapv (fn [row] (into {} (map (fn [column] [column (chapter02/number row column)]))
                                feature-columns))
                (:rows table))
        t (mapv #(chapter02/number % target) (:rows table))
        outer (chapter02/split-train-test x t test-size seed)
        inner (chapter02/split-train-test (:x-train outer) (:t-train outer) validation-size seed)
        scaler (scaler-fit (:x-train inner) feature-columns)]
    {:x-train (scaler-transform scaler (:x-train inner)) :t-train (:t-train inner)
     :x-valid (scaler-transform scaler (:x-test inner)) :t-valid (:t-test inner)
     :x-test (scaler-transform scaler (:x-test outer)) :t-test (:t-test outer)
     :feature-names (feature-names scaler)
     :kept (count (:rows table))}))

;; ## 実データでの実行

(def ^:private test-size 0.3)
(def ^:private validation-size 0.3)
(def ^:private seed 0)
(def ^:private alphas [0.0 0.1 1.0 10.0 100.0])
(def ^:private lasso-alpha 0.5)

(defn run
  "正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を表示する。"
  []
  ;; Tribuo の座標降下法が収束のたびに出す INFO のログを表示しない
  (.setLevel (Logger/getLogger (.getName ElasticNetCDTrainer)) Level/WARNING)
  (let [csv-file (str (dataset/dir) "/Boston.csv")
        total (count (:rows (chapter02/load-table csv-file)))
        {:keys [x-train t-train x-valid t-valid x-test t-test kept] :as data}
        (prepare-boston csv-file test-size validation-size seed)
        experiments (run-ridge-experiments x-train t-train x-valid t-valid alphas)
        best (best-experiment experiments)
        linear (ridge-fit x-train t-train 0.0)
        ridge (ridge-fit x-train t-train (:alpha best))
        lasso (fit-lasso x-train t-train lasso-alpha)]
    (println (str "データ件数: " kept "（外れ値 " (- total kept) " 件を除外）"))
    (println (str "訓練データ: " (count t-train) " 件, 検証データ: " (count t-valid)
                  " 件, テストデータ: " (count t-test) " 件"))
    (println (str "特徴量: " (str/join ", " (:feature-names data))))
    (println "alpha  訓練 R²  検証 R²  係数の絶対値の合計")
    (doseq [{:keys [alpha train-score validation-score coefficient-abs-sum]} experiments]
      (println (format "%5.1f  %.4f  %.4f  %.3f"
                       alpha train-score validation-score coefficient-abs-sum)))
    (println (str "検証データで選んだ alpha: " (:alpha best)))
    (println (format "テストデータの決定係数: 線形回帰 %.4f, リッジ回帰 %.4f"
                     (metrics/r2-score t-test (predict linear x-test))
                     (metrics/r2-score t-test (predict ridge x-test))))
    (println (str "ラッソ回帰（alpha=" lasso-alpha "）で係数が 0 になった特徴量: "
                  (str/join ", " (zero-coefficient-names (:coefficients lasso)
                                                         (:feature-names data)))))))
