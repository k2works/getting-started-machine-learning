(ns getting-started-ml.survived-data-test
  "実データ（Survived.csv）でパイプラインを学習するテスト。学習データが無ければスキップする。"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.chapter08 :as ch]
            [getting-started-ml.dataset :as dataset]))

(def ^:private csv-file (delay (str (dataset/dir) "/Survived.csv")))

(defn- data?
  "学習データがあるかどうかを返す。無ければ理由を標準エラーに出す。"
  []
  (or (.exists (java.io.File. ^String @csv-file))
      (binding [*out* *err*]
        (println "学習データ Survived.csv が配置されていない（gulp data:setup）のでスキップする")
        false)))

(defn- survived-split
  "第 2 章と同じ手順で分割した訓練データとテストデータを返す。"
  []
  (let [rows (:rows (chapter02/load-table @csv-file))]
    (chapter02/split-train-test rows (ch/target-labels rows) 0.2 0)))

(defn- fit-pipeline
  [split max-depth class-weight]
  (ch/fit (ch/build-pipeline max-depth class-weight)
          (ch/features-table (:x-train split)) (:t-train split)))

(deftest 深さ二ではbalancedにすると見つけられる生存者が四十一人から六十八人に増える
  (when (data?)
    (let [split (survived-split)]
      (is (= 41 (:found-survivors (ch/evaluate (fit-pipeline split 2 :none) split))))
      (is (= 68 (:found-survivors (ch/evaluate (fit-pipeline split 2 :balanced) split)))))))

(deftest 重み付けなしなら前処理後のテストデータで第三章の決定木と予測が一致する
  (when (data?)
    (let [split (survived-split)]
      (doseq [max-depth (range 1 11)]
        (let [pipeline (fit-pipeline split max-depth :none)
              x-train (ch/features pipeline (ch/features-table (:x-train split)))
              x-test (ch/features pipeline (ch/features-table (:x-test split)))
              theirs (chapter03/predict
                      (chapter03/fit x-train (mapv str (:t-train split))
                                     (:columns pipeline) max-depth)
                      x-test)]
          (is (= (mapv str (ch/predict pipeline (ch/features-table (:x-test split)))) theirs)
              (str "深さ " max-depth)))))))

(deftest TribuoのCARTと予測が違うのは深さ五で二件と深さ九で一件だけ
  (when (data?)
    (let [split (survived-split)]
      (is (= [0 0 0 0 2 0 0 0 1 0]
             (mapv (fn [max-depth]
                     (let [pipeline (fit-pipeline split max-depth :none)
                           x-train (ch/features pipeline (ch/features-table (:x-train split)))
                           x-test (ch/features pipeline (ch/features-table (:x-test split)))
                           ours (mapv str (ch/predict pipeline
                                                      (ch/features-table (:x-test split))))
                           theirs (chapter03/tribuo-predict x-train (mapv str (:t-train split))
                                                            x-test (:columns pipeline) max-depth)]
                       (count (remove true? (map = ours theirs)))))
                   (range 1 11)))))))

(deftest 実行すると重みごとの評価と保存したモデルの予測を表示する
  (when (data?)
    (let [model-file (io/file (System/getProperty "java.io.tmpdir")
                              (str "survived-" (System/nanoTime) ".model"))]
      (try
        (is (= ["データ件数: 891（生存 342, 死亡 549）"
                "訓練データ: 712 件, テストデータ: 179 件"
                "classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見"
                "classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見"
                (str "保存したモデル: " (.getName model-file))
                "架空の乗客の予測: [1 0]"]
               (str/split-lines (with-out-str (ch/run model-file)))))
        (finally (io/delete-file model-file true))))))
