(ns getting-started-ml.chapter15.api
  "第 15 章のプレゼンテーション層。Ring の予測 API。

   Ring では、ハンドラーは「要求のマップを受け取って応答のマップを返す関数」である。
   フレームワークの型も注釈も無いので、テストはこの関数を直に呼べばよく、サーバーを起動しなくてよい。
   経路の振り分けのライブラリ（reitit・compojure）は使わず、:request-method と :uri の組で分ける。"
  (:require [cheshire.core :as json]
            [getting-started-ml.chapter15.domain :as domain]
            [getting-started-ml.chapter15.service :as service]
            [getting-started-ml.chapter15.validation :as validation]))

(def ^:private invalid-json "JSON の形式または値の型が正しくありません")

(def ^:private allowed-methods
  "パスごとに許しているメソッド。知っているパスに違うメソッドが来たら 405 にする。"
  {"/health" "GET" "/cinema/sales" "POST" "/survived" "POST"})

(defn- json-response
  "JSON の応答のマップを作る。"
  ([status body] (json-response status body {}))
  ([status body headers]
   {:status status
    :headers (assoc headers "Content-Type" "application/json; charset=utf-8")
    :body (json/generate-string body)}))

(defn- rejected
  "検証に落ちた要求の応答。"
  [errors]
  (json-response 422 {:detail errors}))

(defn- predict
  "本文を読んで検証し、正しければ f で予測する。失敗はステータスコードに変える。"
  [request types validate-request f]
  (try
    (let [parsed (validation/read-json (slurp (:body request)) types)]
      (if (nil? parsed)
        (rejected [invalid-json])
        (let [validated (validate-request parsed)]
          (if (validation/valid? validated)
            (json-response 200 (f (:value validated)))
            (rejected (:errors validated))))))
    (catch clojure.lang.ExceptionInfo e
      (if (domain/model-not-found? e)
        (json-response 503 {:detail (ex-message e)})
        (json-response 500 {:detail "予測できませんでした"})))
    ;; 例外のメッセージには内部の事情が入るので、応答には出さない
    (catch Exception _ (json-response 500 {:detail "予測できませんでした"}))))

(defn- health
  "モデルを読み込めるかどうかを返す。1 つでも読み込めなければ degraded。"
  [store]
  (let [models (service/health store)]
    (json-response 200 {:status (if (every? true? (vals models)) "ok" "degraded")
                        :models models})))

(defn handler
  "置き場を使う Ring のハンドラーを作る。返るのは要求のマップを受け取る関数。"
  [store]
  (fn [request]
    (let [method (:request-method request)
          uri (:uri request)]
      (cond
        (= [:get "/health"] [method uri])
        (health store)

        (= [:post "/cinema/sales"] [method uri])
        (predict request validation/movie-types validation/movie
                 #(hash-map :sales (service/predict-sales store %)))

        (= [:post "/survived"] [method uri])
        (predict request validation/passenger-types validation/passenger
                 #(hash-map :survived (service/predict-survival store %)))

        (contains? allowed-methods uri)
        (json-response 405 {:detail "許していないメソッドです"}
                       {"Allow" (get allowed-methods uri)})

        :else
        (json-response 404 {:detail "見つかりません"})))))
