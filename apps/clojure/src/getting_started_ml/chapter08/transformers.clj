(ns getting-started-ml.chapter08.transformers
  "第 8 章: 前処理。訓練データから値を求める fit と、そのデータを変換する transform に分ける。

   前処理は :type の鍵を持つマップで表し、fit と transform をマルチメソッドで :type に振り分ける。
   Scala 3 の sealed trait・Rust の enum と違い、どんな :type があるかをコンパイラは数え上げない。
   そのかわり、学習済みの前処理はただのマップなので、EDN でそのまま保存・復元できる。"
  (:require [getting-started-ml.chapter02 :as chapter02]))

(defmulti fit
  "訓練データから変換に必要な値を求め、学習済みの前処理を返す。"
  (fn [transformer _x] (:type transformer)))

(defmulti transform
  "学習済みの前処理でデータを変換する。"
  (fn [fitted _x] (:type fitted)))

(defn- update-cell
  "列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変えない。"
  [row column value]
  (assoc row column value))

;; 数値の列の欠損値を、同じグループ（by の列の値の組）の中央値で補完する

(defn group-median-imputer
  "グループ別の中央値で補完する前処理を作る。"
  [column by]
  {:type :group-median :column column :by by})

(defn median
  "中央値。件数が偶数なら中央の 2 つの平均。"
  [values]
  (let [sorted (vec (sort values))
        middle (quot (count sorted) 2)]
    (if (odd? (count sorted))
      (nth sorted middle)
      (/ (+ (nth sorted (dec middle)) (nth sorted middle)) 2))))

(defn- group-of
  "行のグループ。by の列の値を並べたベクタで、マップの鍵に使う。"
  [row by]
  (mapv #(chapter02/text row %) by))

(defmethod fit :group-median
  [{:keys [column by] :as transformer} x]
  (let [known (remove #(chapter02/missing? % column) (:rows x))
        values (mapv #(chapter02/number % column) known)]
    (assoc transformer
           :medians (update-vals (group-by #(group-of % by) known)
                                 (fn [rows] (median (mapv #(chapter02/number % column) rows))))
           :overall-median (median values))))

(defmethod transform :group-median
  [{:keys [column by medians overall-median]} x]
  (update x :rows
          (fn [rows]
            (mapv (fn [row]
                    (if (chapter02/missing? row column)
                      (update-cell row column
                                   (str (get medians (group-of row by) overall-median)))
                      row))
                  rows))))

;; 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する

(defn most-frequent-imputer
  "最頻値で補完する前処理を作る。"
  [column]
  {:type :most-frequent :column column})

(defmethod fit :most-frequent
  [{:keys [column] :as transformer} x]
  (let [counts (frequencies (map #(chapter02/text % column)
                                 (remove #(chapter02/missing? % column) (:rows x))))]
    ;; 値の順に並べ、厳密な不等号で畳むので、同数なら値の順で前のものを選ぶ
    ;; （max-key は同数なら後ろのほうを返すので使えない）
    (assoc transformer
           :most-frequent (first (reduce (fn [best entry]
                                           (if (> (second entry) (second best)) entry best))
                                         (sort-by first counts))))))

(defmethod transform :most-frequent
  [{:keys [column most-frequent]} x]
  (update x :rows
          (fn [rows]
            (mapv (fn [row]
                    (if (chapter02/missing? row column)
                      (update-cell row column most-frequent)
                      row))
                  rows))))

;; カテゴリ値の列を、最初のカテゴリを除いたカテゴリごとの 0 と 1 の列（ダミー変数）にする

(defn dummy-encoder
  "ダミー変数化する前処理を作る。"
  [columns]
  {:type :dummy :columns columns})

(defn- categories-of
  "列の値を重複なく並べ替える。欠損値は除く。"
  [x column]
  (vec (sort (distinct (map #(chapter02/text % column)
                            (remove #(chapter02/missing? % column) (:rows x)))))))

(defn- dummy-column
  "ダミー変数の列の名前。「元の列名_カテゴリ」にする。"
  [column category]
  (keyword (str (name column) "_" category)))

(defmethod fit :dummy
  [{:keys [columns] :as transformer} x]
  ;; 学習した値は「(列名, カテゴリの並び) の組のベクタ」。マップにすると 9 個目から順が崩れる
  (assoc transformer
         :dummies (mapv (fn [column] [column (vec (rest (categories-of x column)))]) columns)))

(defmethod transform :dummy
  [{:keys [dummies]} x]
  {:columns (reduce (fn [columns [column categories]]
                      (into (vec (remove #(= column %) columns))
                            (map #(dummy-column column %))
                            categories))
                    (:columns x)
                    dummies)
   :rows (mapv (fn [row]
                 (reduce (fn [encoded [column categories]]
                           (let [value (chapter02/text row column)]
                             (reduce (fn [encoded category]
                                       (update-cell encoded (dummy-column column category)
                                                    (if (= value category) "1" "0")))
                                     encoded
                                     categories)))
                         row
                         dummies))
               (:rows x))})
