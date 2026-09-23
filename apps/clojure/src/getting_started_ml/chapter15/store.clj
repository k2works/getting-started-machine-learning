(ns getting-started-ml.chapter15.store
  "第 15 章のインフラ層。学習済みモデルをディレクトリのファイルに EDN で保存し、読み込む。

   第 8 章で、学習済みのパイプラインが pr-str と clojure.edn/read-string の往復で戻ることを
   確かめてある。第 7 章の線形回帰のモデルもマップとベクタなので、同じやり方で保存できる。"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [getting-started-ml.chapter08 :as chapter08]
            [getting-started-ml.chapter15.domain :as domain]))

(defn- model-file
  "モデルの保存先のファイル。"
  [model-dir model]
  (str (io/file model-dir (str model ".edn"))))

(defn- read-model
  "ファイルを読む。無ければ「モデルが無い」に変える。"
  [model-dir model read-fn]
  (let [file (model-file model-dir model)]
    (when-not (.exists (io/file file))
      (throw (domain/model-not-found model)))
    (read-fn file)))

(defrecord FileModelStore [model-dir]
  domain/ModelStore
  (load-sales-model [_]
    (domain/linear-sales-model
     (read-model model-dir domain/sales-model #(edn/read-string (slurp %)))))
  (load-survival-model [_]
    (domain/pipeline-survival-model
     (read-model model-dir domain/survival-model chapter08/load-model))))

(defn file-model-store
  "ディレクトリを置き場にする。ディレクトリはまだ無くてもよい。"
  [model-dir]
  (->FileModelStore model-dir))

(defn save-sales-model
  "第 7 章の線形回帰のモデルを EDN で保存する。"
  [store model]
  (let [file (model-file (:model-dir store) domain/sales-model)]
    (io/make-parents file)
    (spit file (pr-str model))))

(defn save-survival-model
  "第 8 章の学習済みパイプラインを、第 8 章の save-model で保存する。"
  [store pipeline]
  (chapter08/save-model pipeline (model-file (:model-dir store) domain/survival-model)))
