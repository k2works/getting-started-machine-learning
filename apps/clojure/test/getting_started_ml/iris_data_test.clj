(ns getting-started-ml.iris-data-test
  "実データ（iris.csv）を使うテスト。学習データが無ければスキップする。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter02 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- csv-file
  "学習データのファイルを返す。無ければ nil（テストはスキップする）。"
  []
  (let [path (str (dataset/dir) "/iris.csv")]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest 実データの列ごとの欠損値の数を数える
  (when-let [path (csv-file)]
    (let [table (ch/load-table path)]
      (is (= 150 (count (:rows table))))
      (is (= [[:がく片長さ 2] [:がく片幅 1] [:花弁長さ 2] [:花弁幅 2] [:種類 0]]
             (ch/count-missing table))))))

(deftest 実データを百五件と四十五件に分けて欠損値を補完する
  (when-let [path (csv-file)]
    (let [split (ch/prepare-iris path 0.3 0)]
      (is (= [105 45 105 45] [(count (:x-train split)) (count (:x-test split))
                              (count (:t-train split)) (count (:t-test split))]))
      (is (every? (fn [features] (every? some? (vals features))) (:x-train split))))))

(deftest 訓練データの平均値はJava版Scala版と一致する
  ;; java.util.Random を使うので、分かれる行も平均値も Java 版・Scala 版と同じになる
  (when-let [path (csv-file)]
    (let [{:keys [columns rows labels]} (ch/split-features-and-target (ch/load-table path) ch/target)
          split (ch/split-train-test rows labels 0.3 0)
          means (ch/column-means (:x-train split) columns)]
      (doseq [[column want] {:がく片長さ 0.4215384615384616 :がく片幅 0.43826923076923074
                             :花弁長さ 0.47644230769230766 :花弁幅 0.4475}]
        (is (< (abs (- (get means column) want)) 1e-12) (name column)))
      (is (= ["Iris-setosa" "Iris-virginica" "Iris-setosa"] (take 3 (:t-test split)))))))
