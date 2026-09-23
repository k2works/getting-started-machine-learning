---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "素のマップとベクタで表を自作し、nil で欠損値を表して iris データを前処理し、java.util.Random と Fisher-Yates による訓練・テストデータ分割を Clojure の TDD で実装する。get の既定値が先に評価される落とし穴も扱う。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:54:53Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Scala 版の第 2 章](../scala/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。Python 版は pandas、Kotlin 版は Kotlin DataFrame を使いましたが、Clojure 版は Java 版・Scala 版と同じく **データフレームのライブラリを使わず**、小さな表を自分で組み立てます。Clojure では、その表が**素のマップとベクタ**になります。次の 3 点に注目してください。

- **`nil` で欠損値を表す** — Clojure には `Option` も `Optional` もありません。Ruby 版・Python 版と同じ `nil`／`None` の世界ですが、`keep` や `or` といった「`nil` を前提にした道具」が標準でそろっているのが違いです
- **マップは値で比べられる** — Java 版・C# 版は「`double` の配列を包んで `equals` を自分で書く」工夫が要りましたが、Clojure のマップとベクタは宣言なしで中身が比較されます。第 1 章と同じ性質がここでも効きます
- **Java 版・Scala 版と同じ乱数で分ける** — 分割に `java.util.Random` と Fisher-Yates を使うので、並べ替えの結果も、そこから求まる平均値も Java 版・Scala 版と一致します。標準の `shuffle` を使わない理由がここにあります

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Clojure 版での読み方 |
|----|------|------------------|
| がく片長さ | がく片の長さ | `(number row :がく片長さ)` → `double` か `nil` |
| がく片幅 | がく片の幅 | `(number row :がく片幅)` → `double` か `nil` |
| 花弁長さ | 花弁の長さ | `(number row :花弁長さ)` → `double` か `nil` |
| 花弁幅 | 花弁の幅 | `(number row :花弁幅)` → `double` か `nil` |
| 種類 | 品種（3 種類が 50 件ずつ） | `(text row :種類)` → 文字列 |

特徴量の 4 列には合わせて 7 件の欠損値があります。Clojure には「値があるかもしれない」を表す型がないので、**無いことは `nil` で表します**。

| 言語 | 欠損値の表し方 | 値を取り出す |
|------|--------------|------------|
| **Clojure** | **`nil`** | **`or`・`keep`・`some?`・`when-let`** |
| Scala | `Option[Double]`（`Some`／`None`） | `getOrElse`・`map`・`flatMap`・`match` |
| F# | `float option`（`Some`／`None`） | `Option.defaultValue`・`match` |
| Java | `OptionalDouble` | `orElse`・`ifPresent` |
| Ruby・Python | `nil`／`None` | `\|\|`・`or`・`compact`／`is None` |

型で表せないぶん弱いのは確かです。そのかわり、Clojure の標準のコレクション関数は **`nil` を第一級の値として扱う**ように作られています。この章で使う `keep`（`nil` を落としながら写す）と `or`（`nil` なら次を返す）がその例で、`Option` の `flatMap`・`getOrElse` に当たる働きを、包みを剥がす手間なしにします。

### 訓練データとテストデータ

学習に使うデータでそのまま性能を測ると、「答えを覚えただけ」のモデルを高く評価してしまいます。そこでデータを 2 つに分けます。この章では 150 件を訓練データ 105 件・テストデータ 45 件に分けます。

### Java 版・Scala 版と数値が一致する理由

分割の前に並べ替えますが、この並べ替えに **`java.util.Random` と Fisher-Yates** を使います。`java.util.Random` は乱数の作り方が仕様として決まっているので、同じシードなら JVM 上のどの言語からでも同じ数列が出ます。Java 版・Scala 版と同じ手順で並べ替えれば、**訓練データに入る行が一致し、そこから求まる平均値も一致します**。この章の終わりで実際に確かめます。

Clojure には `shuffle` がありますが、これは使えません。`(shuffle coll)` は内部で `java.util.Collections/shuffle` を引数 1 つで呼ぶので、**シードを渡す口がありません**。再現性が要る場面では自分で書く、という判断になります。

## 2.3 開発環境の準備

第 1 章で作った `apps/clojure/` にファイルを足します。

```text
apps/clojure/
├── deps.edn
├── src/
│   └── getting_started_ml/
│       ├── dataset.clj
│       ├── chapter01.clj
│       ├── chapter02.clj    # この章
│       └── main.clj
└── test/
    └── getting_started_ml/
        ├── chapter02_test.clj
        └── iris_data_test.clj  # 実データを使うテスト
```

依存は第 1 章のまま（`org.clojure/data.csv` だけ）で足ります。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] 表を読み込む
  - [ ] 数値の列を読む。空欄は欠損値にする
  - [ ] 数値として読めない値・列の不足を弾く
  - [ ] 列ごとに欠損値の数を数える
- [ ] 平均値で欠損値を補完する
  - [ ] 欠損値を除いて列ごとの平均値を求める
  - [ ] 欠損値を平均値で置き換える
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] 指定した割合で分ける
  - [ ] 特徴量と正解ラベルの対応を崩さない
  - [ ] シードを指定すれば同じ分け方になる
- [ ] 前処理をまとめる
- [ ] 実データで前処理の結果を表示する

## 2.5 不変の表で読み込む

### データの表し方を決める

最初に決めるのは、**表をどう表すか**です。第 1 章では「人物」という 1 つのマップにしましたが、この章では列の並びも扱うので、表そのものが必要になります。

| 対象 | 表し方 |
|------|--------|
| 読み込んだ行 | 列名のキーワードから文字列へのマップ（`{:がく片長さ "5.1" :種類 "Iris-setosa"}`） |
| 表 | `{:columns [キーワード...] :rows [行...]}` |
| 補完した後の特徴量 | 列名のキーワードから `double` へのマップ（`{:がく片長さ 5.1 ...}`） |
| 分割の結果 | `{:x-train ... :x-test ... :t-train ... :t-test ...}` |

Scala 版は `Row`・`Table`・`Features`・`TrainTestSplit` の 4 つの `case class` を宣言しました。Clojure 版はどれも素のマップです。**新しい型を宣言しない**ことの意味は 2 つあります。

1. すべてのコレクション関数がそのまま使える。`Table` 用の `map` を書く必要はありません
2. 何が入っているかはコードとテストからしか分からない。`Features` と `Row` がどちらもマップなので、**「補完済みかどうか」を型で区別できません**。Scala 版が `Row` と `Features` を分けて「欠損値が残っていないこと」を戻り値の型で保証したところは、Clojure 版ではテストで保証することになります

表を `{:columns ... :rows ...}` にしたのは、**列の順を保つ**ためです。マップの鍵の並びは保証されないので（8 個を超えるとハッシュマップになり、並びが変わります）、「がく片長さ、がく片幅、花弁長さ、花弁幅、種類」の順で出力したいなら、その順をベクタで別に持つしかありません。

### テストファースト

セルを読むところから始めます。

```clojure
(deftest 行の読み出し
  (testing "数値の列を読む"
    (is (== 5.1 (ch/number {:がく片長さ "5.1"} :がく片長さ))))
  (testing "空欄の列は欠損値になる"
    (is (nil? (ch/number {:がく片長さ ""} :がく片長さ)))
    (is (ch/missing? {:がく片長さ ""} :がく片長さ)))
  (testing "数値として読めない列は失敗する"
    (is (thrown-with-msg? IllegalArgumentException #"がく片長さ を数値として読めません: たくさん"
                          (ch/number {:がく片長さ "たくさん"} :がく片長さ))))
  (testing "列が無ければ失敗する"
    (is (thrown-with-msg? IllegalArgumentException #"列がありません: 花弁幅"
                          (ch/text {:がく片長さ "5.1"} :花弁幅))))
  (testing "文字列の列を読む"
    (is (= "Iris-setosa" (ch/text {:種類 "Iris-setosa"} :種類)))))
```

**テストが行のリテラルをそのまま書けます。** 素のマップなのでコンストラクタも組み立て関数も要らず、`{:がく片長さ "5.1"}` と書けば 1 行です。型を宣言する言語では、この 1 行のためにフィクスチャの用意が要ります。動的型付けの代償を払っているぶんの見返りが、ここに出ています。

### Green: 明白な実装

```clojure
(defn text
  "文字列の列を読む。列が無ければ失敗する。"
  [row column]
  (if (contains? row column)
    (get row column)
    (throw (IllegalArgumentException. (str "列がありません: " (name column))))))

(defn missing?
  "セルが空欄かどうかを返す。"
  [row column]
  (str/blank? (text row column)))

(defn number
  "数値の列を読む。空欄なら nil を返す。数値として読めなければ失敗する。
   Clojure には Option が無いので、欠損値は nil で表す。"
  [row column]
  (let [cell (str/trim (text row column))]
    (when-not (str/blank? cell)
      (try
        (Double/parseDouble cell)
        (catch NumberFormatException _
          (throw (IllegalArgumentException.
                  (str (name column) " を数値として読めません: " cell))))))))
```

読みどころは `number` の `when-not` です。**`when-not` は条件が偽のときに本体を評価し、真なら `nil` を返します。** つまり「空欄なら `nil`」が、`else` 節を書かずに済みます。Scala 版の `if cell.trim.isEmpty then None else Some(...)` に当たる形が、`nil` を暗黙の戻り値にすることで片側だけの式になりました。

`if`／`when` が式であること、そして **`nil` が「無い」の自然な表現であること**が組み合わさると、こういう省き方ができます。読みやすさは慣れ次第で、「`when` は `nil` を返しうる」を知らないと戻り値が見えません。

`missing?` の末尾の `?` は「真偽値を返す」という Clojure の命名の慣習です（Ruby の `?` と同じ）。`(str/blank? ...)` も同じ慣習に沿っています。

### 行末の空欄の落とし穴

Scala 版・Java 版は、ここで `split(",", -1)` の `-1` に苦しみました。`"4,5,"` を素直に `split` すると末尾の空文字列が落ち、列の数が合わなくなるからです。

**`clojure.data.csv` ではこの問題は起きません。** 確かめました。

```clojure
(spit "/tmp/trail.csv" "﻿a,b,c\n1,,3\n4,5,\n")
(with-open [r (io/reader "/tmp/trail.csv")]
  (println "行 =" (pr-str (vec (rest (csv/read-csv r))))))
```

```text
行 = [["1" "" "3"] ["4" "5" ""]]
```

途中の空欄も行末の空欄も、空文字列として残っています。`clojure.data.csv` は文字列を分割するのではなく CSV として解析するので、`split` の都合に振り回されません。**「文字列操作で済ませる」か「解析器を使う」かの違い**で、後者を選んだぶんの手間がここで返ってきています。

いっぽうで、第 1 章で見た BOM は相変わらず取り除いてくれません（上の出力でも列名は省いていますが、先頭には BOM が残ります）。**取り除いてくれるものと、くれないものが同じライブラリの中に混ざる**ので、どちらなのかは実際に動かして確かめるしかありません。

### 表として読み込む

```clojure
(def ^:private bom
  "UTF-8 の BOM。data.csv は取り除かないので、先頭の列名から自分で取り除く。"
  "﻿")

(defn load-table
  "CSV を読み込んで表にする。列の順は CSV の順のまま。"
  [csv-file]
  (with-open [reader (io/reader csv-file)]
    (let [[header & rows] (csv/read-csv reader)
          columns (mapv #(keyword (str/replace-first % bom "")) header)]
      {:columns columns
       :rows (mapv #(zipmap columns %) rows)})))
```

第 1 章の `load-people` とほぼ同じ形ですが、2 つ違います。

- **`columns` を `mapv` にしました。** 第 1 章は `map`（遅延）のままでしたが、ここでは列の並びを表に入れて外へ持ち出すので、`with-open` を抜ける前に確定させる必要があります。遅延シーケンスと `with-open` の組み合わせは第 1 章で踏んだ落とし穴で、**値を外に返す形に変えた瞬間にまた効いてきます**
- **行を人物に変換しません。** `zipmap` で列名を付けるところまでで止め、数値への変換は後の工程（`fill-missing`）に回しています。欠損値の補完に平均値が要り、その平均値は訓練データからしか計算できないので、**「読む」と「数値にする」を分けないと順番が組めない**からです

### 列ごとの欠損値の数

```clojure
(defn count-missing
  "列ごとに欠損値の数を数える。列の順は表の列の順のまま。"
  [{:keys [columns rows]}]
  (mapv (fn [column] [column (count (filter #(missing? % column) rows))]) columns))
```

`[{:keys [columns rows]}]` が**分配束縛（destructuring）**です。引数のマップから `:columns` と `:rows` を取り出して、同じ名前の局所変数に束縛します。`(let [columns (:columns table) rows (:rows table)] ...)` を 1 行にしたもので、**マップを引数に取る関数がこれだけ読みやすくなる**のは、素のマップで通す設計の見返りです。

戻り値をマップではなくベクタのベクタ（`[[:がく片長さ 2] [:がく片幅 1] ...]`）にしているのは、ここでも**順を保つため**です。

```clojure
(deftest 欠損値を数える
  (testing "列ごとに欠損値を数える。列の順は表の列の順のまま"
    (let [table {:columns [:がく片長さ :種類]
                 :rows [{:がく片長さ "5.1" :種類 "Iris-setosa"}
                        {:がく片長さ "" :種類 "Iris-setosa"}
                        {:がく片長さ "" :種類 "Iris-virginica"}]}]
      (is (= [[:がく片長さ 2] [:種類 0]] (ch/count-missing table))))))
```

期待値が `[[:がく片長さ 2] [:種類 0]]` とそのまま書けます。ベクタもキーワードも値で比べられるので、比較のための道具は何も要りません。

## 2.6 平均値で欠損値を補完する

### 平均値を求める

```clojure
(defn column-means
  "欠損値を除いて、列ごとの平均値を求める。"
  [rows columns]
  (into {} (map (fn [column]
                  (let [values (keep #(number % column) rows)]
                    (when (empty? values)
                      (throw (IllegalArgumentException. (str "値がすべて空欄です: " (name column)))))
                    [column (/ (reduce + values) (count values))])))
        columns))
```

**`keep` が、この章でいちばん Clojure らしい関数です。** `(keep f coll)` は `f` を各要素に適用し、**結果が `nil` でないものだけ**を残します。`map` してから `filter some?` するのを 1 つにしたもので、Scala 版の `rows.flatMap(_.number(column))`（`Option` を平らにする）と同じ働きです。

```clojure
(keep #(number % column) rows)   ; Clojure: nil が落ちる
rows.flatMap(_.number(column))   // Scala: None が落ちる
```

**「欠損値を除いて集計する」が 1 つの関数で書ける**という点で、`nil` は `Option` に負けていません。負けるのは「除き忘れたときに気づけるかどうか」で、Scala なら `Option[Double]` を `+` に渡した時点でコンパイルが止まりますが、Clojure は実行して `NullPointerException` が出るまで分かりません。

`(into {} (map f) columns)` の形にも触れておきます。`map` に **1 引数**（変換関数だけ）を渡すと、コレクションを返す代わりに **トランスデューサ**（変換の手続きそのもの）を返します。それを `into` に渡すと、中間のシーケンスを作らずに変換しながらマップへ積み上げます。`(into {} (map f columns))` と書いても結果は同じですが、こちらは一度シーケンスを作ります。**変換とその適用先を分けて書ける**のがトランスデューサの要点です。

```clojure
(deftest 欠損値の補完
  (testing "欠損値を除いて列ごとの平均値を求める"
    (let [rows [{:がく片長さ "1.0"} {:がく片長さ ""} {:がく片長さ "3.0"}]]
      (is (= {:がく片長さ 2.0} (ch/column-means rows [:がく片長さ])))))
  (testing "値がすべて空欄なら平均値を求められない"
    (is (thrown-with-msg? IllegalArgumentException #"値がすべて空欄です: がく片長さ"
                          (ch/column-means [{:がく片長さ ""}] [:がく片長さ])))))
```

### 補完して特徴量にする

```clojure
(defn fill-missing
  "欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。"
  [rows columns fill-values]
  (mapv (fn [row]
          (into {} (map (fn [column]
                          [column (or (number row column) (fill-value fill-values column))]))
                columns))
        rows))
```

`(or a b)` は「`a` が `nil`（か `false`）なら `b`」です。**`number` が `nil` を返したときだけ補完する**、が 1 つの式で書けました。Scala 版の `.getOrElse(...)`、Ruby 版の `||` に当たります。

そして `or` は**短絡評価**です。値があれば `fill-value` は呼ばれません。これが次の落とし穴の伏線になります。

### `get` の既定値は先に評価される

補完する値が無ければ失敗させたい。最初はこう書きました。

```clojure
;; 落ちた版
(get fill-values column (throw (IllegalArgumentException. (str "補完する値がありません: " (name column)))))
```

`(get m k not-found)` は「`k` が無ければ `not-found` を返す」なので、一見よさそうです。**しかしテストが落ちました。** 補完する値がそろっているはずのケースで、例外が飛んだのです。

理由は単純で、**`get` はマクロではなく関数**です。関数の引数は呼ぶ前にすべて評価されるので、`(throw ...)` は「鍵があるかどうかに関係なく」先に実行されます。確かめました。

```clojure
(println "既定値（鍵なし） ="
         (try (get {} :花弁幅 (throw (IllegalArgumentException. "補完する値がありません: 花弁幅")))
              (catch IllegalArgumentException e (str "例外: " (.getMessage e)))))
(println "既定値（鍵あり） ="
         (try (get {:花弁幅 1.0} :花弁幅 (throw (IllegalArgumentException. "補完する値がありません: 花弁幅")))
              (catch IllegalArgumentException e (str "例外: " (.getMessage e)))))
```

```text
既定値（鍵なし） = 例外: 補完する値がありません: 花弁幅
既定値（鍵あり） = 例外: 補完する値がありません: 花弁幅
```

**鍵があっても例外が飛びます。** すぐ上で使った `or` が短絡するので同じ感覚で書いてしまいましたが、`or` はマクロ、`get` は関数、という違いがここに出ます。Scala 版の `getOrElse` は既定値が名前渡し（by-name）なので遅延しますし、Java 版の `orElseThrow` は関数を受け取ります。**「既定値が遅延するかどうか」は言語・API ごとに違う**ので、思い込まずに確かめるところでした。

直した形が、分けて書く版です。

```clojure
(defn- fill-value
  "補完する値を返す。無ければ失敗する。
   get の既定値は先に評価されるので、例外は when-not で投げる。"
  [fill-values column]
  (when-not (contains? fill-values column)
    (throw (IllegalArgumentException. (str "補完する値がありません: " (name column)))))
  (get fill-values column))
```

`contains?` で確かめてから `get` します。`when-not` はマクロなので、条件が真のときにしか本体を評価しません。docstring に理由を残したのは、**同じ誤りを繰り返さないため**です。

このバグを見つけたのはテストでした。テストを先に書いていなければ、「補完する値が無いときだけ落ちるはず」の例外が常時飛ぶことに、実データを流すまで気づけません。

```clojure
(testing "欠損値を平均値で補完する。元の行は変えない"
  (let [rows [{:がく片長さ "1.0"} {:がく片長さ ""}]]
    (is (= [{:がく片長さ 1.0} {:がく片長さ 2.0}]
           (ch/fill-missing rows [:がく片長さ] {:がく片長さ 2.0})))))
(testing "補完する値が無ければ失敗する"
  (is (thrown-with-msg? IllegalArgumentException #"補完する値がありません: がく片長さ"
                        (ch/fill-missing [{:がく片長さ ""}] [:がく片長さ] {}))))
```

「元の行は変えない」をテスト名に書いていますが、**Clojure では確かめるまでもありません**。マップもベクタも不変なので、`fill-missing` が入力を書き換える書き方が存在しないからです。それでも名前に残したのは、ほかの言語版と読み比べたときに「ここは何も手当てしていない」ことが伝わるようにするためです。

## 2.7 特徴量と正解ラベルに分ける

```clojure
(def target
  "アヤメのデータの正解ラベルの列。"
  :種類)

(defn split-features-and-target
  "正解ラベルの列を取り出し、残りの列を特徴量の列にする。"
  [{:keys [columns rows]} target-column]
  {:columns (vec (remove #(= target-column %) columns))
   :rows rows
   :labels (mapv #(text % target-column) rows)})
```

戻り値を 3 要素のベクタではなくマップにしました。第 1 章では `[x t]` のベクタを返して `(let [[x t] ...])` で受けましたが、3 つになると位置で覚えるのが辛くなります。**マップで返せば、受ける側が `{:keys [columns rows labels]}` と名前で取り出せます。** Scala 版はここでタプル `(Vector[String], Vector[Row], Vector[String])` を返していて、型で見分けがつかない同じ型が 2 つ並んでいます。名前で受けるほうが取り違えにくい場面です。

`rows` をそのまま通していることに注意してください。正解ラベルの列は**行から取り除いていません**。取り除くのは「特徴量として扱う列の一覧」からだけで、後の `fill-missing` が `columns` の並びだけを見て新しいマップを作るので、余った `:種類` は自然に落ちます。行を削って回るより、**使う列を指定するほうが工程が少ない**、という選択です。

## 2.8 訓練データとテストデータに分ける

### 仮実装から

最初のテストは件数だけを見ます。

```clojure
(testing "テストデータの割合で分ける"
  (let [x (vec (range 10))
        split (ch/split-train-test x (mapv str x) 0.3 0)]
    (is (= [7 3 7 3] [(count (:x-train split)) (count (:x-test split))
                      (count (:t-train split)) (count (:t-test split))]))))
```

`0.3` を掛けて切り上げるので、10 件なら 3 件がテストデータです。4 つの件数をベクタにまとめて 1 つの `is` で比べているのは、**ベクタが値で比べられる**からできる書き方です。失敗したときも、期待と実際のベクタが並んで表示されます。

### 三角測量: 並び順に頼らない分け方にする

先頭から順に分ければ件数のテストは通りますが、それでは困ります。iris.csv は品種ごとに並んでいるので、先頭 105 件を取ると訓練データに `Iris-virginica` がほとんど入りません。並べ替えが要ります。

```clojure
(testing "同じシードなら同じ並びになる"
  (let [items (vec (range 10))]
    (is (= (ch/shuffle-with-seed items 0) (ch/shuffle-with-seed items 0)))
    (is (not= (ch/shuffle-with-seed items 0) (ch/shuffle-with-seed items 1)))))
(testing "並べ替えても要素は変わらない"
  (is (= (vec (range 10)) (vec (sort (ch/shuffle-with-seed (vec (range 10)) 0))))))
```

「同じシードなら同じ、違うシードなら違う」「並べ替えても要素は変わらない」の 2 つで、並べ替えの性質を挟み撃ちにします。中身がどう並ぶかは、この時点では決めていません。

### Green: シード付きの乱数で並べ替える

```clojure
(defn shuffle-with-seed
  "シードを使って Fisher-Yates のシャッフルで並べ替える。元のベクタは変えない。
   java.util.Random を使うので、Java 版・Scala 版と同じ並びになる。"
  [items seed]
  (let [random (Random. seed)
        shuffled (object-array items)]
    (doseq [i (range (dec (count items)) 0 -1)]
      (let [j (.nextInt random (inc i))
            tmp (aget shuffled i)]
        (aset shuffled i (aget shuffled j))
        (aset shuffled j tmp)))
    (vec shuffled)))
```

**この関数だけ、Clojure らしくありません。** `object-array` で Java の配列を作り、`aset` で書き換えています。不変のデータを旨とする言語で、意図して可変の配列を使っています。

理由は「Java 版・Scala 版と同じ並びにする」という目的から来ています。Fisher-Yates は「末尾から順に、ランダムに選んだ要素と入れ替える」というアルゴリズムで、**入れ替えの順序そのものが結果を決めます**。不変のベクタで同じことをやると、`assoc` を 2 回ずつ重ねる形になり、書いたコードと元のアルゴリズムの対応が読み取りにくくなります。ここでは「Java 版と同じ手順であることが一目で分かる」ことを優先しました。

安全なのは、**可変性が関数の中に閉じている**からです。`object-array` が作るのはこの呼び出し専用の配列で、外からは見えません。最後に `(vec shuffled)` で不変のベクタに戻すので、呼び出し側から見れば純粋な関数です。**局所的な可変は、外に漏れなければ設計を壊しません。**

`(Random. seed)` の末尾のドットがコンストラクタの呼び出し、`(.nextInt random (inc i))` の先頭のドットがインスタンスメソッドの呼び出しです。第 1 章で見た Java の相互運用が、そのまま使えています。

`doseq` は副作用のための反復です。`for` は遅延シーケンスを返すので、ここで使うと**何も起きません**（返り値を誰も見ないため評価されない）。副作用が目的のときは `doseq`、値が欲しいときは `for`、という使い分けです。遅延評価のある言語で繰り返し出会う区別で、第 1 章の `map` と `mapv` の話と根は同じです。

### Java 版・Scala 版と同じ並びになることを確かめる

```clojure
(testing "Java 版・Scala 版と同じ並びになる"
  (is (= [4 8 9 6 3 5 2 1 7 0] (ch/shuffle-with-seed (vec (range 10)) 0))))
```

**`[4 8 9 6 3 5 2 1 7 0]` は、Java 版・Scala 版と同じ並びです。** 言語をまたいだ約束を、テストで固定しました。片方を変えればもう片方が落ちるので、この 1 行が「同じ乱数を使い続ける」という設計判断の見張り番になります。

### 分割の本体

```clojure
(defn split-train-test
  "並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。"
  [x t test-size seed]
  (when-not (= (count x) (count t))
    (throw (IllegalArgumentException. (str "件数が違います: " (count x) " と " (count t)))))
  (let [shuffled (shuffle-with-seed (mapv vector x t) seed)
        train-count (- (count shuffled) (long (Math/ceil (* (count shuffled) test-size))))
        [train test] (split-at train-count shuffled)]
    {:x-train (mapv first train) :x-test (mapv first test)
     :t-train (mapv second train) :t-test (mapv second test)}))
```

`(mapv vector x t)` が要点です。**`map` は複数のコレクションを受け取れます。** `x` と `t` を同時にたどって `[特徴量 ラベル]` の組を作るので、並べ替えても対応が崩れません。Scala 版の `x.zip(t)` に当たりますが、Clojure では `zip` という専用の関数はなく、`map` に `vector` を渡すことでそうなります。関数を値として渡せることの、素直な応用です。

対応が崩れないことは、テストで別に確かめます。

```clojure
(testing "分けても特徴量と正解ラベルの対応は崩れない"
  (let [x (vec (range 10))
        split (ch/split-train-test x (mapv str x) 0.3 0)]
    (is (= (mapv str (:x-train split)) (:t-train split)))
    (is (= (mapv str (:x-test split)) (:t-test split)))))
```

特徴量を `0, 1, 2...`、ラベルをその文字列版にしておくと、**どんな並べ替えをされても「ラベルは特徴量の文字列版」という関係だけは保たれる**はずです。並び順を書き下さずに対応関係だけを確かめる書き方で、`shuffle-with-seed` の実装を変えてもこのテストは通り続けます。

戻り値は 4 つの鍵を持つマップです。Scala 版は `TrainTestSplit[X, T]` という型引数 2 つのクラスを宣言しましたが、**動的型付けならジェネリクスは要りません**。ここに入るのが `Long` でも特徴量のマップでも、同じ関数が通ります。上のテストが `(range 10)` で済んでいるのはそのおかげです。

## 2.9 前処理をまとめる

```clojure
(defn prepare-iris
  "iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。"
  [csv-file test-size seed]
  (let [{:keys [columns rows labels]} (split-features-and-target (load-table csv-file) target)
        split (split-train-test rows labels test-size seed)
        means (column-means (:x-train split) columns)]
    (assoc split
           :x-train (fill-missing (:x-train split) columns means)
           :x-test (fill-missing (:x-test split) columns means))))
```

**順序が大事です。** 分割してから、**訓練データの平均値で**両方を補完しています。先に全体の平均で補完すると、テストデータの情報が訓練データの前処理に混ざります（**データリーク**）。テストデータは「まだ見ていないデータ」のつもりで扱うので、そこから計算した値を学習側に持ち込んではいけません。

`assoc` は「マップの一部を差し替えた新しいマップを返す」関数です。`split` の 4 つの鍵のうち 2 つだけを置き換えるのが 1 つの式で書けます。`:t-train`・`:t-test` はそのまま残るので、書き忘れる余地がありません。Scala 版は `TrainTestSplit(...)` を 4 引数で組み立て直していて、**変えない 2 つも書く**ことになっていました。素のマップで通す設計が効く場面です。

`prepare-iris` の戻り値には、補完済みの特徴量と補完前の行が混ざらないようになっています。ただし**型では区別できません**。`:x-train` に補完前の行が入っていても、Clojure は何も言いません。そこはテストで固定します。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

実データを使うテストは、第 1 章と同じく名前空間を分けて `iris_data_test.clj` に置き、データが無ければ早期に戻ります。

```clojure
(deftest 実データの列ごとの欠損値の数を数える
  (when-let [path (csv-file)]
    (let [table (ch/load-table path)]
      (is (= 150 (count (:rows table))))
      (is (= [[:がく片長さ 2] [:がく片幅 1] [:花弁長さ 2] [:花弁幅 2] [:種類 0]]
             (ch/count-missing table))))))

(deftest 実データを百五件と四十五件に分けて欠損値を補完する
  (when-let [path (csv-file)]
    (let [split (ch/prepare-iris path 0.3 0)]
      (is (= [105 45 105 45] [(count (:x-train split)) (count (:x-test split))
                              (count (:t-train split)) (count (:t-test split))]))
      (is (every? (fn [features] (every? some? (vals features))) (:x-train split))))))
```

2 つめのテストの最後の `is` が、**Scala 版なら型が保証していたこと**を確かめています。

```clojure
(every? (fn [features] (every? some? (vals features))) (:x-train split))
```

「すべての行の、すべての値が `nil` でない」。Scala 版は `Features` の値が `Vector[Double]` なので `None` が入りようがなく、この表明そのものが書けません（書く必要がありません）。Clojure 版では、**補完が済んでいることを実行時に確かめる**しかありません。動的型付けの代償を、テストの 1 行で払っている形です。

`(deftest 実データを百五件と四十五件に分けて欠損値を補完する)` と数字を漢数字にしているのは、`deftest` の名前がシンボルになるためです。**シンボルは数字で始められない**ので、`105` をそのまま名前の先頭に置けません。日本語の名前がそのまま使えるぶん、こういう小さな制約に当たります。

### Java 版・Scala 版との突き合わせ

この章の山場のテストです。

```clojure
(deftest 訓練データの平均値はJava版Scala版と一致する
  ;; java.util.Random を使うので、分かれる行も平均値も Java 版・Scala 版と同じになる
  (when-let [path (csv-file)]
    (let [{:keys [columns rows labels]} (ch/split-features-and-target (ch/load-table path) ch/target)
          split (ch/split-train-test rows labels 0.3 0)
          means (ch/column-means (:x-train split) columns)]
      (doseq [[column want] {:がく片長さ 0.4215384615384616 :がく片幅 0.43826923076923074
                             :花弁長さ 0.47644230769230766 :花弁幅 0.4475}]
        (is (< (abs (- (get means column) want)) 1e-12) (name column)))
      (is (= ["Iris-setosa" "Iris-virginica" "Iris-setosa"] (take 3 (:t-test split)))))))
```

**4 列の平均値が、Java 版・Scala 版の値と小数点以下 16 桁まで一致しました。** 平均値が一致するということは、**訓練データに入った 105 行がまったく同じ**ということです。`java.util.Random` の数列が仕様で決まっていて、Fisher-Yates の手順をそろえたので、3 つの言語で同じ行が同じ側に分かれました。

`doseq` でテーブル駆動にしているのは、4 列を 4 行の `is` で書くより、期待値の表として読めるからです。`is` の 2 つ目の引数（`(name column)`）は失敗したときの手がかりで、どの列が合わなかったかが分かります。

浮動小数点数なので `=` ではなく許容誤差付きで比べています。第 1 章の正解率と同じ理由です。

テストデータの先頭 3 件のラベルも固定しました。平均値が一致すれば行も一致するはずですが、**並び順まで同じであること**は平均値では確かめられません（平均は順に依存しないので）。1 行足すだけで、並びまで込みの約束になります。

### 結果を表示する

```clojure
(defn run
  "アヤメのデータの前処理の結果を表示する。"
  []
  (let [csv-file (str (dataset/dir) "/iris.csv")
        table (load-table csv-file)
        split (prepare-iris csv-file 0.3 0)]
    (println (str "データ件数: " (count (:rows table))))
    (println (str "欠損値の数: "
                  (str/join ", " (map (fn [[column n]] (str (name column) "=" n))
                                      (count-missing table)))))
    (println (str "訓練データ: " (count (:x-train split)) " 件, "
                  "テストデータ: " (count (:x-test split)) " 件"))
    (println (str "特徴量: " (str/join ", " (map name (keys (first (:x-train split)))))))))
```

`main.clj` の対応表に 1 行足せば、章を選んで実行できます。

```clojure
(def ^:private chapters
  {"chapter01" chapter01/run
   "chapter02" chapter02/run})
```

第 1 章で見たとおり、**足すのはマップの 1 行だけ**です。使い方のメッセージ（`(sort (keys chapters))`）も自動で追いつきます。

実行します。

```text
$ clojure -M:run chapter02
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

最後の行の「特徴量」に注意してください。`(keys (first (:x-train split)))` で**マップの鍵をそのまま並べています**。4 つなので Clojure の小さなマップ（配列マップ）になり、たまたま挿入した順に出ています。**鍵が 8 個を超えるとハッシュマップになり、この順は崩れます。** ここで正しい順に見えているのは偶然に近く、順が要る場所では `:columns` を使うべきです——この表示は「何の列が入っているか」を確かめるためのものなので、そのままにしました。第 3 章では、この鍵の並びを列の一覧として使うことになるので、その性質にもう一度触れます。

テストを走らせます。

```text
$ clojure -M:test
Ran 15 tests containing 52 assertions.
0 failures, 0 errors.
```

（第 1 章・第 3 章のテストも含めた数です。）

## 2.11 可視化について

Clojure 版には Notebook の節を設けません。欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが（Clojure 版は Java 版・Scala 版と同じです）、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

## 2.12 まとめ

この章では、データフレームのライブラリを使わずに、欠損値を含むデータを前処理し、訓練データとテストデータに分けました。Clojure に固有の論点は次のとおりです。

1. **型を宣言せず、素のマップとベクタで通す** — 表は `{:columns ... :rows ...}`、特徴量も分割の結果もマップ。分配束縛（`{:keys [columns rows]}`）で名前で受け取れ、`assoc` で一部だけ差し替えられる。ジェネリクスも要らない
2. **欠損値は `nil`** — `Option` は無いが、`keep`（`nil` を落として写す）と `or`（`nil` なら次）で「欠損値を除いて集計する」「無ければ補う」が 1 つの式になる。弱いのは、除き忘れてもコンパイル時に気づけないこと
3. **補完済みかどうかは型で表せない** — Scala 版が `Row` と `Features` を分けて保証したことを、Clojure 版は `(every? some? (vals features))` というテストで保証する
4. **`get` の既定値は先に評価される** — `or` はマクロなので短絡するが、`get` は関数なので既定値が必ず評価される。「無ければ例外」は `when-not` と `contains?` に分けて書く。テストが落ちて初めて気づいた
5. **`clojure.data.csv` は行末の空欄を落とさない** — Scala 版・Java 版が `split(",", -1)` で手当てした問題は起きない。いっぽう BOM は相変わらず残る。同じライブラリの中で親切な部分とそうでない部分が混ざる
6. **並べ替えだけは可変の配列で書いた** — Java 版・Scala 版と同じ Fisher-Yates であることを読めるようにするため。`object-array` と `aset` の可変性は関数の中に閉じていて、`(vec shuffled)` で不変に戻る
7. **`shuffle` にはシードを渡せない** — 再現性が要るなら自分で書くしかない。その代わり `[4 8 9 6 3 5 2 1 7 0]` という並びと、訓練データの平均値（がく片長さ 0.4215384615384616 ほか）が **Java 版・Scala 版と一致**した

**TODO リスト（この章の完了時点）**:

- [x] 表を読み込む
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 前処理をまとめる
- [x] 実データで前処理の結果を表示する

次の章では、この訓練データから決定木を自作します。木を表す判別共用体が Clojure には無いので、葉と節をどちらもマップで表すことになります。そして JVM の機械学習ライブラリ Tribuo の決定木と予測を突き合わせ、Java 版・Scala 版と同じ結果が出るかを確かめます。
