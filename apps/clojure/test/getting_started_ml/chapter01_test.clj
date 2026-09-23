(ns getting-started-ml.chapter01-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [getting-started-ml.chapter01 :as ch]))

(defn- features [height weight age-group]
  {:身長 height :体重 weight :年代 age-group})

(deftest ルールによる判定
  (testing "二十代はきのこ派と判定する"
    (is (= ch/kinoko (ch/predict-by-rule (features 170 60 20)))))
  (testing "二十代以外はたけのこ派と判定する"
    (doseq [age-group [10 30 40 50]]
      (is (= ch/takenoko (ch/predict-by-rule (features 170 60 age-group)))))))

(deftest 正解率
  (testing "全部当たれば正解率は一になる"
    (is (== 1.0 (ch/accuracy [ch/kinoko ch/takenoko] [ch/kinoko ch/takenoko]))))
  (testing "半分当たれば正解率は零点五になる"
    (is (== 0.5 (ch/accuracy [ch/kinoko ch/kinoko] [ch/kinoko ch/takenoko]))))
  (testing "件数が違えば正解率を求められない"
    (is (thrown-with-msg? IllegalArgumentException #"予測と正解ラベルの件数が違います: 1 と 2"
                          (ch/accuracy [ch/kinoko] [ch/kinoko ch/takenoko])))))

(deftest 特徴量と正解ラベルに分ける
  (testing "人物のリストを特徴量と正解ラベルに分ける"
    (let [people [{:身長 170 :体重 60 :年代 20 :派閥 ch/kinoko}
                  {:身長 160 :体重 50 :年代 30 :派閥 ch/takenoko}]
          [x t] (ch/split-features-and-labels people)]
      (is (= [(features 170 60 20) (features 160 50 30)] x))
      (is (= [ch/kinoko ch/takenoko] t)))))

(deftest CSV-の読み込み
  (testing "BOM 付きの CSV を列名で読み込む"
    (let [file (java.io.File/createTempFile "people" ".csv")]
      (try
        (spit file "\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n")
        (is (= [{:身長 170 :体重 60 :年代 20 :派閥 ch/kinoko}] (ch/load-people (.getPath file))))
        (finally (io/delete-file file true)))))
  (testing "数値でない値があれば読み込めない"
    (let [file (java.io.File/createTempFile "people" ".csv")]
      (try
        (spit file "身長,体重,年代,派閥\n高い,60,20,きのこ\n")
        (is (thrown-with-msg? IllegalArgumentException #"身長 を数値として読めません: 高い"
                              (ch/load-people (.getPath file))))
        (finally (io/delete-file file true)))))
  (testing "列が無ければ読み込めない"
    (let [file (java.io.File/createTempFile "people" ".csv")]
      (try
        (spit file "身長,体重,年代\n170,60,20\n")
        (is (thrown-with-msg? IllegalArgumentException #"列がありません: 派閥"
                              (ch/load-people (.getPath file))))
        (finally (io/delete-file file true))))))
