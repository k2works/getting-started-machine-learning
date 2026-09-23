(ns getting-started-ml.chapter08.transformers-test
  "前処理（グループ別中央値・最頻値・ダミー変数化）のテスト。自作の小さな表だけを使う。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter08.transformers :as tr]))

(defn- table
  "列名と、行ごとの値のベクタから表を作る。"
  [columns & rows]
  {:columns (vec columns) :rows (mapv #(zipmap columns %) rows)})

(defn- values
  "表から列の値を並べて取り出す。"
  [t column]
  (mapv #(get % column) (:rows t)))

;; グループ別の中央値で補完する

(def ^:private ages
  (table [:Pclass :Sex :Age]
         ["1" "female" "30"]
         ["1" "female" "40"]
         ["3" "male" "10"]
         ["3" "male" "20"]
         ["3" "male" ""]))

(deftest グループの中央値で欠損値を補完する
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)]
    (is (= ["30" "40" "10" "20" "15.0"] (values (tr/transform fitted ages) :Age)))))

(deftest 件数が偶数なら中央の二つの平均が中央値になる
  (is (= 15.0 (tr/median [10.0 20.0])))
  (is (= 20.0 (tr/median [10.0 20.0 30.0]))))

(deftest 訓練データで求めた中央値を別のデータに使う
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)
        other (table [:Pclass :Sex :Age] ["1" "female" ""])]
    (is (= ["35.0"] (values (tr/transform fitted other) :Age)))))

(deftest 訓練データに無いグループは全体の中央値で補完する
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)
        other (table [:Pclass :Sex :Age] ["2" "male" ""])]
    ;; 欠けていない年齢は 30, 40, 10, 20 なので全体の中央値は 25.0
    (is (= ["25.0"] (values (tr/transform fitted other) :Age)))))

;; 最頻値で補完する

(def ^:private ports
  (table [:Embarked] ["S"] ["S"] ["C"] [""]))

(deftest 最頻値で欠損値を補完する
  (let [fitted (tr/fit (tr/most-frequent-imputer :Embarked) ports)]
    (is (= ["S" "S" "C" "S"] (values (tr/transform fitted ports) :Embarked)))))

(deftest 同数なら値の順で前のものを最頻値にする
  (let [tied (table [:Embarked] ["S"] ["C"] [""])
        fitted (tr/fit (tr/most-frequent-imputer :Embarked) tied)]
    (is (= "C" (:most-frequent fitted)))))

;; ダミー変数にする

(def ^:private sexes
  (table [:Sex :Embarked] ["male" "S"] ["female" "C"] ["female" "Q"]))

(deftest カテゴリ値を最初のカテゴリを除いたダミー変数にする
  (let [fitted (tr/fit (tr/dummy-encoder [:Sex :Embarked]) sexes)
        encoded (tr/transform fitted sexes)]
    (is (= [:Sex_male :Embarked_Q :Embarked_S] (:columns encoded)))
    (is (= ["1" "0" "0"] (values encoded :Sex_male)))
    (is (= ["0" "0" "1"] (values encoded :Embarked_Q)))
    (is (= ["1" "0" "0"] (values encoded :Embarked_S)))))

(deftest 別のデータにも訓練データと同じダミー変数の列を作る
  (let [fitted (tr/fit (tr/dummy-encoder [:Sex :Embarked]) sexes)
        other (table [:Sex :Embarked] ["female" "S"])
        encoded (tr/transform fitted other)]
    (is (= [:Sex_male :Embarked_Q :Embarked_S] (:columns encoded)))
    (is (= [["0" "0" "1"]] (mapv (juxt :Sex_male :Embarked_Q :Embarked_S) (:rows encoded))))))

(deftest 元の列はダミー変数の列に置き換わる
  (let [fitted (tr/fit (tr/dummy-encoder [:Sex]) sexes)
        encoded (tr/transform fitted sexes)]
    (is (= [:Embarked :Sex_male] (:columns encoded)))))
