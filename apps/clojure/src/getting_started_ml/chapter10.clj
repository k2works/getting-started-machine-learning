(ns getting-started-ml.chapter10
  "第 10 章: ロジスティック回帰とアンサンブル学習。第 3 章の決定木を使ったランダムフォレストと、
   Tribuo のロジスティック回帰・ランダムフォレストとの突き合わせ。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter01 :as chapter01]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.dataset :as dataset])
  (:import [com.oracle.labs.mlrg.olcut.provenance Provenance]
           [java.util Random]
           [java.util.logging Level Logger]
           [org.tribuo Example Model MutableDataset Trainer]
           [org.tribuo.classification Label LabelFactory]
           [org.tribuo.classification.dtree CARTClassificationTrainer]
           [org.tribuo.classification.ensemble VotingCombiner]
           [org.tribuo.classification.sgd.linear LinearSGDTrainer LogisticRegressionTrainer]
           [org.tribuo.classification.sgd.objectives LogMulticlass]
           [org.tribuo.common.tree RandomForestTrainer]
           [org.tribuo.datasource ListDataSource]
           [org.tribuo.impl ArrayExample]
           [org.tribuo.math.optimisers AdaGrad]
           [org.tribuo.provenance SimpleDataSourceProvenance]))

;; 分類器は「訓練データを受け取り、予測する関数を返す関数」で表す。
;; Clojure には interface も trait も要らず、第 3 章の決定木も Tribuo のトレーナーも
;; 同じ形の関数に包むだけで、同じ score で評価できる。

;; ## ソフトマックス関数と交差エントロピー

(def ^:private epsilon
  "対数が -∞ にならないように足す、ごく小さい値。"
  1e-12)

(defn softmax
  "スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。"
  [z]
  (let [maximum (reduce max z)
        exps (mapv #(Math/exp (- % maximum)) z)
        total (reduce + exps)]
    (mapv #(/ % total) exps)))

(defn cross-entropy
  "交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。"
  [probabilities targets]
  (- (/ (reduce + (map (fn [p target] (Math/log (+ (nth p target) epsilon)))
                       probabilities targets))
        (count probabilities))))

;; ## ロジスティック回帰

(def ^:private default-learning-rate "学習率の既定値。" 1.0)
(def ^:private default-epochs "繰り返しの既定値。" 5000)

(defn- row-scores
  "1 行のスコア（品種ごと）を求める。weights は [特徴量][品種] を 1 本に並べた配列。"
  ^doubles [^doubles row ^doubles weights ^doubles bias]
  (let [classes (alength bias)
        out (aclone bias)]
    (dotimes [f (alength row)]
      (let [value (aget row f)]
        (dotimes [k classes]
          (aset out k (+ (aget out k) (* value (aget weights (+ (* f classes) k))))))))
    out))

(defn- gradient
  "1 つの重みの勾配。行ごとの特徴量と誤差の積の和。"
  ^double [^objects rows ^objects errors f k]
  (loop [i 0 sum 0.0]
    (if (< i (alength rows))
      (recur (inc i) (+ sum (* (aget ^doubles (aget rows i) f)
                               (aget ^doubles (aget errors i) k))))
      sum)))

(defn logistic-fit
  "バッチ勾配降下法で重みと切片を学習する。品種は名前の順に並べる。"
  ([x t columns] (logistic-fit x t columns default-learning-rate default-epochs))
  ([x t columns learning-rate epochs]
   (let [classes (vec (sort (distinct t)))
         k (count classes)
         nf (count columns)
         n (count x)
         rows (object-array (map (fn [features]
                                   (double-array (map #(get features %) columns)))
                                 x))
         targets (mapv #(.indexOf ^java.util.List classes %) t)
         weights (double-array (* nf k))
         bias (double-array k)
         losses (volatile! [])]
     (dotimes [_ epochs]
       (let [probabilities (object-array (map (fn [row] (double-array
                                                         (softmax (vec (row-scores row weights bias)))))
                                              rows))]
         (vswap! losses conj (cross-entropy (mapv vec probabilities) targets))
         ;; 確率 − 正解（正解の品種だけ 1 を引く）と、その誤差による更新
         (dotimes [i n]
           (let [^doubles p (aget probabilities i)
                 target (nth targets i)]
             (aset p target (- (aget p target) 1.0))))
         (dotimes [j k]
           (dotimes [f nf]
             (let [index (+ (* f k) j)]
               (aset weights index (- (aget weights index)
                                      (/ (* learning-rate (gradient rows probabilities f j)) n)))))
           (let [bias-gradient (reduce + (map (fn [i] (aget ^doubles (aget probabilities i) j))
                                              (range n)))]
             (aset bias j (- (aget bias j) (/ (* learning-rate bias-gradient) n)))))))
     {:columns (vec columns)
      :classes classes
      :weights (vec weights)
      :bias (vec bias)
      :losses @losses})))

(defn- argmax
  "いちばん大きい値の位置を返す。同じ値なら先に現れたほうを選ぶ。"
  [values]
  (reduce (fn [best i] (if (> (nth values i) (nth values best)) i best))
          0
          (range 1 (count values))))

(defn logistic-predict
  "スコアが最大の品種を予測する。"
  [{:keys [columns classes weights bias]} x]
  (let [weight-array (double-array weights)
        bias-array (double-array bias)]
    (mapv (fn [features]
            (nth classes (argmax (vec (row-scores (double-array (map #(get features %) columns))
                                                  weight-array bias-array)))))
          x)))

(defn logistic-trainer
  "ロジスティック回帰の分類器。学習して、予測する関数を返す。"
  ([] (logistic-trainer default-learning-rate default-epochs))
  ([learning-rate epochs]
   (fn [x t columns]
     (let [model (logistic-fit x t columns learning-rate epochs)]
       (fn [x] (logistic-predict model x))))))

;; ## ランダムフォレスト

(defn majority-vote
  "サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。"
  [votes]
  (mapv (fn [sample] (chapter03/majority (mapv #(nth % sample) votes)))
        (range (count (first votes)))))

(defn bootstrap-sample
  "0 から size - 1 までの行番号を、重複を許して size 個選ぶ。"
  [size ^Random random]
  (mapv (fn [_] (.nextInt random (int size))) (range size)))

(defn- shuffle-with-random
  "渡された乱数で Fisher-Yates のシャッフルをする。Java の Collections.shuffle と同じ手順。"
  [items ^Random random]
  (let [shuffled (object-array items)]
    (doseq [i (range (dec (count items)) 0 -1)]
      (let [j (.nextInt random (int (inc i)))
            tmp (aget shuffled i)]
        (aset shuffled i (aget shuffled j))
        (aset shuffled j tmp)))
    (vec shuffled)))

(defn- select-columns
  "特徴量から、指定した列だけを取り出す。"
  [x columns]
  (mapv #(select-keys % columns) x))

(defn forest-fit
  "ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を n-estimators 本学習する。"
  [x t columns n-estimators max-features max-depth seed]
  (let [random (Random. (long seed))]
    {:trees (mapv (fn [_]
                    (let [rows (bootstrap-sample (count x) random)
                          chosen (set (take max-features (shuffle-with-random columns random)))
                          ;; 列の順は元のまま残す
                          tree-columns (vec (filter chosen columns))
                          sample-x (mapv (fn [row] (select-keys (nth x row) tree-columns)) rows)
                          sample-t (mapv #(nth t %) rows)]
                      {:columns tree-columns
                       :rows rows
                       :tree (chapter03/fit sample-x sample-t tree-columns max-depth)}))
                  (range n-estimators))}))

(defn forest-predict
  "木ごとの予測を多数決でまとめる。"
  [forest x]
  (majority-vote (mapv (fn [{:keys [columns tree]}]
                         (chapter03/predict tree (select-columns x columns)))
                       (:trees forest))))

(defn forest-trainer
  "ランダムフォレストの分類器。max-depth が nil なら深さの上限なし。"
  [n-estimators max-features max-depth seed]
  (fn [x t columns]
    (let [forest (forest-fit x t columns n-estimators max-features max-depth seed)]
      (fn [x] (forest-predict forest x)))))

(defn tree-trainer
  "第 3 章の決定木の分類器。"
  [max-depth]
  (fn [x t columns]
    (let [tree (chapter03/fit x t columns max-depth)]
      (fn [x] (chapter03/predict tree x)))))

;; ## 特徴量の重要度

(defn- impurity-decreases
  "分割ごとに減った不純度（件数で重み付け）を、[列 減った量] の並びにする。"
  [tree x t]
  (if (chapter03/leaf? tree)
    []
    (let [{:keys [feature threshold impurity]} (:split tree)
          pairs (map vector x t)
          left (filter (fn [[features _]] (<= (get features feature) threshold)) pairs)
          right (remove (fn [[features _]] (<= (get features feature) threshold)) pairs)]
      (into [[feature (* (count t) (- (chapter03/gini t) impurity))]]
            (concat (impurity-decreases (:left tree) (mapv first left) (mapv second left))
                    (impurity-decreases (:right tree) (mapv first right) (mapv second right)))))))

(defn- normalize
  "合計が 1 になるようにそろえる。合計が 0 ならそのまま返す。"
  [totals]
  (let [total (reduce + (vals totals))]
    (if (zero? total)
      totals
      (into {} (map (fn [[feature value]] [feature (/ value total)])) totals))))

(defn tree-importances
  "決定木 1 本の重要度。合計が 1 になるようにする。"
  [tree x t columns]
  (normalize (reduce (fn [totals [feature amount]] (update totals feature + amount))
                     (zipmap columns (repeat 0.0))
                     (impurity-decreases tree x t))))

(defn forest-importances
  "木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。"
  [forest x t columns]
  (let [trees (:trees forest)]
    (normalize
     (reduce (fn [totals {tree-columns :columns :keys [rows tree]}]
               (let [sample-x (mapv (fn [row] (select-keys (nth x row) tree-columns)) rows)
                     sample-t (mapv #(nth t %) rows)]
                 (reduce (fn [acc [feature value]] (update acc feature + (/ value (count trees))))
                         totals
                         (tree-importances tree sample-x sample-t tree-columns))))
             (zipmap columns (repeat 0.0))
             trees))))

;; ## Tribuo のトレーナー

(def ^:private label-factory
  "Tribuo のラベルの作り方。"
  (LabelFactory.))

(def ^:private fraction-features-in-split
  "Tribuo のランダムフォレストが分割ごとに使う特徴量の割合。"
  0.5)

(defn- to-example
  "特徴量とラベルを Tribuo の事例にする。列名は文字列の配列で渡す。"
  ^Example [features columns ^Label label]
  (ArrayExample. label
                 ^"[Ljava.lang.String;" (into-array String (map name columns))
                 (double-array (map #(get features %) columns))))

(defn- to-dataset
  "特徴量と正解ラベルを Tribuo のデータセットにする。"
  [x t columns]
  (let [examples (mapv (fn [features label] (to-example features columns (Label. label))) x t)
        provenance (SimpleDataSourceProvenance. "features" label-factory)]
    (MutableDataset. (ListDataSource. examples label-factory ^Provenance provenance))))

(defn tribuo-trainer
  "Tribuo のトレーナーを、学習して予測する関数を返す分類器にする。"
  [^Trainer trainer]
  (fn [x t columns]
    (let [^Model model (.train trainer (to-dataset x t columns))]
      (fn [x]
        (mapv (fn [features]
                (.getLabel ^Label (.getOutput (.predict model (to-example features columns
                                                                          LabelFactory/UNKNOWN_LABEL)))))
              x)))))

(defn tribuo-logistic-regression
  "Tribuo のロジスティック回帰。エポック数を指定しなければ既定（5 エポック）のまま。"
  ([] (LogisticRegressionTrainer.))
  ([epochs]
   (LinearSGDTrainer. (LogMulticlass.) (AdaGrad. 1.0 0.1) (int epochs) Trainer/DEFAULT_SEED)))

(defn tribuo-random-forest
  "Tribuo のランダムフォレスト。分割ごとに半分の特徴量から分割の候補を選ぶ CART を多数決する。"
  [n-estimators max-depth seed]
  (RandomForestTrainer. (CARTClassificationTrainer. (int (or max-depth Integer/MAX_VALUE))
                                                    (float fraction-features-in-split)
                                                    (long seed))
                        (VotingCombiner.) (int n-estimators) (long seed)))

;; ## 実データでの実行

(defn score
  "分類器を訓練データで学習させてから、訓練データとテストデータの正解率を求める。"
  [trainer split columns]
  (let [predict (trainer (:x-train split) (:t-train split) columns)]
    {:train (chapter01/accuracy (predict (:x-train split)) (:t-train split))
     :test (chapter01/accuracy (predict (:x-test split)) (:t-test split))}))

(def ^:private n-estimators "森に作る木の数。" 100)
(def ^:private max-features "1 本の木が使う特徴量の数。" 2)
(def ^:private shallow-depth "浅い木の深さ。" 2)
(def ^:private seed "乱数のシード。" 0)

(defn models
  "名前と分類器。表示する順に並べる。"
  []
  [[(str "決定木（深さ " shallow-depth "）") (tree-trainer shallow-depth)]
   ["ロジスティック回帰" (logistic-trainer)]
   [(str "ランダムフォレスト（" n-estimators " 本）")
    (forest-trainer n-estimators max-features nil seed)]
   [(str "ランダムフォレスト（" n-estimators " 本・深さ " shallow-depth "）")
    (forest-trainer n-estimators max-features shallow-depth seed)]
   ["Tribuo ロジスティック回帰" (tribuo-trainer (tribuo-logistic-regression))]
   [(str "Tribuo ランダムフォレスト（" n-estimators " 本）")
    (tribuo-trainer (tribuo-random-forest n-estimators nil seed))]])

(defn- format-score
  "正解率を小数 4 桁の文字列にする。"
  [value]
  (format "%.4f" value))

(defn run
  "モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。"
  []
  ;; Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
  (.setLevel (Logger/getLogger "org.tribuo") Level/WARNING)
  (let [split (chapter02/prepare-iris (str (dataset/dir) "/iris.csv") 0.3 seed)
        columns (vec (keys (first (:x-train split))))]
    (println "モデル\t訓練データ\tテストデータ")
    (doseq [[label trainer] (models)]
      (let [{:keys [train test]} (score trainer split columns)]
        (println (str/join "\t" [label (format-score train) (format-score test)]))))
    (let [forest (forest-fit (:x-train split) (:t-train split) columns
                             n-estimators max-features nil seed)
          importances (forest-importances forest (:x-train split) (:t-train split) columns)]
      (println)
      (println (str "ランダムフォレスト（" n-estimators " 本）の特徴量の重要度:"))
      (doseq [column columns]
        (println (str (name column) "\t" (format-score (get importances column))))))))
