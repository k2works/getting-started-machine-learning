(ns getting-started-ml.boston-pca-data-test
  "実データ（Boston.csv）で主成分分析するテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter07.matrix :as matrix]
            [getting-started-ml.chapter13 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- boston
  "前処理した特徴量と列名を返す。学習データが無ければ nil。"
  []
  (let [path (str (dataset/dir) "/Boston.csv")]
    (if (.exists (java.io.File. path))
      (ch/load-boston path)
      (binding [*out* *err*]
        (println "学習データ Boston.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest CRIMEをダミー変数にして百件十五列の標準化済みデータにする
  (when-let [{:keys [columns x]} (boston)]
    (is (= 100 (count x)))
    (is (= 15 (count columns)))
    (is (= ["ZN" "INDUS" "CHAS" "NOX" "RM" "AGE" "DIS" "RAD" "TAX" "PTRATIO" "B" "LSTAT"
            "PRICE" "CRIME_low" "CRIME_very_low"]
           (mapv name columns)))))

(deftest 実データの主成分も分散共分散行列の固有ベクトルになる
  (when-let [{:keys [columns x]} (boston)]
    (let [m (ch/to-matrix x columns)
          {:keys [components explained-variance explained-variance-ratio]} (ch/fit m (count columns))
          c (ch/covariance-matrix m)]
      (is (< (abs (- 1.0 (reduce + explained-variance-ratio))) 1e-9))
      (doseq [i (range 3)]
        (let [v (components i)
              multiplied (first (matrix/transpose (matrix/multiply c (matrix/column-vector v))))]
          (is (every? #(< (abs %) 1e-9)
                      (map (fn [a b] (- a (* (explained-variance i) b))) multiplied v))))))))

(deftest 実行すると寄与率と主成分の解釈を表示する
  (when (boston)
    (is (= ["データ件数: 100, 列数: 15"
            "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581"
            "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）"
            "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328"
            "第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405"]
           (str/split-lines (with-out-str (ch/run)))))))
