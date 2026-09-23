(ns getting-started-ml.wholesale-data-test
  "実データ（Wholesale.csv）で K-means を試すテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter14 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- wholesale
  "支出額の列と特徴量を返す。学習データが無ければ nil。"
  []
  (let [path (str (dataset/dir) "/Wholesale.csv")]
    (if (.exists (java.io.File. path))
      (ch/load-spending path)
      (binding [*out* *err*]
        (println "学習データ Wholesale.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest 実データから四百四十件の支出額六列を読み込む
  (when-let [{:keys [columns x]} (wholesale)]
    (is (= 440 (count x)))
    (is (= ["Fresh" "Milk" "Grocery" "Frozen" "Detergents_Paper" "Delicassen"]
           (mapv name columns)))))

(deftest 標準化したデータのクラスタ数一のSSEは件数と列数の積になる
  (when-let [{:keys [columns x]} (wholesale)]
    ;; 件数で割る標準偏差で標準化したので、列ごとの分散は 1 になる
    (let [points (ch/standardize x columns)
          sse (second (first (ch/sse-by-cluster-count points [1] 0)))]
      (is (< (abs (- sse (* 440.0 6))) 1e-6)))))

(deftest クラスタ数を増やすほどSSEが小さくなる
  (when-let [{:keys [columns x]} (wholesale)]
    (let [sse (mapv second (ch/sse-by-cluster-count (ch/standardize x columns) (range 1 11) 0))]
      (is (every? true? (map < (rest sse) sse))))))

(deftest 実行するとSSEとクラスタごとの件数と平均支出額を表示する
  (when (wholesale)
    (is (= ["データ件数: 440（支出額 6 列）"
            "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:"
            "クラスタ数\t自作\tTribuo（k-means++）"
            "1\t2640.00\t2640.00"
            "2\t1954.18\t1954.78"
            "3\t1614.52\t1607.67"
            "4\t1334.36\t1317.90"
            "5\t1085.27\t1058.77"
            "6\t947.20\t917.67"
            "7\t888.22\t839.38"
            "8\t775.24\t742.02"
            "9\t690.81\t655.14"
            "10\t618.17\t606.81"
            ""
            "クラスタ数 5 のクラスタごとの件数と平均支出額:"
            "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen"
            "2\t265\t8909\t2967\t3804\t2248\t989\t962"
            "1\t96\t5509\t10556\t16478\t1420\t7199\t1659"
            "0\t65\t31117\t4260\t5374\t7225\t849\t2286"
            "3\t10\t15965\t34709\t48537\t3055\t24875\t2943"
            "4\t4\t52022\t31696\t18491\t29826\t2699\t19656"]
           (str/split-lines (with-out-str (ch/run)))))))
