(ns getting-started-ml.chapter13
  "第 13 章: 主成分分析による次元削減。

   第 7 章の行列（ベクタのベクタ）で中心化と分散共分散行列を求め、固有値分解だけを
   Tribuo の DenseMatrix に任せる。Tribuo 4.3.2 に主成分分析のモジュールは無いので、
   この自作が最終実装になる。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter09 :as chapter09]
            [getting-started-ml.dataset :as dataset])
  (:import [org.tribuo.math.la DenseMatrix]))

;; ## 中心化と分散共分散行列

(defn column-means
  "列ごとの平均を返す。"
  [m]
  (mapv (fn [j] (let [values (matrix/column m j)]
                  (/ (reduce + values) (count values))))
        (range (matrix/column-count (matrix/check m)))))

(defn center
  "各列から平均を引く（中心化）。"
  [m means]
  (mapv (fn [row] (mapv - row means)) m))

(defn covariance-matrix
  "列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。"
  [m]
  (let [c (center m (column-means m))
        n (matrix/row-count m)]
    (mapv (fn [row] (mapv #(/ % (dec n)) row))
          (matrix/multiply (matrix/transpose c) c))))

;; ## 固有値分解

(defn eigen-decomposition
  "Tribuo で対称行列を固有値分解し、固有値と固有ベクトルを大きい順に返す。
   対称でない行列は分解できないので失敗する。"
  [m]
  (let [^DenseMatrix dense (DenseMatrix/createDenseMatrix
                            (into-array (map double-array m)))
        decomposition (.eigenDecomposition dense)]
    (when-not (.isPresent decomposition)
      (throw (IllegalArgumentException. "固有値分解できません（対称行列ではありません）")))
    (let [eigen (.get decomposition)
          values (vec (.toArray (.eigenvalues eigen)))]
      {:values values
       :vectors (mapv #(vec (.toArray (.getEigenVector eigen %))) (range (count values)))})))

(defn normalize-signs
  "固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。"
  [components]
  (mapv (fn [row]
          ;; max-key は同値のとき後ろを返すので、絶対値が同じなら前を残すように > で畳む
          (let [largest (reduce (fn [a b] (if (> (abs b) (abs a)) b a)) row)
                sign (Math/signum (double largest))]
            (mapv #(* sign %) row)))
        components))

(defn fit
  "分散共分散行列を固有値分解し、寄与率の大きい順に n-components 個の主成分を求める。"
  [m n-components]
  (let [{:keys [values vectors]} (eigen-decomposition (covariance-matrix m))
        total (reduce + values)
        variances (vec (take n-components values))]
    {:mean (column-means m)
     :components (normalize-signs (vec (take n-components vectors)))
     :explained-variance variances
     :explained-variance-ratio (mapv #(/ % total) variances)}))

(defn transform
  "平均を引いてから、データを主成分の向きに射影する。"
  [{:keys [mean components]} m]
  (matrix/multiply (center m mean) (matrix/transpose components)))

;; ## 主成分の数と解釈

(defn components-needed
  "累積寄与率がしきい値に届くまでの主成分の数。届かなければすべての主成分の数。"
  [ratios threshold]
  (or (first (keep-indexed (fn [i cumulative] (when (>= cumulative threshold) (inc i)))
                           (reductions + ratios)))
      (count ratios)))

(defn top-loadings
  "主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。
   sort-by は安定なので、絶対値が同じなら元の列の順が残る。"
  [component columns k]
  (vec (take k (sort-by #(- (abs (:value %)))
                        (mapv (fn [column value] {:column column :value value})
                              columns component)))))

;; ## Boston を前処理する

(def category
  "カテゴリ値の列。"
  :CRIME)

(defn standardize-table
  "CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。"
  [table]
  (let [crimes (mapv #(chapter02/text % category) (:rows table))
        {:keys [columns rows]} (chapter09/encode table category (chapter09/categories crimes))
        filled (chapter02/fill-missing rows columns (chapter02/column-means rows columns))]
    {:columns columns
     :x (chapter09/standardize-all (chapter09/standardizer filled columns) filled)}))

(defn load-boston
  "CSV を読み込んで前処理する。"
  [csv-file]
  (standardize-table (chapter02/load-table csv-file)))

(defn to-matrix
  "特徴量のリストを、1 件を 1 行とする行列にする。
   マップは 9 要素以上で順序を保たないので、列の順はベクタで持ち回る。"
  [x columns]
  (mapv (fn [features] (mapv #(get features %) columns)) x))

;; ## 実データでの実行

(def ^:private threshold
  "何割のばらつきを説明できれば十分とみなすか。"
  0.8)

(def ^:private top-k
  "主成分ごとに表示する列の数。"
  3)

(def ^:private components-to-explain
  "意味を読む主成分の数。"
  2)

(defn run
  "ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。"
  []
  (let [{:keys [columns x]} (load-boston (str (dataset/dir) "/Boston.csv"))
        m (to-matrix x columns)
        {:keys [components explained-variance-ratio]} (fit m (count columns))
        needed (components-needed explained-variance-ratio threshold)
        cumulative (reduce + (take needed explained-variance-ratio))]
    (println (str "データ件数: " (matrix/row-count m) ", 列数: " (count columns)))
    (println (str "寄与率: "
                  (str/join ", " (map-indexed (fn [i ratio] (format "PC%d %.4f" (inc i) ratio))
                                              (take needed explained-variance-ratio)))))
    (println (str "累積寄与率が " threshold " に届く主成分の数: " needed
                  "（累積寄与率 " (format "%.4f" cumulative) "）"))
    (doseq [i (range components-to-explain)]
      (println (str "第 " (inc i) " 主成分で影響の大きい列: "
                    (str/join ", " (map (fn [{:keys [column value]}]
                                          (format "%s %.3f" (name column) value))
                                        (top-loadings (components i) columns top-k))))))))
