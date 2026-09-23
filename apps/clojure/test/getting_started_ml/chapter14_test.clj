(ns getting-started-ml.chapter14-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter14 :as ch])
  (:import [org.tribuo.clustering.kmeans KMeansTrainer KMeansTrainer$Initialisation]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-9))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(defn- two-groups
  "0 の近くと 10 の近くに、はっきり分かれた 2 グループ。"
  []
  [[0.0 0.0] [0.0 1.0] [1.0 0.0] [10.0 10.0] [10.0 11.0] [11.0 10.0]])

(defn- two-pairs
  "1 次元に 2 組の点を並べた、クラスタ数がはっきり決まるデータ。"
  []
  [[0.0] [2.0] [10.0] [12.0]])

(defn- three-pairs
  "1 次元に 3 組の点を並べたデータ。"
  []
  [[0.0] [1.0] [10.0] [11.0] [20.0] [21.0]])

;; ## 割り当てと中心の更新

(deftest 各点を最も近い中心のクラスタに割り当てる
  (testing "1 次元の点"
    (is (= [0 0 1 1] (ch/assign-clusters [[0.0] [1.0] [10.0] [11.0]] [[0.5] [10.5]]))))
  (testing "2 次元の点をユークリッド距離で割り当てる"
    (is (= [0 0 0 1 1 1] (ch/assign-clusters (two-groups) [[0.0 0.0] [10.0 10.0]]))))
  (testing "距離が同じなら先に並ぶ中心を選ぶ"
    (is (= [0] (ch/assign-clusters [[1.0]] [[0.0] [2.0]])))))

(deftest 距離の二乗を求める
  (is (close-to? 25.0 (ch/squared-distance [0.0 0.0] [3.0 4.0]))))

(deftest クラスタごとに割り当てられた点の平均を新しい中心にする
  (testing "平均が新しい中心になる"
    (is (= [[0.5] [10.5]]
           (ch/update-centers [[0.0] [1.0] [10.0] [11.0]] [0 0 1 1] [[0.0] [10.0]]))))
  (testing "点が 1 つも割り当てられなかったクラスタは中心を変えない"
    (is (= [[0.5] [99.0]]
           (ch/update-centers [[0.0] [1.0]] [0 0] [[0.0] [99.0]])))))

;; ## 誤差平方和

(deftest 各点と所属するクラスタの中心との距離の二乗を合計する
  (testing "中心にぴったり重なれば 0"
    (is (close-to? 0.0 (ch/sum-of-squared-errors [[1.0] [2.0]] [0 1] [[1.0] [2.0]]))))
  (testing "中心から離れた点ほど誤差が大きくなる"
    (is (close-to? 0.5 (ch/sum-of-squared-errors [[0.0] [1.0]] [0 0] [[0.5]])))
    (is (close-to? 4.0 (ch/sum-of-squared-errors [[-1.0] [1.0]] [0 0] [[1.0]])))))

;; ## 収束まで繰り返す

(deftest 割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す
  (let [result (ch/fit (two-groups) [[0.0 0.0] [1.0 1.0]])]
    (is (= [0 0 0 1 1 1] (:labels result)))
    (is (= [[(/ 1.0 3) (/ 1.0 3)] [(/ 31.0 3) (/ 31.0 3)]]
           (mapv #(mapv double %) (:centers result))))))

(deftest 最大反復回数に達したら収束していなくても打ち切る
  (let [once (ch/fit (three-pairs) [[0.0] [2.0]] 1)
        none (ch/fit (three-pairs) [[0.0] [2.0]] 0)]
    (is (= [[0.5] [15.5]] (mapv #(mapv double %) (:centers once))))
    (is (= [[0.0] [2.0]] (:centers none)))
    (is (> (:sse none) (:sse once)))))

;; ## 初期中心

(deftest データの中から重複なくクラスタ数だけ点を選ぶ
  (let [centers (ch/choose-initial-centers (three-pairs) 3 0)]
    (is (= 3 (count centers)))
    (is (= 3 (count (distinct centers))))
    (is (every? (set (three-pairs)) centers))))

(deftest 同じシードなら同じ点を選びシードが違えば違う点を選ぶ
  (is (= (ch/choose-initial-centers (three-pairs) 3 0)
         (ch/choose-initial-centers (three-pairs) 3 0)))
  (is (not= (ch/choose-initial-centers (three-pairs) 3 0)
            (ch/choose-initial-centers (three-pairs) 3 7))))

;; ## エルボー法と局所解

(deftest クラスタ数ごとにクラスタリングしたときのSSEを求める
  (is (= [[1 104.0] [2 4.0]] (ch/sse-by-cluster-count (two-pairs) [1 2] 0))))

(deftest 初期中心によっては局所解に陥る
  (is (close-to? 101.0 (:sse (ch/fit (three-pairs) [[0.0] [1.0] [10.0]])))))

(deftest 複数の初期中心の候補のうちSSEが最小の結果を返す
  (let [result (ch/best (three-pairs) [[[0.0] [1.0] [10.0]] [[0.0] [10.0] [20.0]]])]
    (is (close-to? 1.5 (:sse result)))
    (is (= [[0.5] [10.5] [20.5]] (mapv #(mapv double %) (:centers result))))))

(deftest 初期中心を変えて繰り返し最小のSSEを使う
  (is (close-to? 1.5 (:sse (ch/fit-with-restarts (three-pairs) 3 0 10)))))

;; ## Tribuo

(deftest KMeansTrainerの初期化方法はRANDOMとPLUSPLUSだけ
  (is (= ["RANDOM" "PLUSPLUS"] (mapv #(.name %) (KMeansTrainer$Initialisation/values))))
  (is (not-any? (fn [constructor]
                  (some #(or (.isArray ^Class %)
                             (.isAssignableFrom java.util.Collection ^Class %))
                        (.getParameterTypes constructor)))
                (.getConstructors KMeansTrainer))))

(deftest はっきり分かれた二グループならTribuoのSSEも自作と同じになる
  (is (close-to? (:sse (ch/fit-with-restarts (two-groups) 2 0 3))
                 (ch/tribuo-best-sse (two-groups) 2 0 3))))

;; ## 支出額

(defn- spending-table
  "Wholesale.csv に似た小さな表。"
  []
  {:columns [:Channel :Region :Fresh :Milk]
   :rows [{:Channel "1" :Region "3" :Fresh "100" :Milk "200"}
          {:Channel "2" :Region "3" :Fresh "300" :Milk "400"}
          {:Channel "1" :Region "1" :Fresh "500" :Milk "600"}]})

(deftest ChannelとRegionを除いた支出額の列を読み込む
  (let [{:keys [columns x]} (ch/spending (spending-table))]
    (is (= [:Fresh :Milk] columns))
    (is (= [100.0 300.0 500.0] (mapv :Fresh x)))))

(deftest 列ごとに平均零と標準偏差一の点に変換する
  (let [{:keys [columns x]} (ch/spending (spending-table))
        points (ch/standardize x columns)]
    (is (= 3 (count points)))
    (doseq [j (range 2)]
      (let [values (mapv #(nth % j) points)
            mean (/ (reduce + values) (count values))]
        (is (close-to? 0.0 mean))
        (is (close-to? 1.0 (Math/sqrt (/ (reduce + (map #(* % %) values)) (count values)))))))))

(deftest クラスタごとの件数と平均を件数の多い順に並べる
  (let [{:keys [columns x]} (ch/spending (spending-table))
        summaries (ch/summarize-clusters x columns [1 0 1])]
    (is (= [1 0] (mapv :cluster summaries)))
    (is (= [2 1] (mapv :count summaries)))
    (is (close-to? 300.0 (get-in (first summaries) [:means :Fresh])))))
