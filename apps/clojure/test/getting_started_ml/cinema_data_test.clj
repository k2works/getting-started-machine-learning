(ns getting-started-ml.cinema-data-test
  "実データ（cinema.csv）で線形回帰を学習するテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter07 :as ch]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.chapter07.tribuo :as tribuo]
            [getting-started-ml.dataset :as dataset])
  (:import [org.tribuo.regression.slm LARSTrainer SLMTrainer]))

(def ^:private csv-file (delay (str (dataset/dir) "/cinema.csv")))

(defn- data?
  "学習データがあるかどうかを返す。無ければ理由を標準エラーに出す。"
  []
  (or (.exists (java.io.File. ^String @csv-file))
      (binding [*out* *err*]
        (println "学習データ cinema.csv が配置されていない（gulp data:setup）のでスキップする")
        false)))

(deftest 実データから外れ値を一件取り除く
  (when (data?)
    (let [table (chapter02/load-table @csv-file)]
      (is (= 100 (count (:rows table))))
      (is (= 99 (count (:rows (ch/remove-outliers table))))))))

(deftest 実データで自作のモデルとTribuoのSLMTrainerとLARSTrainerのR2が一致する
  (when (data?)
    (let [split (ch/prepare-cinema @csv-file 0.2 0)
          columns ch/feature-columns
          mine (metrics/r2-score (:t-test split)
                                 (ch/predict (ch/fit (:x-train split) (:t-train split) columns)
                                             (:x-test split)))]
      (doseq [trainer [(SLMTrainer. true) (LARSTrainer.)]]
        (let [model (tribuo/train trainer (:x-train split) (:t-train split) columns)
              theirs (metrics/r2-score (:t-test split)
                                       (tribuo/predict model (:x-test split) columns))]
          (is (< (abs (- theirs mine)) 1e-9) (str (.getSimpleName (class trainer)))))))))

(deftest 実行すると学習した係数と評価指標を表示する
  (when (data?)
    (is (= ["データ件数: 100"
            "外れ値を除いた件数: 99"
            "訓練データ: 79 件, テストデータ: 20 件"
            "切片: 6114.60"
            "係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827"
            "テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14"]
           (str/split-lines (with-out-str (ch/run)))))))
