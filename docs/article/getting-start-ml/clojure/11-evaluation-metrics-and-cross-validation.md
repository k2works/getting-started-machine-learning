---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値と K 分割交差検証を Clojure の TDD で自作し、評価関数もモデルも「ただの関数」で表して Tribuo の LabelEvaluator・KFoldSplitter と数値を突き合わせる。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:45:00Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

第 3 章から第 10 章まで、モデルの良し悪しは **正解率** で測ってきました。この章では、その測り方そのものを作り直します。

- **正解率だけでは足りない** — 見つけたいものが少ないデータでは、正解率が高くても役に立たないモデルができます。混同行列を数え、適合率・再現率・F 値で見ます
- **1 回の分け方に頼らない** — 第 2 章の `split-train-test` は 1 回きりの分割です。**K 分割交差検証** は、分け方を入れ替えて何度も測り、平均を取ります

第 10 章では、分類器を「訓練データを受け取り、予測する関数を返す関数」で表しました。この章では **評価関数** も同じように関数で表します。Java 版は `Metric<T>` という関数型インターフェースを、Scala 版は `type Metric[T]` という型の別名を用意しました。**Clojure 版はどちらも書きません。** 評価関数は「正解と予測を受け取って 1 つの数を返す関数」というだけで、名前を付けるところがありません。

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進め、同じ JVM・同じ Tribuo 4.3.2 を使う [Java 版](../java/11-evaluation-metrics-and-cross-validation.md)・[Scala 版](../scala/11-evaluation-metrics-and-cross-validation.md) と対比します。注目してほしいのは次の 3 点です。

- **型で名前を付けるところが 1 つも無い。** `Model<T>` も `Metric<T>` も `Fold` も、Clojure では関数とマップとベクタのままです。そのぶん、約束はドキュメント文字列にしか書けません
- **遅延は言語が持っているが、細かさが違う。** Java の `DoubleStream`、Scala の `LazyList` に当たるのは Clojure の遅延シーケンスです。ただし `map` はベクタに対して **32 件ずつまとめて** 実現するので、「1 件だけ取り出せば 1 回だけ学習する」とは限りません
- **数値は Java 版・Scala 版と完全に一致する。** 分割の並べ替えを `java.util.Random` の Fisher-Yates（第 2 章の `shuffle-with-seed`）で行い、余りの配り方までそろえたので、**この章の 6 つの数値は Java 版と 1 桁も違いません**

データは第 8 章の Survived.csv（タイタニックの生存）と、第 7 章の cinema.csv（映画の興行収入）を使います。

## 11.2 正解率だけでは足りない理由

100 人のうち 5 人だけが病気の検査を考えます。「全員が健康」と答えるモデルの正解率は 0.95 です。数字は高いのに、病気の人を 1 人も見つけていません。

見つけたいほう（この例では病気）を **正例** と決めると、予測の当たり外れは 4 つに分かれます。

| | 正例と予測 | 負例と予測 |
|---|---|---|
| **実際に正例** | 真陽性（tp） | 偽陰性（fn） |
| **実際に負例** | 偽陽性（fp） | 真陰性（tn） |

この 4 つの数が **混同行列** です。ここから 3 つの指標を作ります。

| 指標 | 式 | 読み方 |
|---|---|---|
| 適合率（precision） | tp / (tp + fp) | 正例と予測したうち、本当に正例だった割合 |
| 再現率（recall） | tp / (tp + fn) | 本当の正例のうち、正例と予測できた割合 |
| F 値（F1） | 2pr / (p + r) | 適合率と再現率の調和平均 |

「全員が健康」のモデルは、tp が 0 なので適合率も再現率も 0 です。正解率 0.95 が隠していたことが見えます。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
  - [ ] 正解と予測を 4 つに分ける
  - [ ] 正例の決め方を変えると数え方も変わる
  - [ ] 件数が違えば失敗する
- [ ] 適合率・再現率・F 値を求める
  - [ ] 正例を 1 件も予測しなければ 0 にする（NaN にしない）
- [ ] 正解率と平均二乗誤差を求める
- [ ] 混同行列の指標を評価関数に変える
- [ ] K 分割交差検証
  - [ ] テストデータは重ならず全体をおおう
  - [ ] 割り切れないときは余りを先頭の分割から 1 件ずつ配る
  - [ ] 同じシードなら同じ分け方になる
- [ ] 交差検証で分割ごとに学習して採点する
  - [ ] 評価関数を差し替えられる
  - [ ] 取り出すまで学習しない
  - [ ] 回帰でも同じ関数で評価できる
- [ ] Tribuo の評価器・KFoldSplitter と突き合わせる
- [ ] 実データ（Survived・cinema）で評価する

## 11.4 混同行列を数える

### Red: 最初のテスト

混同行列は 4 つの数です。Clojure では、そのまま 4 つの鍵を持つマップにします。

```clojure
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
```

Java 版は `record ConfusionMatrix(int tp, int fp, int fn, int tn)` を定義し、Scala 版は `case class` にしました。**Clojure では定義するものがありません。** 素のマップなので、`=` で期待値とそのまま比べられ、`{:tp 2 …}` というリテラルがそのままテストの読み仕様になります。

Java 版は正例を入れ替えたときに tp と tn が入れ替わるテストを書いていますが、上のテストでは予測を少しずらして、4 つの数がすべて変わる例にしました。「正例をどちらに決めるかで見えるものが変わる」ことのほうが、この章で伝えたい性質だからです。

### Green: 数え上げは分類してから数える

Java 版・Scala 版は、4 つのカウンタを 1 件ずつ増やしていきます。Clojure では「1 件ずつ 4 つのどれかに分類してから、まとめて数える」と書けます。

```clojure
(defn confusion-matrix
  "正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。"
  [actual predicted positive]
  (require-same-size actual predicted)
  (merge {:tp 0 :fp 0 :fn 0 :tn 0}
         (frequencies (map (fn [a p]
                             (case [(= a positive) (= p positive)]
                               [true true] :tp
                               [false true] :fp
                               [true false] :fn
                               [false false] :tn))
                           actual predicted))))
```

- `case` は 2 つの真偽値のベクタで分岐します。Scala 版のタプルのパターンマッチと同じで、**4 つの場合が形として並びます**。`if` のはしごより、抜けに気づきやすい書き方です
- `frequencies` は「どの鍵が何回現れたか」を数えます。1 件も現れなかった鍵は入らないので、`merge` で 0 の入った土台に重ねます。これをしないと、`(:tp cm)` が `nil` になって足し算で落ちます
- `map` に 2 つのコレクションを渡すと、対応する要素どうしを関数に渡します。短いほうで止まるので、**件数の検査を先に書くことが必須** です

### 件数が違うときは黙って切り詰めない

```clojure
(defn require-same-size
  "正解と予測の件数が同じでなければ失敗する。短いほうに合わせて黙って切り詰めない。"
  [actual predicted]
  (when-not (= (count actual) (count predicted))
    (throw (IllegalArgumentException.
            (str "正解と予測の件数が違います（正解 " (count actual) " 件、予測 " (count predicted) " 件）")))))
```

Clojure の `map`・`zipmap`・`mapv` は、長さが違うコレクションを渡されても例外を投げず、短いほうに合わせます。便利ですが、評価指標では「予測が 1 件足りない」ことが黙って通ると、スコアが少しだけ良く見えます。**言語の寛容さを、そのままドメインの寛容さにしない** ために、入口で止めます。

## 11.5 適合率・再現率・F 値

### 明白な実装

式がはっきりしているので、そのまま書きます。

```clojure
(defn- ratio
  "分母が 0 なら 0 を返す割り算。NaN にしない。"
  [numerator denominator]
  (if (zero? denominator) 0.0 (/ (double numerator) denominator)))

(defn precision
  "適合率。正例と予測したうち、本当に正例だった割合。"
  [{:keys [tp fp]}]
  (ratio tp (+ tp fp)))

(defn recall
  "再現率。本当の正例のうち、正例と予測できた割合。"
  [{tp :tp misses :fn}]
  (ratio tp (+ tp misses)))

(defn f1-score
  "F 値。適合率と再現率の調和平均。"
  [cm]
  (let [p (precision cm)
        r (recall cm)]
    (ratio (* 2 p r) (+ p r))))
```

`recall` だけ `{:keys [tp fn]}` と書いていないのは、`:fn` を `:keys` で取り出すと局所の名前が `fn` になり、**関数を作る `fn` を隠してしまう** からです。この関数の本体では `fn` を使っていませんが、あとで使いたくなったときに理由の分からないエラーになります。`{tp :tp misses :fn}` と名前を変えて受けています。マップの鍵としての `:fn` は問題ないので、混同行列の形は変えていません。

### 分母が 0 になる場合

正例を 1 件も予測しなければ、適合率の分母 `tp + fp` が 0 になります。素直に割ると `NaN` になり、平均を取ったとたんに全体が `NaN` に染まります。

```clojure
  (testing "正例を一件も予測しなければ零になり NaN にならない"
    (let [cm {:tp 0 :fp 0 :fn 2 :tn 2}]
      (is (= [0.0 0.0 0.0] [(ch/precision cm) (ch/recall cm) (ch/f1-score cm)]))))
```

期待値を `0.0` と `=` で比べられるのは、`ratio` が `0.0` を **返している** からです（`0/0` の結果ではありません）。この約束は、11.9 節で Tribuo の `LabelEvaluator` も同じであることを確かめます。

## 11.6 正解率と平均二乗誤差

正解率は、一致した件数の割合です。

```clojure
(defn accuracy
  "正解率。正解と予測が一致した割合。"
  [actual predicted]
  (require-same-size actual predicted)
  (ratio (count (filter true? (map = actual predicted))) (count actual)))
```

`(map = actual predicted)` が真偽値の並びになるのは、`=` が 2 引数の関数としてそのまま渡せるからです。第 1 章の `accuracy` と同じ計算ですが、この章では件数の検査を足しています。

平均二乗誤差（MSE）は、誤差の 2 乗の平均です。第 7 章の RMSE の 2 乗にあたります。

```clojure
(defn mean-squared-error
  "平均二乗誤差（MSE）。誤差の 2 乗の平均。"
  [actual predicted]
  (require-same-size actual predicted)
  (/ (reduce + (map (fn [a p] (let [error (- p a)] (* error error))) actual predicted))
     (count actual)))
```

### 学習用テスト: 外れた予測への敏感さ

MSE が MAE と何が違うのかを、テストで確かめます。

```clojure
  (testing "一件だけ大きく外れると MAE より MSE のほうが強く反応する"
    (let [t [1.0 2.0 3.0 4.0]
          spread [2.0 3.0 4.0 5.0]
          one-off [1.0 2.0 3.0 8.0]]
      (is (close-to? (metrics/mean-absolute-error t spread)
                     (metrics/mean-absolute-error t one-off)))
      (is (< (ch/mean-squared-error t spread) (ch/mean-squared-error t one-off)))))
```

誤差の合計が同じ（どちらも 4）でも、1 件に集まっているほうが MSE は大きくなります。RMSE が MAE より大きいとき、「大きく外した予測がある」と読めるのはこのためです。

## 11.7 K 分割交差検証

### なぜ分け方を入れ替えるのか

1 回の分割では、たまたまテストデータに簡単な行が集まることがあります。K 分割交差検証は、全体を K 個に分け、1 つをテストデータ・残りを訓練データにすることを K 回繰り返し、スコアを平均します。すべての行がちょうど 1 回テストデータになります。

### 分け方はただのマップ

Java 版は `record Fold(List<Integer> train, List<Integer> test)`、Scala 版は `case class Fold` を定義しました。Clojure 版は `{:train [...] :test [...]}` のマップです。

```clojure
(defn k-fold
  "シード付きの乱数で行の位置を並べ替え、n-splits 個のテストデータに分ける。
   件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。"
  [n-samples n-splits seed]
  (let [positions (chapter02/shuffle-with-seed (vec (range n-samples)) seed)
        sizes (mapv #(+ (quot n-samples n-splits) (if (< % (rem n-samples n-splits)) 1 0))
                    (range n-splits))
        starts (reductions + 0 sizes)]
    (mapv (fn [from size]
            (let [test (subvec positions from (+ from size))
                  test-set (set test)]
              {:train (vec (remove test-set positions)) :test test}))
          starts sizes)))
```

- 並べ替えは、第 2 章の `shuffle-with-seed` をそのまま使います。`java.util.Random` を使った Fisher-Yates なので、Java 版の `Collections.shuffle(positions, new Random(seed))` と **同じ並び** になります
- 分割の大きさは `n / K` に、余りのぶんを先頭から 1 ずつ足します。`reductions + 0 sizes` が開始位置の並びになり、`mapv` に 2 つのコレクションを渡すと短いほう（`sizes`）で止まるので、`reductions` が 1 つ多く返す末尾は自然に落ちます
- 訓練データは「テストデータ以外を、並べ替えた順のまま」です。`(remove test-set positions)` の `test-set` は集合ですが、**集合は関数としても呼べる** ので、そのまま述語になります。順を保つことは大事で、訓練データの並びが変われば決定木の同点の分け方が変わり、数値がずれます

### 分け方の性質をテストで固定する

```clojure
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
```

「重ならず全体をおおう」ことを、集合の一致と件数の一致の 2 つで書いています。集合だけでは、同じ行が 2 回テストデータに入っていても気づけません。

「同じシードなら同じ分け方になる」が `=` の 1 行で書けるのは、分け方がマップとベクタ（値）だからです。Java 版は `record` の `equals` に、Scala 版は `case class` の `equals` に頼っています。Clojure はもともと値です。

## 11.8 評価関数もモデルも、ただの関数

### 交差検証の手順を 1 つの関数にする

第 10 章の分類器（`(fn [x t] -> (fn [x] -> 予測))`）と、評価関数（`(fn [正解 予測] -> 数)`）を受け取れば、交差検証は 5 行です。

```clojure
(defn cross-validate
  "分割ごとに分類器を訓練データで学習し、テストデータの予測を評価関数で採点する。
   遅延シーケンスを返すので、取り出した分だけ学習する。"
  [trainer x t folds metric]
  (map (fn [{:keys [train test]}]
         (let [predict (trainer (pick x train) (pick t train))]
           (metric (pick t test) (predict (pick x test)))))
       folds))

(defn pick
  "行の位置で値を選ぶ。"
  [values positions]
  (mapv #(nth values %) positions))
```

第 10 章の分類器は `(fn [x t columns] …)` と列も受け取る形でしたが、この章では列を閉じ込めた `(fn [x t] …)` にしています。交差検証は列の存在を知る必要がないからです。第 3 章の決定木も第 7 章の線形回帰も、閉じ込めるだけで通ります。

```clojure
(defn tree-trainer
  "第 3 章の決定木の分類器。"
  [columns max-depth]
  (fn [x t]
    (let [tree (chapter03/fit x t columns max-depth)]
      (fn [x] (chapter03/predict tree x)))))

(defn linear-trainer
  "第 7 章の線形回帰の分類器（回帰なので予測は数値）。"
  [columns]
  (fn [x t]
    (let [model (chapter07/fit x t columns)]
      (fn [x] (chapter07/predict model x)))))
```

Java 版は `interface Model<T>` を定義し、`DecisionTreeModel`・`LinearRegressionModel` という 2 つのアダプタークラスを書きました。Clojure 版はこの 12 行だけです。そのかわり、「トレーナーとは何か」という約束は型のどこにも書かれておらず、ドキュメント文字列と名前空間の冒頭のコメントにしかありません。

### 三角測量: 評価関数を差し替える、回帰も同じ関数で

```clojure
    (testing "評価関数を差し替えても同じ分割で採点する"
      (let [f1 (ch/classification-metric ch/f1-score "1")
            scores (ch/cross-validate (ch/tree-trainer columns 1) x t folds f1)]
        (is (= 4 (count scores)))
        (is (every? #(<= 0.0 % 1.0) scores))))
    …
    (testing "回帰でも同じ関数で評価できる"
      (let [y (mapv #(* 2.0 (:x %)) x)
            scores (ch/cross-validate (ch/linear-trainer columns) x y folds
                                      metrics/root-mean-squared-error)]
        (is (every? #(close-to? 0.0 % 1e-9) scores))))
```

分類と回帰で、`cross-validate` は 1 つです。Java 版は `Model<T>` と `Metric<T>` の型引数 `T` が `String` と `Double` に化けることで同じ働きをします。Clojure は型引数が無いぶん、`String` を返すモデルに回帰の RMSE を渡しても、**実行するまで分かりません**。第 7 章の `mean-absolute-error` が文字列を引き算しようとして落ちます。ここは Java 版・Scala 版のほうが強い点です。

### 混同行列の指標を評価関数に変える

適合率は混同行列を受け取る関数で、評価関数は正解と予測を受け取る関数です。間をつなぐのは、正例を決めて包むだけの高階関数です。

```clojure
(defn classification-metric
  "混同行列から求める指標を、正例を決めて、正解と予測から採点する評価関数に変える。"
  [score positive]
  (fn [actual predicted] (score (confusion-matrix actual predicted positive))))
```

Java 版は `ToDoubleFunction<ConfusionMatrix>` を受け取って `Metric<T>` を返すメソッドです。Clojure では関数を受け取って関数を返すだけで、書くことがこれ以上ありません。

### 遅延の細かさは言語によって違う

Java 版は `DoubleStream`、Scala 版は `LazyList` で「取り出した分だけ学習する」ようにしました。Clojure の `map` も遅延シーケンスを返しますが、**まとめて実現する（チャンクする）** という癖があります。

```clojure
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
```

最初に `(= 1 @trained)` と書いて、`4` が返って失敗しました。`map` はベクタなどのチャンク可能なコレクションに対して **32 件を一度に** 処理します。分け方が 4 件しかないので、1 つ目を取り出した時点で 4 回とも学習されていました。リスト（`(apply list folds)`）に変えるとチャンクされず、1 回だけになります。

「取り出すまで学習しない」ことは守られているので、実害はありません。ですが **「1 件だけ取り出せば 1 回だけ計算される」と思い込むと、副作用や重い処理で足をすくわれます。** テストに両方の振る舞いを書き、言語の癖を仕様として固定しました。

### 指標ごとの平均を、順を保ったまま返す

Java 版は `LinkedHashMap` で指標の順を保ちました。Clojure のマップも 9 要素以上で順を保たないので、**順が意味を持つ対応づけはベクタの組で持ちます**（第 7 章の係数、第 8 章の列の並びと同じ扱いです）。

```clojure
(def survived-metrics
  "Survived の評価指標。表示する順に並べる。"
  [["正解率" accuracy]
   ["適合率" (classification-metric precision survived)]
   ["再現率" (classification-metric recall survived)]
   ["F値" (classification-metric f1-score survived)]])

(defn evaluate
  "同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。名前と平均の組を、指標の順に返す。"
  [trainer {:keys [x t]} metrics]
  (let [folds (k-fold (count x) n-splits seed)]
    (mapv (fn [[name* metric]] [name* (mean (cross-validate trainer x t folds metric))])
          metrics)))
```

分割（`folds`）は 1 回だけ作り、すべての指標で使い回します。指標によって分け方が違うと、指標どうしを比べられないからです。

## 11.9 Tribuo の評価器と突き合わせる

自作した指標と分割が、Tribuo の評価器と同じ結果になることを学習用テストで確かめます。まず、Tribuo の決定木を自作の分類器と同じ形に包みます。

```clojure
(defn tribuo-tree
  "Tribuo の CART（ジニ不純度）を学習する。深さは max-depth（nil なら上限なし）。"
  ^Model [x t columns max-depth]
  (.train (CARTClassificationTrainer. (int (or max-depth Integer/MAX_VALUE))
                                      (float 1.0) (float 0.0) (float 1.0) (GiniIndex.) 0)
          (to-dataset x t columns)))

(defn tribuo-tree-trainer
  "Tribuo の決定木を、自作の分類器と同じ形（学習して予測する関数を返す関数）に包む。"
  [columns max-depth]
  (fn [x t]
    (let [model (tribuo-tree x t columns max-depth)]
      (fn [x] (tribuo-predict model x columns)))))
```

Java 版は `TribuoTree implements Model<String>` というテスト用の入れ子クラスを書きました。Clojure 版は 5 行の関数で、しかも **本体の名前空間に置けます**。実データのテストからも使うためです。

指標の一致を確かめます。

```clojure
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
```

Tribuo の混同行列は件数を `double` で返すので、自作の整数を `double` にそろえています。`.fn` は Clojure から見ると **Java のメソッド名なので `fn` マクロと衝突しません**（`.` が付いた記号はメソッド呼び出しとして読まれます）。

分割の件数も比べます。

```clojure
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

(deftest 分割の件数はTribuoのKFoldSplitterと一致する
  (doseq [[n-samples n-splits] [[10 3] [7 2] [11 4]]]
    (is (= (mapv #(count (:test %)) (ch/k-fold n-samples n-splits 0))
           (tribuo-test-sizes n-samples n-splits))
        (str n-samples " 件を " n-splits " 分割"))))
```

`KFoldSplitter.split` は Java の `Iterator` を返すので、`forEachRemaining` に `java.util.function.Consumer` を `reify` で作って渡しています。1 回分の分け方 `TrainTestFold` は `train`・`test` を **public なフィールド** で持つので、`(.-test fold)` とフィールドアクセスの記法（`.-`）で読みます。最初に `(.test fold)` と書いてメソッドが見つからず落ちました。Java 版が `fold.test.size()` と括弧なしで書いたのと同じ理由です。

最後に、同じ分割なら交差検証の平均も一致することを確かめます。

```clojure
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
```

突き合わせで分かった Tribuo の約束事です。

- `LabelEvaluator` の評価結果（`LabelEvaluation`）は、ラベルを指定して `precision`・`recall`・`f1` を返す。どのラベルを正例にするかを呼ぶたびに指定する設計で、自作の `positive` と同じ考え方
- 正例を一度も予測しない場合、Tribuo も適合率・再現率・F 値を `0.0` にする。`NaN` にはならない
- `RegressionEvaluator` には MSE が無く、RMSE の 2 乗が自作の MSE と一致する
- `KFoldSplitter` と自作の `k-fold` は、テストデータの件数の配り方（余りを先頭から配る）が、試した 3 通りで一致した。並べ替えの乱数は別の実装なので、同じシードでも行の割り当ては一致しない

## 11.10 実データで評価する

### 簡略化した前処理

交差検証にかけるには、欠損値や文字列の列を数値にしておく必要があります。前処理パイプラインは第 8 章で扱ったので、この章ではそれを簡略化したものを使います。

```clojure
(defn prepare-survived
  "客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
   この章では分割の前に全体の平均値で年齢の欠損値を補う、簡略化した前処理を使う。"
  [table]
  (let [age-mean (get (chapter02/column-means (:rows table) [:Age]) :Age)]
    {:x (mapv (fn [row] {:Pclass (chapter02/number row :Pclass)
                         :Age (or (chapter02/number row :Age) age-mean)
                         :male (if (= "male" (chapter02/text row :Sex)) 1.0 0.0)})
              (:rows table))
     :t (mapv #(chapter02/text % :Survived) (:rows table))}))
```

補完に使う平均値を **分割の前に全体から** 求めているので、厳密にはテストデータの情報が訓練に漏れています（リーク）。第 8 章のパイプラインは分割のあとで補完しました。ここは他の言語版と条件をそろえるために、あえて簡略化した手順に合わせています。

`(or (chapter02/number row :Age) age-mean)` は、第 2 章の `number` が欠損値に `nil` を返すことを利用しています。Java 版の `Optional.orElse`、Scala 版の `getOrElse` に当たるものが、Clojure では `or` です。

### 交差検証の実験

```clojure
(defn evaluate-survived
  "Survived.csv を深さ 2 の決定木で評価する。"
  [csv-file]
  (evaluate (tree-trainer survived-columns tree-depth)
            (prepare-survived (chapter02/load-table csv-file))
            survived-metrics))

(defn evaluate-cinema
  "cinema.csv を線形回帰で評価する。"
  [csv-file]
  (evaluate (linear-trainer chapter07/feature-columns)
            (prepare-cinema (chapter02/load-table csv-file))
            cinema-metrics))
```

```console
$ clojure -M:run chapter11
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7811
  適合率: 0.7759
  再現率: 0.6306
  F値: 0.6833
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 405.77
  MAE: 321.53
```

Survived の決定木は、正解率 0.7811 に対して再現率が 0.6306 です。**生存者のうち 4 割近くを見逃している** ことが、正解率だけでは見えませんでした。適合率 0.7759 は、「生存」と予測した人の 8 割近くが本当に生存していたことを表します。11.2 節で述べたとおり、正解率は「どちらを間違えたか」を隠します。

cinema の RMSE（405.77）は MAE（321.53）より大きく、11.6 節で見たとおり、大きく外れた予測があることを示します。第 7 章では 1 回の分割で RMSE が 376.14 でしたが、この章は外れ値を除かずに 5 回の平均を取っているので、単純には比べられません。

**6 つの数値は、Java 版の 11.10 節とすべて一致します。** 分割の並べ替えが `java.util.Random` の Fisher-Yates で、余りの配り方も訓練データの並びもそろえたためです。乱数が別実装の Kotlin 版とは一致しません（Kotlin 版の正解率は 0.7677）。

### 実データのテスト

学習データが無ければ、理由を標準エラーに出して早く戻ります。Clojure の `clojure.test` にはスキップの仕組みが無いので、第 2 章から続けている形です。

```clojure
(defn- survived-data
  "前処理した Survived の特徴量と正解ラベルを返す。学習データが無ければ nil。"
  []
  (when-let [path (data-file "Survived.csv")]
    (ch/prepare-survived (chapter02/load-table path))))

(deftest 同じ分割ならTribuoの評価器で採点したSurvivedの平均と一致する
  (when-let [{:keys [x t]} (survived-data)]
    (let [folds (ch/k-fold (count x) ch/n-splits ch/seed)
          positive (Label. "1")
          evaluations (mapv (fn [{:keys [train test]}]
                              (let [model (ch/tribuo-tree (ch/pick x train) (ch/pick t train)
                                                          ch/survived-columns 2)]
                                (.evaluate (LabelEvaluator.) model
                                           (ch/to-dataset (ch/pick x test) (ch/pick t test)
                                                          ch/survived-columns))))
                            folds)
          scores (into {} (ch/evaluate (ch/tribuo-tree-trainer ch/survived-columns 2)
                                       {:x x :t t} ch/survived-metrics))]
      …)))
```

`evaluate` が返す「名前と平均の組」のベクタは、`(into {} …)` でマップに変えられます。**順が要るところではベクタ、引きたいところではマップ** と、その場で変えられるのがデータで持つ利点です。

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

```clojure
(deftest 実行すると交差検証の平均を表示する
  (when (and (data-file "Survived.csv") (data-file "cinema.csv"))
    (is (= ["Survived（決定木、5 分割交差検証の平均）"
            "  正解率: 0.7811"
            …
            "  MAE: 321.53"]
           (str/split-lines (with-out-str (ch/run)))))))
```

## 11.11 品質チェック

```console
$ cljfmt check src test && clj-kondo --lint src test --fail-level warning && clojure -M:test
All source files formatted correctly
linting took 2417ms, errors: 0, warnings: 0
…
Ran 139 tests containing 380 assertions.
0 failures, 0 errors.
```

clj-kondo は、11.8 節の遅延のテストで最初に「Unused value」「missing test assertion」の 4 件を出しました。`(first (cross-validate …))` を副作用のためだけに呼んでいたからです。`(is (some? …))` で包んで、**値を捨てていないこと** を明示しました。捨てているつもりのない値を捨てると教えてくれるのは、テストでも役に立ちます。

```console
$ clojure -M:coverage
|-------------------------------------------+---------+---------|
|                                 Namespace | % Forms | % Lines |
|-------------------------------------------+---------+---------|
|              getting-started-ml.chapter11 |   95.85 |   99.22 |
|-------------------------------------------+---------+---------|
```

学習データが無い環境（`ML_DATA_DIR=/nonexistent clojure -M:test`）では、実データのテストが理由を標準エラーに出して通ります。

## 11.12 可視化について

Clojure 版では Notebook と可視化を扱いません。混同行列のヒートマップ、分割ごとのスコアのばらつきのグラフは、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) の「Notebook による探索と可視化」の節を参照してください。

`cross-validate` が返すのは素の数の並びなので、平均だけでなくばらつきを見たいときは `(sort scores)` や分位数をそのまま求められます。

## 11.13 まとめ

この章では、評価指標と K 分割交差検証を Clojure の TDD で自作し、Tribuo の評価器と突き合わせました。

| 作ったもの | Clojure での表し方 | 他の版 |
|-----------|------------------|-------|
| 混同行列 | `{:tp .. :fp .. :fn .. :tn ..}` のマップ | `record` / `case class` |
| 評価関数 | `(fn [正解 予測] -> 数)` | `Metric<T>` / `type Metric[T]` |
| 分類器 | `(fn [x t] -> (fn [x] -> 予測))` | `interface Model<T>` とアダプター 2 つ |
| 分け方 | `{:train [..] :test [..]}` のマップ | `record Fold` / `case class Fold` |
| 指標の並び | `[["正解率" 関数] …]` のベクタ | `LinkedHashMap` / `ListMap` |

Clojure 版ならではの学びです。

1. **名前を付けるところが無い** — `Metric` も `Model` も `Fold` も定義しなかった。定義しないので、名前がずれることも、アダプターを書くこともない。引き換えに、分類のモデルに回帰の指標を渡す取り違えは実行するまで分からない
2. **`case` で場合を並べる** — 4 つの数を 1 つずつ増やす代わりに、「どの箱に入るか」を `case` の 4 行で並べてから `frequencies` で数えた。場合の網羅が形として読める
3. **`:fn` は鍵としては安全でも、束縛名としては危ない** — `{:keys [tp fn]}` は `fn` マクロを隠す。鍵の名前は変えず、`{tp :tp misses :fn}` と受ける側で名前を変えた
4. **遅延の細かさを思い込まない** — `map` はベクタを 32 件ずつまとめて実現する。「1 件取り出せば 1 回だけ学習する」は成り立たなかった。癖をテストに書いて固定した
5. **順が要るならベクタ、引きたいならマップ** — 指標の並びはベクタの組で持ち、テストでは `(into {} …)` でマップにして引いた。同じデータを場面で持ち替えられる
6. **手順をそろえると数値が一致する** — 並べ替え・余りの配り方・訓練データの並びまでそろえたので、6 つの数値が Java 版・Scala 版と完全に一致した

次の章では、正則化で過学習を抑え、検証データでモデルを選ぶ方法を実装します。
