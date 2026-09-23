(ns getting-started-ml.chapter03-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter03 :as ch]))

(defn- column [value] {:花弁幅 value})

(def ^:private three-species
  [(mapv column [0.2 0.3 1.2 1.4 2.0 2.2])
   ["setosa" "setosa" "versicolor" "versicolor" "virginica" "virginica"]])

(deftest ジニ不純度
  (testing "一種類だけならジニ不純度は零になる"
    (is (< (abs (ch/gini ["setosa" "setosa"])) 1e-12)))
  (testing "二種類が半々ならジニ不純度は零点五になる"
    (is (< (abs (- (ch/gini ["setosa" "virginica"]) 0.5)) 1e-12)))
  (testing "三種類が均等ならジニ不純度は三分の二になる"
    (is (< (abs (- (ch/gini ["setosa" "versicolor" "virginica"]) (/ 2.0 3))) 1e-12))))

(deftest いちばん多いラベル
  (testing "いちばん多いラベルを返す"
    (is (= "virginica" (ch/majority ["setosa" "virginica" "virginica"]))))
  (testing "同数なら先に現れたラベルを返す"
    (is (= "virginica" (ch/majority ["virginica" "setosa"])))))

(deftest 分割の選び方
  (testing "分けられないときは分割を返さない"
    (is (nil? (ch/best-split [(column 0.2) (column 0.3)] ["setosa" "setosa"] [:花弁幅]))))
  (testing "不純度がいちばん小さくなる分割を選ぶ"
    (let [[x t] three-species
          split (ch/best-split x t [:花弁幅])]
      (is (= :花弁幅 (:feature split)))
      (is (< (abs (- (:threshold split) 0.75)) 1e-12)))))

(deftest 決定木の学習と予測
  (testing "深さを制限しなければ訓練データを全部当てる"
    (let [[x t] three-species]
      (is (= t (ch/predict (ch/fit x t [:花弁幅] nil) x)))))
  (testing "深さ一なら二つの葉になる"
    (let [[x t] three-species
          tree (ch/fit x t [:花弁幅] 1)]
      (is (ch/node? tree))
      (is (ch/leaf? (:left tree)))
      (is (ch/leaf? (:right tree)))))
  (testing "木を字下げ付きの文字列にする"
    (let [[x t] three-species]
      (is (= "花弁幅 <= 0.7500\n  setosa\n花弁幅 > 0.7500\n  versicolor\n"
             (ch/format-tree (ch/fit x t [:花弁幅] 1)))))))
