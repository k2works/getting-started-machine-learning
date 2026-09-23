(ns getting-started-ml.chapter15.fakes
  "第 15 章のテストで使う偽物。実データも学習も使わずに API とサービスを確かめる。"
  (:require [getting-started-ml.chapter15.domain :as domain]))

(def fixed-sales
  "偽物の置き場が返す興行収入。"
  4321.5)

(defn fake-store
  "モデルがあるかどうかを差し替えられる置き場。reify で約束をその場で満たす。"
  [{:keys [sales survival]}]
  (reify domain/ModelStore
    (load-sales-model [_]
      (if sales
        (fn [_movie] fixed-sales)
        (throw (domain/model-not-found domain/sales-model))))
    (load-survival-model [_]
      (if survival
        (fn [_passenger] true)
        (throw (domain/model-not-found domain/survival-model))))))
