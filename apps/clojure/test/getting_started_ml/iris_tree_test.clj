(ns getting-started-ml.iris-tree-test
  "実データ（iris.csv）で決定木を学習するテスト。学習データが無ければスキップする。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter01 :as chapter01]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter03 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- iris-split
  "前処理した訓練データとテストデータを返す。学習データが無ければ nil。"
  []
  (let [path (str (dataset/dir) "/iris.csv")]
    (if (.exists (java.io.File. path))
      (chapter02/prepare-iris path 0.3 0)
      (binding [*out* *err*]
        (println "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(defn- columns [split] (vec (keys (first (:x-train split)))))

(deftest 深さ二の決定木はテストデータの四十五件中四十三件を正しく分類する
  (when-let [split (iris-split)]
    (let [tree (ch/fit (:x-train split) (:t-train split) (columns split) 2)
          predictions (ch/predict tree (:x-test split))]
      (is (< (abs (- (chapter01/accuracy predictions (:t-test split)) (/ 43.0 45))) 1e-12)))))

(deftest 自作とTribuoの予測はどの深さでも一致する
  ;; java.util.Random と同じ手順で分けているので、Java 版・Scala 版とも同じ結果になる
  (when-let [split (iris-split)]
    (doseq [max-depth [1 2 3 4 5 nil]]
      (let [ours (ch/predict (ch/fit (:x-train split) (:t-train split) (columns split) max-depth)
                             (:x-test split))
            theirs (ch/tribuo-predict (:x-train split) (:t-train split) (:x-test split)
                                      (columns split) max-depth)]
        (is (= ours theirs) (str "深さ " max-depth))))))

(deftest 深さ二の決定木は花弁幅で三種類に分かれる
  (when-let [split (iris-split)]
    (is (= (str "花弁幅 <= 0.2950\n  Iris-setosa\n"
                "花弁幅 > 0.2950\n  花弁幅 <= 0.6500\n    Iris-versicolor\n"
                "  花弁幅 > 0.6500\n    Iris-virginica\n")
           (ch/format-tree (ch/fit (:x-train split) (:t-train split) (columns split) 2))))))
