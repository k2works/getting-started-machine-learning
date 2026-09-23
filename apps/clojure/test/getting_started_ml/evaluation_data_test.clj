(ns getting-started-ml.evaluation-data-test
  "実データ（Survived.csv・cinema.csv）で交差検証するテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter11 :as ch]
            [getting-started-ml.dataset :as dataset])
  (:import [org.tribuo.classification Label]
           [org.tribuo.classification.evaluation LabelEvaluation LabelEvaluator]))

(defn- data-file
  "学習データのファイルを返す。無ければ理由を標準エラーに出して nil を返す。"
  [file-name]
  (let [path (str (dataset/dir) "/" file-name)]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println (str "学習データ " file-name " が配置されていない（gulp data:setup）のでスキップする"))
        nil))))

(defn- survived-data
  "前処理した Survived の特徴量と正解ラベルを返す。学習データが無ければ nil。"
  []
  (when-let [path (data-file "Survived.csv")]
    (ch/prepare-survived (chapter02/load-table path))))

(defn- mean-of
  "分割ごとの評価結果を、採点する関数で平均する。"
  [evaluations score]
  (ch/mean (mapv score evaluations)))

(deftest 実データを八百九十一件読み欠損値の年齢を平均値で補う
  (when-let [{:keys [x t]} (survived-data)]
    (is (= 891 (count x)))
    (is (= 891 (count t)))
    (is (every? (fn [features] (every? some? (vals features))) x))
    (is (= #{"0" "1"} (set t)))))

(deftest 同じ分割ならTribuoの評価器で採点したSurvivedの平均と一致する
  (when-let [{:keys [x t]} (survived-data)]
    (let [folds (ch/k-fold (count x) ch/n-splits ch/seed)
          positive (Label. "1")
          evaluations (mapv (fn [{:keys [train test]}]
                              (let [model (ch/tribuo-tree (ch/pick x train) (ch/pick t train)
                                                          ch/survived-columns 2)]
                                (.evaluate (LabelEvaluator.) model
                                           (ch/to-dataset (ch/pick x test) (ch/pick t test)
                                                          ch/survived-columns))))
                            folds)
          scores (into {} (ch/evaluate (ch/tribuo-tree-trainer ch/survived-columns 2)
                                       {:x x :t t} ch/survived-metrics))]
      (is (< (abs (- (mean-of evaluations #(.accuracy ^LabelEvaluation %)) (get scores "正解率")))
             1e-12))
      (is (< (abs (- (mean-of evaluations #(.precision ^LabelEvaluation % positive))
                     (get scores "適合率")))
             1e-12))
      (is (< (abs (- (mean-of evaluations #(.recall ^LabelEvaluation % positive))
                     (get scores "再現率")))
             1e-12))
      (is (< (abs (- (mean-of evaluations #(.f1 ^LabelEvaluation % positive)) (get scores "F値")))
             1e-12)))))

(deftest 実行すると交差検証の平均を表示する
  (when (and (data-file "Survived.csv") (data-file "cinema.csv"))
    (is (= ["Survived（決定木、5 分割交差検証の平均）"
            "  正解率: 0.7811"
            "  適合率: 0.7759"
            "  再現率: 0.6306"
            "  F値: 0.6833"
            "cinema（線形回帰、5 分割交差検証の平均）"
            "  RMSE: 405.77"
            "  MAE: 321.53"]
           (str/split-lines (with-out-str (ch/run)))))))
