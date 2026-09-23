(ns getting-started-ml.chapter12-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter12 :as ch]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-9))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(defn- close-to-all?
  "並んだ小数を、誤差を許して比べる。"
  ([a b] (close-to-all? a b 1e-9))
  ([a b tolerance]
   (and (= (count a) (count b)) (every? true? (map #(close-to? %1 %2 tolerance) a b)))))

(def ^:private x
  "1 列ずつずらした 2 列の特徴量。"
  [[1.0 2.0] [2.0 1.0] [3.0 4.0] [4.0 3.0] [5.0 6.0]])

(def ^:private t
  "x の 1 列目の 2 倍と 2 列目の 3 倍に 1 を足した正解。"
  [9.0 8.0 19.0 18.0 29.0])

(deftest 行列の演算
  (testing "同じ大きさの行列を足す"
    (is (= [[2.0 4.0] [6.0 8.0]] (ch/matrix-plus [[1.0 2.0] [3.0 4.0]] [[1.0 2.0] [3.0 4.0]]))))
  (testing "大きさが違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/matrix-plus [[1.0 2.0]] [[1.0] [2.0]]))))
  (testing "すべての成分を定数倍する"
    (is (= [[2.0 4.0] [6.0 8.0]] (ch/matrix-scale 2.0 [[1.0 2.0] [3.0 4.0]]))))
  (testing "単位行列は対角だけが一になる"
    (is (= [[1.0 0.0 0.0] [0.0 1.0 0.0] [0.0 0.0 1.0]] (ch/identity-matrix 3))))
  (testing "単位行列を掛けても変わらない"
    (is (= x (matrix/multiply x (ch/identity-matrix 2))))))

(deftest リッジ回帰
  (testing "alpha が零なら最小二乗法と同じ解になる"
    (let [model (ch/ridge-fit x t 0.0)]
      (is (close-to-all? [2.0 3.0] (:coefficients model)))
      (is (close-to? 1.0 (:intercept model)))))
  (testing "第 7 章の正規方程式と同じ係数と切片になる"
    (let [features (mapv (fn [[a b]] {:a a :b b}) x)
          theirs (chapter07/fit features t [:a :b])
          mine (ch/ridge-fit x t 0.0)]
      (is (close-to-all? (mapv second (:coefficients theirs)) (:coefficients mine)))
      (is (close-to? (:intercept theirs) (:intercept mine)))))
  (testing "alpha を大きくするほど係数は小さくなる"
    (let [sums (mapv #(ch/coefficient-abs-sum (ch/ridge-fit x t %)) [0.0 1.0 10.0 100.0])]
      (is (= sums (reverse (sort sums))))
      (is (< (last sums) (first sums)))))
  (testing "切片には罰則がかからないので正解の平均に近づく"
    (is (close-to? (/ (reduce + t) (count t)) (:intercept (ch/ridge-fit x t 1e9)) 1e-3)))
  (testing "件数が違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/ridge-fit x [1.0] 0.0)))))

(deftest 予測
  (testing "係数と切片から行ごとに予測する"
    (is (close-to-all? t (ch/predict {:coefficients [2.0 3.0] :intercept 1.0} x))))
  (testing "列数と係数の数が違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/predict {:coefficients [1.0] :intercept 0.0} x)))))

(deftest 実験の記録とモデル選択
  (let [experiments (ch/run-ridge-experiments x t x t [0.0 1.0 10.0])]
    (testing "alpha ごとに訓練と検証の決定係数を記録する"
      (is (= [0.0 1.0 10.0] (mapv :alpha experiments)))
      (is (close-to? 1.0 (:train-score (first experiments)))))
    (testing "検証データの決定係数が最も高い実験を選ぶ"
      (is (= 0.0 (:alpha (ch/best-experiment experiments)))))
    (testing "同じ値なら先の実験を選ぶ"
      (is (= 1.0 (:alpha (ch/best-experiment [{:alpha 1.0 :validation-score 0.5}
                                              {:alpha 2.0 :validation-score 0.5}])))))
    (testing "実験が一件も無ければ失敗する"
      (is (thrown? IllegalArgumentException (ch/best-experiment []))))))

(deftest 零になった係数の特徴量名
  (testing "係数がちょうど零の列だけを列の順に返す"
    (is (= ["b" "d"] (ch/zero-coefficient-names [1.0 0.0 -2.0 0.0] ["a" "b" "c" "d"]))))
  (testing "数が違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/zero-coefficient-names [1.0] ["a" "b"])))))

(deftest 標準化して多項式特徴量にする
  (let [features [{:a 1.0 :b 2.0} {:a 3.0 :b 4.0} {:a 5.0 :b 6.0}]
        scaler (ch/scaler-fit features [:a :b])
        transformed (ch/scaler-transform scaler features)]
    (testing "元の列と二乗の項と積の項が並ぶ"
      (is (= ["a" "b" "a^2" "a b" "b^2"] (ch/feature-names scaler))))
    (testing "標準化した列は平均が零になる"
      (is (close-to? 0.0 (reduce + (map first transformed))))
      (is (close-to-all? [-1.224744871391589 0.0 1.224744871391589] (mapv first transformed))))
    (testing "二次の項は標準化した値の積になる"
      (is (every? (fn [row] (close-to? (nth row 2) (* (nth row 0) (nth row 0)))) transformed))
      (is (every? (fn [row] (close-to? (nth row 3) (* (nth row 0) (nth row 1)))) transformed)))
    (testing "別のデータも訓練データの平均と標準偏差で変換する"
      (is (close-to-all? (first transformed)
                         (first (ch/scaler-transform scaler [{:a 1.0 :b 2.0}])))))))

(deftest 外れ値を除く
  (let [table {:columns [:v] :rows (conj (mapv (fn [v] {:v (str v)}) (range 20)) {:v "1000"})}]
    (testing "z スコアが閾値を超える行を除く"
      (is (= 20 (count (:rows (ch/remove-outliers table [:v] 3.0))))))
    (testing "閾値を上げれば残る"
      (is (= 21 (count (:rows (ch/remove-outliers table [:v] 10.0))))))))

(deftest TribuoのElasticNetCDTrainerと突き合わせる
  (testing "同じ alpha の尺度でリッジ回帰の係数と切片が一致する"
    (doseq [alpha [0.1 1.0 10.0]]
      (let [mine (ch/ridge-fit x t alpha)
            theirs (ch/fit-ridge x t alpha)]
        (is (close-to-all? (:coefficients mine) (:coefficients theirs) 1e-6) (str "alpha=" alpha))
        (is (close-to? (:intercept mine) (:intercept theirs) 1e-6) (str "alpha=" alpha)))))
  (testing "ラッソ回帰は係数をちょうど零にする"
    (let [noisy (mapv (fn [[a b]] [a b (* 0.001 a)]) x)
          lasso (ch/fit-lasso noisy t 1.0)]
      (is (= 3 (count (:coefficients lasso))))
      (is (seq (ch/zero-coefficient-names (:coefficients lasso) ["a" "b" "noise"])))))
  (testing "l1Ratio に零は受け付けないので極小の値を使う"
    (is (thrown-with-msg? com.oracle.labs.mlrg.olcut.config.PropertyException
                          #"L1 Ratio must be between 0 and 1"
                          (ch/fit-elastic-net x t 0.1 0.0)))
    (is (some? (ch/fit-elastic-net x t 0.1 1e-12))))
  (testing "ラッソ回帰は alpha が強いほど係数が小さくなる"
    (is (< (ch/coefficient-abs-sum (ch/fit-lasso x t 5.0))
           (ch/coefficient-abs-sum (ch/fit-lasso x t 0.1))))))
