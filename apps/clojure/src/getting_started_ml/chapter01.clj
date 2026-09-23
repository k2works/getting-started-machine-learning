(ns getting-started-ml.chapter01
  "第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [getting-started-ml.dataset :as dataset]))

(def kinoko "きのこ派の呼び名。" "きのこ")
(def takenoko "たけのこ派の呼び名。" "たけのこ")

(def ^:private kinoko-age-group
  "「20 代ならきのこ派」というルールの年代。"
  20)

(def ^:private bom
  "UTF-8 の BOM。data.csv は取り除かないので、先頭の列名から自分で取り除く。"
  "﻿")

(def ^:private feature-keys
  "判定の手がかりになる列。"
  [:身長 :体重 :年代])

(defn- number
  "列名で数値を読む。整数として読めなければ例外を投げる。"
  [row column]
  (let [cell (get row column)]
    (try
      (Long/parseLong cell)
      (catch NumberFormatException _
        (throw (IllegalArgumentException. (str (name column) " を数値として読めません: " cell)))))))

(defn- to-person
  "1 行の値を人物にする。列が無ければ例外を投げる。"
  [row]
  (doseq [column (conj feature-keys :派閥)]
    (when-not (contains? row column)
      (throw (IllegalArgumentException. (str "列がありません: " (name column))))))
  {:身長 (number row :身長)
   :体重 (number row :体重)
   :年代 (number row :年代)
   :派閥 (get row :派閥)})

(defn load-people
  "CSV を読み込み、列名で値を取り出して人物のリストにする。"
  [csv-file]
  (with-open [reader (io/reader csv-file)]
    (let [[header & rows] (csv/read-csv reader)
          columns (map #(keyword (str/replace-first % bom "")) header)]
      (mapv #(to-person (zipmap columns %)) rows))))

(defn split-features-and-labels
  "人物のリストを特徴量と正解ラベルに分ける。"
  [people]
  [(mapv #(select-keys % feature-keys) people)
   (mapv :派閥 people)])

(defn predict-by-rule
  "人間が決めたルールで派閥を判定する。"
  [features]
  (if (= kinoko-age-group (:年代 features)) kinoko takenoko))

(defn accuracy
  "予測が正解ラベルと一致した割合を返す。件数が違えば例外を投げる。"
  [predictions labels]
  (when-not (= (count predictions) (count labels))
    (throw (IllegalArgumentException.
            (str "予測と正解ラベルの件数が違います: " (count predictions) " と " (count labels)))))
  (/ (double (count (filter true? (map = predictions labels)))) (count labels)))

(defn run
  "実データでルールによる判定の正解率を表示する。"
  []
  (let [people (load-people (str (dataset/dir) "/KvsT.csv"))
        [x t] (split-features-and-labels people)
        predictions (mapv predict-by-rule x)]
    (println (str "データ件数: " (count people)))
    (println (format "ルールによる判定の正解率: %.4f" (accuracy predictions t)))))
