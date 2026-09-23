(ns getting-started-ml.chapter15-test
  "実データで学習したモデルを置き場に保存し、API から予測するテスト。学習データが無ければスキップする。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter15 :as ch]
            [getting-started-ml.chapter15.api :as api]
            [getting-started-ml.chapter15.service :as service]
            [getting-started-ml.chapter15.store :as store]
            [getting-started-ml.chapter15.store-contract :as contract]
            [getting-started-ml.dataset :as dataset]))

(defn- trained-store
  "実データで学習したモデルを保存した置き場を返す。学習データが無ければ nil。"
  []
  (let [dir (dataset/dir)]
    (if (every? #(.exists (java.io.File. (str dir "/" %))) ["cinema.csv" "Survived.csv"])
      (let [store (store/file-model-store
                   (str (java.io.File. (System/getProperty "java.io.tmpdir")
                                       (str "chapter15-trained-" (System/nanoTime)))))]
        (ch/train-and-save-models dir store)
        store)
      (binding [*out* *err*]
        (println "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest 実データで学習したモデルを保存して予測できる
  (when-let [store (trained-store)]
    ;; Java 版・Scala 版と同じ分割・同じ手順なので、予測値も一致する
    (is (< (abs (- 7730.457421687023 (service/predict-sales store contract/movie))) 1e-6))
    (is (true? (service/predict-survival store contract/passenger)))))

(deftest 実データで学習したモデルを使う_API_が予測を返す
  (when-let [store (trained-store)]
    (let [handler (api/handler store)
          body (fn [request] (:body (handler request)))
          json (fn [uri text]
                 {:request-method :post :uri uri
                  :body (java.io.ByteArrayInputStream. (.getBytes ^String text "UTF-8"))})]
      (is (= "{\"status\":\"ok\",\"models\":{\"cinema\":true,\"survived\":true}}"
             (body {:request-method :get :uri "/health"})))
      (is (= "{\"sales\":7730.457421687023}"
             (body (json "/cinema/sales"
                         "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"))))
      (is (= "{\"survived\":true}"
             (body (json "/survived"
                         "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 50}")))))))
