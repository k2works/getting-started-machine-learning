(ns getting-started-ml.chapter07.matrix
  "第 7 章: 行列。第 11〜13 章でも使う。

   行列は「長さのそろったベクタのベクタ」でそのまま表す。Clojure のベクタは変更できないので、
   Java 版・C# 版のように配列を写して守る必要が無く、= がそのまま値の比較になる。
   型が無いぶん、形が正しいかどうかは check で自分から確かめる。")

(defn row-count "行数を返す。" [m] (count m))
(defn column-count "列数を返す。" [m] (count (first m)))

(defn check
  "行列として使える形かどうかを確かめ、そのまま返す。形が違えば失敗する。"
  [m]
  (when (or (empty? m) (empty? (first m)))
    (throw (IllegalArgumentException. "行列は 1 行 1 列以上でなければなりません")))
  (when-not (apply = (map count m))
    (throw (IllegalArgumentException. "行によって列数が違います")))
  m)

(defn column
  "j 列目（0 始まり）の値を返す。"
  [m j]
  (mapv #(nth % j) m))

(defn transpose
  "行と列を入れ替えた行列を返す。"
  [m]
  (mapv #(column m %) (range (column-count (check m)))))

(defn multiply
  "行列の積。左の列数と右の行数が同じでなければならない。"
  [a b]
  (check a)
  (check b)
  (when-not (= (column-count a) (row-count b))
    (throw (IllegalArgumentException.
            (str "左の行列の列数 " (column-count a) " と右の行列の行数 " (row-count b) " が違います"))))
  (let [columns (transpose b)]
    (mapv (fn [row] (mapv (fn [col] (reduce + (map * row col))) columns)) a)))

(defn column-vector
  "値を縦に並べた 1 列の行列（列ベクトル）を作る。"
  [values]
  (mapv vector values))

(defn- swap-largest
  "ピボットの列の絶対値が最も大きい行を、ピボットの行と入れ替える。"
  [rows pivot]
  (let [largest (apply max-key #(abs (nth (rows %) pivot)) (range pivot (count rows)))]
    (assoc rows pivot (rows largest) largest (rows pivot))))

(defn- eliminate
  "拡大係数行列を、上三角行列になるまで前進消去する。"
  [rows pivot]
  (if (>= pivot (count rows))
    rows
    (let [swapped (swap-largest rows pivot)
          pivot-row (swapped pivot)]
      (recur (mapv (fn [i row]
                     (if (<= i pivot)
                       row
                       (let [factor (/ (nth row pivot) (nth pivot-row pivot))]
                         (mapv #(- %1 (* factor %2)) row pivot-row))))
                   (range (count swapped)) swapped)
             (inc pivot)))))

(defn- back-substitute
  "上三角行列を、下の行から順に代入して解く。"
  [rows]
  (let [n (count rows)]
    (reduce (fn [x i]
              (let [known (reduce + (map #(* (nth (rows i) %) (nth x %)) (range (inc i) n)))]
                (assoc x i (/ (- (nth (rows i) n) known) (nth (rows i) i)))))
            (vec (repeat n 0.0))
            (range (dec n) -1 -1))))

(defn solve
  "正方行列 a について a x = b を満たす列ベクトル x を、部分ピボット選択つきのガウスの消去法で求める。"
  [a b]
  (check a)
  (check b)
  (when-not (= (row-count a) (column-count a))
    (throw (IllegalArgumentException.
            (str "正方行列ではありません（" (row-count a) " 行 " (column-count a) " 列）"))))
  (when-not (and (= (row-count b) (row-count a)) (= 1 (column-count b)))
    (throw (IllegalArgumentException. "右辺は同じ行数の列ベクトルでなければなりません")))
  (column-vector (back-substitute (eliminate (mapv conj a (column b 0)) 0))))
