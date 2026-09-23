(ns getting-started-ml.iris-models-test
  "実データ（iris.csv）でモデルを比べるテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter10 :as ch]
            [getting-started-ml.dataset :as dataset])
  (:import [org.tribuo.classification.dtree CARTClassificationTrainer]
           [org.tribuo.classification.dtree.impurity GiniIndex]
           [org.tribuo.classification.ensemble VotingCombiner]
           [org.tribuo.common.tree RandomForestTrainer]))

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

(deftest 実行するとモデルごとの正解率と特徴量の重要度を表示する
  ;; 10 個の数値は Java 版と一致する（分割も乱数も同じ手順で、Tribuo も同じ 4.3.2 のため）
  (when (iris-split)
    (is (= ["モデル\t訓練データ\tテストデータ"
            "決定木（深さ 2）\t0.9333\t0.9556"
            "ロジスティック回帰\t0.9143\t0.9111"
            "ランダムフォレスト（100 本）\t1.0000\t0.9333"
            "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556"
            "Tribuo ロジスティック回帰\t0.9238\t0.8889"
            "Tribuo ランダムフォレスト（100 本）\t0.9905\t0.9556"
            ""
            "ランダムフォレスト（100 本）の特徴量の重要度:"
            "がく片長さ\t0.1882"
            "がく片幅\t0.1271"
            "花弁長さ\t0.2708"
            "花弁幅\t0.4140"]
           (str/split-lines (with-out-str (ch/run)))))))

(deftest ロジスティック回帰はテストデータの四十五件中四十一件を正しく分類する
  (when-let [split (iris-split)]
    (let [{:keys [test]} (ch/score (ch/logistic-trainer) split (columns split))]
      (is (< (abs (- test (/ 41.0 45))) 1e-12)))))

(deftest ランダムフォレストは訓練データを分け切りテストデータの四十五件中四十二件を正しく分類する
  (when-let [split (iris-split)]
    (let [{:keys [train test]} (ch/score (ch/forest-trainer 100 2 nil 0) split (columns split))]
      (is (= 1.0 train))
      (is (< (abs (- test (/ 42.0 45))) 1e-12)))))

(deftest Tribuoのロジスティック回帰は五百エポックで自作と同じ正解率になる
  (when-let [split (iris-split)]
    (let [columns (columns split)]
      (is (= (ch/score (ch/logistic-trainer) split columns)
             (ch/score (ch/tribuo-trainer (ch/tribuo-logistic-regression 500)) split columns))))))

(deftest Tribuoの既定のロジスティック回帰は五エポックと同じ正解率になる
  (when-let [split (iris-split)]
    (let [columns (columns split)]
      (is (= (ch/score (ch/tribuo-trainer (ch/tribuo-logistic-regression)) split columns)
             (ch/score (ch/tribuo-trainer (ch/tribuo-logistic-regression 5)) split columns))))))

(deftest Tribuoのランダムフォレストは子の節の重みの下限を一にすると訓練データを分け切る
  (when-let [split (iris-split)]
    (let [tree (CARTClassificationTrainer. Integer/MAX_VALUE (float 1.0) (float 0.0) (float 0.5)
                                           (GiniIndex.) 0)
          trainer (ch/tribuo-trainer (RandomForestTrainer. tree (VotingCombiner.) 100 0))
          {:keys [train test]} (ch/score trainer split (columns split))]
      (is (= 1.0 train))
      (is (< (abs (- test (/ 42.0 45))) 1e-12)))))
