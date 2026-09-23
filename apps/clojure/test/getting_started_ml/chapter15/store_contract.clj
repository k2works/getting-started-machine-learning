(ns getting-started-ml.chapter15.store-contract
  "モデルの置き場の約束を、テストとして書いたもの。Java 版の interface ModelStore に当たる。

   defprotocol は「メソッドの名前と引数の数」しか決めないので、
   「無ければ ModelNotFound を投げる」という取り決めは型では書けない。
   本物の置き場とテスト用の偽物の両方にこの関数を呼ぶことで、同じ約束を確かめる。"
  (:require [clojure.test :refer [is testing]]
            [getting-started-ml.chapter15.domain :as domain]))

(def movie
  "約束の確認に使う映画の特徴量。"
  {:sns1 200.0 :sns2 500.0 :actor 3000.0 :original 1})

(def passenger
  "約束の確認に使う乗客の特徴量。年齢と乗船港は分からない。"
  {:pclass 1 :sex "female" :age nil :sib-sp 0 :parch 0 :fare 50.0 :embarked nil})

(defn check
  "置き場の約束を確かめる。with はモデルを 2 つ読み込める置き場、without はどちらも無い置き場。"
  [with without]
  (testing "約束: モデルがあれば予測する関数を返す"
    (is (satisfies? domain/ModelStore with))
    (is (number? ((domain/load-sales-model with) movie)))
    (is (contains? #{true false} ((domain/load-survival-model with) passenger))))
  (testing "約束: モデルが無ければ ModelNotFound を投げる"
    (doseq [[load model] [[domain/load-sales-model domain/sales-model]
                          [domain/load-survival-model domain/survival-model]]]
      (let [thrown (try (load without) (catch clojure.lang.ExceptionInfo e e))]
        (is (instance? clojure.lang.ExceptionInfo thrown) model)
        (is (domain/model-not-found? thrown) model)
        (is (= model (:model (ex-data thrown))))))))
