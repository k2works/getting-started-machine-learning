(ns getting-started-ml.kvst-data-test
  "実データ（KvsT.csv）を使うテスト。学習データが無ければスキップする。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter01 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- csv-file
  "学習データのファイルを返す。無ければ nil（テストはスキップする）。
   clojure.test にはスキップが無いので、理由を標準エラーに出して早く戻る。"
  []
  (let [path (str (dataset/dir) "/KvsT.csv")]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println "学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest 実データを読み込んで正解率を求める
  (when-let [path (csv-file)]
    (let [people (ch/load-people path)
          [x t] (ch/split-features-and-labels people)
          predictions (mapv ch/predict-by-rule x)]
      (is (= 19 (count people)))
      (is (< (abs (- (ch/accuracy predictions t) (/ 14.0 19))) 1e-12)))))
