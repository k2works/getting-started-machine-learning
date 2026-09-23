(ns getting-started-ml.chapter07-test
  "線形回帰のモデル・学習・外れ値の除去のテスト。自作の小さなデータだけを使う。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07 :as ch]))

(def ^:private columns [:SNS1 :actor])

(defn- near? [a b] (< (abs (- a b)) 1e-9))

(deftest 切片と係数から予測値を計算する
  (let [model (ch/model 1.0 columns [2.0 3.0])]
    (is (near? 1.0 (ch/predict-one model {:SNS1 0.0 :actor 0.0})))
    (is (near? 14.0 (ch/predict-one model {:SNS1 2.0 :actor 3.0})))))

(deftest 列の並び順が違っても列名で係数を対応させる
  (let [model (ch/model 1.0 columns [2.0 3.0])]
    (is (near? (ch/predict-one model {:SNS1 2.0 :actor 3.0})
               (ch/predict-one model (array-map :actor 3.0 :SNS1 2.0))))))

(deftest 列名で係数を読む
  (is (near? 3.0 (ch/coefficient (ch/model 1.0 columns [2.0 3.0]) :actor))))

(deftest 無い列の係数を読むと失敗する
  (is (thrown? IllegalArgumentException (ch/coefficient (ch/model 1.0 columns [2.0 3.0]) :SNS2))))

(deftest 列名と係数の数が違えばモデルを作れない
  (is (thrown? IllegalArgumentException (ch/model 1.0 columns [2.0]))))

(deftest 計画行列の先頭には一の列が入る
  (is (= [[1.0 1.0 2.0] [1.0 3.0 4.0]]
         (ch/design-matrix [{:SNS1 1.0 :actor 2.0} {:SNS1 3.0 :actor 4.0}] columns))))

(deftest 直線上の点から切片と係数を求める
  ;; y = 3 + 2x をちょうど通る 3 点
  (let [model (ch/fit [{:x 0.0} {:x 1.0} {:x 2.0}] [3.0 5.0 7.0] [:x])]
    (is (near? 3.0 (:intercept model)))
    (is (near? 2.0 (ch/coefficient model :x)))))

(deftest 複数の特徴量から切片と係数を求める
  ;; y = 1 + 2a - 3b をちょうど通る 4 点
  (let [x [{:a 0.0 :b 0.0} {:a 1.0 :b 0.0} {:a 0.0 :b 1.0} {:a 1.0 :b 1.0}]
        model (ch/fit x [1.0 3.0 -2.0 0.0] [:a :b])]
    (is (near? 1.0 (:intercept model)))
    (is (near? 2.0 (ch/coefficient model :a)))
    (is (near? -3.0 (ch/coefficient model :b)))))

(deftest 訓練データが空なら学習できない
  (is (thrown? IllegalArgumentException (ch/fit [] [] [:x]))))

(deftest 特徴量と実測値の件数が違えば学習できない
  (is (thrown? IllegalArgumentException (ch/fit [{:x 1.0}] [1.0 2.0] [:x]))))

;; 外れ値の除去（SNS2 が 1000 を超え、かつ sales が 8500 未満の行を取り除く）

(def ^:private table
  {:columns [:SNS2 :sales]
   :rows [{:SNS2 "1200" :sales "8000"}   ; 両方満たす → 外れ値
          {:SNS2 "1200" :sales "9000"}   ; SNS2 だけ → 残す
          {:SNS2 "500" :sales "8000"}    ; sales だけ → 残す
          {:SNS2 "500" :sales "9000"}]}) ; どちらも満たさない → 残す

(deftest SNS2が千を超え売上が八千五百未満の行を取り除く
  (is (= 3 (count (:rows (ch/remove-outliers table))))))

(deftest 条件の片方だけを満たす行は残す
  (is (= [["1200" "9000"] ["500" "8000"] ["500" "9000"]]
         (mapv (juxt :SNS2 :sales) (:rows (ch/remove-outliers table))))))
