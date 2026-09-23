(ns getting-started-ml.chapter07.matrix-test
  "行列の積・転置・連立方程式の解法のテスト。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07.matrix :as matrix]))

(deftest 行列の積を求める
  (is (= [[19.0 22.0] [43.0 50.0]]
         (matrix/multiply [[1.0 2.0] [3.0 4.0]] [[5.0 6.0] [7.0 8.0]]))))

(deftest 行数と列数が違う行列の積を求める
  (is (= [[7.0] [16.0]]
         (matrix/multiply [[1.0 2.0 3.0] [4.0 5.0 6.0]] [[1.0] [0.0] [2.0]]))))

(deftest 左の列数と右の行数が違えば積を求められない
  (is (thrown? IllegalArgumentException
               (matrix/multiply [[1.0 2.0]] [[1.0 2.0]]))))

(deftest 行によって列数が違う値からは行列にならない
  (is (thrown? IllegalArgumentException (matrix/check [[1.0 2.0] [3.0]]))))

(deftest 列ベクトルは値を縦に並べた一列の行列になる
  (is (= [[1.0] [2.0] [3.0]] (matrix/column-vector [1.0 2.0 3.0]))))

(deftest 行と列を入れ替える
  (is (= [[1.0 4.0] [2.0 5.0] [3.0 6.0]]
         (matrix/transpose [[1.0 2.0 3.0] [4.0 5.0 6.0]]))))

(deftest 連立方程式の解を求める
  ;; 2x + y = 5, x + 3y = 5 → x = 2, y = 1
  (is (= [[2.0] [1.0]]
         (matrix/solve [[2.0 1.0] [1.0 3.0]] (matrix/column-vector [5.0 5.0])))))

(deftest 三元の連立方程式の解を求める
  (let [solution (matrix/solve [[2.0 1.0 -1.0] [-3.0 -1.0 2.0] [-2.0 1.0 2.0]]
                               (matrix/column-vector [8.0 -11.0 -3.0]))]
    (is (every? #(< (abs %) 1e-12)
                (map - (matrix/column solution 0) [2.0 3.0 -1.0])))))

(deftest 対角成分が零でも行を入れ替えて解を求める
  ;; 先頭の行の 1 列目が 0 なので、部分ピボット選択が無ければ 0 で割ってしまう
  (is (= [[3.0] [2.0]]
         (matrix/solve [[0.0 1.0] [1.0 1.0]] (matrix/column-vector [2.0 5.0])))))

(deftest 正方行列でなければ解を求められない
  (is (thrown? IllegalArgumentException
               (matrix/solve [[1.0 2.0]] (matrix/column-vector [1.0])))))
