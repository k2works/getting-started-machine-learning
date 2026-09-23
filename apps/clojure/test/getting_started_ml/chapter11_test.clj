(ns getting-started-ml.chapter11-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter07.metrics :as metrics]
            [getting-started-ml.chapter07.tribuo :as tribuo]
            [getting-started-ml.chapter11 :as ch])
  (:import [org.tribuo.classification Label]
           [org.tribuo.classification.evaluation LabelEvaluation LabelEvaluator]
           [org.tribuo.evaluation KFoldSplitter]
           [org.tribuo.regression.evaluation RegressionEvaluator]
           [org.tribuo.regression.slm SLMTrainer]))

(defn- close-to?
  "小数の誤差を許して比べる。"
  ([a b] (close-to? a b 1e-12))
  ([a b tolerance] (< (abs (- a b)) tolerance)))

(def ^:private columns
  "テストで使う特徴量の列。"
  [:x])

(defn- features
  "1 列の特徴量にする。"
  [values]
  (mapv (fn [value] {:x (double value)}) values))

(defn- positive-label
  "Tribuo の正例のラベル。"
  ^Label []
  (Label. "1"))

(defn- evaluate-tribuo
  "Tribuo の決定木を学習し、LabelEvaluator で採点した評価結果を返す。"
  ^LabelEvaluation [x-train t-train x-test t-test max-depth]
  (let [model (ch/tribuo-tree x-train t-train columns max-depth)]
    (.evaluate (LabelEvaluator.) model (ch/to-dataset x-test t-test columns))))

(defn- tribuo-test-sizes
  "Tribuo の KFoldSplitter が作る、分割ごとのテストデータの件数。"
  [n-samples n-splits]
  (let [x (features (range n-samples))
        t (mapv #(if (< % (quot n-samples 2)) "0" "1") (range n-samples))
        sizes (volatile! [])]
    (.forEachRemaining (.split (KFoldSplitter. (int n-splits) 0) (ch/to-dataset x t columns) true)
                       (reify java.util.function.Consumer
                         (accept [_ fold]
                           (vswap! sizes conj (.size ^org.tribuo.Dataset (.-test fold))))))
    @sizes))

(deftest 混同行列
  (testing "正解と予測を四つに数える"
    (is (= {:tp 2 :fp 1 :fn 1 :tn 2}
           (ch/confusion-matrix ["1" "1" "1" "0" "0" "0"]
                                ["1" "1" "0" "1" "0" "0"]
                                "1"))))
  (testing "正例の決め方を変えると数え方も変わる"
    (is (= {:tp 1 :fp 1 :fn 2 :tn 2}
           (ch/confusion-matrix ["1" "1" "1" "0" "0" "0"]
                                ["1" "0" "0" "1" "0" "0"]
                                "1")))
    (is (= {:tp 2 :fp 2 :fn 1 :tn 1}
           (ch/confusion-matrix ["1" "1" "1" "0" "0" "0"]
                                ["1" "0" "0" "1" "0" "0"]
                                "0"))))
  (testing "三値以上でも正例以外はまとめて負例になる"
    (is (= {:tp 1 :fp 1 :fn 1 :tn 1}
           (ch/confusion-matrix ["a" "a" "b" "c"] ["a" "b" "a" "c"] "a"))))
  (testing "件数が違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/confusion-matrix ["1"] ["1" "0"] "1")))))

(deftest 適合率と再現率とF値
  (let [cm (ch/confusion-matrix ["1" "1" "1" "0" "0" "0"]
                                ["1" "1" "0" "1" "0" "0"]
                                "1")]
    (testing "正例と予測したうち本当に正例だった割合"
      (is (close-to? (/ 2.0 3) (ch/precision cm))))
    (testing "本当の正例のうち正例と予測できた割合"
      (is (close-to? (/ 2.0 3) (ch/recall cm))))
    (testing "適合率と再現率の調和平均"
      (is (close-to? (/ 2.0 3) (ch/f1-score cm)))))
  (testing "適合率と再現率が違えば F 値はその間に入る"
    (let [cm {:tp 1 :fp 0 :fn 1 :tn 2}]
      (is (close-to? 1.0 (ch/precision cm)))
      (is (close-to? 0.5 (ch/recall cm)))
      (is (close-to? (/ 2.0 3) (ch/f1-score cm)))))
  (testing "正例を一件も予測しなければ零になり NaN にならない"
    (let [cm {:tp 0 :fp 0 :fn 2 :tn 2}]
      (is (= [0.0 0.0 0.0] [(ch/precision cm) (ch/recall cm) (ch/f1-score cm)])))))

(deftest 正解率
  (testing "一致した割合を返す"
    (is (close-to? 0.75 (ch/accuracy ["a" "b" "c" "d"] ["a" "b" "c" "x"]))))
  (testing "件数が違えば失敗する"
    (is (thrown? IllegalArgumentException (ch/accuracy ["a"] ["a" "b"])))))

(deftest 平均二乗誤差
  (testing "誤差の二乗の平均"
    (is (close-to? 0.125 (ch/mean-squared-error [1.0 2.0 3.0 4.0] [1.5 2.0 3.0 4.5]))))
  (testing "RMSE の二乗と一致する"
    (let [t [1.0 2.0 3.0 4.0]
          y [1.5 2.0 3.0 6.0]
          rmse (metrics/root-mean-squared-error t y)]
      (is (close-to? (* rmse rmse) (ch/mean-squared-error t y)))))
  (testing "一件だけ大きく外れると MAE より MSE のほうが強く反応する"
    (let [t [1.0 2.0 3.0 4.0]
          spread [2.0 3.0 4.0 5.0]
          one-off [1.0 2.0 3.0 8.0]]
      (is (close-to? (metrics/mean-absolute-error t spread)
                     (metrics/mean-absolute-error t one-off)))
      (is (< (ch/mean-squared-error t spread) (ch/mean-squared-error t one-off))))))

(deftest 評価関数
  (testing "混同行列の指標を正解と予測から採点する関数に変える"
    (let [precision (ch/classification-metric ch/precision "1")]
      (is (close-to? (/ 2.0 3) (precision ["1" "1" "1" "0" "0" "0"]
                                          ["1" "1" "0" "1" "0" "0"]))))))

(deftest K分割
  (testing "分割の数だけ分け方を作る"
    (is (= 3 (count (ch/k-fold 9 3 0)))))
  (testing "テストデータは重ならず全体をおおう"
    (let [folds (ch/k-fold 10 5 0)]
      (is (= (set (range 10)) (set (mapcat :test folds))))
      (is (= 10 (count (mapcat :test folds))))))
  (testing "訓練データとテストデータは重ならず合わせて全体になる"
    (doseq [{:keys [train test]} (ch/k-fold 10 3 0)]
      (is (empty? (set/intersection (set train) (set test))))
      (is (= (set (range 10)) (into (set train) test)))))
  (testing "割り切れないときは余りを先頭の分割から一件ずつ配る"
    (is (= [4 3 3] (mapv #(count (:test %)) (ch/k-fold 10 3 0))))
    (is (= [4 4 3] (mapv #(count (:test %)) (ch/k-fold 11 3 0)))))
  (testing "同じシードなら同じ分け方になる"
    (is (= (ch/k-fold 20 4 0) (ch/k-fold 20 4 0))))
  (testing "シードが違えば別の分け方になる"
    (is (not= (ch/k-fold 20 4 0) (ch/k-fold 20 4 1)))))

(deftest 交差検証
  (let [x (features (range 1 21))
        t (mapv #(if (or (zero? (mod % 3)) (> % 12)) "1" "0") (range 1 21))
        folds (ch/k-fold 20 4 0)]
    (testing "分割ごとのスコアを分割の数だけ返す"
      (is (= 4 (count (ch/cross-validate (ch/tree-trainer columns 1) x t folds ch/accuracy)))))
    (testing "評価関数を差し替えても同じ分割で採点する"
      (let [f1 (ch/classification-metric ch/f1-score "1")
            scores (ch/cross-validate (ch/tree-trainer columns 1) x t folds f1)]
        (is (= 4 (count scores)))
        (is (every? #(<= 0.0 % 1.0) scores))))
    (testing "取り出すまで学習しない"
      (let [trained (atom 0)
            counting (fn [x t]
                       (swap! trained inc)
                       ((ch/tree-trainer columns 1) x t))
            scores (ch/cross-validate counting x t folds ch/accuracy)]
        (is (zero? @trained))
        (is (some? (first scores)))
        (is (pos? @trained))))
    (testing "分け方をベクタで渡すと三十二件ずつまとめて学習する"
      (let [trained (atom 0)
            counting (fn [x t]
                       (swap! trained inc)
                       ((ch/tree-trainer columns 1) x t))]
        (is (some? (first (ch/cross-validate counting x t folds ch/accuracy))))
        (is (= (count folds) @trained))
        (reset! trained 0)
        (is (some? (first (ch/cross-validate counting x t (apply list folds) ch/accuracy))))
        (is (= 1 @trained))))
    (testing "回帰でも同じ関数で評価できる"
      (let [y (mapv #(* 2.0 (:x %)) x)
            scores (ch/cross-validate (ch/linear-trainer columns) x y folds
                                      metrics/root-mean-squared-error)]
        (is (every? #(close-to? 0.0 % 1e-9) scores))))))

(deftest 評価の平均
  (let [x (features (range 1 21))
        t (mapv #(if (> % 12) "1" "0") (range 1 21))
        scores (ch/evaluate (ch/tree-trainer columns 1) {:x x :t t}
                            [["正解率" ch/accuracy]
                             ["F値" (ch/classification-metric ch/f1-score "1")]])]
    (testing "指標の名前と順を保ったまま平均を返す"
      (is (= ["正解率" "F値"] (mapv first scores))))
    (testing "分けられるデータはどの指標も一になる"
      (is (every? #(close-to? 1.0 (second %)) scores)))))

(deftest Tribuoの評価器と突き合わせる
  (let [x (features [0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0])
        t ["0" "0" "1" "0" "0" "1" "1" "0" "1" "1"]
        model (ch/tribuo-tree x t columns 1)
        predicted (ch/tribuo-predict model x columns)
        evaluation (evaluate-tribuo x t x t 1)
        cm (ch/confusion-matrix t predicted "1")
        matrix (.getConfusionMatrix evaluation)]
    (testing "混同行列が一致する"
      (is (= [(double (:tp cm)) (double (:fp cm)) (double (:fn cm)) (double (:tn cm))]
             [(.tp matrix (positive-label)) (.fp matrix (positive-label))
              (.fn matrix (positive-label)) (.tn matrix (positive-label))])))
    (testing "正解率と適合率と再現率と F 値が一致する"
      (is (close-to? (ch/accuracy t predicted) (.accuracy evaluation)))
      (is (close-to? (ch/precision cm) (.precision evaluation (positive-label))))
      (is (close-to? (ch/recall cm) (.recall evaluation (positive-label))))
      (is (close-to? (ch/f1-score cm) (.f1 evaluation (positive-label)))))
    (testing "正例を一件も予測しなければ Tribuo も零にする"
      ;; 深さ 0 の木は、訓練データの多数派の "0" だけを予測する
      (let [negative (conj (vec (repeat 9 "0")) "1")
            zero (evaluate-tribuo x negative x t 0)]
        (is (= [0.0 0.0 0.0]
               [(.precision zero (positive-label)) (.recall zero (positive-label))
                (.f1 zero (positive-label))]))))))

(deftest 分割の件数はTribuoのKFoldSplitterと一致する
  (doseq [[n-samples n-splits] [[10 3] [7 2] [11 4]]]
    (is (= (mapv #(count (:test %)) (ch/k-fold n-samples n-splits 0))
           (tribuo-test-sizes n-samples n-splits))
        (str n-samples " 件を " n-splits " 分割"))))

(deftest 同じ分割ならTribuoの評価器で採点した平均と一致する
  (let [x (features (map #(* % 0.05) (range 1 21)))
        t (mapv #(if (or (zero? (mod % 3)) (> % 12)) "1" "0") (range 1 21))
        folds (ch/k-fold 20 4 0)
        mine (ch/mean (ch/cross-validate (ch/tribuo-tree-trainer columns 1) x t folds ch/accuracy))
        theirs (ch/mean (mapv (fn [{:keys [train test]}]
                                (.accuracy (evaluate-tribuo (ch/pick x train) (ch/pick t train)
                                                            (ch/pick x test) (ch/pick t test) 1)))
                              folds))]
    (is (close-to? theirs mine))))

(deftest MSEはTribuoのRMSEの二乗と一致する
  (let [x (features [1 2 3 4 5 6])
        t [1.1 2.3 2.8 4.4 4.9 6.2]
        model (tribuo/train (SLMTrainer. true) x t columns)
        rmse (first (vals (.rmse (.evaluate (RegressionEvaluator.) model
                                            (tribuo/to-dataset x t columns)))))]
    (is (close-to? (* rmse rmse) (ch/mean-squared-error t (tribuo/predict model x columns))
                   1e-9))))
