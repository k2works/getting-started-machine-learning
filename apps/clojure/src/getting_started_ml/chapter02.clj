(ns getting-started-ml.chapter02
  "第 2 章: データの前処理。表の読み込み・欠損値の補完・訓練データとテストデータへの分割。"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [getting-started-ml.dataset :as dataset])
  (:import [java.util Random]))

(def target
  "アヤメのデータの正解ラベルの列。"
  :種類)

(def ^:private bom
  "UTF-8 の BOM。data.csv は取り除かないので、先頭の列名から自分で取り除く。"
  "﻿")

(defn text
  "文字列の列を読む。列が無ければ失敗する。"
  [row column]
  (if (contains? row column)
    (get row column)
    (throw (IllegalArgumentException. (str "列がありません: " (name column))))))

(defn missing?
  "セルが空欄かどうかを返す。"
  [row column]
  (str/blank? (text row column)))

(defn number
  "数値の列を読む。空欄なら nil を返す。数値として読めなければ失敗する。
   Clojure には Option が無いので、欠損値は nil で表す。"
  [row column]
  (let [cell (str/trim (text row column))]
    (when-not (str/blank? cell)
      (try
        (Double/parseDouble cell)
        (catch NumberFormatException _
          (throw (IllegalArgumentException.
                  (str (name column) " を数値として読めません: " cell))))))))

(defn load-table
  "CSV を読み込んで表にする。列の順は CSV の順のまま。"
  [csv-file]
  (with-open [reader (io/reader csv-file)]
    (let [[header & rows] (csv/read-csv reader)
          columns (mapv #(keyword (str/replace-first % bom "")) header)]
      {:columns columns
       :rows (mapv #(zipmap columns %) rows)})))

(defn count-missing
  "列ごとに欠損値の数を数える。列の順は表の列の順のまま。"
  [{:keys [columns rows]}]
  (mapv (fn [column] [column (count (filter #(missing? % column) rows))]) columns))

(defn column-means
  "欠損値を除いて、列ごとの平均値を求める。"
  [rows columns]
  (into {} (map (fn [column]
                  (let [values (keep #(number % column) rows)]
                    (when (empty? values)
                      (throw (IllegalArgumentException. (str "値がすべて空欄です: " (name column)))))
                    [column (/ (reduce + values) (count values))])))
        columns))

(defn- fill-value
  "補完する値を返す。無ければ失敗する。
   get の既定値は先に評価されるので、例外は when-not で投げる。"
  [fill-values column]
  (when-not (contains? fill-values column)
    (throw (IllegalArgumentException. (str "補完する値がありません: " (name column)))))
  (get fill-values column))

(defn fill-missing
  "欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。"
  [rows columns fill-values]
  (mapv (fn [row]
          (into {} (map (fn [column]
                          [column (or (number row column) (fill-value fill-values column))]))
                columns))
        rows))

(defn split-features-and-target
  "正解ラベルの列を取り出し、残りの列を特徴量の列にする。"
  [{:keys [columns rows]} target-column]
  {:columns (vec (remove #(= target-column %) columns))
   :rows rows
   :labels (mapv #(text % target-column) rows)})

(defn shuffle-with-seed
  "シードを使って Fisher-Yates のシャッフルで並べ替える。元のベクタは変えない。
   java.util.Random を使うので、Java 版・Scala 版と同じ並びになる。"
  [items seed]
  (let [random (Random. seed)
        shuffled (object-array items)]
    (doseq [i (range (dec (count items)) 0 -1)]
      (let [j (.nextInt random (inc i))
            tmp (aget shuffled i)]
        (aset shuffled i (aget shuffled j))
        (aset shuffled j tmp)))
    (vec shuffled)))

(defn split-train-test
  "並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。"
  [x t test-size seed]
  (when-not (= (count x) (count t))
    (throw (IllegalArgumentException. (str "件数が違います: " (count x) " と " (count t)))))
  (let [shuffled (shuffle-with-seed (mapv vector x t) seed)
        train-count (- (count shuffled) (long (Math/ceil (* (count shuffled) test-size))))
        [train test] (split-at train-count shuffled)]
    {:x-train (mapv first train) :x-test (mapv first test)
     :t-train (mapv second train) :t-test (mapv second test)}))

(defn prepare-iris
  "iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。"
  [csv-file test-size seed]
  (let [{:keys [columns rows labels]} (split-features-and-target (load-table csv-file) target)
        split (split-train-test rows labels test-size seed)
        means (column-means (:x-train split) columns)]
    (assoc split
           :x-train (fill-missing (:x-train split) columns means)
           :x-test (fill-missing (:x-test split) columns means))))

(defn run
  "アヤメのデータの前処理の結果を表示する。"
  []
  (let [csv-file (str (dataset/dir) "/iris.csv")
        table (load-table csv-file)
        split (prepare-iris csv-file 0.3 0)]
    (println (str "データ件数: " (count (:rows table))))
    (println (str "欠損値の数: "
                  (str/join ", " (map (fn [[column n]] (str (name column) "=" n))
                                      (count-missing table)))))
    (println (str "訓練データ: " (count (:x-train split)) " 件, "
                  "テストデータ: " (count (:x-test split)) " 件"))
    (println (str "特徴量: " (str/join ", " (map name (keys (first (:x-train split)))))))))
