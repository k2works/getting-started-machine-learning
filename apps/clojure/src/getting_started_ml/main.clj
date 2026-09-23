(ns getting-started-ml.main
  "章を選んで実行する。使い方: clojure -M:run chapter01"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter01 :as chapter01]
            [getting-started-ml.chapter02 :as chapter02]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.chapter07 :as chapter07]
            [getting-started-ml.chapter08 :as chapter08]
            [getting-started-ml.chapter09 :as chapter09]
            [getting-started-ml.chapter10 :as chapter10]
            [getting-started-ml.chapter11 :as chapter11]
            [getting-started-ml.chapter12 :as chapter12]))

(def ^:private chapters
  {"chapter01" chapter01/run
   "chapter02" chapter02/run
   "chapter03" chapter03/run
   "chapter07" chapter07/run
   "chapter08" chapter08/run
   "chapter09" chapter09/run
   "chapter10" chapter10/run
   "chapter11" chapter11/run
   "chapter12" chapter12/run})

(defn -main
  "章の名前を受け取り、その章を実行する。"
  [& args]
  (if-let [run (get chapters (first args))]
    (run)
    (binding [*out* *err*]
      (println (str "使い方: clojure -M:run <章>（章: " (str/join ", " (sort (keys chapters))) "）"))
      (System/exit 1))))
