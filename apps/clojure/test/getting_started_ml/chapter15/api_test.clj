(ns getting-started-ml.chapter15.api-test
  "第 15 章: 予測 API のテスト。ハンドラーは関数なので、サーバーを起動せずに直接呼ぶ。"
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter15.api :as api]
            [getting-started-ml.chapter15.domain :as domain]
            [getting-started-ml.chapter15.fakes :as fakes]))

(defn- request
  "Ring の要求のマップを作る。本文は入力ストリームで渡す（Jetty が渡すものと同じ形）。"
  ([method uri] {:request-method method :uri uri})
  ([method uri body]
   (assoc (request method uri)
          :body (java.io.ByteArrayInputStream. (.getBytes ^String body "UTF-8")))))

(defn- call
  "偽物の置き場を使うハンドラーを、1 回の要求で呼ぶ。"
  [store-options request-map]
  ((api/handler (fakes/fake-store store-options)) request-map))

(def ^:private ready {:sales true :survival true})

(deftest ヘルスチェック
  (testing "すべて読み込めれば ok を返す"
    (let [response (call ready (request :get "/health"))]
      (is (= 200 (:status response)))
      (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
      (is (= "{\"status\":\"ok\",\"models\":{\"cinema\":true,\"survived\":true}}" (:body response)))))
  (testing "読み込めないモデルがあれば degraded を返す"
    (is (= "{\"status\":\"degraded\",\"models\":{\"cinema\":true,\"survived\":false}}"
           (:body (call {:sales true :survival false} (request :get "/health")))))))

(deftest 興行収入を予測する
  (let [response (call ready (request :post "/cinema/sales"
                                      "{\"sns1\": 100, \"sns2\": 2000, \"actor\": 300, \"original\": 1}"))]
    (is (= 200 (:status response)))
    (is (= "{\"sales\":4321.5}" (:body response)))))

(deftest 生存を予測する
  (let [response (call ready (request :post "/survived"
                                      "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 80}"))]
    (is (= 200 (:status response)))
    (is (= "{\"survived\":true}" (:body response)))))

(deftest 不正な入力は四百二十二を返す
  (testing "検証の理由を並べて返す"
    (let [response (call ready (request :post "/cinema/sales"
                                        "{\"sns1\": -1, \"sns2\": 2000, \"actor\": 300, \"original\": 2}"))]
      (is (= 422 (:status response)))
      (is (= "{\"detail\":[\"sns1 は 0 以上にしてください\",\"original は 0、1 のどれかにしてください\"]}"
             (:body response)))))
  (testing "JSON として読めない入力も四百二十二にする"
    (let [response (call ready (request :post "/cinema/sales" "{\"sns1\": \"たくさん\"}"))]
      (is (= 422 (:status response)))
      (is (= "{\"detail\":[\"JSON の形式または値の型が正しくありません\"]}" (:body response))))))

(deftest モデルが無ければ五百三を返す
  (let [response (call {:sales false :survival false}
                       (request :post "/cinema/sales"
                                "{\"sns1\": 100, \"sns2\": 2000, \"actor\": 300, \"original\": 1}"))]
    (is (= 503 (:status response)))
    (is (= "{\"detail\":\"学習済みモデル cinema が見つかりません\"}" (:body response)))))

(deftest 予測が失敗すれば五百を返す
  ;; 置き場が約束と違う例外を投げる。応答には内部の事情を出さない
  (let [broken (reify domain/ModelStore
                 (load-sales-model [_] (throw (RuntimeException. "内部の秘密")))
                 (load-survival-model [_] (throw (RuntimeException. "内部の秘密"))))
        response ((api/handler broken)
                  (request :post "/cinema/sales"
                           "{\"sns1\": 100, \"sns2\": 2000, \"actor\": 300, \"original\": 1}"))]
    (is (= 500 (:status response)))
    (is (= "{\"detail\":\"予測できませんでした\"}" (:body response)))))

(deftest 知らないパスは四百四_許していないメソッドは四百五
  (let [not-found (call ready (request :get "/unknown"))
        not-allowed (call ready (request :get "/cinema/sales"))]
    (is (= 404 (:status not-found)))
    (is (= "{\"detail\":\"見つかりません\"}" (:body not-found)))
    (is (= 405 (:status not-allowed)))
    (is (= "POST" (get-in not-allowed [:headers "Allow"])))
    (is (= "{\"detail\":\"許していないメソッドです\"}" (:body not-allowed)))))
