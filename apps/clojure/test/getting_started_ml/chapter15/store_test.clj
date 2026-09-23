(ns getting-started-ml.chapter15.store-test
  "第 15 章: 学習済みモデルの保存と読み込みのテスト。手で作った小さなモデルを使う。"
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter08 :as chapter08]
            [getting-started-ml.chapter15.domain :as domain]
            [getting-started-ml.chapter15.store :as store]
            [getting-started-ml.chapter15.store-contract :as contract]))

(defn- temp-dir
  "テスト用の一時ディレクトリのパス。"
  [name]
  (str (java.io.File. (System/getProperty "java.io.tmpdir")
                      (str "chapter15-" name "-" (System/nanoTime)))))

(def ^:private sales-model
  "切片 10、係数 1・2・3・4 の線形回帰のモデル。"
  (chapter07/model 10.0 chapter07/feature-columns [1.0 2.0 3.0 4.0]))

(def ^:private survival-rows
  "女性が生存し、男性が死亡する 4 件の作り物のデータ。"
  [{:Pclass "1" :Sex "female" :Age "30" :SibSp "0" :Parch "0" :Fare "80" :Embarked "C"}
   {:Pclass "3" :Sex "male" :Age "40" :SibSp "0" :Parch "0" :Fare "8" :Embarked "S"}
   {:Pclass "2" :Sex "female" :Age "20" :SibSp "1" :Parch "0" :Fare "30" :Embarked "S"}
   {:Pclass "3" :Sex "male" :Age "25" :SibSp "0" :Parch "0" :Fare "10" :Embarked "S"}])

(defn- survival-pipeline []
  (chapter08/fit (chapter08/build-pipeline 2 :balanced)
                 (chapter08/features-table survival-rows)
                 [1 0 1 0]))

(defn- store-with-models []
  (let [store (store/file-model-store (temp-dir "with"))]
    (store/save-sales-model store sales-model)
    (store/save-survival-model store (survival-pipeline))
    store))

(defn- store-without-models []
  (store/file-model-store (temp-dir "without")))

(deftest ファイルの置き場は置き場の約束を満たす
  (contract/check (store-with-models) (store-without-models)))

(deftest 保存した線形回帰で予測できる
  ;; 10 + 1×200 + 2×500 + 3×3000 + 4×1
  (is (< (abs (- 10214.0 ((domain/load-sales-model (store-with-models)) contract/movie))) 1e-9)))

(deftest 保存したパイプラインで予測できる
  (is (true? ((domain/load-survival-model (store-with-models)) contract/passenger))))

(deftest 保存先のディレクトリは無ければ作られる
  (let [dir (temp-dir "makes-parents")]
    (store/save-sales-model (store/file-model-store dir) sales-model)
    (is (.exists (io/file dir)))))
