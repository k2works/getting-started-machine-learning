(ns getting-started-ml.chapter15.service-test
  "第 15 章: 予測サービスのテスト。偽物の置き場を渡すので、実データも学習も要らない。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter15.fakes :as fakes]
            [getting-started-ml.chapter15.service :as service]
            [getting-started-ml.chapter15.store-contract :as contract]))

(deftest 偽物の置き場も置き場の約束を満たす
  (contract/check (fakes/fake-store {:sales true :survival true})
                  (fakes/fake-store {:sales false :survival false})))

(deftest サービスは置き場からモデルを読んで予測する
  (let [store (fakes/fake-store {:sales true :survival true})]
    (is (= fakes/fixed-sales (service/predict-sales store contract/movie)))
    (is (true? (service/predict-survival store contract/passenger)))))

(deftest ヘルスチェックはモデルごとに読み込めるかどうかを返す
  (is (= {"cinema" true "survived" true}
         (service/health (fakes/fake-store {:sales true :survival true}))))
  (is (= {"cinema" true "survived" false}
         (service/health (fakes/fake-store {:sales true :survival false})))))

(deftest ヘルスチェックの並びはモデルの順のまま
  (is (= ["cinema" "survived"]
         (keys (service/health (fakes/fake-store {:sales true :survival true}))))))

(deftest モデルが無ければ予測は失敗する
  (let [store (fakes/fake-store {:sales false :survival false})]
    (is (thrown? clojure.lang.ExceptionInfo (service/predict-sales store contract/movie)))
    (is (thrown? clojure.lang.ExceptionInfo (service/predict-survival store contract/passenger)))))
