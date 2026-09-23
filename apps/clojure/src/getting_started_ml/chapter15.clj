(ns getting-started-ml.chapter15
  "第 15 章: 機械学習 API とモジュール設計。第 7・8 章のモデルを学習して保存し、予測 API を起動する。"
  (:require [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter08 :as chapter08]
            [getting-started-ml.chapter15.api :as api]
            [getting-started-ml.chapter15.service :as service]
            [getting-started-ml.chapter15.store :as store]
            [getting-started-ml.dataset :as dataset]
            [ring.adapter.jetty :as jetty]))

(def model-dir
  "学習済みモデルの保存先（apps/clojure/model/ は .gitignore の対象）。"
  "model")

(def port
  "予測 API のポート。"
  8015)

(def ^:private host
  "自分のマシンからだけ接続できるループバックのアドレス。"
  "127.0.0.1")

(def ^:private test-size 0.2)
(def ^:private seed 0)
(def ^:private max-depth 5)

(defn- survival-pipeline
  "第 8 章と同じ条件で、生存予測のパイプラインを学習する。"
  [csv-file]
  (let [rows (:rows (chapter02/load-table csv-file))
        t (chapter08/target-labels rows)
        split (chapter02/split-train-test rows t test-size seed)]
    (chapter08/fit (chapter08/build-pipeline max-depth :balanced)
                   (chapter08/features-table (:x-train split))
                   (:t-train split))))

(defn train-and-save-models
  "第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。"
  [data-dir store]
  (let [cinema (chapter07/prepare-cinema (str data-dir "/cinema.csv") test-size seed)]
    (store/save-sales-model store (chapter07/fit (:x-train cinema) (:t-train cinema)
                                                 chapter07/feature-columns)))
  (store/save-survival-model store (survival-pipeline (str data-dir "/Survived.csv"))))

(defn run
  "モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。"
  ([] (run model-dir))
  ([model-dir]
   (let [store (store/file-model-store model-dir)]
     (train-and-save-models (dataset/dir) store)
     (doseq [[model ready] (service/health store)]
       (println (str "モデル " model ": " ready)))
     (println (str "http://" host ":" port " で待ち受けます"))
     (flush)
     (jetty/run-jetty (api/handler store) {:host host :port port :join? true}))))
