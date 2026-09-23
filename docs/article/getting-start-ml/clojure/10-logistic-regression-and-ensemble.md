---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下法のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Clojure の TDD で自作し、分類器を「学習して予測する関数を返す関数」で表して Tribuo と数値を突き合わせる。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:20:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。Java 版は `interface Classifier`、Scala 版は `trait Classifier` を用意し、第 3 章の決定木をアダプターで包みました。**Clojure 版はインターフェースを作りません。** 分類器を「訓練データを受け取り、予測する関数を返す関数」とすれば、第 3 章の決定木も自作のロジスティック回帰も Tribuo のトレーナーも、同じ `score` で評価できます。

最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作し、Tribuo のロジスティック回帰・ランダムフォレストと正解率を突き合わせます。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、同じ JVM・同じ Tribuo 4.3.2 を使う [Java 版](../java/10-logistic-regression-and-ensemble.md)・[Scala 版](../scala/10-logistic-regression-and-ensemble.md)・[Kotlin 版](../kotlin/10-logistic-regression-and-ensemble.md) と、分類器を関数の型で表した [F# 版](../fsharp/10-logistic-regression-and-ensemble.md) を対比します。注目してほしいのは次の 3 点です。

- **インターフェースもアダプターも要らない。** 第 3 章の決定木を変更せず、包む型も作らず、`(fn [x t columns] ...)` を返す関数を書くだけで共通化できます。F# 版が「分類器は関数の型」と表したのと同じ発想を、型を書かずに実現します
- **速さのために配列を使う。** 勾配降下法は 105 件 × 4 特徴量 × 3 品種 × 5000 回の計算です。不変のベクタのままだと箱詰めの費用が効くので、内側のループだけ `double-array` を使います。「不変が既定、必要なところだけ可変」という Clojure の使い分けが出ます
- **乱数と手順を Java 版にそろえる。** ブートストラップ標本と列の並べ替えを `java.util.Random` と `Collections.shuffle` と同じ手順で書くので、**この章の 10 個の数値は Java 版と完全に一致します**（Tribuo の結果も含めて）

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepare-iris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 合計が 1 になる確率にする
  - [ ] 大きな値でもあふれない
- [ ] 交差エントロピーで損失を測る
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 分けられるデータを正しく予測する
  - [ ] 学習した品種が名前の順に並ぶ
  - [ ] 繰り返すほど損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
  - [ ] 同じシードなら同じ森になる
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度を求める
  - [ ] 森の重要度を求める
- [ ] すべてのモデルを同じ関数で評価する
- [ ] Tribuo のロジスティック回帰・ランダムフォレストと突き合わせる

コードは `getting-started-ml.chapter10` の 1 つの名前空間に置きます。

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和」を求め、それを確率に変換します。変換に使うのが **ソフトマックス関数** です。

```text
softmax(z)ᵢ = exp(zᵢ) / Σ exp(zⱼ)
```

すべての要素が 0 より大きく、合計が 1 になるので、確率として読めます。

```clojure
(deftest ソフトマックス関数
  (testing "合計が一になる確率にする"
    (let [p (ch/softmax [1.0 2.0 3.0])]
      (is (close-to? 1.0 (reduce + p)))
      (is (every? pos? p))
      (is (= p (sort p)))))
  (testing "同じスコアなら同じ確率になる"
    (is (every? #(close-to? (/ 1.0 3) %) (ch/softmax [2.0 2.0 2.0]))))
  (testing "大きな値でもあふれない"
    (let [p (ch/softmax [1000.0 1001.0])]
      (is (close-to? 1.0 (reduce + p)))
      (is (every? #(not (Double/isNaN %)) p)))))
```

`(= p (sort p))` は「入力の大小の順が確率の順に保たれる」ことを確かめています。

### 大きな値でもあふれない

定義どおりに `exp` を取ると、`exp(1000)` は `Double` の上限を超えて `Infinity` になり、`Infinity / Infinity` が `NaN` になります。JVM は例外を投げず、黙って `NaN` を返します。すべての要素から最大値を引いてから `exp` を取れば、指数が 0 以下になるのであふれません。引いた分は分母と分子で打ち消し合うので、結果は変わりません。

```clojure
(defn softmax
  "スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。"
  [z]
  (let [maximum (reduce max z)
        exps (mapv #(Math/exp (- % maximum)) z)
        total (reduce + exps)]
    (mapv #(/ % total) exps)))
```

`reduce max` は、Java 版の `Arrays.stream(z).max().orElseThrow()`、Scala 版の `z.max` に当たります。空のコレクションを渡すと `max` が引数無しで呼ばれて例外になるので、「空なら」の分岐は書いていません。この関数は必ず品種の数だけの要素を受け取ります。

## 10.4 ロジスティック回帰

### 交差エントロピー

損失（予測の悪さ）は **交差エントロピー** で測ります。正解の品種に与えた確率の対数を取り、平均してマイナスを付けます。確率が 1 なら 0、小さいほど大きくなります。

```clojure
(defn cross-entropy
  "交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。"
  [probabilities targets]
  (- (/ (reduce + (map (fn [p target] (Math/log (+ (nth p target) epsilon)))
                       probabilities targets))
        (count probabilities))))
```

`map` に 2 つのコレクションを渡すと、対応する要素どうしを関数に渡します。確率が 0 のときに `log 0` が `-Infinity` にならないよう、`epsilon`（1e-12）を足しているのは Java 版・Scala 版と同じです。

### 学習と予測

バッチ勾配降下法では、次を繰り返します。

1. すべての行のスコアを求め、ソフトマックスで確率にする
2. 損失を記録する
3. 誤差（確率 − 正解。正解の品種だけ 1 を引く）を求める
4. 誤差から勾配を求め、学習率を掛けて重みと切片から引く

```clojure
(deftest ロジスティック回帰
  (let [[x t] two-species]
    (testing "分けられるデータを正しく予測する"
      (let [predict ((ch/logistic-trainer) x t columns)]
        (is (= t (predict x)))))
    (testing "学習した品種は名前の順に並ぶ"
      (is (= ["setosa" "virginica"] (:classes (ch/logistic-fit x t columns)))))
    (testing "繰り返すほど損失が小さくなる"
      (let [losses (:losses (ch/logistic-fit x t columns 1.0 50))]
        (is (= 50 (count losses)))
        (is (< (last losses) (first losses)))))
    (testing "学習率が零なら重みは変わらず損失も変わらない"
      (let [model (ch/logistic-fit x t columns 0.0 3)]
        (is (every? zero? (:weights model)))
        (is (apply = (:losses model)))))))
```

`((ch/logistic-trainer) x t columns)` の二重のかっこが、この章の設計を表しています。`(logistic-trainer)` が **分類器** を返し、それに訓練データを渡すと **予測する関数** が返ります。

実装のうち、繰り返しの中だけは配列で書きました。

```clojure
(defn logistic-fit
  "バッチ勾配降下法で重みと切片を学習する。品種は名前の順に並べる。"
  ([x t columns] (logistic-fit x t columns default-learning-rate default-epochs))
  ([x t columns learning-rate epochs]
   (let [classes (vec (sort (distinct t)))
         k (count classes)
         nf (count columns)
         n (count x)
         rows (object-array (map (fn [features]
                                   (double-array (map #(get features %) columns)))
                                 x))
         targets (mapv #(.indexOf ^java.util.List classes %) t)
         weights (double-array (* nf k))
         bias (double-array k)
         losses (volatile! [])]
     (dotimes [_ epochs]
       (let [probabilities (object-array (map (fn [row] (double-array
                                                         (softmax (vec (row-scores row weights bias)))))
                                              rows))]
         (vswap! losses conj (cross-entropy (mapv vec probabilities) targets))
         ;; 確率 − 正解（正解の品種だけ 1 を引く）と、その誤差による更新
         (dotimes [i n]
           (let [^doubles p (aget probabilities i)
                 target (nth targets i)]
             (aset p target (- (aget p target) 1.0))))
         (dotimes [j k]
           (dotimes [f nf]
             (let [index (+ (* f k) j)]
               (aset weights index (- (aget weights index)
                                      (/ (* learning-rate (gradient rows probabilities f j)) n)))))
           (let [bias-gradient (reduce + (map (fn [i] (aget ^doubles (aget probabilities i) j))
                                              (range n)))]
             (aset bias j (- (aget bias j) (/ (* learning-rate bias-gradient) n)))))))
     {:columns (vec columns)
      :classes classes
      :weights (vec weights)
      :bias (vec bias)
      :losses @losses})))
```

Clojure らしくない見た目です。理由と、どこで線を引いたかを書きます。

- **重みは `double[]` 1 本にした。** `weights[特徴量][品種]` を `(+ (* f k) j)` の位置に平らに並べています。ベクタのベクタで持ち、毎回 `assoc-in` で作り直すと、5000 回 × 12 個の更新でそのつど新しいベクタができます。ここは可変にする価値がありました
- **境界の内側だけ可変。** 可変なのは `logistic-fit` の中だけで、戻り値は `{:columns ... :classes ... :weights ... :bias ... :losses ...}` という不変のマップです。呼ぶ側から見れば純粋な関数で、同じ入力からは同じモデルが返ります。**「不変が既定、必要なところだけ可変」** という使い分けです
- **`volatile!` で損失をためる。** `atom` でもよいのですが、1 つのスレッドの中だけで使う可変の箱には `volatile!` のほうが軽く、意図（共有しない）も表せます
- **`(.indexOf ^java.util.List classes %)` は Java のメソッド。** Clojure のベクタは `java.util.List` を実装しているので、`indexOf` がそのまま使えます。型ヒントが無いとリフレクションになります

スコアと勾配は、型ヒントを付けた小さな関数に切り出しました。

```clojure
(defn- row-scores
  "1 行のスコア（品種ごと）を求める。weights は [特徴量][品種] を 1 本に並べた配列。"
  ^doubles [^doubles row ^doubles weights ^doubles bias]
  (let [classes (alength bias)
        out (aclone bias)]
    (dotimes [f (alength row)]
      (let [value (aget row f)]
        (dotimes [k classes]
          (aset out k (+ (aget out k) (* value (aget weights (+ (* f classes) k))))))))
    out))
```

ループの順（特徴量が外、品種が内）は Java 版と同じにしました。浮動小数点の足し算は順によって結果が変わるので、**数値を Java 版と一致させるには、順まで合わせる必要があります。**

予測は、スコアがいちばん大きい品種を選びます。

```clojure
(defn- argmax
  "いちばん大きい値の位置を返す。同じ値なら先に現れたほうを選ぶ。"
  [values]
  (reduce (fn [best i] (if (> (nth values i) (nth values best)) i best))
          0
          (range 1 (count values))))
```

`>` を厳密な不等号にしているので、同点なら先に現れた品種を選びます。Java 版の `argmax` と同じ規則です。

そして、分類器はこれだけです。

```clojure
(defn logistic-trainer
  "ロジスティック回帰の分類器。学習して、予測する関数を返す。"
  ([] (logistic-trainer default-learning-rate default-epochs))
  ([learning-rate epochs]
   (fn [x t columns]
     (let [model (logistic-fit x t columns learning-rate epochs)]
       (fn [x] (logistic-predict model x))))))
```

Java 版・Scala 版には「学習する前に予測するとエラーになる」というテストがありました。`fit` を呼ぶ前は `model` が `null`（または未初期化）で、`predict` が `IllegalStateException` を投げるという振る舞いです。**Clojure 版にはこのテストがありません。** 予測する関数は学習が終わってからしか作られないので、「学習前のモデル」という状態がそもそも存在しないからです。**表せない状態は、テストする必要もありません。** F# 版が同じ理由で同じテストを持たないのと同じです。

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、少しずつ違う決定木をたくさん作り、多数決で予測します。違いの作り方は 2 つです。

- **ブートストラップ標本**: 訓練データから、重複を許して同じ件数だけ選ぶ
- **特徴量の部分集合**: 木ごとに、使える特徴量をいくつかに絞る

### 多数決とブートストラップ標本

```clojure
(deftest 多数決とブートストラップ標本
  (testing "サンプルごとに最も多い予測を選ぶ"
    (is (= ["a" "b"] (ch/majority-vote [["a" "b"] ["a" "c"] ["b" "b"]]))))
  (testing "同数なら先に現れた予測を選ぶ"
    (is (= ["a"] (ch/majority-vote [["a"] ["b"]]))))
  (testing "行番号を重複を許して件数と同じだけ選ぶ"
    (let [sample (ch/bootstrap-sample 5 (Random. 0))]
      (is (= 5 (count sample)))
      (is (every? #(< -1 % 5) sample))))
  (testing "同じシードなら同じ標本になる"
    (is (= (ch/bootstrap-sample 10 (Random. 0)) (ch/bootstrap-sample 10 (Random. 0)))))
  (testing "シードが違えば別の標本になる"
    (is (not= (ch/bootstrap-sample 10 (Random. 0)) (ch/bootstrap-sample 10 (Random. 1))))))
```

```clojure
(defn majority-vote
  "サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。"
  [votes]
  (mapv (fn [sample] (chapter03/majority (mapv #(nth % sample) votes)))
        (range (count (first votes)))))

(defn bootstrap-sample
  "0 から size - 1 までの行番号を、重複を許して size 個選ぶ。"
  [size ^Random random]
  (mapv (fn [_] (.nextInt random (int size))) (range size)))
```

多数決は、第 3 章の `majority`（いちばん多いラベル、同数なら先に現れたほう）をそのまま使えました。Java 版は `RandomForest.mostCommon` を別に書いていますが、規則は同じです。**同じ規則が 2 か所にあるなら、1 つにできないか探します。**

`bootstrap-sample` に `mapv` を使い、`repeatedly` を使わなかったのには理由があります。`repeatedly` は遅延シーケンスを返すので、**いつ `.nextInt` が呼ばれるかが、いつ結果を使うかで決まります**。乱数のように呼ぶ順が結果を決めるものと遅延評価を混ぜると、Java 版と同じ並びになりません。`mapv` は即座にすべてを計算するので、順が決まります。

### 森を作る

木ごとに使う特徴量を選ぶところでは、Java 版の `Collections.shuffle` と同じ手順を自分で書きます。

```clojure
(defn- shuffle-with-random
  "渡された乱数で Fisher-Yates のシャッフルをする。Java の Collections.shuffle と同じ手順。"
  [items ^Random random]
  (let [shuffled (object-array items)]
    (doseq [i (range (dec (count items)) 0 -1)]
      (let [j (.nextInt random (int (inc i)))
            tmp (aget shuffled i)]
        (aset shuffled i (aget shuffled j))
        (aset shuffled j tmp)))
    (vec shuffled)))
```

第 2 章の `shuffle-with-seed` とほとんど同じですが、**シードではなく `Random` そのものを受け取ります**。森を作る途中で乱数の状態を引き継ぐ必要があるからです（標本を引いた続きから並べ替える）。Clojure の `shuffle` は `java.util.Collections/shuffle` を呼びますが、乱数を指定できないので使えません。

```clojure
(defn forest-fit
  "ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を n-estimators 本学習する。"
  [x t columns n-estimators max-features max-depth seed]
  (let [random (Random. (long seed))]
    {:trees (mapv (fn [_]
                    (let [rows (bootstrap-sample (count x) random)
                          chosen (set (take max-features (shuffle-with-random columns random)))
                          ;; 列の順は元のまま残す
                          tree-columns (vec (filter chosen columns))
                          sample-x (mapv (fn [row] (select-keys (nth x row) tree-columns)) rows)
                          sample-t (mapv #(nth t %) rows)]
                      {:columns tree-columns
                       :rows rows
                       :tree (chapter03/fit sample-x sample-t tree-columns max-depth)}))
                  (range n-estimators))}))
```

- **第 3 章の決定木をそのまま呼ぶ。** `chapter03/fit` は特徴量・正解・列・深さの上限を受け取って木（マップ）を返す純粋な関数なので、包む必要がありません。Java 版・Scala 版は `DecisionTree` が可変のオブジェクトだったので、`DecisionTreeClassifier` というアダプターを書きました
- **`(filter chosen columns)` の `chosen` は集合。** Clojure の集合は「入っていればその要素、入っていなければ `nil`」を返す関数なので、そのまま述語として使えます。列の順を元のまま残すために、選んだ集合で元の列を絞り込みます
- **1 本分は `{:columns ... :rows ... :tree ...}`。** Java 版の `FittedTree` という record に当たります。重要度の計算で、その木が使った列と行番号が要ります

森全体は `{:trees [...]}` という素のマップなので、**値として比較できます**。「同じシードなら同じ森になる」というテストが `(= (forest-fit ...) (forest-fit ...))` の 1 行で書けるのは、木も森も不変のデータだからです。Java 版・Scala 版では、木の構造を比べるために `equals` を定義するか、予測が同じであることで代用する必要がありました。

```clojure
(defn forest-predict
  "木ごとの予測を多数決でまとめる。"
  [forest x]
  (majority-vote (mapv (fn [{:keys [columns tree]}]
                         (chapter03/predict tree (select-columns x columns)))
                       (:trees forest))))
```

## 10.6 特徴量の重要度

### 計算方法

決定木は、分割のたびに不純度（ジニ不純度）を下げます。「その分割で減った不純度 × その節に来た件数」を、分割に使った列ごとに足し合わせ、合計が 1 になるようにそろえると **特徴量の重要度** になります。

第 3 章の節は `{:split {:feature ... :threshold ... :impurity ...} :left ... :right ...}` というマップで、`:impurity` に「分割後の左右の不純度の重み付き平均」が入っています。分割前の不純度は、その節に来たラベルから `chapter03/gini` で求められます。

```clojure
(defn- impurity-decreases
  "分割ごとに減った不純度（件数で重み付け）を、[列 減った量] の並びにする。"
  [tree x t]
  (if (chapter03/leaf? tree)
    []
    (let [{:keys [feature threshold impurity]} (:split tree)
          pairs (map vector x t)
          left (filter (fn [[features _]] (<= (get features feature) threshold)) pairs)
          right (remove (fn [[features _]] (<= (get features feature) threshold)) pairs)]
      (into [[feature (* (count t) (- (chapter03/gini t) impurity))]]
            (concat (impurity-decreases (:left tree) (mapv first left) (mapv second left))
                    (impurity-decreases (:right tree) (mapv first right) (mapv second right)))))))
```

Java 版・Scala 版は、葉か節かを `switch`（Java 21 のパターンマッチ）と `match` で分けました。Clojure には判別共用体が無いので、第 3 章で決めたとおり `leaf?`（`:label` の鍵があるか）で見分けます。**網羅性は検査されません。** 第 3 章と同じく、木の形を変えたときにここが漏れないよう、木を作る側と読む側を同じ約束（葉は `:label`、節は `:split`）でそろえておきます。

```clojure
(deftest 特徴量の重要度
  (let [[x t] two-species]
    (testing "一つの列だけで分ける木は、その列の重要度が一になる"
      (let [tree (chapter03/fit x t [:花弁幅] nil)]
        (is (= {:花弁幅 1.0} (ch/tree-importances tree x t [:花弁幅])))))
    (testing "使わなかった列の重要度は零になる"
      (let [tree (chapter03/fit x t columns 1)
            importances (ch/tree-importances tree x t columns)]
        (is (close-to? 1.0 (reduce + (vals importances))))
        (is (= 1 (count (filter pos? (vals importances)))))))
```

```clojure
(defn tree-importances
  "決定木 1 本の重要度。合計が 1 になるようにする。"
  [tree x t columns]
  (normalize (reduce (fn [totals [feature amount]] (update totals feature + amount))
                     (zipmap columns (repeat 0.0))
                     (impurity-decreases tree x t))))
```

`(zipmap columns (repeat 0.0))` で「すべての列が 0」から始め、`update` で足し込みます。`(update m k + v)` は「`k` の値に `+` を当てて `v` を足した新しいマップ」を返すので、Java 版の `merge(feature, amount, Double::sum)` にそのまま対応します。使わなかった列が 0 のまま残るのが大事で、これが無いと森の平均を取るときに列が欠けます。

### ランダムフォレストの重要度

木ごとの重要度を、木の数で割って足し合わせます。木ごとの重要度は、**その木が学習したデータ（ブートストラップ標本）と、その木が使った列** で計算します。

```clojure
(defn forest-importances
  "木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。"
  [forest x t columns]
  (let [trees (:trees forest)]
    (normalize
     (reduce (fn [totals {tree-columns :columns :keys [rows tree]}]
               (let [sample-x (mapv (fn [row] (select-keys (nth x row) tree-columns)) rows)
                     sample-t (mapv #(nth t %) rows)]
                 (reduce (fn [acc [feature value]] (update acc feature + (/ value (count trees))))
                         totals
                         (tree-importances tree sample-x sample-t tree-columns))))
             (zipmap columns (repeat 0.0))
             trees))))
```

`{tree-columns :columns :keys [rows tree]}` は、`:columns` だけ別名（`tree-columns`）で受け取る分配束縛です。引数の `columns`（森全体の列）と木の列は別物なので、名前を分けました。

Java 版は「木ごとに正規化してから平均し、最後にもう一度正規化する」という順で、Scala 版の記事には **正規化の順を間違えて値がずれた** 話が載っています。Clojure 版は先に Java 版の値を知っていたので、この間違いは踏みませんでした。ほかの版が照合先としてあることの効き目です。

## 10.7 モデル共通の約束

Java 版・Scala 版・Kotlin 版は、`fit` と `predict` を持つことを `interface`・`trait` で宣言し、第 3 章の決定木をアダプターで包みました。Clojure 版の「共通の約束」は次の 1 行です。

```text
分類器 = (fn [x t columns] -> (fn [x] -> ラベルの並び))
```

宣言する場所がないので、名前空間の先頭にコメントで書きました。

```clojure
;; 分類器は「訓練データを受け取り、予測する関数を返す関数」で表す。
;; Clojure には interface も trait も要らず、第 3 章の決定木も Tribuo のトレーナーも
;; 同じ形の関数に包むだけで、同じ score で評価できる。
```

第 3 章の決定木は、これだけで分類器になります。

```clojure
(defn tree-trainer
  "第 3 章の決定木の分類器。"
  [max-depth]
  (fn [x t columns]
    (let [tree (chapter03/fit x t columns max-depth)]
      (fn [x] (chapter03/predict tree x)))))
```

評価する関数も短くなります。

```clojure
(defn score
  "分類器を訓練データで学習させてから、訓練データとテストデータの正解率を求める。"
  [trainer split columns]
  (let [predict (trainer (:x-train split) (:t-train split) columns)]
    {:train (chapter01/accuracy (predict (:x-train split)) (:t-train split))
     :test (chapter01/accuracy (predict (:x-test split)) (:t-test split))}))
```

5 つの言語で、この「共通の約束」の表し方が分かれます。

| 観点 | Python の `Protocol` | Java/Kotlin の `interface`・Scala の `trait` | F# の関数の型 | Clojure の関数 |
|------|--------------------|-----------------------------------|-------------|--------------|
| 型が合う条件 | 同じ名前・型のメソッドを持つ（構造的部分型） | 継承を宣言している（名前的部分型） | 引数と戻り値の形が合う | 呼べれば合う（検査は実行時） |
| 既存の実装（第 3 章の決定木） | そのまま入れられる | アダプターで包む | 関数で包む | 関数で包む |
| 約束の書き場所 | `Protocol` の宣言 | `interface`・`trait` の宣言 | 型注釈 | **コメントとドキュメント文字列** |
| 間違いに気付くとき | mypy を実行したとき | コンパイルのとき | コンパイルのとき | **実行したとき** |
| 「学習前に予測」の状態 | ある（テストが要る） | ある（テストが要る） | ない | **ない** |

Clojure には `defprotocol` があり、`interface` に近いものを定義できます。この章で使わなかったのは、**分類器が持つ操作が 1 つ（学習する）しかない** からです。操作が 1 つなら、それは関数です。`defprotocol` は「同じデータに対する複数の操作を、データの型ごとに切り替えたい」ときに使います。

引き換えに失うものもはっきりしています。引数の数や順を間違えた分類器を `models` に足しても、**実行してその行に来るまで分かりません**。型のある版が「コンパイルのとき」に捕まえる間違いを、Clojure 版はテストで捕まえます。この章のテストが「5 つの分類器をすべて同じ `score` に通す」形になっているのは、そのためです。

```clojure
(deftest Tribuoのトレーナーも同じ関数で評価できる
  (let [[x t] two-species
        split {:x-train x :t-train t :x-test x :t-test t}]
    (doseq [trainer [(ch/tree-trainer 1)
                     (ch/logistic-trainer)
                     (ch/forest-trainer 10 1 nil 0)
                     (ch/tribuo-trainer (ch/tribuo-logistic-regression 100))
                     (ch/tribuo-trainer (ch/tribuo-random-forest 10 nil 0))]]
      (is (= {:train 1.0 :test 1.0} (ch/score trainer split columns))))))
```

## 10.8 Tribuo のトレーナーを同じ形に包む

Tribuo では、学習の設定を持つ **トレーナー**（`Trainer<Label>`）が、データセットから **モデル**（`Model<Label>`）を作ります。トレーナーを受け取り、学習したモデルを閉じ込めた予測の関数を返せば、Tribuo も分類器になります。

```clojure
(defn tribuo-trainer
  "Tribuo のトレーナーを、学習して予測する関数を返す分類器にする。"
  [^Trainer trainer]
  (fn [x t columns]
    (let [^Model model (.train trainer (to-dataset x t columns))]
      (fn [x]
        (mapv (fn [features]
                (.getLabel ^Label (.getOutput (.predict model (to-example features columns
                                                                          LabelFactory/UNKNOWN_LABEL)))))
              x)))))
```

`model` は `let` の束縛で、外から見えません。Java 版は `private Model<Label> model;` というフィールドが学習前は `null` になるので、`predict` で `IllegalStateException` を投げる必要がありました。Kotlin 版は型を `Model<Label>?` にしてコンパイラに任せました。**Clojure 版は「まだ学習していないモデル」を表せないので、その分岐ごと消えます。**

特徴量と Tribuo の `Example` の変換は、第 3 章と同じ形で書き直しました。第 3 章の変換は CART 専用（`tribuo-predict` の中）だったので、この章ではトレーナー全般に使える形にしています。

```clojure
(defn- to-example
  "特徴量とラベルを Tribuo の事例にする。列名は文字列の配列で渡す。"
  ^Example [features columns ^Label label]
  (ArrayExample. label
                 ^"[Ljava.lang.String;" (into-array String (map name columns))
                 (double-array (map #(get features %) columns))))
```

`^"[Ljava.lang.String;"` は「`String[]` である」ことを示す型ヒントです。`ArrayExample` には引数の型が違う複数のコンストラクターがあるので、これが無いとどれを呼ぶか決まりません。**Java の配列の型をヒントで書く必要があるのは、Clojure から Java を呼ぶときに一番よく出てくる手間です。**

使うトレーナーは 2 つです。

```clojure
(defn tribuo-logistic-regression
  "Tribuo のロジスティック回帰。エポック数を指定しなければ既定（5 エポック）のまま。"
  ([] (LogisticRegressionTrainer.))
  ([epochs]
   (LinearSGDTrainer. (LogMulticlass.) (AdaGrad. 1.0 0.1) (int epochs) Trainer/DEFAULT_SEED)))

(defn tribuo-random-forest
  "Tribuo のランダムフォレスト。分割ごとに半分の特徴量から分割の候補を選ぶ CART を多数決する。"
  [n-estimators max-depth seed]
  (RandomForestTrainer. (CARTClassificationTrainer. (int (or max-depth Integer/MAX_VALUE))
                                                    (float fraction-features-in-split)
                                                    (long seed))
                        (VotingCombiner.) (int n-estimators) (long seed)))
```

`(int epochs)`・`(float ...)`・`(long seed)` は、Java のコンストラクターの引数の型に合わせるための変換です。Clojure の数値は既定で `long` と `double` なので、`int` や `float` を受け取る Java の API には明示的に渡します。深さの上限は、第 3 章と同じく `nil`（上限なし）を `Integer/MAX_VALUE` に読み替えます。Java 版が `UNLIMITED = Integer.MAX_VALUE` という定数を用意したところです。

`RandomForestTrainer` は、内側の決定木が分割ごとに特徴量を絞らない設定だと、コンストラクターで例外を投げます。これもテストに残しました。

```clojure
(deftest Tribuoのランダムフォレストは分割ごとに特徴量を絞らない決定木を受け付けない
  (is (thrown? RuntimeException
               (org.tribuo.common.tree.RandomForestTrainer.
                (org.tribuo.classification.dtree.CARTClassificationTrainer. Integer/MAX_VALUE)
                (org.tribuo.classification.ensemble.VotingCombiner.) 10 0))))
```

### Tribuo の設定と自作との違い

既定の設定は、自作と次の点が違います（Java 版・Kotlin 版で確かめた内容と同じです）。

| 項目 | 自作 | Tribuo |
|------|------|--------|
| ロジスティック回帰の損失 | 交差エントロピー（ソフトマックス） | `LogMulticlass`（多クラスのロジスティック損失） |
| ロジスティック回帰の最適化 | バッチ勾配降下法（学習率 1.0）、5000 回 | `LogisticRegressionTrainer` は AdaGrad、ミニバッチの大きさ 1 の確率的勾配降下法、5 エポック、シード 12345 |
| ランダムフォレストで特徴量を選ぶ単位 | 木ごと（2 つ） | 分割ごと（`fractionFeaturesInSplit`。この章では 0.5） |
| 決定木を分割する最小の件数 | 1 件になるまで分ける | `minChildWeight` の既定値 5（重みの合計が 5 未満の節は分割しない） |

## 10.9 実データで突き合わせる

### モデルを比べる

```clojure
(defn models
  "名前と分類器。表示する順に並べる。"
  []
  [[(str "決定木（深さ " shallow-depth "）") (tree-trainer shallow-depth)]
   ["ロジスティック回帰" (logistic-trainer)]
   [(str "ランダムフォレスト（" n-estimators " 本）")
    (forest-trainer n-estimators max-features nil seed)]
   [(str "ランダムフォレスト（" n-estimators " 本・深さ " shallow-depth "）")
    (forest-trainer n-estimators max-features shallow-depth seed)]
   ["Tribuo ロジスティック回帰" (tribuo-trainer (tribuo-logistic-regression))]
   [(str "Tribuo ランダムフォレスト（" n-estimators " 本）")
    (tribuo-trainer (tribuo-random-forest n-estimators nil seed))]])
```

Java 版は順を保つために `LinkedHashMap` を使い、Scala 版・Kotlin 版は組の並びにしました。Clojure 版も `[[名前 分類器] ...]` のベクタです。第 9 章で書いたとおり、**順に意味がある結果はマップにしません**。

```clojure
(defn run
  "モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。"
  []
  ;; Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
  (.setLevel (Logger/getLogger "org.tribuo") Level/WARNING)
  (let [split (chapter02/prepare-iris (str (dataset/dir) "/iris.csv") 0.3 seed)
        columns (vec (keys (first (:x-train split))))]
    (println "モデル\t訓練データ\tテストデータ")
    (doseq [[label trainer] (models)]
      (let [{:keys [train test]} (score trainer split columns)]
        (println (str/join "\t" [label (format-score train) (format-score test)]))))
    (let [forest (forest-fit (:x-train split) (:t-train split) columns
                             n-estimators max-features nil seed)
          importances (forest-importances forest (:x-train split) (:t-train split) columns)]
      (println)
      (println (str "ランダムフォレスト（" n-estimators " 本）の特徴量の重要度:"))
      (doseq [column columns]
        (println (str (name column) "\t" (format-score (get importances column))))))))
```

`(vec (keys (first (:x-train split))))` で列の順を取り出しているのは、iris の特徴量が 4 列（8 件以下の配列マップ）で挿入順が保たれるからです。第 9 章の Boston（14 列）ではこの書き方が使えず、`:columns` を持ち回りました。重要度の表示も、マップの順ではなく `columns` の順に引いています。

```bash
ML_DATA_DIR=<学習データの置き場> clojure -M:run chapter10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9143	0.9111
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556
Tribuo ロジスティック回帰	0.9238	0.8889
Tribuo ランダムフォレスト（100 本）	0.9905	0.9556

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1882
がく片幅	0.1271
花弁長さ	0.2708
花弁幅	0.4140
```

**この 10 個の数値は、[Java 版の 10.10 節](../java/10-logistic-regression-and-ensemble.md) と完全に一致します。** 自作のモデルは、分割（第 2 章の `java.util.Random` + Fisher-Yates）・ブートストラップ標本・列の並べ替え・ループの順をすべて Java 版にそろえたからです。Tribuo の 2 行が一致するのは、同じ 4.3.2 を同じ JDK 21 で、同じ設定・同じシードで呼んでいるからです。Scala 版はこの章で Tribuo との突き合わせを行っていないので、Clojure 版は自作の 8 個が Scala 版と、10 個すべてが Java 版と一致することになります。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです。第 3 章の深さ 2 の木は、2 回とも花弁幅で分割していました

### Tribuo と突き合わせる

**ロジスティック回帰**: `LinearSGDTrainer` でエポック数だけを変えて実測しました。

| エポック数 | 訓練データ | テストデータ |
|-----------|-----------|-------------|
| 5（既定） | 0.9238 | 0.8889 |
| 50 | 0.9143 | 0.9111 |
| 500 | 0.9143 | 0.9111 |
| 5000 | 0.9143 | 0.9111 |

既定の 5 エポックの結果は、50 エポック以降と違い、テストデータの正解率が 1 件分低くなりました。50 エポック以降は変わらないので、既定の 5 エポックでは学習が落ち着いていないと言えます。500 エポックで、自作（5000 回）と同じ訓練 0.9143・テスト 0.9111 になり、この組み合わせはテストで固定しています。**この表も Java 版の値と完全に一致しました。**

自作のほうも繰り返し回数を変えて確かめました。1000 回では訓練 0.9238・テスト 0.9111、5000 回と 20000 回では訓練 0.9143・テスト 0.9111 でした。テストデータの正解率は変わらないので、既定の繰り返し回数は Python 版・Java 版と同じ 5000 回のままにしています。

**ランダムフォレスト**: Tribuo は訓練データで 0.9905（105 件中 104 件）と分け切っていません。10.8 節の表の `minChildWeight`（既定値 5）を 1.0 にすると、訓練データは 1.0000 になり、テストデータは 0.9556 から 0.9333 に下がりました。重みの合計が 5 未満の節を分割しないという既定の設定が、1 本 1 本の木の深さを抑えていたことが分かります。この設定は、`CARTClassificationTrainer` の 6 引数のコンストラクター（第 3 章で使ったもの）で指定します。

```clojure
(deftest Tribuoのランダムフォレストは子の節の重みの下限を一にすると訓練データを分け切る
  (when-let [split (iris-split)]
    (let [tree (CARTClassificationTrainer. Integer/MAX_VALUE (float 1.0) (float 0.0) (float 0.5)
                                           (GiniIndex.) 0)
          trainer (ch/tribuo-trainer (RandomForestTrainer. tree (VotingCombiner.) 100 0))
          {:keys [train test]} (ch/score trainer split (columns split))]
      (is (= 1.0 train))
      (is (< (abs (- test (/ 42.0 45))) 1e-12)))))
```

同じ「ランダムフォレスト」という名前でも、特徴量を選ぶ単位（木ごとか分割ごとか）や、分割を止める条件が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

### 実データのテスト

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。Clojure 版では `run` が標準出力に書くので、`with-out-str` で受け取って行ごとに比べます。

```clojure
(deftest 実行するとモデルごとの正解率と特徴量の重要度を表示する
  ;; 10 個の数値は Java 版と一致する（分割も乱数も同じ手順で、Tribuo も同じ 4.3.2 のため）
  (when (iris-split)
    (is (= ["モデル\t訓練データ\tテストデータ"
            "決定木（深さ 2）\t0.9333\t0.9556"
            "ロジスティック回帰\t0.9143\t0.9111"
            "ランダムフォレスト（100 本）\t1.0000\t0.9333"
            "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556"
            "Tribuo ロジスティック回帰\t0.9238\t0.8889"
            "Tribuo ランダムフォレスト（100 本）\t0.9905\t0.9556"
            ""
            "ランダムフォレスト（100 本）の特徴量の重要度:"
            "がく片長さ\t0.1882"
            "がく片幅\t0.1271"
            "花弁長さ\t0.2708"
            "花弁幅\t0.4140"]
           (str/split-lines (with-out-str (ch/run)))))))
```

Scala 版は `Main.run(print: String => Unit)` と、表示する関数を引数で受け取る形にしました。Clojure では `*out*` が動的な変数で、`with-out-str` がそれを文字列の書き出し先に束縛してくれるので、**`run` の側は何も用意しなくてもテストから出力を取れます**。動的束縛は乱用すると追いにくくなりますが、標準出力の差し替えは本来の使いどころです。

学習データが無い環境では、第 2 章・第 3 章と同じく「理由を標準エラーに出して早く戻る」形にしています。

```clojure
(defn- iris-split
  "前処理した訓練データとテストデータを返す。学習データが無ければ nil。"
  []
  (let [path (str (dataset/dir) "/iris.csv")]
    (if (.exists (java.io.File. path))
      (chapter02/prepare-iris path 0.3 0)
      (binding [*out* *err*]
        (println "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))
```

## 10.10 Notebook で探索する

Clojure 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。

Clojure 版の `logistic-fit` が返すマップの `:losses`（繰り返しごとの損失のベクタ）と `forest-importances`（特徴量ごとの重要度のマップ）は、ほかの版と同じ形のデータを返すので、同じ観点で読めます。Java 版と分割も乱数も同じなので、値そのものも一致します。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、分類器を関数で表してまとめて評価しました。

1. **分類器は関数でよい** — `interface`・`trait`・アダプターを作らず、`(fn [x t columns] -> (fn [x] -> ラベル))` という形だけを約束にした。第 3 章の決定木も Tribuo のトレーナーも、包む関数を 1 つ書くだけで同じ `score` に通せる。引き換えに、約束はコメントとドキュメント文字列にしか書けず、違反は実行するまで分からない
2. **表せない状態はテストも要らない** — 予測する関数は学習が終わってからしか作られないので、「学習前のモデル」が存在しない。Java 版・Scala 版にあった「学習する前に予測するとエラーになる」テストは、Clojure 版には無い
3. **不変が既定、必要なところだけ可変** — 勾配降下法の内側だけ `double-array` と `aset` を使い、境界の外は不変のマップにした。可変が `logistic-fit` の中で閉じているので、呼ぶ側から見れば純粋な関数のまま
4. **値だから比べられる** — 木も森も素のマップなので、「同じシードなら同じ森になる」が `=` の 1 行で書ける。`equals` を書く必要も、予測で代用する必要もない
5. **手順をそろえると数値が一致する** — 乱数生成器だけでなく、並べ替えの手順（`Collections.shuffle` と同じ Fisher-Yates）、ループの順、`repeatedly` を避けて `mapv` にする（遅延させない）ところまでそろえたので、10 個の数値が Java 版と完全に一致した

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
