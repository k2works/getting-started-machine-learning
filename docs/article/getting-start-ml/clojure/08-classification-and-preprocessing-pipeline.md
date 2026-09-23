---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "Survived データのグループ別中央値・最頻値の補完とダミー変数化をマルチメソッドの前処理にまとめ、クラスの重みを付けた決定木と EDN によるモデルの保存・読み込みを Clojure で TDD で実装し、Java 版・Scala 版・Tribuo の CART と突き合わせる。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:18:04Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

これまでの章のデータは、欠損値が少なく、特徴量もすべて数値でした。現実のデータはそうはいきません。この章で扱うタイタニック号の乗客データには、次の 3 つの難しさがあります。

- **欠損値が多い** — 年齢が 2 割近く欠けている
- **カテゴリ値がある** — 性別や乗船した港が文字列で記録されている
- **クラスが偏っている** — 生存者より死亡者のほうが多い

この章では、これらを 1 つずつ小さな部品として TDD で実装し、前処理からモデルまでを 1 つの **パイプライン** につなぎます。最後にそのパイプラインをファイルに保存し、読み込んで予測できるようにします。保存したモデルは、第 15 章で作る機械学習 API から使います。

[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) は、scikit-learn の変換器・`Pipeline`・`DecisionTreeClassifier` の `class_weight` を使いました。Clojure 版には、次の 3 つの違いがあります。

- **前処理をマルチメソッドで表す** — 学習済みの前処理を「`:type` の鍵を持つマップ」にし、`fit` と `transform` を `:type` で振り分ける。[Scala 版](../scala/08-classification-and-preprocessing-pipeline.md) が `sealed trait` と case class で書いた ADT にあたるが、**どんな `:type` があるかをコンパイラは数え上げない**
- **クラスの重みを付けた決定木を自作する** — Tribuo の決定木にはクラスの重み付けが無い（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）ので、第 3 章の決定木を重み付きに書き直す。Tribuo とは重み付けなしで突き合わせる
- **モデルは EDN で保存する** — 学習済みのパイプラインがマップ・ベクタ・キーワードだけでできているので、`pr-str` と `clojure.edn/read-string` でそのまま往復できる。Java 版・Kotlin 版の Java シリアライズも、Scala 版の自作のテキスト形式も要らない

実装は [Java 版の第 8 章](../java/08-classification-and-preprocessing-pipeline.md)・[Scala 版の第 8 章](../scala/08-classification-and-preprocessing-pipeline.md) と同じ乱数・同じ手順で分割するので、正解率や見つけた生存者の数も一致するはずです。8.12 節でそれを確かめます。

## 8.2 題材とデータ

### Survived.csv

`apps/data/sukkiri-ml/Survived.csv` は、タイタニック号の乗客 891 人の記録です。列は次のとおりです。

| 列 | 意味 | 型 |
|----|------|----|
| PassengerId | 乗客の番号 | 数値 |
| Survived | 生存（1）か死亡（0）か | 数値（正解ラベル） |
| Pclass | 客室のクラス（1・2・3） | 数値 |
| Sex | 性別（male・female） | 文字列 |
| Age | 年齢 | 数値（欠損あり） |
| SibSp | 同乗した兄弟・配偶者の数 | 数値 |
| Parch | 同乗した親・子の数 | 数値 |
| Ticket | 切符の番号 | 文字列 |
| Fare | 運賃 | 数値 |
| Cabin | 客室の番号 | 文字列（欠損が多い） |
| Embarked | 乗船した港（C・Q・S） | 文字列（欠損あり） |

### 使う特徴量と、使わない列

`PassengerId`・`Ticket`・`Cabin` は使いません。番号は生存と関係がなく、`Cabin` は 891 人中 687 人で欠けているからです。使うのは 7 列です。

```clojure
(def feature-columns
  "モデルに渡す特徴量の列。PassengerId・Ticket・Cabin は使わない。"
  [:Pclass :Sex :Age :SibSp :Parch :Fare :Embarked])
```

列名はキーワードです。第 2 章で決めたとおり、行は「列名のキーワードから文字列へのマップ」で、表は `{:columns [...] :rows [...]}` です。**列の順は `:columns` のベクタが持ち、行のマップの鍵の順には頼りません。** この章では前処理でダミー変数が増えて列が 8 つになるので、順を保たないマップに頼っていたら壊れていました。

### 年齢はグループごとの中央値で補完する

年齢を全体の平均や中央値で埋めると、1 等客室の女性も 3 等客室の男性も同じ年齢になってしまいます。客室のクラスと性別の組ごとに中央値を求め、その組の中央値で埋めます。平均ではなく中央値を使うのは、年齢のような偏った分布では極端な値に引かれにくいためです。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] 年齢をグループごとの中央値で補完する
  - [ ] グループごとに中央値を求める
  - [ ] 訓練データで求めた中央値を別のデータに使う
  - [ ] 訓練データに無いグループは全体の中央値で補完する
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする
  - [ ] 最初のカテゴリを除く
  - [ ] 別のデータにも同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
  - [ ] 重み付きのジニ不純度
  - [ ] balanced の重み
  - [ ] 重み付けなしなら第 3 章の決定木と同じになる
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] モデルを保存して読み込む
- [ ] 正解率と、見つけた生存者の数を求める
- [ ] 実データでクラスの重みの効果を確かめる

ファイルの置き方は第 7 章と同じです。

| ファイル | 名前空間 | 中身 |
|---------|---------|------|
| `src/getting_started_ml/chapter08/transformers.clj` | `…chapter08.transformers` | 3 つの前処理 |
| `src/getting_started_ml/chapter08/tree.clj` | `…chapter08.tree` | クラスの重みを付けた決定木 |
| `src/getting_started_ml/chapter08.clj` | `…chapter08` | パイプライン・保存・評価・`run` |

## 8.4 前処理をマルチメソッドで表す

前処理には 2 つの段階があります。訓練データから値を求める **fit** と、求めた値でデータを変換する **transform** です。Scala 版は `trait Transformer`（fit だけ）と `sealed trait FittedTransformer`（transform だけ）に分け、「fit する前は transform できない」ことを型で保証しました。

Clojure には型がありません。そこで、**前処理を `:type` の鍵を持つマップで表し、`fit` と `transform` をマルチメソッドで `:type` に振り分けます**。

```clojure
;; src/getting_started_ml/chapter08/transformers.clj
(defmulti fit
  "訓練データから変換に必要な値を求め、学習済みの前処理を返す。"
  (fn [transformer _x] (:type transformer)))

(defmulti transform
  "学習済みの前処理でデータを変換する。"
  (fn [fitted _x] (:type fitted)))
```

`defmulti` の第 2 引数は **振り分けの関数** です。引数を受け取って値を返し、その値に合う `defmethod` が呼ばれます。ここでは 1 つ目の引数の `:type` を見ます。

この設計は、Scala 版の ADT と 2 つの点で違います。

| | Scala 版（sealed trait） | Clojure 版（マルチメソッド） |
|---|---|---|
| どんな前処理があるか | コンパイラが数え上げ、`match` の漏れを警告する | 数え上げられない。知らない `:type` を渡すと実行時に「no method」で落ちる |
| 前処理を足す | sealed trait のファイルに case class を足す（1 か所） | どの名前空間からでも `defmethod` を足せる（開いている） |

**閉じているか開いているかの違い** です。Scala 版は「前処理はこの 3 つだけ」と宣言して漏れを防ぎ、Clojure 版は「あとから足せる」代わりに漏れを自分で防ぎます。この章では前処理が 3 つに閉じているので、閉じられる Scala 版のほうが向いています。一方、次の 8.10 節で見るとおり、**マップであることは保存のときに効いてきます**。

`fit` が学習した値を「元のマップに `assoc` して返す」形にしておくと、学習前と学習後が同じ `:type` を持つ 1 つのマップになり、`transform` も同じ振り分けで書けます。

## 8.5 年齢をグループごとの中央値で補完する

### Red: 最初のテスト

```clojure
;; test/getting_started_ml/chapter08/transformers_test.clj
(def ^:private ages
  (table [:Pclass :Sex :Age]
         ["1" "female" "30"]
         ["1" "female" "40"]
         ["3" "male" "10"]
         ["3" "male" "20"]
         ["3" "male" ""]))

(deftest グループの中央値で欠損値を補完する
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)]
    (is (= ["30" "40" "10" "20" "15.0"] (values (tr/transform fitted ages) :Age)))))
```

`table` と `values` は、テストの名前空間に置いた小さな道具です。

```clojure
(defn- table
  "列名と、行ごとの値のベクタから表を作る。"
  [columns & rows]
  {:columns (vec columns) :rows (mapv #(zipmap columns %) rows)})

(defn- values
  "表から列の値を並べて取り出す。"
  [t column]
  (mapv #(get % column) (:rows t)))
```

Scala 版・Java 版はテスト用の `Tables`・`Passengers` というクラスを作りました。Clojure では 2 つの小さな関数で足ります。`zipmap` が「列名と値を組にしてマップにする」ことをそのまま表すので、フィクスチャの表が読みやすい形で書けます。

### Green: 中央値と、グループごとの補完

```clojure
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
```

- `[{:keys [column by] :as transformer} x]` は **分配束縛** です。マップの `:column`・`:by` を同名の名前で取り出しつつ、マップ全体も `transformer` で受け取ります。`assoc` で学習した値を足して返すので、この形が便利です
- `group-by` は「関数の値ごとに要素を集めたマップ」を返します。グループの鍵は `["1" "female"]` のようなベクタです。Clojure のベクタは値として等しさが決まるので、**そのままマップの鍵にできます**。Java 版は `List<String>` を鍵にするために `equals`・`hashCode` の振る舞いに頼り、Kotlin 版は `data class` を作りました
- `update-vals` は「マップの値だけに関数をかける」関数（Clojure 1.11 から）です。Scala 版の `view.mapValues(...).toMap` にあたります

`transform` は、欠けている行だけを差し替えます。

```clojure
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
```

`(get medians グループ overall-median)` の 3 つ目の引数が **既定値** です。訓練データに無いグループが来たら、全体の中央値で補完します。Scala 版の `getOrElse` と同じですが、Clojure では `get` そのものが既定値を取ります。

### 訓練データで求めた値を別のデータに使う

前処理の要点は「**訓練データで求めた値を、テストデータにも使う**」ことです。テストデータから中央値を求めると、テストデータの情報が予測に漏れます。テストで固定します。

```clojure
(deftest 訓練データで求めた中央値を別のデータに使う
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)
        other (table [:Pclass :Sex :Age] ["1" "female" ""])]
    (is (= ["35.0"] (values (tr/transform fitted other) :Age)))))

(deftest 訓練データに無いグループは全体の中央値で補完する
  (let [fitted (tr/fit (tr/group-median-imputer :Age [:Pclass :Sex]) ages)
        other (table [:Pclass :Sex :Age] ["2" "male" ""])]
    ;; 欠けていない年齢は 30, 40, 10, 20 なので全体の中央値は 25.0
    (is (= ["25.0"] (values (tr/transform fitted other) :Age)))))
```

`fit` の結果を変数に取って別のデータに `transform` する、という書き方そのものが、この規律を形にしています。

## 8.6 乗船した港を最頻値で補完する

港は文字列なので、中央値ではなく **最頻値**（いちばん多い値）で補完します。

```clojure
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
```

ここで 1 度失敗しました。最初は `(apply max-key second (sort-by first counts))` と書いたのですが、同数のときのテストが落ちました。

```console
FAIL in (同数なら値の順で前のものを最頻値にする) (transformers_test.clj:57)
expected: (= "C" (:most-frequent fitted))
  actual: (not (= "C" "S"))
```

**`max-key` は同じ値が並んだとき、後ろのほうを返します。** Scala の `maxBy`・Kotlin の `maxByOrNull` は最初のものを返すので、そのまま置き換えると結果が変わります。第 3 章の `majority` で同じ判断をしたときと同様、厳密な不等号（`>`）で畳む形に直しました。「同じなら左を残す」と書いてあることが読めるので、こちらのほうが意図も明確です。

このテストが無ければ、実データで Java 版・Scala 版と数値が食い違ってから原因を探すことになっていました。**同点のときの決め方をテストで固定しておくのは、言語をまたいで結果をそろえるための必須の道具です。**

## 8.7 カテゴリ値をダミー変数にする

決定木は数値しか扱えないので、`Sex`・`Embarked` を 0 と 1 の列に変えます。カテゴリが n 種類なら列は n - 1 本です（最初のカテゴリを除く）。全部作ると 1 本が残りから決まってしまい、列が重複するためです。

```clojure
(deftest カテゴリ値を最初のカテゴリを除いたダミー変数にする
  (let [fitted (tr/fit (tr/dummy-encoder [:Sex :Embarked]) sexes)
        encoded (tr/transform fitted sexes)]
    (is (= [:Sex_male :Embarked_Q :Embarked_S] (:columns encoded)))
    (is (= ["1" "0" "0"] (values encoded :Sex_male)))
    (is (= ["0" "0" "1"] (values encoded :Embarked_Q)))
    (is (= ["1" "0" "0"] (values encoded :Embarked_S)))))
```

学習した値は「カテゴリの並び」です。

```clojure
(defmethod fit :dummy
  [{:keys [columns] :as transformer} x]
  ;; 学習した値は「(列名, カテゴリの並び) の組のベクタ」。マップにすると 9 個目から順が崩れる
  (assoc transformer
         :dummies (mapv (fn [column] [column (vec (rest (categories-of x column)))]) columns)))
```

第 7 章の係数と同じ判断です。**順を保ちたい対応づけはマップにしません。** ここは列が 2 つなので配列マップでも順は保たれますが、「順に頼る値はベクタで持つ」と決めておくほうが、あとで列が増えたときに壊れません。

変換では、列の並びも作り替えます。

```clojure
(defmethod transform :dummy
  [{:keys [dummies]} x]
  {:columns (reduce (fn [columns [column categories]]
                      (into (vec (remove #(= column %) columns))
                            (map #(dummy-column column %))
                            categories))
                    (:columns x)
                    dummies)
   :rows (mapv (fn [row] …) (:rows x))})
```

元の列を取り除き、ダミーの列を末尾に足す、を列ごとに畳み込みます。7 列から始めて次のように変わります。

```text
[:Pclass :Sex :Age :SibSp :Parch :Fare :Embarked]
  → Sex を除いて Sex_male を足す
[:Pclass :Age :SibSp :Parch :Fare :Embarked :Sex_male]
  → Embarked を除いて Embarked_Q :Embarked_S を足す
[:Pclass :Age :SibSp :Parch :Fare :Sex_male :Embarked_Q :Embarked_S]
```

この **8 列の順が、次の節の決定木が分割の候補を試す順** になります。同じ不純度の分割が複数あるときにどれを選ぶかがここで決まるので、Java 版・Scala 版と同じ順にそろえることが、数値を一致させるための条件です。

別のデータにも訓練データと同じ列ができることも固定します。訓練データに無かったカテゴリの列は、すべて 0 になります。

```clojure
(deftest 別のデータにも訓練データと同じダミー変数の列を作る
  (let [fitted (tr/fit (tr/dummy-encoder [:Sex :Embarked]) sexes)
        other (table [:Sex :Embarked] ["female" "S"])
        encoded (tr/transform fitted other)]
    (is (= [:Sex_male :Embarked_Q :Embarked_S] (:columns encoded)))
    (is (= [["0" "0" "1"]] (mapv (juxt :Sex_male :Embarked_Q :Embarked_S) (:rows encoded))))))
```

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

Survived.csv は死亡 549 人・生存 342 人と偏っています。普通に学習すると、決定木は「迷ったら死亡」と予測しがちで、助かった人を見落とします。scikit-learn には `class_weight="balanced"` がありますが、**Tribuo の CART にはクラスの重みを渡す口がありません**（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。そこで、第 3 章で作った決定木を「1 件ごとの重み」を通す形に書き直します。ここは Tribuo に無いので、自作が最終実装になります。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルの件数の割合」で計算しました。重み付きでは、件数の代わりに **重みの合計** で割合を求めます。

```clojure
(defn weighted-gini
  "重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。"
  [labels weights]
  (let [total (reduce + weights)]
    (- 1.0 (reduce + (map (fn [[_ weight]] (let [share (/ weight total)] (* share share)))
                          (weight-sums labels weights))))))
```

`weight-sums` は「ラベルごとの重みの合計」を、**ラベルが先に現れた順のベクタ** で返します。

```clojure
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
```

Scala 版は `ListMap`（挿入順を保つマップ）を使いました。Clojure の標準ライブラリに順を保つマップはないので、ベクタで書きます。この章のラベルは 0 と 1 の 2 種類だけなので、線形に探す実装で十分です。

不純度そのものは順に左右されませんが、**同じ重みのときにどのラベルを葉にするか** は順で決まります。そこが Java 版・Scala 版と一致する条件です。

```clojure
(deftest 重み付きのジニ不純度は重みがすべて一なら第三章のジニ不純度と同じになる
  (let [labels [0 0 1 1 1]]
    (is (near? (chapter03/gini labels) (tree/weighted-gini labels (vec (repeat 5 1.0)))))))

(deftest 重みの合計が同じなら先に現れたラベルを選ぶ
  (is (= 0 (tree/weighted-majority [0 1] [1.0 1.0]))))
```

1 本目は「新しい実装が古い実装を含む」ことを固定するテストです。第 3 章の `gini` をそのまま参照できるので、書き直しの安全網になります。

### balanced の重み

`balanced` は、クラスの件数に反比例する重みです。

```clojure
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
```

重みの付け方はキーワード（`:none`・`:balanced`）で表します。Scala 版の `enum ClassWeight`、Java 版の `enum` にあたりますが、**キーワードなので「知らない値」を渡せます**。`case` は該当しなければ例外を投げるので、実行時には気づけます。コンパイル時に数え上げられないのは 8.4 節と同じ性質です。

### 重み付けなしなら第 3 章の決定木と同じ

重みをすべて 1 にしたときに第 3 章と同じ木になることを、小さなデータで固定します。

```clojure
(deftest 重み付けなしなら第三章の決定木と同じ木になる
  (let [ours (tree/fit x t [:a :b] 2 :none)
        theirs (chapter03/fit x (mapv str t) [:a :b] 2)]
    (is (= (mapv str (tree/predict ours x)) (chapter03/predict theirs x)))))
```

第 3 章の決定木はラベルが文字列、この章は整数（0 と 1）なので、比べるときに `str` で合わせます。整数のままにしたのは、`Survived` が 0 か 1 の 2 値で、生存者の数を数えるときに整数のほうが素直だからです。

### balanced で予測が変わる

```clojure
(deftest balancedにすると少数派のラベルを予測しやすくなる
  ;; 1 が 2 件、0 が 5 件。深さ 1 の木では、重みを付けないとどの葉も多数派の 0 になる
  (let [x2 (mapv #(hash-map :a (double %)) (range 1 8))
        t2 [0 0 1 0 1 0 0]]
    (is (= [0 0 0 0 0 0 0] (tree/predict (tree/fit x2 t2 [:a] 1 :none) x2)))
    (is (= [1 1 1 1 1 0 0] (tree/predict (tree/fit x2 t2 [:a] 1 :balanced) x2)))))
```

重みを付けないと、深さ 1 の木ではどちらの葉も多数派の 0 になり、少数派を 1 件も当てられません。`balanced` にすると少数派の重みが 2.5 倍になり、左の葉が 1 になります。

このテストは、最初に書いた期待値が外れて 1 度落ちました。小さなデータでも「どこで分割されるか」は手で追いにくいので、**実際に動かして観察してから期待値を決め、そのうえでその値を固定する** という進め方をしています。予測ではなく観察に基づく期待値なので、TDD の Red としては弱い形ですが、木の形そのものを後から変えないための杭にはなります。

## 8.9 前処理とモデルをパイプラインにつなぐ

### 前処理の順番と fit の順番

パイプラインは、前処理の並びとモデルの設定を持つマップです。

```clojure
(defn build-pipeline
  "Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。"
  [max-depth class-weight]
  {:transformers [(tr/group-median-imputer :Age [:Pclass :Sex])
                  (tr/most-frequent-imputer :Embarked)
                  (tr/dummy-encoder [:Sex :Embarked])]
   :max-depth max-depth
   :class-weight class-weight})
```

学習では、**前の前処理で変換したデータで次の前処理を fit します**。ダミー変数化は「港を補完したあとのデータ」で fit しなければ、欠損値がカテゴリとして混ざってしまいます。

```clojure
(defn fit
  "訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。"
  [pipeline x t]
  (let [[fitted prepared]
        (reduce (fn [[fitted prepared] transformer]
                  (let [f (tr/fit transformer prepared)]
                    [(conj fitted f) (tr/transform f prepared)]))
                [[] x]
                (:transformers pipeline))]
    {:transformers fitted
     :columns (:columns prepared)
     :tree (tree/fit (to-features prepared) t (:columns prepared)
                     (:max-depth pipeline) (:class-weight pipeline))}))
```

`reduce` の状態を「学習済みの前処理のベクタ」と「変換済みのデータ」の組にしています。Scala 版の `foldLeft` と同じ形です。

学習済みのパイプラインが `:columns` を持つのは Clojure 版だけの都合です。決定木が分割の候補を試す順は列の並びで決まるので、**予測のときにも学習したときと同じ並びを使う** 必要があります。Scala 版は `Features` が列名を持っているのでこの鍵が要りませんでした。

### 予測では transform だけを使う

```clojure
(defn transform
  "学習済みの前処理を順に合成して、データを変換する。"
  [fitted-pipeline x]
  (reduce (fn [prepared f] (tr/transform f prepared)) x (:transformers fitted-pipeline)))
```

Scala 版は `transformers.map(_.transform).foldLeft(identity[Table])(_ andThen _)` と書き、「変換の関数を合成してから 1 回かける」形にしました。Clojure では `reduce` でデータに順に適用します。`(apply comp (reverse (map …)))` で関数を合成することもできますが、**中間の関数を作らないぶん `reduce` のほうが読みやすい** ので、こちらを選びました。

### 前処理が済んだ表を特徴量にする

```clojure
(defn to-features
  "前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。"
  [x]
  (mapv (fn [row]
          (into {} (map (fn [column]
                          (if-let [value (chapter02/number row column)]
                            [column value]
                            (throw (IllegalArgumentException.
                                    (str "欠損値が残っています: " (name column)))))))
                (:columns x)))
        (:rows x)))
```

Scala 版は「補完の結果が `Features` 型である」ことで欠損値が無いことを保証しました。Clojure では型がないので、ここで数えて確かめます。`if-let` は「`nil` でなければ束縛する」ので、`chapter02/number` が欠損値に `nil` を返す規約とそのまま噛み合います。

```clojure
(deftest 欠損値が残っていれば特徴量にできない
  (is (thrown? IllegalArgumentException
               (ch/to-features {:columns [:Age] :rows [{:Age ""}]}))))
```

### 欠損値を含むデータで学習して予測する

パイプライン全体を、年齢と港が欠けた小さなデータで確かめます。

```clojure
(deftest 前処理の順に学習して欠損値の残らない特徴量にする
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)]
    (is (= [:Pclass :Age :SibSp :Parch :Fare :Sex_male :Embarked_S] (:columns fitted)))
    (is (every? (fn [row] (every? #(number? (get row %)) (:columns fitted)))
                (ch/features fitted train-x)))))

(deftest 学習済みのパイプラインで欠損値を含むデータを予測する
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)]
    (is (= [1 0] (ch/predict fitted new-passengers)))))
```

年齢が空欄の架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）でも、前処理が年齢と港を埋めてから木に渡すので、予測できます。

## 8.10 モデルを EDN で保存して読み込む

### マップとベクタなら、そのまま書ける

学習済みのパイプラインは、マップ・ベクタ・キーワード・文字列・数値だけでできています。**EDN（Extensible Data Notation）はこれらをそのまま書き出して読み戻せる** ので、保存は `pr-str`、読み込みは `clojure.edn/read-string` の 1 行ずつです。

```clojure
(def ^:private format-version
  "形式の版。読み込むときに確かめる。"
  1)

(defn save-model
  "学習済みのパイプライン（前処理で求めた値とモデル）を EDN で保存する。"
  [fitted-pipeline model-file]
  (io/make-parents model-file)
  (spit model-file (pr-str (assoc fitted-pipeline :format format-version))))

(defn load-model
  "保存したパイプラインを読み込む。形式が違えば失敗する。"
  [model-file]
  (let [loaded (edn/read-string (slurp model-file))]
    (when-not (= format-version (:format loaded))
      (throw (IllegalArgumentException.
              (str "対応していない形式のモデルです: " (:format loaded)))))
    (dissoc loaded :format)))
```

**`read-string` ではなく `clojure.edn/read-string` を使う** のが要点です。`clojure.core/read-string` は読み込み時に評価を起こせるので、信頼できないファイルを読むと任意のコードが動きます。`clojure.edn/read-string` はデータしか読まないので安全です。Java 版・Kotlin 版が Java シリアライズの危険に対して `ObjectInputFilter` でクラスを絞ったのと、同じ問題への別の答えです。

保存したファイルは、そのまま読めます。8.9 節の小さな訓練データで学習したパイプラインの中身です。

```clojure
{:transformers
 [{:type :group-median, :column :Age, :by [:Pclass :Sex],
   :medians {["1" "female"] 35.0, ["3" "male"] 20.0}, :overall-median 30.0}
  {:type :most-frequent, :column :Embarked, :most-frequent "S"}
  {:type :dummy, :columns [:Sex :Embarked], :dummies [[:Sex ["male"]] [:Embarked ["S"]]]}],
 :columns [:Pclass :Age :SibSp :Parch :Fare :Sex_male :Embarked_S],
 :tree {:split {:feature :Pclass, :threshold 2.0, :impurity 0.0},
        :left {:label 1}, :right {:label 0}},
 :format 1}
```

前処理が学習した中央値も最頻値もカテゴリも、木の境界も、**そのまま目で読めます**。Scala 版は ADT をタブ区切りのテキストに書き出す変換と、それを読み戻す構文解析の両方を自分で書きました（行きがけ順のトークン列から木を組み立てる処理を含めて 100 行ほど）。Clojure ではその両方が要りません。

これは 8.4 節で「マルチメソッドは前処理を数え上げられない」と書いたことの裏返しです。**前処理をデータ（マップ）として表したから、保存も比較もそのままできます。** 型で守るか、データとして扱えるようにするかのトレードオフが、章の最初と最後で 1 往復した形です。

### 往復することをテストする

```clojure
(deftest 保存して読み込んだパイプラインは同じ予測をする
  (let [fitted (ch/fit (ch/build-pipeline 3 :balanced) train-x train-t)
        model-file (io/file (System/getProperty "java.io.tmpdir")
                            (str "survived-" (System/nanoTime) ".model"))]
    (try
      (ch/save-model fitted model-file)
      (is (= fitted (ch/load-model model-file)))
      (is (= (ch/predict fitted new-passengers)
             (ch/predict (ch/load-model model-file) new-passengers)))
      (finally (io/delete-file model-file true)))))
```

`(is (= fitted (ch/load-model model-file)))` の 1 行で、**パイプライン全体が値として等しいこと** を確かめています。Scala 版・Java 版は「予測が一致すること」で間接的に確かめました。Clojure ではモデルがただのマップなので、直接比べられます。関数やクラスのインスタンスを含んでいたら、この比較は書けませんでした。

形式の版を確かめることもテストで固定します。

```clojure
(deftest 形式の版が違うモデルは読み込めない
  (let [model-file (io/file (System/getProperty "java.io.tmpdir")
                            (str "broken-" (System/nanoTime) ".model"))]
    (try
      (spit model-file (pr-str {:format 999}))
      (is (thrown? IllegalArgumentException (ch/load-model model-file)))
      (finally (io/delete-file model-file true)))))
```

保存先はテストごとに一時ディレクトリの別名にし、`finally` で消します。`run` が保存先を引数で受け取れるようにしてあるのは、このためです。

```clojure
(defn run
  ([] (run model-file))
  ([model-file] …))
```

## 8.11 評価する

正解率だけでは、クラスの重みの効果が見えません。「テストデータの生存者のうち、何人を生存と予測できたか」も数えます。

```clojure
(defn evaluate
  "学習済みのパイプラインを、訓練データとテストデータで評価する。"
  [fitted-pipeline split]
  (let [predictions (predict fitted-pipeline (features-table (:x-test split)))
        labels (:t-test split)]
    {:train-accuracy (accuracy (predict fitted-pipeline (features-table (:x-train split)))
                               (:t-train split))
     :test-accuracy (accuracy predictions labels)
     :found-survivors (count (filter (fn [[p l]] (and (= survived p) (= survived l)))
                                     (map vector predictions labels)))
     :survivors (count (filter #(= survived %) labels))}))
```

評価の結果もマップなので、テストの表明は 1 行です。

```clojure
(deftest 正解率と見つけた生存者の数を求める
  (let [fitted (ch/fit (ch/build-pipeline 3 :none) train-x train-t)
        split {:x-train (:rows train-x) :t-train train-t
               :x-test (:rows new-passengers) :t-test [1 1]}]
    (is (= {:train-accuracy 1.0 :test-accuracy 0.5 :found-survivors 1 :survivors 2}
           (ch/evaluate fitted split)))))
```

Scala 版は `case class Evaluation` を、Java 版は `record` を作って同じことを書きました。Clojure ではマップリテラルがそのまま期待値になります。落ちたときは、`clojure.test` が `(not (= 期待のマップ 実際のマップ))` の形で両方を並べるので、どの鍵が違うかは自分で見比べることになります。

## 8.12 実データでクラスの重みの効果を確かめる

### 実行する

```console
$ clojure -M:run chapter08
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見
classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見
保存したモデル: survived.model
架空の乗客の予測: [1 0]
```

深さ 5 の木で、`balanced` にすると正解率はわずかに上がり（0.799 → 0.804）、見つけた生存者は 59 人から 65 人に増えました。年齢の分からない架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）は、生存・死亡と予測されました。

### Java 版・Scala 版と数値が一致するか

分割は第 2 章で `java.util.Random` と Fisher-Yates を使う形にそろえてあるので、Java 版・Scala 版とまったく同じ行が訓練データとテストデータに入ります。実測した結果は次のとおりで、**すべて一致しました**。

| 指標 | Clojure 版 | Java 版・Scala 版 |
|------|-----------|------------------|
| データ件数（生存・死亡） | 891（342・549） | 891（342・549） |
| 訓練・テストの件数 | 712・179 | 712・179 |
| 深さ 5・重み付けなしの正解率（訓練・テスト） | 0.854・0.799 | 0.854・0.799 |
| 深さ 5・balanced の正解率（訓練・テスト） | 0.848・0.804 | 0.848・0.804 |
| 深さ 5 で見つけた生存者（79 人中） | 59 人 → 65 人 | 59 人 → 65 人 |
| 深さ 2 で見つけた生存者（79 人中） | 41 人 → 68 人 | 41 人 → 68 人 |
| Tribuo の CART と予測が違う件数（深さ 1〜10） | 0,0,0,0,2,0,0,0,1,0 | 0,0,0,0,2,0,0,0,1,0 |
| 架空の乗客 2 人の予測 | [1 0] | 同じ |

Kotlin 版は `kotlin.random.Random` を使うので分割が違い、深さ 5 では `balanced` が見落としを減らしませんでした。同じアルゴリズムでも、1 回の分割の結果から「この設定のほうが良い」と一般化してはいけない、ということです。

一致には、次の 3 つの「同点のときの決め方」がすべてそろっている必要がありました。どれか 1 つでもずれると、深さの深い木で予測が食い違います。

| 場面 | 決め方 | Clojure での書き方 |
|------|-------|------------------|
| 最頻値が同数 | 値の順で前のもの | `sort-by` してから `>` で畳む（`max-key` は不可） |
| 分割の不純度が同じ | 列の順で前のもの | `mapcat` で列の順に候補を並べ、`<` で畳む |
| 葉の重みの合計が同じ | 先に現れたラベル | 挿入順のベクタで合計を持つ |

### 効果は深さによって変わる

深さ 2 の木では、効果がはっきり出ます。

```clojure
(deftest 深さ二ではbalancedにすると見つけられる生存者が四十一人から六十八人に増える
  (when (data?)
    (let [split (survived-split)]
      (is (= 41 (:found-survivors (ch/evaluate (fit-pipeline split 2 :none) split))))
      (is (= 68 (:found-survivors (ch/evaluate (fit-pipeline split 2 :balanced) split)))))))
```

浅い木は葉が大きく、多数派に引きずられやすいので、重みを変えた効果が出やすくなります。深い木では葉が小さくなり、重みを付けなくても少数のクラスだけの葉ができるので、差は小さくなります。

### 第 3 章の決定木・Tribuo との突き合わせ

前処理の結果を、第 3 章の決定木と Tribuo の CART にも渡します。

```clojure
(deftest 重み付けなしなら前処理後のテストデータで第三章の決定木と予測が一致する
  (when (data?)
    (let [split (survived-split)]
      (doseq [max-depth (range 1 11)]
        (let [pipeline (fit-pipeline split max-depth :none)
              x-train (ch/features pipeline (ch/features-table (:x-train split)))
              x-test (ch/features pipeline (ch/features-table (:x-test split)))
              theirs (chapter03/predict
                      (chapter03/fit x-train (mapv str (:t-train split))
                                     (:columns pipeline) max-depth)
                      x-test)]
          (is (= (mapv str (ch/predict pipeline (ch/features-table (:x-test split)))) theirs)
              (str "深さ " max-depth)))))))
```

Tribuo との違いは深さ 5 で 2 件、深さ 9 で 1 件だけでした。第 3 章で確かめたとおり、不純度が同じ分割候補の選び方の違いによるもので、実装の誤りではありません（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。前処理が入ってもこの性質が変わらないことを、深さ 1〜10 の件数のベクタで固定しておきます。

```clojure
(deftest TribuoのCARTと予測が違うのは深さ五で二件と深さ九で一件だけ
  (when (data?)
    (let [split (survived-split)]
      (is (= [0 0 0 0 2 0 0 0 1 0]
             (mapv (fn [max-depth] …) (range 1 11)))))))
```

**この 10 個の数字が Java 版・Scala 版と同じであることが、この章でいちばん強い突き合わせです。** 前処理で作った 8 列の中身と順、決定木の分割の選び方、Tribuo への値の渡し方のすべてがそろっていなければ、同じにはなりません。

### 実データのテスト

実データを使うテストは、第 3 章・第 7 章と同じく「データが無ければ理由を標準エラーに出して早く戻る」形です。

```clojure
(defn- data?
  []
  (or (.exists (java.io.File. ^String @csv-file))
      (binding [*out* *err*]
        (println "学習データ Survived.csv が配置されていない（gulp data:setup）のでスキップする")
        false)))
```

`(when (data?) …)` で包むので、データが無ければ表明が 0 個で成功します。

## 8.13 品質チェック

```console
$ cljfmt check src test && clj-kondo --lint src test --fail-level warning && clojure -M:test
linting took 374ms, errors: 0, warnings: 0
…
Ran 86 tests containing 169 assertions.
0 failures, 0 errors.
```

```console
$ clojure -M:coverage
|-------------------------------------------+---------+---------|
|                                 Namespace | % Forms | % Lines |
|-------------------------------------------+---------+---------|
|              getting-started-ml.chapter08 |   99.35 |   98.94 |
| getting-started-ml.chapter08.transformers |  100.00 |  100.00 |
|         getting-started-ml.chapter08.tree |   98.12 |  100.00 |
|-------------------------------------------+---------+---------|
```

## 8.14 探索と可視化

クラス分布、性別・客室クラス別の生存率、木の深さとクラスの重み、混同行列、分割に使われた特徴量の探索と可視化は、[Python 版の 8.13 節](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版の 8.13 節](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。Clojure 版では、深さごとの結果を 8.12 節の表とテストにまとめました。

## 8.15 まとめ

この章では、欠損値・カテゴリ値・クラスの偏りを含む現実的なデータで、前処理からモデルの保存までを TDD で実装しました。

1. **前処理はデータ、振り分けはマルチメソッド** — `:type` を持つマップにして `fit`・`transform` を `defmulti` で振り分けた。Scala 版の `sealed trait` と違って数え上げられないが、**どこからでも足せる** 開いた設計になった
2. **データだから保存が 2 行で済む** — 学習済みのパイプラインがマップとベクタだけなので、`pr-str` と `clojure.edn/read-string` でそのまま往復できた。Scala 版が 100 行ほど書いた書き出しと読み取りが消え、保存したファイルは目で読める。`clojure.edn/read-string` を使うことで、Java シリアライズの危険も避けられる
3. **モデルを値として比べられる** — 保存と読み込みのテストが `(= fitted (load-model …))` の 1 行になった。関数やインスタンスを含まないモデルの利点がそのまま出た
4. **順を保ちたい対応づけはベクタで持つ** — 列の並び・ダミーのカテゴリ・ラベルごとの重みの合計。マップは 9 要素を超えると順を保たないので、順に頼るところではベクタにする。学習済みのパイプラインが `:columns` を持つのもこのため
5. **同点のときの決め方を先にテストで固定する** — `max-key` が同数のとき後ろを返すことに、小さなテストで気づけた。最頻値・分割・葉のラベルの 3 か所すべてで「先を選ぶ」ことをそろえたので、実データの数値が Java 版・Scala 版と一致した
6. **1 回の分割の結果を一般化しない** — 分割が同じ Java 版・Scala 版とは数値がすべて一致したが、乱数の違う Kotlin 版では深さ 5 の `balanced` の効果が出なかった。深さによっても効果は変わる

第 15 章の API は、この章の `build-pipeline` で学習して `save-model` で保存したパイプラインを `load-model` で読み込み、`features-table` で作った表を `predict` に渡して予測します。公開している入口は次の 5 つです。

| 入口 | 役割 |
|-----|------|
| `feature-columns` / `features-table` / `target-labels` | 特徴量の列の表と正解ラベルを作る |
| `build-pipeline` | この章の前処理とモデルの並びを作る |
| `fit` | 訓練データで学習する |
| `save-model` / `load-model` | 学習済みのパイプラインを保存・読み込みする |
| `predict` / `features` | 前処理をしてから予測する／前処理だけを行う |

次の章では、住宅価格のデータを使い、標準化や多項式特徴量など、特徴量そのものを作り変える方法を学びます。
