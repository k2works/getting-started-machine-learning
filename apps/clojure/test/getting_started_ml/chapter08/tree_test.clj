(ns getting-started-ml.chapter08.tree-test
  "クラスの重みを付けた決定木のテスト。自作の小さなデータだけを使う。"
  (:require [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter03 :as chapter03]
            [getting-started-ml.chapter08.tree :as tree]))

(defn- near? [a b] (< (abs (- a b)) 1e-12))

(deftest 重み付きのジニ不純度は重みがすべて一なら第三章のジニ不純度と同じになる
  (let [labels [0 0 1 1 1]]
    (is (near? (chapter03/gini labels) (tree/weighted-gini labels (vec (repeat 5 1.0)))))))

(deftest 重み付きのジニ不純度はラベルが一種類なら零になる
  (is (near? 0.0 (tree/weighted-gini [1 1 1] [0.5 2.0 1.0]))))

(deftest 重みを変えると不純度が変わる
  ;; 少数派の 1 を重くすると、重みで見た割合が均等に近づくので不純度が上がる
  (is (< (tree/weighted-gini [0 0 0 1] [1.0 1.0 1.0 1.0])
         (tree/weighted-gini [0 0 0 1] [1.0 1.0 1.0 3.0]))))

(deftest balancedの重みはクラスの件数に反比例する
  ;; 4 件、クラスは 2 種類。0 が 3 件、1 が 1 件 → 4/(2*3) と 4/(2*1)
  (is (= [(/ 4.0 6) (/ 4.0 6) (/ 4.0 6) (/ 4.0 2)] (tree/balanced-weights [0 0 0 1]))))

(deftest 重みを付けないときの重みはすべて一になる
  (is (= [1.0 1.0 1.0] (tree/weights-of [0 1 0] :none))))

(deftest 重みの合計が大きいラベルを葉のラベルにする
  (is (= 1 (tree/weighted-majority [0 0 1] [1.0 1.0 3.0])))
  (is (= 0 (tree/weighted-majority [0 0 1] [1.0 1.0 1.0]))))

(deftest 重みの合計が同じなら先に現れたラベルを選ぶ
  (is (= 0 (tree/weighted-majority [0 1] [1.0 1.0]))))

(def ^:private x
  [{:a 1.0 :b 0.0} {:a 2.0 :b 0.0} {:a 3.0 :b 0.0} {:a 4.0 :b 0.0}])

(def ^:private t [0 0 0 1])

(deftest 重み付けなしなら第三章の決定木と同じ木になる
  (let [ours (tree/fit x t [:a :b] 2 :none)
        theirs (chapter03/fit x (mapv str t) [:a :b] 2)]
    (is (= (mapv str (tree/predict ours x)) (chapter03/predict theirs x)))))

(deftest 深さの上限が零なら葉だけの木になる
  (is (= {:label 0} (tree/fit x t [:a :b] 0 :none))))

(deftest balancedにすると少数派のラベルを予測しやすくなる
  ;; 1 が 2 件、0 が 5 件。深さ 1 の木では、重みを付けないとどの葉も多数派の 0 になる
  (let [x2 (mapv #(hash-map :a (double %)) (range 1 8))
        t2 [0 0 1 0 1 0 0]]
    (is (= [0 0 0 0 0 0 0] (tree/predict (tree/fit x2 t2 [:a] 1 :none) x2)))
    (is (= [1 1 1 1 1 0 0] (tree/predict (tree/fit x2 t2 [:a] 1 :balanced) x2)))))
