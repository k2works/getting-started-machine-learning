(ns getting-started-ml.chapter03
  "第 3 章: 決定木による分類。自作の決定木と Tribuo の CART を突き合わせる。"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter01 :as chapter01]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.dataset :as dataset])
  (:import [com.oracle.labs.mlrg.olcut.provenance Provenance]
           [org.tribuo Example MutableDataset]
           [org.tribuo.classification Label LabelFactory]
           [org.tribuo.classification.dtree CARTClassificationTrainer]
           [org.tribuo.classification.dtree.impurity GiniIndex]
           [org.tribuo.datasource ListDataSource]
           [org.tribuo.impl ArrayExample]
           [org.tribuo.provenance SimpleDataSourceProvenance]))

;; 木は葉か節のどちらかで、どちらもマップで表す。
;; 葉は {:label "setosa"}、節は {:split ... :left ... :right ...}。
;; Clojure には判別共用体が無いので、鍵があるかどうかで見分ける（網羅性は検査されない）。

(defn leaf? "葉かどうかを返す。" [tree] (contains? tree :label))
(defn node? "節かどうかを返す。" [tree] (contains? tree :split))

(defn gini
  "ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。"
  [labels]
  (if (empty? labels)
    0.0
    (- 1.0 (reduce + (map (fn [[_ n]] (let [share (/ (double n) (count labels))] (* share share)))
                          (frequencies labels))))))

(defn majority
  "いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
   frequencies は順を保たないので、最初に現れた順（distinct）でたどり、厳密な不等号で比べる。"
  [labels]
  (let [counts (frequencies labels)]
    (reduce (fn [best label] (if (> (counts label) (counts best)) label best))
            (distinct labels))))

(defn- weighted-gini
  "左右の不純度の重み付き平均。"
  [left right]
  (/ (+ (* (count left) (gini left)) (* (count right) (gini right)))
     (+ (count left) (count right))))

(defn- candidates
  "1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
   sort-by は安定なので、同じ値の並びは元の順のまま。"
  [x t feature]
  (let [sorted (sort-by first (map (fn [features label] [(get features feature) label]) x t))
        values (mapv first sorted)
        labels (mapv second sorted)]
    (keep (fn [i]
            (when-not (== (values (dec i)) (values i))
              {:feature feature
               :threshold (/ (+ (values (dec i)) (values i)) 2.0)
               :impurity (weighted-gini (subvec labels 0 i) (subvec labels i))}))
          (range 1 (count sorted)))))

(defn best-split
  "左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ nil。
   同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。"
  [x t columns]
  (when-not (or (empty? x) (zero? (gini t)))
    (let [all (mapcat #(candidates x t %) columns)]
      (when (seq all)
        (reduce (fn [best candidate]
                  (if (< (:impurity candidate) (:impurity best)) candidate best))
                all)))))

(defn- goes-left?
  "分割の境界以下なら左へ進む。"
  [split features]
  (<= (get features (:feature split)) (:threshold split)))

(defn fit
  "深さの上限まで分割を繰り返して木を作る。max-depth が nil なら上限なし。"
  [x t columns max-depth]
  (let [split (when-not (and max-depth (zero? max-depth)) (best-split x t columns))]
    (if (nil? split)
      {:label (majority t)}
      (let [pairs (map vector x t)
            [left right] [(filter #(goes-left? split (first %)) pairs)
                          (remove #(goes-left? split (first %)) pairs)]
            next-depth (when max-depth (dec max-depth))]
        {:split split
         :left (fit (mapv first left) (mapv second left) columns next-depth)
         :right (fit (mapv first right) (mapv second right) columns next-depth)}))))

(defn predict-one
  "木をたどって 1 件のラベルを予測する。"
  [tree features]
  (if (leaf? tree)
    (:label tree)
    (recur (if (goes-left? (:split tree) features) (:left tree) (:right tree)) features)))

(defn predict
  "特徴量ごとのラベルを予測する。"
  [tree x]
  (mapv #(predict-one tree %) x))

(defn format-tree
  "木を字下げ付きの文字列にする。"
  ([tree] (format-tree tree ""))
  ([tree indent]
   (if (leaf? tree)
     (str indent (:label tree) "\n")
     (let [{:keys [feature threshold]} (:split tree)
           border (format "%.4f" threshold)]
       (str indent (name feature) " <= " border "\n" (format-tree (:left tree) (str indent "  "))
            indent (name feature) " > " border "\n" (format-tree (:right tree) (str indent "  ")))))))

(def ^:private label-factory
  "Tribuo のラベルの作り方。"
  (LabelFactory.))

(def ^:private min-child-weight
  "子の節に必要な事例の重みの最小値。1 にすると、自作の木と同じく 1 件になるまで分けられる。"
  1.0)

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

(defn tribuo-predict
  "Tribuo の CART（ジニ不純度）で学習して予測する。深さは max-depth（nil なら上限なし）。"
  [x-train t-train x-test columns max-depth]
  (let [trainer (CARTClassificationTrainer. (int (or max-depth Integer/MAX_VALUE))
                                            (float min-child-weight) (float 0.0) (float 1.0)
                                            (GiniIndex.) 0)
        model (.train trainer (to-dataset x-train t-train columns))]
    (mapv (fn [features]
            (.getLabel (.getOutput (.predict model (to-example features columns LabelFactory/UNKNOWN_LABEL)))))
          x-test)))

(def ^:private max-depths
  "正解率を比べる深さ。nil は制限なし。"
  [1 2 3 4 5 nil])

(defn- score
  "正解率を小数 4 桁の文字列にする。"
  [predictions labels]
  (format "%.4f" (chapter01/accuracy predictions labels)))

(defn- accuracy-row
  "自作と Tribuo で学習し、正解率を 1 行にする。"
  [max-depth split columns]
  (let [tree (fit (:x-train split) (:t-train split) columns max-depth)
        tribuo (tribuo-predict (:x-train split) (:t-train split) (:x-test split) columns max-depth)]
    (str/join "\t" [(or max-depth "制限なし")
                    (score (predict tree (:x-train split)) (:t-train split))
                    (score (predict tree (:x-test split)) (:t-test split))
                    (score tribuo (:t-test split))])))

(defn run
  "深さごとの正解率と、深さ 2 の決定木を表示する。"
  []
  (let [split (chapter02/prepare-iris (str (dataset/dir) "/iris.csv") 0.3 0)
        columns (vec (keys (first (:x-train split))))]
    (println "深さ\t訓練データ\tテストデータ\tTribuo")
    (doseq [max-depth max-depths]
      (println (accuracy-row max-depth split columns)))
    (println)
    (println "深さ 2 の決定木:")
    (print (format-tree (fit (:x-train split) (:t-train split) columns 2)))))
