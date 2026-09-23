(ns getting-started-ml.chapter13-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter13 :as ch])
  (:import [org.tribuo.math.la DenseMatrix]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-9))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(defn- correlated
  "2 列目が 1 列目のちょうど 2 倍になる、完全に相関するデータ。"
  []
  [[1.0 2.0] [2.0 4.0] [3.0 6.0] [4.0 8.0]])

;; ## Tribuo の固有値分解の学習用テスト

(deftest Tribuoの固有値分解は固有値を大きい順に返す
  (let [^DenseMatrix m (DenseMatrix/createDenseMatrix
                        (into-array [(double-array [2.0 0.0]) (double-array [0.0 5.0])]))
        eigen (.orElseThrow (.eigenDecomposition m))]
    (is (= [5.0 2.0] (vec (.toArray (.eigenvalues eigen)))))))

(deftest Tribuoの固有ベクトルは掛けても向きが変わらず固有値倍になる
  (let [^DenseMatrix m (DenseMatrix/createDenseMatrix
                        (into-array [(double-array [2.0 1.0]) (double-array [1.0 2.0])]))
        eigen (.orElseThrow (.eigenDecomposition m))
        values (vec (.toArray (.eigenvalues eigen)))]
    (doseq [i (range 2)]
      (let [v (vec (.toArray (.getEigenVector eigen i)))
            multiplied (first (matrix/transpose
                               (matrix/multiply (mapv vec (map vec (.toArray m)))
                                                (matrix/column-vector v))))]
        (is (every? true? (map #(close-to? %1 (* (values i) %2)) multiplied v)))))))

(deftest 対称でない行列は固有値分解できない
  (let [^DenseMatrix m (DenseMatrix/createDenseMatrix
                        (into-array [(double-array [1.0 2.0]) (double-array [3.0 4.0])]))]
    (is (not (.isPresent (.eigenDecomposition m))))))

;; ## 分散共分散行列

(deftest 分散共分散行列は分散と共分散を並べる
  (testing "2 列の分散と共分散"
    (let [c (ch/covariance-matrix [[1.0 2.0] [2.0 4.0] [3.0 6.0]])]
      (is (close-to? 1.0 (get-in c [0 0])))
      (is (close-to? 4.0 (get-in c [1 1])))
      (is (close-to? 2.0 (get-in c [0 1])))
      (is (= (get-in c [0 1]) (get-in c [1 0])))))
  (testing "3 列でも各列の分散と 2 列ずつの共分散を並べる"
    (let [c (ch/covariance-matrix [[1.0 2.0 1.0] [2.0 4.0 1.0] [3.0 6.0 1.0]])]
      (is (= 3 (matrix/row-count c)))
      (is (= 3 (matrix/column-count c)))
      (is (close-to? 0.0 (get-in c [2 2]))))))

(deftest 列ごとの平均を求める
  (is (= [2.5 5.0] (ch/column-means (correlated)))))

;; ## 主成分

(deftest 完全に相関する二列なら第一主成分だけで分散をすべて説明する
  (let [model (ch/fit (correlated) 2)]
    (is (close-to? 1.0 (first (:explained-variance-ratio model))))
    (is (close-to? 0.0 (second (:explained-variance-ratio model))))))

(deftest 主成分は寄与率の大きい順に指定した数だけ並ぶ
  (let [model (ch/fit [[1.0 2.0 0.5] [2.0 4.0 0.1] [3.0 6.0 0.9] [4.0 8.0 0.2]] 2)]
    (is (= 2 (matrix/row-count (:components model))))
    (is (>= (first (:explained-variance model)) (second (:explained-variance model))))))

(deftest 主成分の向きは絶対値が最大の要素が正になるようにそろえる
  (testing "絶対値が最大の要素が負なら符号を反転する"
    (is (= [[0.8 -0.6]] (ch/normalize-signs [[-0.8 0.6]]))))
  (testing "絶対値が最大の要素がすでに正ならそのまま"
    (is (= [[-0.3 0.4 0.9]] (ch/normalize-signs [[-0.3 0.4 0.9]])))))

(deftest 主成分は長さ一で互いに直交する
  (let [components (:components (ch/fit [[1.0 2.0 0.5] [2.0 4.0 0.1]
                                         [3.0 6.0 0.9] [4.0 8.0 0.2]] 3))]
    (doseq [row components]
      (is (close-to? 1.0 (Math/sqrt (reduce + (map * row row))))))
    (is (close-to? 0.0 (reduce + (map * (components 0) (components 1)))))
    (is (close-to? 0.0 (reduce + (map * (components 0) (components 2)))))))

(deftest 主成分は分散共分散行列の固有ベクトルになる
  (let [x [[1.0 2.0 0.5] [2.0 4.0 0.1] [3.0 6.0 0.9] [4.0 8.0 0.2]]
        {:keys [components explained-variance]} (ch/fit x 3)
        c (ch/covariance-matrix x)]
    (doseq [i (range 3)]
      (let [v (components i)
            multiplied (first (matrix/transpose (matrix/multiply c (matrix/column-vector v))))]
        (is (every? true? (map #(close-to? %1 (* (explained-variance i) %2)) multiplied v)))))))

(deftest 平均を引いてから主成分の向きに射影する
  (let [x (correlated)
        model (ch/fit x 1)
        projected (ch/transform model x)]
    (is (= 4 (matrix/row-count projected)))
    (is (= 1 (matrix/column-count projected)))
    (is (close-to? 0.0 (reduce + (map first projected))))))

;; ## 主成分の数と解釈

(deftest 累積寄与率がしきい値に届くまでの主成分の数を返す
  (testing "3 つで 0.8 に届く"
    (is (= 3 (ch/components-needed [0.5 0.2 0.15 0.1 0.05] 0.8))))
  (testing "しきい値を上げると必要な主成分の数が増える"
    (is (= 4 (ch/components-needed [0.5 0.2 0.15 0.1 0.05] 0.9))))
  (testing "どこまで足しても届かなければすべての主成分を使う"
    (is (= 2 (ch/components-needed [0.5 0.2] 0.99)))))

(deftest 係数の絶対値が大きい順に列名と係数を返す
  (is (= [{:column :LSTAT :value -0.9} {:column :RM :value 0.5} {:column :ZN :value 0.1}]
         (ch/top-loadings [0.5 -0.9 0.1] [:RM :LSTAT :ZN] 3)))
  (is (= [{:column :LSTAT :value -0.9}] (ch/top-loadings [0.5 -0.9 0.1] [:RM :LSTAT :ZN] 1))))

;; ## Boston の前処理

(defn- boston-like
  "CRIME が 3 種類で RM に欠損値が 1 件ある、Boston.csv に似た小さな表。"
  []
  {:columns [:CRIME :RM :PRICE]
   :rows [{:CRIME "low" :RM "6.0" :PRICE "20.0"}
          {:CRIME "very_low" :RM "7.0" :PRICE "30.0"}
          {:CRIME "high" :RM "" :PRICE "10.0"}
          {:CRIME "low" :RM "5.0" :PRICE "25.0"}]})

(deftest CRIMEをダミー変数の列に置き換える
  (is (= [:RM :PRICE :CRIME_low :CRIME_very_low] (:columns (ch/standardize-table (boston-like))))))

(deftest 欠損値を補完してから各列を平均零と標準偏差一にそろえる
  (let [{:keys [columns x]} (ch/standardize-table (boston-like))]
    (doseq [column columns]
      (let [values (mapv #(get % column) x)
            mean (/ (reduce + values) (count values))
            variance (/ (reduce + (map #(* (- % mean) (- % mean)) values)) (count values))]
        (is (close-to? 0.0 mean) (name column))
        (is (close-to? 1.0 (Math/sqrt variance)) (name column))))))

(deftest 特徴量のリストを列の順の行列にする
  (let [{:keys [columns x]} (ch/standardize-table (boston-like))
        m (ch/to-matrix x columns)]
    (is (= 4 (matrix/row-count m)))
    (is (= 4 (matrix/column-count m)))))
