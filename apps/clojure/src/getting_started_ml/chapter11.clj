(ns getting-started-ml.chapter11
  "第 11 章: 評価指標と交差検証。混同行列・適合率・再現率・F 値と K 分割交差検証を自作し、
   Tribuo の評価器・KFoldSplitter と突き合わせる。"
  (:require [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.dataset :as dataset])
  (:import [com.oracle.labs.mlrg.olcut.provenance Provenance]
           [org.tribuo Example Model MutableDataset]
           [org.tribuo.classification Label LabelFactory]
           [org.tribuo.classification.dtree CARTClassificationTrainer]
           [org.tribuo.classification.dtree.impurity GiniIndex]
           [org.tribuo.datasource ListDataSource]
           [org.tribuo.impl ArrayExample]
           [org.tribuo.provenance SimpleDataSourceProvenance]))

;; 混同行列は {:tp .. :fp .. :fn .. :tn .. } のマップで表す。
;; 評価関数（メトリック）は「正解と予測を受け取って 1 つの数を返す関数」、
;; 分類器（トレーナー）は第 10 章と同じく「訓練データを受け取って予測する関数を返す関数」。
;; どちらもただの関数なので、インターフェースも型引数も要らない。

;; ## 件数の検査

(defn require-same-size
  "正解と予測の件数が同じでなければ失敗する。短いほうに合わせて黙って切り詰めない。"
  [actual predicted]
  (when-not (= (count actual) (count predicted))
    (throw (IllegalArgumentException.
            (str "正解と予測の件数が違います（正解 " (count actual) " 件、予測 " (count predicted) " 件）")))))

;; ## 混同行列

(defn confusion-matrix
  "正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。"
  [actual predicted positive]
  (require-same-size actual predicted)
  (merge {:tp 0 :fp 0 :fn 0 :tn 0}
         (frequencies (map (fn [a p]
                             (case [(= a positive) (= p positive)]
                               [true true] :tp
                               [false true] :fp
                               [true false] :fn
                               [false false] :tn))
                           actual predicted))))

;; ## 混同行列から求める指標

(defn- ratio
  "分母が 0 なら 0 を返す割り算。NaN にしない。"
  [numerator denominator]
  (if (zero? denominator) 0.0 (/ (double numerator) denominator)))

(defn precision
  "適合率。正例と予測したうち、本当に正例だった割合。"
  [{:keys [tp fp]}]
  (ratio tp (+ tp fp)))

(defn recall
  "再現率。本当の正例のうち、正例と予測できた割合。"
  [{tp :tp misses :fn}]
  (ratio tp (+ tp misses)))

(defn f1-score
  "F 値。適合率と再現率の調和平均。"
  [cm]
  (let [p (precision cm)
        r (recall cm)]
    (ratio (* 2 p r) (+ p r))))

(defn classification-metric
  "混同行列から求める指標を、正例を決めて、正解と予測から採点する評価関数に変える。"
  [score positive]
  (fn [actual predicted] (score (confusion-matrix actual predicted positive))))

;; ## 正解と予測から直接求める指標

(defn accuracy
  "正解率。正解と予測が一致した割合。"
  [actual predicted]
  (require-same-size actual predicted)
  (ratio (count (filter true? (map = actual predicted))) (count actual)))

(defn mean-squared-error
  "平均二乗誤差（MSE）。誤差の 2 乗の平均。"
  [actual predicted]
  (require-same-size actual predicted)
  (/ (reduce + (map (fn [a p] (let [error (- p a)] (* error error))) actual predicted))
     (count actual)))

(defn mean
  "平均。1 つも無ければ失敗する。"
  [values]
  (when (empty? values)
    (throw (IllegalArgumentException. "平均を求める値が 1 つもありません")))
  (/ (reduce + values) (count values)))

;; ## K 分割交差検証

(defn k-fold
  "シード付きの乱数で行の位置を並べ替え、n-splits 個のテストデータに分ける。
   件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。"
  [n-samples n-splits seed]
  (let [positions (chapter02/shuffle-with-seed (vec (range n-samples)) seed)
        sizes (mapv #(+ (quot n-samples n-splits) (if (< % (rem n-samples n-splits)) 1 0))
                    (range n-splits))
        starts (reductions + 0 sizes)]
    (mapv (fn [from size]
            (let [test (subvec positions from (+ from size))
                  test-set (set test)]
              {:train (vec (remove test-set positions)) :test test}))
          starts sizes)))

(defn pick
  "行の位置で値を選ぶ。"
  [values positions]
  (mapv #(nth values %) positions))

(defn cross-validate
  "分割ごとに分類器を訓練データで学習し、テストデータの予測を評価関数で採点する。
   遅延シーケンスを返すので、取り出した分だけ学習する。"
  [trainer x t folds metric]
  (map (fn [{:keys [train test]}]
         (let [predict (trainer (pick x train) (pick t train))]
           (metric (pick t test) (predict (pick x test)))))
       folds))

;; ## 分類器

(defn tree-trainer
  "第 3 章の決定木の分類器。"
  [columns max-depth]
  (fn [x t]
    (let [tree (chapter03/fit x t columns max-depth)]
      (fn [x] (chapter03/predict tree x)))))

(defn linear-trainer
  "第 7 章の線形回帰の分類器（回帰なので予測は数値）。"
  [columns]
  (fn [x t]
    (let [model (chapter07/fit x t columns)]
      (fn [x] (chapter07/predict model x)))))

;; ## Tribuo との突き合わせ

(def ^:private label-factory
  "Tribuo のラベルの作り方。"
  (LabelFactory.))

(defn- to-example
  "特徴量とラベルを Tribuo の事例にする。列名は文字列の配列で渡す。"
  ^Example [features columns ^Label label]
  (ArrayExample. label
                 ^"[Ljava.lang.String;" (into-array String (map name columns))
                 (double-array (map #(get features %) columns))))

(defn to-dataset
  "特徴量と正解ラベルを Tribuo のデータセットにする。評価器にもそのまま渡せる。"
  ^MutableDataset [x t columns]
  (let [examples (mapv (fn [features label] (to-example features columns (Label. label))) x t)
        provenance (SimpleDataSourceProvenance. "features" label-factory)]
    (MutableDataset. (ListDataSource. examples label-factory ^Provenance provenance))))

(defn tribuo-tree
  "Tribuo の CART（ジニ不純度）を学習する。深さは max-depth（nil なら上限なし）。"
  ^Model [x t columns max-depth]
  (.train (CARTClassificationTrainer. (int (or max-depth Integer/MAX_VALUE))
                                      (float 1.0) (float 0.0) (float 1.0) (GiniIndex.) 0)
          (to-dataset x t columns)))

(defn tribuo-predict
  "学習した Tribuo のモデルで、特徴量ごとのラベルを予測する。"
  [^Model model x columns]
  (mapv (fn [features]
          (.getLabel ^Label (.getOutput (.predict model (to-example features columns
                                                                    LabelFactory/UNKNOWN_LABEL)))))
        x))

(defn tribuo-tree-trainer
  "Tribuo の決定木を、自作の分類器と同じ形（学習して予測する関数を返す関数）に包む。"
  [columns max-depth]
  (fn [x t]
    (let [model (tribuo-tree x t columns max-depth)]
      (fn [x] (tribuo-predict model x columns)))))

;; ## 実データの前処理

(def survived-columns
  "Survived.csv の特徴量の列。"
  [:Pclass :Age :male])

(defn prepare-survived
  "客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
   この章では分割の前に全体の平均値で年齢の欠損値を補う、簡略化した前処理を使う。"
  [table]
  (let [age-mean (get (chapter02/column-means (:rows table) [:Age]) :Age)]
    {:x (mapv (fn [row] {:Pclass (chapter02/number row :Pclass)
                         :Age (or (chapter02/number row :Age) age-mean)
                         :male (if (= "male" (chapter02/text row :Sex)) 1.0 0.0)})
              (:rows table))
     :t (mapv #(chapter02/text % :Survived) (:rows table))}))

(defn prepare-cinema
  "第 7 章の 4 列を特徴量に、興行収入を正解にする。特徴量の欠損値は列ごとの平均値で補う。"
  [table]
  (let [means (chapter02/column-means (:rows table) chapter07/feature-columns)]
    {:x (chapter02/fill-missing (:rows table) chapter07/feature-columns means)
     :t (mapv #(chapter02/number % chapter07/target) (:rows table))}))

;; ## 実データでの実行

(def n-splits
  "分割の数。"
  5)

(def seed
  "分割の乱数のシード。"
  0)

(def ^:private tree-depth "決定木の深さ。" 2)
(def ^:private survived "正例にするラベル（生存）。" "1")

(def survived-metrics
  "Survived の評価指標。表示する順に並べる。"
  [["正解率" accuracy]
   ["適合率" (classification-metric precision survived)]
   ["再現率" (classification-metric recall survived)]
   ["F値" (classification-metric f1-score survived)]])

(def cinema-metrics
  "cinema の評価指標。第 7 章の RMSE・MAE をそのまま渡す。"
  [["RMSE" metrics/root-mean-squared-error]
   ["MAE" metrics/mean-absolute-error]])

(defn evaluate
  "同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。名前と平均の組を、指標の順に返す。"
  [trainer {:keys [x t]} metrics]
  (let [folds (k-fold (count x) n-splits seed)]
    (mapv (fn [[name* metric]] [name* (mean (cross-validate trainer x t folds metric))])
          metrics)))

(defn evaluate-survived
  "Survived.csv を深さ 2 の決定木で評価する。"
  [csv-file]
  (evaluate (tree-trainer survived-columns tree-depth)
            (prepare-survived (chapter02/load-table csv-file))
            survived-metrics))

(defn evaluate-cinema
  "cinema.csv を線形回帰で評価する。"
  [csv-file]
  (evaluate (linear-trainer chapter07/feature-columns)
            (prepare-cinema (chapter02/load-table csv-file))
            cinema-metrics))

(defn- print-scores
  "指標ごとの平均を、桁数をそろえて表示する。"
  [scores pattern]
  (doseq [[name* score] scores]
    (println (str "  " name* ": " (format pattern score)))))

(defn run
  "Survived と cinema を K 分割交差検証で評価し、指標ごとの平均を表示する。"
  []
  (let [dir (dataset/dir)]
    (println (str "Survived（決定木、" n-splits " 分割交差検証の平均）"))
    (print-scores (evaluate-survived (str dir "/Survived.csv")) "%.4f")
    (println (str "cinema（線形回帰、" n-splits " 分割交差検証の平均）"))
    (print-scores (evaluate-cinema (str dir "/cinema.csv")) "%.2f")))
