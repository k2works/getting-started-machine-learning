(ns getting-started-ml.chapter09-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter09 :as ch])
  (:import [java.io File]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-12))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(defn- rm-lstat
  "RM と LSTAT の 2 列の特徴量を作る。"
  [rm lstat]
  {:RM (double rm) :LSTAT (double lstat)})

(def ^:private rm-lstat-columns [:RM :LSTAT])

(defn- temp-file
  "一時ファイルに文字コードを指定して書き出す。"
  [name content charset]
  (let [file (File/createTempFile name ".tmp")]
    (.deleteOnExit file)
    (with-open [writer (java.io.OutputStreamWriter. (io/output-stream file) (str charset))]
      (.write writer ^String content))
    (.getPath file)))

(deftest カテゴリ値をダミー変数にする
  (testing "先頭を除いたカテゴリを辞書順に返す"
    (is (= ["low" "very_low"] (ch/categories ["low" "high" "very_low" "low"]))))
  (testing "欠損値は数えない"
    (is (= ["low"] (ch/categories ["low" "" "high"]))))
  (testing "カテゴリごとに零と一の列を作り元の列を取り除く"
    (let [table {:columns [:RM :CRIME]
                 :rows [{:RM "6.0" :CRIME "low"} {:RM "6.0" :CRIME "high"}
                        {:RM "6.0" :CRIME "very_low"}]}
          encoded (ch/encode table :CRIME ["low" "very_low"])]
      (is (= [:RM :CRIME_low :CRIME_very_low] (:columns encoded)))
      (is (= ["1" "0" "0"] (mapv :CRIME_low (:rows encoded))))
      (is (= ["0" "0" "1"] (mapv :CRIME_very_low (:rows encoded))))
      (is (every? #(not (contains? % :CRIME)) (:rows encoded)))))
  (testing "カテゴリに無い値はすべての列が零になる"
    (let [encoded (ch/encode {:columns [:CRIME] :rows [{:CRIME "medium"}]} :CRIME ["low"])]
      (is (= ["0"] (mapv :CRIME_low (:rows encoded)))))))

(deftest 特徴量の標準化
  (testing "訓練データから列ごとの平均と標準偏差を求める"
    (let [std (ch/standardizer [(rm-lstat 1 10) (rm-lstat 2 10) (rm-lstat 3 40)]
                               rm-lstat-columns)]
      (is (close-to? 2.0 (get-in std [:means :RM])))
      (is (close-to? 20.0 (get-in std [:means :LSTAT])))
      (is (close-to? (Math/sqrt (/ 2.0 3)) (get-in std [:stds :RM])))
      (is (close-to? (Math/sqrt 200.0) (get-in std [:stds :LSTAT])))))
  (testing "値がすべて同じ列の標準偏差は一にして零を返す"
    (let [std (ch/standardizer [(rm-lstat 5 1) (rm-lstat 5 2)] rm-lstat-columns)]
      (is (close-to? 1.0 (get-in std [:stds :RM])))
      (is (close-to? 0.0 (:RM (ch/standardize std (rm-lstat 5 1)))))))
  (testing "訓練データの平均と標準偏差で別のデータを標準化する"
    (let [std (ch/standardizer [(rm-lstat 1 10) (rm-lstat 3 10)] rm-lstat-columns)]
      (is (close-to? 1.0 (:RM (ch/standardize std (rm-lstat 3 10)))))))
  (testing "平均と標準偏差を持たない列はそのまま残す"
    (let [std (ch/standardizer [(rm-lstat 1 10) (rm-lstat 3 10)] [:RM])]
      (is (close-to? 10.0 (:LSTAT (ch/standardize std (rm-lstat 1 10)))))))
  (testing "特徴量が一件も無ければ失敗する"
    (is (thrown? IllegalArgumentException (ch/standardizer [] rm-lstat-columns)))))

(deftest Tribuoの標準化との突き合わせ
  (let [train [6.2 5.8 7.1 6.5]
        values [6.2 8.0]
        mean (/ (reduce + train) (count train))
        sum-of-squares (reduce + (map #(* (- % mean) (- % mean)) train))
        sample-std (Math/sqrt (/ sum-of-squares (dec (count train))))]
    (testing "TribuoのMeanStdDevTransformationは件数から一を引いて割る標準偏差を使う"
      (let [standardized (ch/tribuo-standardize train values)]
        (is (close-to? (/ (- 6.2 mean) sample-std) (first standardized)))
        (is (close-to? (/ (- 8.0 mean) sample-std) (second standardized)))))
    (testing "自作の標準化に件数から決まる係数を掛けるとTribuoの値になる"
      (let [std (ch/standardizer (mapv #(hash-map :RM %) train) [:RM])
            ratio (Math/sqrt (/ (dec (count train)) (double (count train))))]
        (doseq [[value expected] (map vector values (ch/tribuo-standardize train values))]
          (is (close-to? expected (* ratio (:RM (ch/standardize std {:RM value}))))))))))

(deftest 多項式特徴量
  (testing "二列なら二乗の列と二つの列の積の列を加える"
    (let [x [(rm-lstat 2 5) (rm-lstat 3 7)]
          expanded (ch/expand x rm-lstat-columns)]
      (is (= [:RM :LSTAT (keyword "RM^2") (keyword "RM LSTAT") (keyword "LSTAT^2")]
             (ch/expanded-columns rm-lstat-columns)))
      (is (= [10.0 21.0] (mapv #(get % (keyword "RM LSTAT")) expanded)))
      (is (= [25.0 49.0] (mapv #(get % (keyword "LSTAT^2")) expanded)))))
  (testing "三列ならscikit-learnと同じ順の九列になる"
    (is (= ["RM" "LSTAT" "PTRATIO" "RM^2" "RM LSTAT" "RM PTRATIO" "LSTAT^2"
            "LSTAT PTRATIO" "PTRATIO^2"]
           (mapv name (ch/expanded-columns [:RM :LSTAT :PTRATIO])))))
  (testing "使う項だけをその順に選ぶ"
    (let [selected (ch/select-columns (ch/expand [(rm-lstat 2 5)] rm-lstat-columns)
                                      [:RM (keyword "RM^2")])]
      (is (= [{:RM 2.0 (keyword "RM^2") 4.0}] selected)))))

(deftest 外れ値の検出
  (testing "四分位数の位置が値の間にあれば前後の値から線形補間する"
    (is (close-to? 1.75 (ch/quantile [4.0 1.0 3.0 2.0] 0.25))))
  (testing "第三四分位数からIQRの一点五倍より大きい値を外れ値とする"
    (is (= [false false false false true] (ch/iqr-outliers [1.0 2.0 3.0 4.0 100.0]))))
  (testing "訓練データから正解が外れ値の行を取り除きテストデータは残す"
    (let [split {:columns [:RM]
                 :x-train (mapv #(hash-map :RM %) [1.0 2.0 3.0 4.0 5.0])
                 :t-train [1.0 2.0 3.0 4.0 100.0]
                 :x-test [{:RM 9.0}] :t-test [100.0]}
          removed (ch/remove-target-outliers split)]
      (is (= [1.0 2.0 3.0 4.0] (:t-train removed)))
      (is (= 4 (count (:x-train removed))))
      (is (= [100.0] (:t-test removed))))))

(deftest 区切り文字と文字コードを指定した読み込み
  (testing "タブ区切りのファイルを読み込む"
    (let [path (temp-file "bike" "weather_id\tcnt\n1\t100\n" "UTF-8")
          table (ch/load-delimited path "UTF-8" \tab)]
      (is (= [:weather_id :cnt] (:columns table)))
      (is (= [{:weather_id "1" :cnt "100"}] (:rows table)))))
  (testing "Shift_JISのファイルを文字コードを渡して読み込む"
    (let [path (temp-file "weather" "weather_id,weather\n1,晴れ\n" "Shift_JIS")
          table (ch/load-delimited path "Shift_JIS" \,)]
      (is (= ["晴れ"] (mapv :weather (:rows table))))))
  (testing "Shift_JISのファイルをUTF-8として読むと例外を投げずに文字化けする"
    (let [path (temp-file "weather" "weather_id,weather\n1,晴れ\n" "Shift_JIS")
          table (ch/load-delimited path "UTF-8" \,)]
      (is (not= ["晴れ"] (mapv :weather (:rows table)))))))

(deftest 表の結合と集計
  (let [bike {:columns [:weather_id :cnt]
              :rows [{:weather_id "1" :cnt "100"} {:weather_id "2" :cnt "50"}
                     {:weather_id "1" :cnt "300"} {:weather_id "9" :cnt "999"}]}
        weather {:columns [:weather_id :weather]
                 :rows [{:weather_id "1" :weather "晴れ"} {:weather_id "2" :weather "雨"}]}]
    (testing "天気IDで結合し天気の表に無い行は残さない"
      (let [joined (ch/join-weather bike weather)]
        (is (= [:weather_id :cnt :weather] (:columns joined)))
        (is (= ["晴れ" "雨" "晴れ"] (mapv :weather (:rows joined))))))
    (testing "天気ごとの平均利用者数を多い順に返す"
      (is (= [["晴れ" 200.0] ["雨" 50.0]]
             (ch/mean-count-by-weather (ch/join-weather bike weather)))))
    (testing "天気IDが一意でなければ失敗する"
      (is (thrown? IllegalArgumentException
                   (ch/join-weather bike (update weather :rows conj
                                                 {:weather_id "1" :weather "曇り"})))))))

(deftest 線形回帰と決定係数
  (testing "直線に乗るデータの決定係数は一になる"
    (let [rows [[1.0] [2.0] [3.0]]
          t [3.0 5.0 7.0]
          model (ch/linear-fit rows t)]
      (is (close-to? 1.0 (:intercept model) 1e-9))
      (is (close-to? 2.0 (first (:weights model)) 1e-9))
      (is (close-to? 1.0 (ch/r-squared t (ch/linear-predict model rows)) 1e-9))))
  (testing "列が互いに独立でなければ失敗する"
    (is (thrown? IllegalArgumentException
                 (ch/linear-fit [[1.0 2.0] [2.0 4.0] [3.0 6.0]] [1.0 2.0 3.0]))))
  (testing "平均を予測に使うと決定係数は零になる"
    (is (close-to? 0.0 (ch/r-squared [1.0 2.0 3.0] [2.0 2.0 2.0])))))

(deftest 二乗の項を加えると曲線を当てられる
  ;; 価格を 3 × RM² + 1 にした架空のデータ
  (let [price (fn [rm] (+ 1.0 (* 3.0 rm rm)))
        rms [1.0 2.0 3.0 4.0 5.0 6.0]
        split {:columns [:RM]
               :x-train (mapv #(hash-map :RM %) rms)
               :t-train (mapv price rms)
               :x-test (mapv #(hash-map :RM %) [1.5 2.5])
               :t-test (mapv price [1.5 2.5])}
        linear (ch/score-feature-set split [:RM] [:RM])
        squared (ch/score-feature-set split [:RM] [:RM (keyword "RM^2")])]
    (testing "元の列だけでは当てきれない"
      (is (< (:train linear) 0.99)))
    (testing "二乗の項を加えると訓練データもテストデータも当てきる"
      (is (close-to? 1.0 (:train squared) 1e-9))
      (is (close-to? 1.0 (:test squared) 1e-9)))))
