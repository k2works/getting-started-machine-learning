(ns getting-started-ml.chapter07.tribuo
  "第 7 章: 特徴量を Tribuo の回帰の事例に変え、Tribuo のトレーナーで学習・予測する。"
  (:import [com.oracle.labs.mlrg.olcut.provenance Provenance]
           [org.tribuo Example Model MutableDataset Trainer]
           [org.tribuo.datasource ListDataSource]
           [org.tribuo.impl ArrayExample]
           [org.tribuo.provenance SimpleDataSourceProvenance]
           [org.tribuo.regression RegressionFactory Regressor]))

(def output-name
  "予測する数値の名前。"
  "sales")

(def ^:private regression-factory
  "Tribuo の回帰の出力の作り方。"
  (RegressionFactory.))

(defn- to-example
  "特徴量と出力を Tribuo の事例にする。列名は文字列の配列で渡す。"
  ^Example [features columns ^Regressor output]
  (ArrayExample. output
                 ^"[Ljava.lang.String;" (into-array String (map name columns))
                 (double-array (map #(get features %) columns))))

(defn to-dataset
  "特徴量と実測値を Tribuo のデータセットにする。"
  ^MutableDataset [x t columns]
  (let [examples (mapv (fn [features value]
                         (to-example features columns (Regressor. output-name (double value))))
                       x t)
        provenance (SimpleDataSourceProvenance. "features" regression-factory)]
    (MutableDataset. (ListDataSource. examples regression-factory ^Provenance provenance))))

(defn train
  "渡したトレーナーで学習する。"
  ^Model [^Trainer trainer x t columns]
  (.train trainer (to-dataset x t columns)))

(defn- predict-one
  "1 件の特徴量の数値を予測する。Tribuo の回帰は複数の数値を同時に予測できるので、配列の先頭を取る。"
  [^Model model features columns]
  (let [example (to-example features columns RegressionFactory/UNKNOWN_REGRESSOR)
        ^Regressor output (.getOutput (.predict model example))]
    (aget ^doubles (.getValues output) 0)))

(defn predict
  "学習したモデルで、特徴量ごとの数値を予測する。"
  [^Model model x columns]
  (mapv #(predict-one model % columns) x))
