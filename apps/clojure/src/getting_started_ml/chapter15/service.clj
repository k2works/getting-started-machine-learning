(ns getting-started-ml.chapter15.service
  "第 15 章のアプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。

   置き場は ModelStore の約束を満たすものなら何でもよいので、テストでは reify の偽物を渡せる。
   状態を持たないので、同時に走るハンドラーから呼ばれても困らない。"
  (:require [getting-started-ml.chapter15.domain :as domain]))

(defn predict-sales
  "映画の特徴量から興行収入を予測する。"
  [store movie]
  ((domain/load-sales-model store) movie))

(defn predict-survival
  "乗客の特徴量から生存するかどうかを予測する。"
  [store passenger]
  ((domain/load-survival-model store) passenger))

(defn- ready?
  "モデルを読み込めるかどうか。読み込めない理由がほかにあれば、そのまま投げる。"
  [load store]
  (try
    (load store)
    true
    (catch clojure.lang.ExceptionInfo e
      (if (domain/model-not-found? e) false (throw e)))))

(defn health
  "モデルごとに、読み込めるかどうかを返す。
   マップは 9 要素以上で順を保たないが、ここは 2 つなので array-map で並びを固定できる。"
  [store]
  (array-map domain/sales-model (ready? domain/load-sales-model store)
             domain/survival-model (ready? domain/load-survival-model store)))
