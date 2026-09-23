(ns getting-started-ml.chapter08.tree
  "第 8 章: クラスの重みを付けた決定木。第 3 章の決定木を「1 件ごとの重み」を通す形に書き直す。

   Tribuo の CART にはクラスの重みを渡す口が無いので、ここは自作が最終実装になる。
   木の表し方は第 3 章と同じで、葉は {:label 1}、節は {:split ... :left ... :right ...}。")

(def class-weights
  "クラスの重みの付け方。:none は重みを付けない、:balanced は件数に反比例する重みを付ける。"
  [:none :balanced])

(defn- weight-sums
  "ラベルごとの重みの合計を、ラベルが先に現れた順のベクタ [[ラベル 合計] ...] で返す。
   マップは順を保たないので、順を使いたいところではベクタで持つ。"
  [labels weights]
  (reduce (fn [sums [label weight]]
            (if-let [i (first (keep-indexed #(when (= label (first %2)) %1) sums))]
              (update-in sums [i 1] + weight)
              (conj sums [label weight])))
          []
          (map vector labels weights)))

(defn weighted-gini
  "重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。"
  [labels weights]
  (let [total (reduce + weights)]
    (- 1.0 (reduce + (map (fn [[_ weight]] (let [share (/ weight total)] (* share share)))
                          (weight-sums labels weights))))))

(defn balanced-weights
  "クラスの件数に反比例する重み（件数 ÷ (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。"
  [t]
  (let [counts (frequencies t)]
    (mapv #(/ (double (count t)) (* (count counts) (counts %))) t)))

(defn weights-of
  "クラスの重みの付け方から、1 件ごとの重みを求める。"
  [t class-weight]
  (case class-weight
    :none (vec (repeat (count t) 1.0))
    :balanced (balanced-weights t)))

(defn weighted-majority
  "重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。"
  [labels weights]
  (first (reduce (fn [best entry] (if (> (second entry) (second best)) entry best))
                 (weight-sums labels weights))))

(defn- candidates
  "1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。"
  [x t w feature total]
  (let [sorted (sort-by first (map (fn [features label weight]
                                     [(get features feature) label weight])
                                   x t w))
        values (mapv first sorted)
        labels (mapv second sorted)
        weights (mapv #(nth % 2) sorted)]
    (keep (fn [i]
            (when-not (== (values (dec i)) (values i))
              (let [[left-labels right-labels] [(subvec labels 0 i) (subvec labels i)]
                    [left-weights right-weights] [(subvec weights 0 i) (subvec weights i)]]
                {:feature feature
                 :threshold (/ (+ (values (dec i)) (values i)) 2.0)
                 :impurity (/ (+ (* (reduce + left-weights) (weighted-gini left-labels left-weights))
                                 (* (reduce + right-weights)
                                    (weighted-gini right-labels right-weights)))
                              total)})))
          (range 1 (count sorted)))))

(defn best-split
  "左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。分けられなければ nil。
   同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。"
  [x t w columns]
  (when-not (or (empty? x) (zero? (weighted-gini t w)))
    (let [total (reduce + w)
          all (mapcat #(candidates x t w % total) columns)]
      (when (seq all)
        (reduce (fn [best candidate]
                  (if (< (:impurity candidate) (:impurity best)) candidate best))
                all)))))

(defn- goes-left?
  "分割の境界以下なら左へ進む。"
  [split features]
  (<= (get features (:feature split)) (:threshold split)))

(defn- build
  "深さの上限まで分割を繰り返して木を作る。max-depth が nil なら上限なし。"
  [x t w columns max-depth]
  (let [split (when-not (and max-depth (zero? max-depth)) (best-split x t w columns))]
    (if (nil? split)
      {:label (weighted-majority t w)}
      (let [triples (map vector x t w)
            left (filterv #(goes-left? split (first %)) triples)
            right (filterv (complement #(goes-left? split (first %))) triples)
            next-depth (when max-depth (dec max-depth))
            branch (fn [rows]
                     (build (mapv first rows) (mapv second rows) (mapv #(nth % 2) rows)
                            columns next-depth))]
        {:split split :left (branch left) :right (branch right)}))))

(defn fit
  "訓練データから、クラスの重みを付けた決定木を作る。"
  [x t columns max-depth class-weight]
  (build x t (weights-of t class-weight) columns max-depth))

(defn predict-one
  "木をたどって 1 件のラベルを予測する。"
  [tree features]
  (if (contains? tree :label)
    (:label tree)
    (recur (if (goes-left? (:split tree) features) (:left tree) (:right tree)) features)))

(defn predict
  "特徴量ごとのラベルを予測する。"
  [tree x]
  (mapv #(predict-one tree %) x))
