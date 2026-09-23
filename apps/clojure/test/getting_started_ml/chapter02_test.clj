(ns getting-started-ml.chapter02-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter02 :as ch]))

(deftest 行の読み出し
  (testing "数値の列を読む"
    (is (== 5.1 (ch/number {:がく片長さ "5.1"} :がく片長さ))))
  (testing "空欄の列は欠損値になる"
    (is (nil? (ch/number {:がく片長さ ""} :がく片長さ)))
    (is (ch/missing? {:がく片長さ ""} :がく片長さ)))
  (testing "数値として読めない列は失敗する"
    (is (thrown-with-msg? IllegalArgumentException #"がく片長さ を数値として読めません: たくさん"
                          (ch/number {:がく片長さ "たくさん"} :がく片長さ))))
  (testing "列が無ければ失敗する"
    (is (thrown-with-msg? IllegalArgumentException #"列がありません: 花弁幅"
                          (ch/text {:がく片長さ "5.1"} :花弁幅))))
  (testing "文字列の列を読む"
    (is (= "Iris-setosa" (ch/text {:種類 "Iris-setosa"} :種類)))))

(deftest 欠損値を数える
  (testing "列ごとに欠損値を数える。列の順は表の列の順のまま"
    (let [table {:columns [:がく片長さ :種類]
                 :rows [{:がく片長さ "5.1" :種類 "Iris-setosa"}
                        {:がく片長さ "" :種類 "Iris-setosa"}
                        {:がく片長さ "" :種類 "Iris-virginica"}]}]
      (is (= [[:がく片長さ 2] [:種類 0]] (ch/count-missing table))))))

(deftest 欠損値の補完
  (testing "欠損値を除いて列ごとの平均値を求める"
    (let [rows [{:がく片長さ "1.0"} {:がく片長さ ""} {:がく片長さ "3.0"}]]
      (is (= {:がく片長さ 2.0} (ch/column-means rows [:がく片長さ])))))
  (testing "値がすべて空欄なら平均値を求められない"
    (is (thrown-with-msg? IllegalArgumentException #"値がすべて空欄です: がく片長さ"
                          (ch/column-means [{:がく片長さ ""}] [:がく片長さ]))))
  (testing "欠損値を平均値で補完する。元の行は変えない"
    (let [rows [{:がく片長さ "1.0"} {:がく片長さ ""}]]
      (is (= [{:がく片長さ 1.0} {:がく片長さ 2.0}]
             (ch/fill-missing rows [:がく片長さ] {:がく片長さ 2.0})))))
  (testing "補完する値が無ければ失敗する"
    (is (thrown-with-msg? IllegalArgumentException #"補完する値がありません: がく片長さ"
                          (ch/fill-missing [{:がく片長さ ""}] [:がく片長さ] {})))))

(deftest 訓練データとテストデータに分ける
  (testing "同じシードなら同じ並びになる"
    (let [items (vec (range 10))]
      (is (= (ch/shuffle-with-seed items 0) (ch/shuffle-with-seed items 0)))
      (is (not= (ch/shuffle-with-seed items 0) (ch/shuffle-with-seed items 1)))))
  (testing "Java 版・Scala 版と同じ並びになる"
    (is (= [4 8 9 6 3 5 2 1 7 0] (ch/shuffle-with-seed (vec (range 10)) 0))))
  (testing "並べ替えても要素は変わらない"
    (is (= (vec (range 10)) (vec (sort (ch/shuffle-with-seed (vec (range 10)) 0))))))
  (testing "テストデータの割合で分ける"
    (let [x (vec (range 10))
          split (ch/split-train-test x (mapv str x) 0.3 0)]
      (is (= [7 3 7 3] [(count (:x-train split)) (count (:x-test split))
                        (count (:t-train split)) (count (:t-test split))]))))
  (testing "分けても特徴量と正解ラベルの対応は崩れない"
    (let [x (vec (range 10))
          split (ch/split-train-test x (mapv str x) 0.3 0)]
      (is (= (mapv str (:x-train split)) (:t-train split)))
      (is (= (mapv str (:x-test split)) (:t-test split)))))
  (testing "特徴量と正解ラベルの件数が違えば分けられない"
    (is (thrown-with-msg? IllegalArgumentException #"件数が違います: 2 と 1"
                          (ch/split-train-test [1 2] [1] 0.3 0)))))

(deftest 正解ラベルの列を分ける
  (testing "正解ラベルの列を取り出して残りを特徴量の列にする"
    (let [table {:columns [:がく片長さ :種類]
                 :rows [{:がく片長さ "5.1" :種類 "Iris-setosa"}
                        {:がく片長さ "6.0" :種類 "Iris-virginica"}]}
          {:keys [columns rows labels]} (ch/split-features-and-target table :種類)]
      (is (= [:がく片長さ] columns))
      (is (= 2 (count rows)))
      (is (= ["Iris-setosa" "Iris-virginica"] labels)))))
