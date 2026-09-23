(ns getting-started-ml.chapter08-test
  "パイプライン・評価・モデルの保存と読み込みのテスト。自作の小さなデータだけを使う。"
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter08 :as ch]))

(defn- passenger
  "特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。"
  [& values]
  (zipmap ch/feature-columns values))

(def ^:private train-x
  (ch/features-table
   [(passenger "1" "female" "30" "0" "0" "100" "C")
    (passenger "1" "female" "40" "0" "0" "80" "S")
    (passenger "3" "male" "20" "0" "0" "8" "S")
    (passenger "3" "male" "" "0" "0" "7" "")]))

(def ^:private train-t [1 1 0 0])

(def ^:private new-passengers
  (ch/features-table [(passenger "1" "female" "" "0" "0" "50" "C")
                      (passenger "3" "male" "" "0" "0" "8" "S")]))

(deftest 前処理の順に学習して欠損値の残らない特徴量にする
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)]
    (is (= [:Pclass :Age :SibSp :Parch :Fare :Sex_male :Embarked_S] (:columns fitted)))
    (is (every? (fn [row] (every? #(number? (get row %)) (:columns fitted)))
                (ch/features fitted train-x)))))

(deftest 学習済みのパイプラインで欠損値を含むデータを予測する
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)]
    (is (= [1 0] (ch/predict fitted new-passengers)))))

(deftest 欠損値が残っていれば特徴量にできない
  (is (thrown? IllegalArgumentException
               (ch/to-features {:columns [:Age] :rows [{:Age ""}]}))))

(deftest 正解率と見つけた生存者の数を求める
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)
        split {:x-train (:rows train-x) :t-train train-t
               :x-test (:rows new-passengers) :t-test [1 1]}]
    (is (= {:train-accuracy 1.0 :test-accuracy 0.5 :found-survivors 1 :survivors 2}
           (ch/evaluate fitted split)))))

(deftest 保存して読み込んだパイプラインは同じ予測をする
  (let [fitted (ch/fit (ch/build-pipeline 3 :balanced) train-x train-t)
        model-file (io/file (System/getProperty "java.io.tmpdir")
                            (str "survived-" (System/nanoTime) ".model"))]
    (try
      (ch/save-model fitted model-file)
      (is (= fitted (ch/load-model model-file)))
      (is (= (ch/predict fitted new-passengers)
             (ch/predict (ch/load-model model-file) new-passengers)))
      (finally (io/delete-file model-file true)))))

(deftest 形式の版が違うモデルは読み込めない
  (let [model-file (io/file (System/getProperty "java.io.tmpdir")
                            (str "broken-" (System/nanoTime) ".model"))]
    (try
      (spit model-file (pr-str {:format 999}))
      (is (thrown? IllegalArgumentException (ch/load-model model-file)))
      (finally (io/delete-file model-file true)))))
