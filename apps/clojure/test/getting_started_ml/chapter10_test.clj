(ns getting-started-ml.chapter10-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.chapter10 :as ch])
  (:import [java.util Random]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-12))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(def ^:private columns
  "テストで使う特徴量の列。"
  [:花弁長さ :花弁幅])

(def ^:private two-species
  "花弁幅で分けられる 2 品種のデータ。"
  [(mapv (fn [[length width]] {:花弁長さ length :花弁幅 width})
         [[1.0 0.1] [1.2 0.2] [1.1 0.15] [4.0 1.8] [4.2 2.0] [4.1 1.9]])
   ["setosa" "setosa" "setosa" "virginica" "virginica" "virginica"]])

(deftest ソフトマックス関数
  (testing "合計が一になる確率にする"
    (let [p (ch/softmax [1.0 2.0 3.0])]
      (is (close-to? 1.0 (reduce + p)))
      (is (every? pos? p))
      (is (= p (sort p)))))
  (testing "同じスコアなら同じ確率になる"
    (is (every? #(close-to? (/ 1.0 3) %) (ch/softmax [2.0 2.0 2.0]))))
  (testing "大きな値でもあふれない"
    (let [p (ch/softmax [1000.0 1001.0])]
      (is (close-to? 1.0 (reduce + p)))
      (is (every? #(not (Double/isNaN %)) p)))))

(deftest 交差エントロピー
  (testing "正解の確率が一なら損失は零になる"
    (is (close-to? 0.0 (ch/cross-entropy [[1.0 0.0] [0.0 1.0]] [0 1]) 1e-11)))
  (testing "正解の確率が小さいほど損失は大きくなる"
    (is (< (ch/cross-entropy [[0.9 0.1]] [0])
           (ch/cross-entropy [[0.5 0.5]] [0])))))

(deftest ロジスティック回帰
  (let [[x t] two-species]
    (testing "分けられるデータを正しく予測する"
      (let [predict ((ch/logistic-trainer) x t columns)]
        (is (= t (predict x)))))
    (testing "学習した品種は名前の順に並ぶ"
      (is (= ["setosa" "virginica"] (:classes (ch/logistic-fit x t columns)))))
    (testing "繰り返すほど損失が小さくなる"
      (let [losses (:losses (ch/logistic-fit x t columns 1.0 50))]
        (is (= 50 (count losses)))
        (is (< (last losses) (first losses)))))
    (testing "学習率が零なら重みは変わらず損失も変わらない"
      (let [model (ch/logistic-fit x t columns 0.0 3)]
        (is (every? zero? (:weights model)))
        (is (apply = (:losses model)))))))

(deftest 多数決とブートストラップ標本
  (testing "サンプルごとに最も多い予測を選ぶ"
    (is (= ["a" "b"] (ch/majority-vote [["a" "b"] ["a" "c"] ["b" "b"]]))))
  (testing "同数なら先に現れた予測を選ぶ"
    (is (= ["a"] (ch/majority-vote [["a"] ["b"]]))))
  (testing "行番号を重複を許して件数と同じだけ選ぶ"
    (let [sample (ch/bootstrap-sample 5 (Random. 0))]
      (is (= 5 (count sample)))
      (is (every? #(< -1 % 5) sample))))
  (testing "同じシードなら同じ標本になる"
    (is (= (ch/bootstrap-sample 10 (Random. 0)) (ch/bootstrap-sample 10 (Random. 0)))))
  (testing "シードが違えば別の標本になる"
    (is (not= (ch/bootstrap-sample 10 (Random. 0)) (ch/bootstrap-sample 10 (Random. 1))))))

(deftest ランダムフォレスト
  (let [[x t] two-species]
    (testing "分けられるデータを正しく予測する"
      (let [predict ((ch/forest-trainer 10 1 nil 0) x t columns)]
        (is (= t (predict x)))))
    (testing "同じシードなら同じ森になる"
      (is (= (ch/forest-fit x t columns 5 1 nil 0) (ch/forest-fit x t columns 5 1 nil 0))))
    (testing "木ごとに使う特徴量を絞る"
      (let [forest (ch/forest-fit x t columns 5 1 nil 0)]
        (is (every? #(= 1 (count (:columns %))) (:trees forest)))
        (is (every? #(= (count x) (count (:rows %))) (:trees forest)))))
    (testing "深さの上限を指定した木を作る"
      (let [forest (ch/forest-fit x t columns 3 2 1 0)]
        (is (every? #(chapter03/node? (:tree %)) (:trees forest)))
        (is (every? #(chapter03/leaf? (get-in % [:tree :left])) (:trees forest)))))))

(deftest 特徴量の重要度
  (let [[x t] two-species]
    (testing "一つの列だけで分ける木は、その列の重要度が一になる"
      (let [tree (chapter03/fit x t [:花弁幅] nil)]
        (is (= {:花弁幅 1.0} (ch/tree-importances tree x t [:花弁幅])))))
    (testing "使わなかった列の重要度は零になる"
      (let [tree (chapter03/fit x t columns 1)
            importances (ch/tree-importances tree x t columns)]
        (is (close-to? 1.0 (reduce + (vals importances))))
        (is (= 1 (count (filter pos? (vals importances)))))))
    (testing "森の重要度は合計が一になり列の数だけ並ぶ"
      (let [forest (ch/forest-fit x t columns 10 1 nil 0)
            importances (ch/forest-importances forest x t columns)]
        (is (= (set columns) (set (keys importances))))
        (is (close-to? 1.0 (reduce + (vals importances))))))))

(deftest Tribuoのトレーナーも同じ関数で評価できる
  (let [[x t] two-species
        split {:x-train x :t-train t :x-test x :t-test t}]
    (doseq [trainer [(ch/tree-trainer 1)
                     (ch/logistic-trainer)
                     (ch/forest-trainer 10 1 nil 0)
                     (ch/tribuo-trainer (ch/tribuo-logistic-regression 100))
                     (ch/tribuo-trainer (ch/tribuo-random-forest 10 nil 0))]]
      (is (= {:train 1.0 :test 1.0} (ch/score trainer split columns))))))

(deftest Tribuoのランダムフォレストは分割ごとに特徴量を絞らない決定木を受け付けない
  (is (thrown? RuntimeException
               (org.tribuo.common.tree.RandomForestTrainer.
                (org.tribuo.classification.dtree.CARTClassificationTrainer. Integer/MAX_VALUE)
                (org.tribuo.classification.ensemble.VotingCombiner.) 10 0))))
