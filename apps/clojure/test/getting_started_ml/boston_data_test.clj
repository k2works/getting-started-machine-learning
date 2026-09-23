(ns getting-started-ml.boston-data-test
  "実データ（Boston.csv・bike.tsv・weather.csv）を使うテスト。学習データが無ければスキップする。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [getting-started-ml.chapter09 :as ch]
            [getting-started-ml.dataset :as dataset]))

(defn- data-file
  "学習データのファイルを返す。無ければ nil（テストはスキップする）。"
  [file-name]
  (let [path (str (dataset/dir) "/" file-name)]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println (str "学習データ " file-name " が配置されていない（gulp data:setup）のでスキップする"))
        nil))))

(defn- boston-split
  "前処理した訓練データとテストデータを返す。学習データが無ければ nil。"
  []
  (when-let [path (data-file "Boston.csv")]
    (ch/prepare-boston path 0.3 0)))

(defn- score
  "決定係数を小数 4 桁の文字列にする。"
  [{:keys [train test]}]
  [(format "%.4f" train) (format "%.4f" test)])

(deftest 実データを七十件と三十件に分けてダミー変数の列を加える
  (when-let [split (boston-split)]
    (is (= [70 30] [(count (:x-train split)) (count (:x-test split))]))
    (is (= ["ZN" "INDUS" "CHAS" "NOX" "RM" "AGE" "DIS" "RAD" "TAX" "PTRATIO" "B" "LSTAT"
            "CRIME_low" "CRIME_very_low"]
           (mapv name (:columns split))))
    (is (every? (fn [features] (every? some? (vals features))) (:x-train split)))))

(deftest 二乗の項を加えるとテストデータの決定係数が上がり交互作用の項では下がる
  ;; 8 つの数値は Java 版・Scala 版と一致する（分割も手順も同じため）
  (when-let [split (boston-split)]
    (let [[_ terms-squares] (second (ch/feature-sets))
          [_ terms-interactions] (nth (ch/feature-sets) 2)]
      (is (= ["0.6056" "0.6950"]
             (score (ch/score-feature-set split ch/columns-to-expand ch/columns-to-expand))))
      (is (= ["0.7740" "0.8628"]
             (score (ch/score-feature-set split ch/columns-to-expand terms-squares))))
      (is (= ["0.7953" "0.8213"]
             (score (ch/score-feature-set split ch/columns-to-expand terms-interactions)))))))

(deftest 訓練データの価格の外れ値は八件で除くとテストデータの決定係数が下がる
  (when-let [split (boston-split)]
    (is (= 8 (count (filter true? (ch/iqr-outliers (:t-train split))))))
    (is (= ["0.6717" "0.7947"]
           (score (ch/score-feature-set (ch/remove-target-outliers split)
                                        ch/columns-to-expand
                                        (second (second (ch/feature-sets)))))))))

(deftest ShiftJISの天気の表を結合して天気ごとの平均利用者数を求める
  (when-let [bike-path (data-file "bike.tsv")]
    (when-let [weather-path (data-file "weather.csv")]
      (let [joined (ch/join-weather (ch/load-delimited bike-path "UTF-8" \tab)
                                    (ch/load-delimited weather-path "Shift_JIS" \,))]
        (is (= 731 (count (:rows joined))))
        (is (= "晴れ=4876.8, 曇り=4052.7, 雨=1803.3"
               (str/join ", " (map (fn [[weather mean]] (str weather "=" (format "%.1f" mean)))
                                   (ch/mean-count-by-weather joined)))))))))
