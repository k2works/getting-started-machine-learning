(ns getting-started-ml.chapter07.tribuo-test
  "Tribuo の線形回帰と自作の線形回帰を突き合わせるテスト。架空のデータだけを使う。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07 :as ch]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.chapter07.tribuo :as tribuo])
  (:import [java.util Random]
           [org.tribuo.regression Regressor]
           [org.tribuo.regression.evaluation RegressionEvaluator]
           [org.tribuo.regression.slm LARSTrainer SLMTrainer SparseLinearModel]))

(def ^:private columns [:a :b :c])
(def ^:private size 30)

(defn- noisy-dataset
  "t = 4 + 1.5a - 0.5b + 2c に正規分布のノイズを加えた 30 件の架空のデータ。
   java.util.Random を Scala 版・Java 版と同じ順で呼ぶので、同じ値になる。"
  []
  (let [random (Random. 0)
        values (mapv (fn [_] (mapv (fn [_] (* 10 (.nextDouble random))) (range size)))
                     (range (count columns)))
        x (mapv (fn [i] (zipmap columns (mapv #(nth % i) values))) (range size))
        t (mapv (fn [features]
                  (+ 4 (* 1.5 (:a features)) (* -0.5 (:b features)) (* 2 (:c features))
                     (.nextGaussian random)))
                x)]
    [x t]))

(defn- centered-norm
  "平均との差の 2 乗の合計の平方根。"
  [values]
  (let [mean (/ (reduce + values) (count values))]
    (Math/sqrt (reduce + (map #(let [d (- % mean)] (* d d)) values)))))

(defn- near? [a b] (< (abs (- a b)) 1e-9))

(deftest 特徴量の行を数値の正解ラベル付きの事例に変換する
  (let [names [:SNS1 :actor]
        dataset (tribuo/to-dataset [{:SNS1 100.0 :actor 9000.0} {:SNS1 200.0 :actor 9500.0}]
                                   [9200.0 9800.0] names)]
    (is (= 2 (.size dataset)))
    (is (= #{"SNS1" "actor"} (set (.keySet (.getFeatureIDMap dataset)))))
    (is (= [9200.0 9800.0]
           (mapv #(aget ^doubles (.getValues ^Regressor (.getOutput %)) 0) (.getData dataset))))))

(deftest SLMTrainerの予測は自作の線形回帰の予測と一致する
  (let [[x t] (noisy-dataset)
        model (tribuo/train (SLMTrainer. true) x t columns)
        mine (ch/predict (ch/fit x t columns) x)]
    (is (every? true? (map near? (tribuo/predict model x columns) mine)))))

(deftest LARSTrainerの予測も自作の線形回帰の予測と一致する
  (let [[x t] (noisy-dataset)
        model (tribuo/train (LARSTrainer.) x t columns)
        mine (ch/predict (ch/fit x t columns) x)]
    (is (every? true? (map near? (tribuo/predict model x columns) mine)))))

(deftest Tribuoの重みは正規化した空間の値で元の単位に戻すと自作の係数と一致する
  (let [[x t] (noisy-dataset)
        ^SparseLinearModel model (tribuo/train (SLMTrainer. true) x t columns)
        weights (.next (.iterator (.values (.getWeights model))))
        mine (ch/fit x t columns)]
    (doseq [column columns]
      (let [values (mapv #(get % column) x)
            weight (.get weights (.getID (.getFeatureIDMap model) (name column)))]
        (is (> (abs (- weight (ch/coefficient mine column))) 1e-3) (str (name column) " の重み"))
        (is (near? (/ (* weight (centered-norm t)) (centered-norm values))
                   (ch/coefficient mine column))
            (str (name column) " を元の単位に戻した値"))))))

(deftest Tribuoの評価器のMAEとRMSEとR2は自作の評価指標と一致する
  (let [[x t] (noisy-dataset)
        model (tribuo/train (SLMTrainer. true) x t columns)
        y (tribuo/predict model x columns)
        evaluation (.evaluate (RegressionEvaluator.) model (tribuo/to-dataset x t columns))
        target (Regressor. tribuo/output-name Double/NaN)]
    (is (near? (.mae evaluation target) (metrics/mean-absolute-error t y)))
    (is (near? (.rmse evaluation target) (metrics/root-mean-squared-error t y)))
    (is (near? (.r2 evaluation target) (metrics/r2-score t y)))))
