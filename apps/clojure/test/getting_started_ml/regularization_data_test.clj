(ns getting-started-ml.regularization-data-test
  "実データ（Boston.csv）でリッジ回帰とラッソ回帰を学習するテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter12 :as ch]
            [getting-started-ml.dataset :as dataset]))

(def ^:private csv-file (delay (str (dataset/dir) "/Boston.csv")))

(defn- data?
  "学習データがあるかどうかを返す。無ければ理由を標準エラーに出す。"
  []
  (or (.exists (java.io.File. ^String @csv-file))
      (binding [*out* *err*]
        (println "学習データ Boston.csv が配置されていない（gulp data:setup）のでスキップする")
        false)))

(defn- boston
  "前処理した訓練・検証・テストデータを返す。"
  []
  (ch/prepare-boston @csv-file 0.3 0.3 0))

(deftest 外れ値を二件除いて四十七件と二十一件と三十件に分ける
  (when (data?)
    (let [data (boston)]
      (is (= 98 (:kept data)))
      (is (= [47 21 30] [(count (:t-train data)) (count (:t-valid data)) (count (:t-test data))]))
      (is (= ["RM" "PTRATIO" "LSTAT" "RM^2" "RM PTRATIO" "RM LSTAT"
              "PTRATIO^2" "PTRATIO LSTAT" "LSTAT^2"]
             (:feature-names data))))))

(deftest 実データでも自作のリッジ回帰はTribuoのElasticNetCDTrainerと一致する
  ;; 係数と切片は Java 版・Scala 版と一致する（分割も手順も同じため）
  (when (data?)
    (let [{:keys [x-train t-train]} (boston)
          mine (ch/ridge-fit x-train t-train 10.0)
          theirs (ch/fit-ridge x-train t-train 10.0)]
      (is (every? true? (map #(< (abs (- %1 %2)) 1e-6)
                             (:coefficients mine) (:coefficients theirs))))
      (is (< (abs (- (:intercept mine) (:intercept theirs))) 1e-6)))))

(deftest 実行すると正則化の強さごとの結果とラッソ回帰で零になった特徴量を表示する
  (when (data?)
    (is (= ["データ件数: 98（外れ値 2 件を除外）"
            "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件"
            "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2"
            "alpha  訓練 R²  検証 R²  係数の絶対値の合計"
            "  0.0  0.8827  0.7272  14.187"
            "  0.1  0.8827  0.7274  14.104"
            "  1.0  0.8823  0.7288  13.594"
            " 10.0  0.8681  0.7349  11.573"
            "100.0  0.6583  0.5985  5.684"
            "検証データで選んだ alpha: 10.0"
            "テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243"
            "ラッソ回帰（alpha=0.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2"]
           (str/split-lines (with-out-str (ch/run)))))))
