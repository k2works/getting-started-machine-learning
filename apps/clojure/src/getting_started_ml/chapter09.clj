(ns getting-started-ml.chapter09
  "第 9 章: 特徴量エンジニアリング。ダミー変数・標準化・多項式特徴量・外れ値・表の結合。"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.dataset :as dataset])
  (:import [java.io InputStreamReader]
           [java.nio.charset Charset]
           [org.tribuo.math.la DenseMatrix DenseVector]
           [org.tribuo.transform TransformStatistics Transformer]
           [org.tribuo.transform.transformations MeanStdDevTransformation]))

(def target
  "ボストンの住宅価格のデータの正解の列。"
  :PRICE)

(def category
  "カテゴリ値の列。"
  :CRIME)

;; ## カテゴリ値をダミー変数にする

(defn categories
  "欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。"
  [values]
  (vec (rest (sort (distinct (remove str/blank? values))))))

(defn- dummy-column
  "ダミー変数の列名。"
  [column category-value]
  (keyword (str (name column) "_" category-value)))

(defn encode
  "列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば \"1\"、それ以外は \"0\"。"
  [table column categories]
  (let [dummy-columns (mapv #(dummy-column column %) categories)]
    {:columns (into (vec (remove #(= column %) (:columns table))) dummy-columns)
     :rows (mapv (fn [row]
                   (let [value (get row column)]
                     (into (dissoc row column)
                           (map (fn [c] [(dummy-column column c) (if (= c value) "1" "0")]))
                           categories)))
                 (:rows table))}))

;; ## 標準化

(defn standardizer
  "列ごとの平均と、件数で割る標準偏差を求める。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。"
  [x columns]
  (when (empty? x)
    (throw (IllegalArgumentException. "特徴量が 1 件もありません")))
  (let [stats (mapv (fn [column]
                      (let [values (mapv #(get % column) x)
                            mean (/ (reduce + values) (count values))
                            variance (/ (reduce + (map #(* (- % mean) (- % mean)) values))
                                        (count values))
                            std (Math/sqrt variance)]
                        [column mean (if (zero? std) 1.0 std)]))
                    columns)]
    {:columns (vec columns)
     :means (into {} (map (fn [[column mean _]] [column mean])) stats)
     :stds (into {} (map (fn [[column _ std]] [column std])) stats)}))

(defn standardize
  "1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。"
  [{:keys [means stds]} features]
  (into {} (map (fn [[column value]]
                  [column (if-let [mean (get means column)]
                            (/ (- value mean) (get stds column))
                            value)]))
        features))

(defn standardize-all
  "特徴量のリストを標準化する。"
  [std x]
  (mapv #(standardize std %) x))

(defn tribuo-standardize
  "Tribuo の MeanStdDevTransformation で、訓練データの値から平均と標準偏差を求めて別の値を標準化する。"
  [train values]
  (let [^TransformStatistics statistics (.createStats (MeanStdDevTransformation.))]
    (doseq [value train]
      (.observeValue statistics (double value)))
    (let [^Transformer transformer (.generateTransformer statistics)]
      (mapv #(.transform transformer (double %)) values))))

;; ## 多項式特徴量

(defn pairs-with-replacement
  "重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。"
  [columns]
  (let [columns (vec columns)]
    (vec (for [i (range (count columns))
               j (range i (count columns))]
           [(columns i) (columns j)]))))

(defn term-name
  "項の名前。scikit-learn の get_feature_names_out と同じ形（\"RM^2\"、\"RM LSTAT\"）にする。"
  [[left right]]
  (if (= left right)
    (keyword (str (name left) "^2"))
    (keyword (str (name left) " " (name right)))))

(defn expanded-columns
  "元の列の後ろに、2 乗の項と交互作用の項の列を並べた列名を返す。"
  [columns]
  (into (vec columns) (map term-name) (pairs-with-replacement columns)))

(defn expand
  "指定した列と、その 2 乗の項・交互作用の項だけを持つ特徴量にする。"
  [x columns]
  (let [pairs (pairs-with-replacement columns)]
    (mapv (fn [features]
            (into (into {} (map (fn [column] [column (get features column)])) columns)
                  (map (fn [[left right :as pair]]
                         [(term-name pair) (* (get features left) (get features right))]))
                  pairs))
          x)))

(defn select-columns
  "指定した列だけを選ぶ。"
  [x columns]
  (mapv #(select-keys % columns) x))

;; ## 外れ値

(def ^:private default-k
  "外れ値とみなす、四分位数から IQR の何倍離れているか。"
  1.5)

(defn quantile
  "分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。"
  [values q]
  (let [sorted (vec (sort values))
        position (* (dec (count sorted)) q)
        lower (long (Math/floor position))
        upper (long (Math/ceil position))]
    (+ (sorted lower) (* (- (sorted upper) (sorted lower)) (- position lower)))))

(defn iqr-outliers
  "第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。"
  ([values] (iqr-outliers values default-k))
  ([values k]
   (let [q1 (quantile values 0.25)
         q3 (quantile values 0.75)
         iqr (- q3 q1)]
     (mapv #(or (< % (- q1 (* k iqr))) (> % (+ q3 (* k iqr)))) values))))

(defn remove-target-outliers
  "訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。"
  [split]
  (let [kept (remove (fn [[_ _ outlier?]] outlier?)
                     (map vector (:x-train split) (:t-train split)
                          (iqr-outliers (:t-train split))))]
    (assoc split :x-train (mapv first kept) :t-train (mapv second kept))))

;; ## 区切り文字と文字コードを指定した読み込みと、表の結合

(def ^:private bom
  "UTF-8 の BOM。data.csv は取り除かないので、先頭の列名から自分で取り除く。"
  "﻿")

(defn load-delimited
  "文字コードと区切り文字を指定して読み込み、1 行目を列名にする。
   文字コードを間違えても例外にはならず、読めないバイトは置換文字になる。"
  [file charset separator]
  (with-open [reader (InputStreamReader. (io/input-stream file) (Charset/forName charset))]
    (let [[header & rows] (csv/read-csv reader :separator separator)
          columns (mapv #(keyword (str/replace-first % bom "")) header)]
      {:columns columns
       :rows (mapv #(zipmap columns %) (remove #(every? str/blank? %) rows))})))

(def ^:private join-key
  "結合に使う列。"
  :weather_id)

(defn join-weather
  "天気 ID をキーにしたマップを引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。"
  [bike weather]
  (let [by-id (into {} (map (fn [row] [(get row join-key) row])) (:rows weather))
        added (vec (remove #(= join-key %) (:columns weather)))]
    (when-not (= (count by-id) (count (:rows weather)))
      (throw (IllegalArgumentException. (str (name join-key) " が一意ではありません"))))
    {:columns (into (vec (:columns bike)) added)
     :rows (into [] (keep (fn [row]
                            (when-let [found (get by-id (get row join-key))]
                              (merge row (select-keys found added)))))
                 (:rows bike))}))

(defn mean-count-by-weather
  "天気ごとの平均利用者数を、多い順に並べて返す。"
  [joined]
  (->> (:rows joined)
       (group-by :weather)
       (mapv (fn [[weather rows]]
               [weather (/ (reduce + (map #(chapter02/number % :cnt) rows)) (count rows))]))
       (sort-by second >)
       vec))

;; ## 線形回帰と決定係数

(defn linear-fit
  "先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く（Tribuo のコレスキー分解）。"
  [rows t]
  (let [design (into-array (map (fn [row] (double-array (cons 1.0 row))) rows))
        ^DenseMatrix x (DenseMatrix/createDenseMatrix design)
        transposed (.transpose x)
        factorization (.choleskyFactorization (.matrixMultiply transposed x))]
    (when-not (.isPresent factorization)
      (throw (IllegalArgumentException. "特徴量の列が互いに独立でないため、正規方程式を解けません")))
    (let [target-vector (DenseVector/createDenseVector (double-array t))
          beta (vec (.toArray (.solve (.get factorization)
                                      (.leftMultiply transposed target-vector))))]
      {:intercept (first beta) :weights (vec (rest beta))})))

(defn linear-predict
  "行ごとに予測する。"
  [{:keys [intercept weights]} rows]
  (mapv (fn [row] (reduce + intercept (map * weights row))) rows))

(defn r-squared
  "決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。"
  [actual predicted]
  (let [mean (/ (reduce + actual) (count actual))
        residual (reduce + (map (fn [a p] (* (- a p) (- a p))) actual predicted))
        total (reduce + (map (fn [a] (* (- a mean) (- a mean))) actual))]
    (- 1.0 (/ residual total))))

;; ## ボストンの住宅価格

(defn prepare-boston
  "CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。"
  [csv-file test-size seed]
  (let [table (chapter02/load-table csv-file)
        crimes (mapv #(chapter02/text % category) (:rows table))
        encoded (encode table category (categories crimes))
        {:keys [columns rows labels]} (chapter02/split-features-and-target encoded target)
        prices (mapv #(Double/parseDouble %) labels)
        split (chapter02/split-train-test rows prices test-size seed)
        means (chapter02/column-means (:x-train split) columns)]
    (assoc split
           :columns columns
           :x-train (chapter02/fill-missing (:x-train split) columns means)
           :x-test (chapter02/fill-missing (:x-test split) columns means))))

(defn- to-rows
  "特徴量のマップを、列の順に並べた数値のベクタにする。"
  [x columns]
  (mapv (fn [features] (mapv #(get features %) columns)) x))

(defn score-feature-set
  "列から多項式特徴量を作って terms の項を選び、訓練データで標準化してから線形回帰で学習し、決定係数を求める。"
  [split columns terms]
  (let [train (select-columns (expand (:x-train split) columns) terms)
        test (select-columns (expand (:x-test split) columns) terms)
        std (standardizer train terms)
        x-train (to-rows (standardize-all std train) terms)
        x-test (to-rows (standardize-all std test) terms)
        model (linear-fit x-train (:t-train split))]
    {:train (r-squared (:t-train split) (linear-predict model x-train))
     :test (r-squared (:t-test split) (linear-predict model x-test))}))

;; ## 実データでの実行

(def columns-to-expand
  "多項式特徴量を作る元の列。"
  [:RM :LSTAT :PTRATIO])

(def squares
  "2 乗の項。"
  [(keyword "RM^2") (keyword "LSTAT^2") (keyword "PTRATIO^2")])

(defn feature-sets
  "特徴量の組の名前と、使う項。表示する順に並べる。"
  []
  [["元の特徴量" columns-to-expand]
   ["2 乗の項を追加" (into columns-to-expand squares)]
   ["交互作用の項も追加" (expanded-columns columns-to-expand)]])

(def ^:private zero-tolerance
  "浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。"
  1e-9)

(defn- format-number
  "小数の桁数をそろえて表示する。"
  [value digits]
  (format (str "%." digits "f") (if (< (abs value) zero-tolerance) 0.0 value)))

(defn- format-scores
  "訓練データとテストデータの決定係数を 1 行にする。"
  [{:keys [train test]}]
  (str "訓練 " (format-number train 4) ", テスト " (format-number test 4)))

(defn run
  "ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。"
  []
  (let [dir (dataset/dir)
        split (prepare-boston (str dir "/Boston.csv") 0.3 0)
        check (standardizer (standardize-all (standardizer (:x-train split) (:columns split))
                                             (:x-train split))
                            (:columns split))]
    (println (str "訓練データ: " (count (:x-train split)) " 件, "
                  "テストデータ: " (count (:x-test split)) " 件"))
    (println (str "特徴量の列: " (str/join ", " (map name (:columns split)))))
    (println (str "標準化した訓練データの RM: 平均 " (format-number (get-in check [:means :RM]) 2)
                  ", 標準偏差 " (format-number (get-in check [:stds :RM]) 2)))
    (println "決定係数:")
    (doseq [[name* terms] (feature-sets)]
      (println (str "  " name* "（" (count terms) " 列）: "
                    (format-scores (score-feature-set split columns-to-expand terms)))))
    (println (str "訓練データの PRICE の外れ値: "
                  (count (filter true? (iqr-outliers (:t-train split)))) " 件"))
    (println (str "  外れ値を除いて 2 乗の項を追加: "
                  (format-scores (score-feature-set (remove-target-outliers split)
                                                    columns-to-expand
                                                    (into columns-to-expand squares)))))
    (println (str "天気ごとの平均利用者数: "
                  (str/join ", " (map (fn [[weather mean]]
                                        (str weather "=" (format-number mean 1)))
                                      (mean-count-by-weather
                                       (join-weather (load-delimited (str dir "/bike.tsv")
                                                                     "UTF-8" \tab)
                                                     (load-delimited (str dir "/weather.csv")
                                                                     "Shift_JIS" \,)))))))))
