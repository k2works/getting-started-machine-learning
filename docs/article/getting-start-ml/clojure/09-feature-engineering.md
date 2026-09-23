---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Clojure の TDD で自作し、特徴量の組み合わせごとの決定係数を測って、Tribuo の標準化との違い（不偏標準偏差）と、文字コードを間違えても例外にならない読み込みを確かめる。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:20:00Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は Tribuo の `MeanStdDevTransformation` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、同じ JVM・同じ Tribuo 4.3.2 を使う [Java 版](../java/09-feature-engineering.md)・[Scala 版](../scala/09-feature-engineering.md)・[Kotlin 版](../kotlin/09-feature-engineering.md) と対比します。Clojure 版は Java 版・Scala 版と同じく、データフレームのライブラリを使わずに、第 2 章で決めた「列名のキーワードをキーにしたマップの並び」で表を持ちます（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。

この章で Clojure ならではの点は 3 つです。

- **マップの鍵の順に頼れない列がある。** Boston のデータは特徴量が 14 列あり、Clojure のマップは 9 件以上になると挿入順を保たないハッシュマップになります。列の順は `:columns` のベクタで持ち回ります
- **文字コードを間違えても例外にならない。** Java 版・Scala 版の `Files.readAllLines` は復号できないバイトで `MalformedInputException` を投げましたが、`java.io.InputStreamReader` は置換文字に置き換えて読み進めます
- **項の名前をキーワードにする。** `"RM^2"`・`"RM LSTAT"` は `(keyword "RM^2")` のように、読み手側では書けない形のキーワードになります

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード |
|---------|------|----------|-----------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

第 2 章の `load-table` は「BOM 付きの UTF-8 のカンマ区切り」を読む関数です。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します（第 2 章の関数は変更しません）。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] Tribuo の `MeanStdDevTransformation` と突き合わせる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項を加える
  - [ ] 交互作用の項を加える
  - [ ] 使う項を選ぶ
- [ ] 外れ値を検出する
  - [ ] 分位数を線形補間で求める
  - [ ] 四分位範囲（IQR）で外れ値を判定する
  - [ ] 訓練データから外れ値の行を取り除く
- [ ] 表を結合して特徴量を増やす
  - [ ] 区切り文字と文字コードを指定して読み込む
  - [ ] 天気 ID で 2 つの表を結合する
  - [ ] 天気ごとの平均利用者数を求める
- [ ] 特徴量の組み合わせごとに決定係数を比べる

Java 版・Scala 版は技法ごとにファイルを分けましたが、Clojure 版は 1 つの名前空間 `getting-started-ml.chapter09` にまとめ、コメントで節に区切ります。関数が名前空間の中で名前で区別されるので、`Dummies.encode` と `PolynomialFeatures.expand` のようにクラス名で修飾する必要がありません。

| 節 | 関数 |
|----|------|
| カテゴリ値をダミー変数にする | `categories`・`encode` |
| 標準化 | `standardizer`・`standardize`・`standardize-all`・`tribuo-standardize` |
| 多項式特徴量 | `pairs-with-replacement`・`term-name`・`expanded-columns`・`expand`・`select-columns` |
| 外れ値 | `quantile`・`iqr-outliers`・`remove-target-outliers` |
| 表の読み込みと結合 | `load-delimited`・`join-weather`・`mean-count-by-weather` |
| 線形回帰 | `linear-fit`・`linear-predict`・`r-squared` |
| ボストンの住宅価格 | `prepare-boston`・`score-feature-set`・`run` |

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```clojure
(deftest カテゴリ値をダミー変数にする
  (testing "先頭を除いたカテゴリを辞書順に返す"
    (is (= ["low" "very_low"] (ch/categories ["low" "high" "very_low" "low"]))))
  (testing "欠損値は数えない"
    (is (= ["low"] (ch/categories ["low" "" "high"])))))
```

第 2 章の行は空欄を空文字列のまま持つので、`clojure.string/blank?` で除きます。

```clojure
(defn categories
  "欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。"
  [values]
  (vec (rest (sort (distinct (remove str/blank? values))))))
```

Java 版の `stream().filter(...).distinct().sorted().skip(1).toList()`、Scala 版の `values.filter(...).distinct.sorted.drop(1)` と、やることは同じです。Clojure ではコレクションが手続きを持つのではなく、`remove`・`distinct`・`sort`・`rest` という **関数がシーケンスを受け取って返す** ので、内側から外側へ読みます。読む向きが逆になるのが気になるなら、スレッディングマクロで左から右に並べ替えられます。

```clojure
(->> values (remove str/blank?) distinct sort rest vec)
```

どちらも同じ計算です。この章では、引数が 1 つで短いものはそのまま入れ子にし、段が 4 つを超えるところ（`mean-count-by-weather`）だけ `->>` を使いました。

### 表に列を加える

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```clojure
  (testing "カテゴリごとに零と一の列を作り元の列を取り除く"
    (let [table {:columns [:RM :CRIME]
                 :rows [{:RM "6.0" :CRIME "low"} {:RM "6.0" :CRIME "high"}
                        {:RM "6.0" :CRIME "very_low"}]}
          encoded (ch/encode table :CRIME ["low" "very_low"])]
      (is (= [:RM :CRIME_low :CRIME_very_low] (:columns encoded)))
      (is (= ["1" "0" "0"] (mapv :CRIME_low (:rows encoded))))
      (is (= ["0" "0" "1"] (mapv :CRIME_very_low (:rows encoded))))
      (is (every? #(not (contains? % :CRIME)) (:rows encoded)))))
```

```clojure
(defn- dummy-column
  "ダミー変数の列名。"
  [column category-value]
  (keyword (str (name column) "_" category-value)))

(defn encode
  "列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば \"1\"、それ以外は \"0\"。"
  [table column categories]
  (let [dummy-columns (mapv #(dummy-column column %) categories)]
    {:columns (into (vec (remove #(= column %) (:columns table))) dummy-columns)
     :rows (mapv (fn [row]
                   (let [value (get row column)]
                     (into (dissoc row column)
                           (map (fn [c] [(dummy-column column c) (if (= c value) "1" "0")]))
                           categories)))
                 (:rows table))}))
```

Java 版は `new HashMap<>(row.cells())` と写してから `remove`・`put` で書き換えました。Clojure のマップは不変なので、`dissoc` が「その列を除いた新しいマップ」を返し、`into` が「そこに対を足した新しいマップ」を返します。Scala の `removed` と `++` にそのまま対応します。元の行が変わらないことをテストで確かめる必要すらありません。

`into` に変換（`(map ...)`）を渡す形は、`(into m (map f coll))` と書くよりも中間のシーケンスを作らない書き方です。結果は同じなので、好みで選べます。

列名は **キーワード** にしました。`:CRIME_low` のように、値としても関数としても使えます（`(mapv :CRIME_low rows)` がそのまま「その列を取り出す」になります）。セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `split-features-and-target`・`column-means`・`fill-missing` を、ダミー変数の列にもそのまま使えます。カテゴリを引数で受け取るのは Java 版・Scala 版と同じ理由で、訓練データで決めたカテゴリをテストデータにも当てはめ、列をそろえるためです。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### 平均と標準偏差を求める

第 14 章（K-means）でもこの関数を使うので、第 2 章の特徴量（列名のキーワードから `double` へのマップ）をそのまま受け取れる形にします。

```clojure
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
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。scikit-learn の `StandardScaler` と同じ定義にしました。

```clojure
(defn standardizer
  "列ごとの平均と、件数で割る標準偏差を求める。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。"
  [x columns]
  (when (empty? x)
    (throw (IllegalArgumentException. "特徴量が 1 件もありません")))
  (let [stats (mapv (fn [column]
                      (let [values (mapv #(get % column) x)
                            mean (/ (reduce + values) (count values))
                            variance (/ (reduce + (map #(* (- % mean) (- % mean)) values))
                                        (count values))
                            std (Math/sqrt variance)]
                        [column mean (if (zero? std) 1.0 std)]))
                    columns)]
    {:columns (vec columns)
     :means (into {} (map (fn [[column mean _]] [column mean])) stats)
     :stds (into {} (map (fn [[column _ std]] [column std])) stats)}))
```

Java 版・Scala 版との違いを 3 つ挙げます。

1. **順序は型ではなく引数で持つ** — Java 版は順序を保つために `LinkedHashMap` を使い、Scala 版は不変で挿入順を保つ `SeqMap` を使いました。Clojure のマップは 8 件までは挿入順を保つ配列マップですが、9 件以上ではハッシュマップになり、**順序は保証されません**。だから「どの列があるか」ではなく「列がどの順か」を表したいときは、マップの鍵の順に頼らず `columns` のベクタを引数で受け取り、`:columns` として結果にも残します。`Boston.csv` の特徴量は 14 列なので、これは実データで必ず効きます
2. **列ごとの統計を 1 回で作る** — 平均と標準偏差を別々のループで作らず、`[列名 平均 標準偏差]` のベクタを 1 回作ってから 2 つのマップに分けます。可変のマップに `put` していく Java 版と違い、途中の状態がありません
3. **「無ければ元の値」を `if-let` で書く** — 標準化する側は次のとおりです

```clojure
(defn standardize
  "1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。"
  [{:keys [means stds]} features]
  (into {} (map (fn [[column value]]
                  [column (if-let [mean (get means column)]
                            (/ (- value mean) (get stds column))
                            value)]))
        features))
```

引数の `{:keys [means stds]}` は、マップを受け取ってその鍵を名前に束縛する分配束縛です。Scala 版の case class のフィールドと同じように読めますが、型は宣言しません。`if-let` は `(get means column)` が `nil` でなければその値を束縛し、`nil` なら else 側に進みます。Java 版の `containsKey` と `get` の 2 回引きが 1 回になるのは Scala 版の `Option` と同じ利点です。ただし **`nil` と「値が無い」を区別しない** ので、平均が `nil` である列は表現できません。今回は平均が `nil` になることはないので問題になりませんが、`Option` を持つ言語との差が出るところです。

### Tribuo の標準化と突き合わせる

Tribuo には `MeanStdDevTransformation` という標準化があります。Java 向けの API ですが、Clojure からも Java の相互運用でそのまま呼べます。

```clojure
(defn tribuo-standardize
  "Tribuo の MeanStdDevTransformation で、訓練データの値から平均と標準偏差を求めて別の値を標準化する。"
  [train values]
  (let [^TransformStatistics statistics (.createStats (MeanStdDevTransformation.))]
    (doseq [value train]
      (.observeValue statistics (double value)))
    (let [^Transformer transformer (.generateTransformer statistics)]
      (mapv #(.transform transformer (double %)) values))))
```

`^TransformStatistics` のような型ヒントは、Clojure のコンパイラにメソッドの解決先を教えるものです。書かなくても動きますが、実行時にリフレクションで探すことになるため、Java の API を繰り返し呼ぶところでは付けます。`(double value)` を挟んでいるのは、`observeValue` が `double` を受け取るのに対し、Clojure の数値がボックス化された `Long` で渡ることがあるからです。**Java の API との境目では、型は自分で合わせます。**

自作の標準化と同じ値になるはずだと考えて比べたところ、値がずれました。比は `√(3/4)`（訓練データは 4 件）で、Tribuo は件数から 1 を引いた 3 で割る **不偏標準偏差** を使っていることが分かります。Kotlin 版（ADR 002）・Java 版（ADR 005）・Scala 版（ADR 007）で確かめた結果と、値まで同じです。この事実をテストに残します。

```clojure
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
```

同じ「標準偏差」でも、ライブラリによって割る数が違います。件数が多ければ差は小さくなりますが、この章の訓練データは 70 件なので、係数は `√(69/70)` ≒ 0.993 です。線形回帰では係数の大きさが変わるだけで予測は変わりませんが、第 14 章の K-means のように距離を使うアルゴリズムでは、どちらの定義を使ったかを記事とコードで明示しておきます。

`(/ (dec (count train)) (double (count train)))` の `double` は、Clojure の `/` が整数どうしでは **有理数** を返すためです。`(/ 3 4)` は `0.75` ではなく `3/4` になります。数値としては正確なので、そのまま `Math/sqrt` に渡しても結果は同じですが、この章では「小数として計算している」ことをコードで示すために明示的に `double` を挟みました。動的型付けでも、数の種類は意識します。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

部屋数（RM）と価格の関係が直線でなく曲線なら、RM の 2 乗の列を加えると線形回帰でも曲線を表せます。2 つの列の積（交互作用の項）を加えると、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表せます。

```clojure
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
```

1 列・2 列・3 列と三角測量し、3 列では scikit-learn の `PolynomialFeatures` と同じ並びの 9 列になることを確かめました。

Java 版は `Pair` という record に、Scala 版は `Term` という case class に、項を表す型を用意しました。Clojure では **2 要素のベクタ** を項とし、名前を作る関数を分けます。

```clojure
(defn pairs-with-replacement
  "重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。"
  [columns]
  (let [columns (vec columns)]
    (vec (for [i (range (count columns))
               j (range i (count columns))]
           [(columns i) (columns j)]))))

(defn term-name
  "項の名前。scikit-learn の get_feature_names_out と同じ形（\"RM^2\"、\"RM LSTAT\"）にする。"
  [[left right]]
  (if (= left right)
    (keyword (str (name left) "^2"))
    (keyword (str (name left) " " (name right)))))
```

`(defn term-name [[left right]] ...)` の二重の角かっこは、引数のベクタを分配束縛しています。呼ぶ側は `[:RM :LSTAT]` という素のベクタを渡すだけで、`Term` のような型を作る必要がありません。**型が要らない分だけ書くことは減りますが、「この 2 要素ベクタが項である」という約束はドキュメント文字列にしか書けません。** Scala 版の `Term` が名前で示していたことを、Clojure では関数名（`term-name`・`pairs-with-replacement`）と置き場所で示します。

二重ループは `for` の内包表記で書きました。`i` の外側と `j` の内側という並びは Java 版の二重の `for` 文と同じで、生成される順も同じです。ベクタを添字で引く `(columns i)` は、ベクタが関数としても使えることを利用した書き方です。

項の名前は `(keyword "RM^2")` のように **読み手の構文では書けないキーワード** になります。`:RM^2` と書くこともできますが、`RM LSTAT` には空白が入るので `(keyword "RM LSTAT")` としか書けません。テストでもこの形で書いています。「キーワードは名前であって識別子ではない」ことがはっきり出るところです。

```clojure
(defn expand
  "指定した列と、その 2 乗の項・交互作用の項だけを持つ特徴量にする。"
  [x columns]
  (let [pairs (pairs-with-replacement columns)]
    (mapv (fn [features]
            (into (into {} (map (fn [column] [column (get features column)])) columns)
                  (map (fn [[left right :as pair]]
                         [(term-name pair) (* (get features left) (get features right))]))
                  pairs))
          x)))
```

Java 版は結果の `double[]` を確保して添字で埋めましたが、Clojure では「元の列のマップ」に「項のマップ」を `into` で足すだけです。添字の計算（`values[columns.size() + k]`）が消えるので、ずれる余地がありません。`[left right :as pair]` は、分配束縛しつつ元のベクタも `pair` として受け取る書き方です。

使う項を選ぶ `select-columns` は `select-keys` の言い換えで済みました。

```clojure
(defn select-columns
  "指定した列だけを選ぶ。"
  [x columns]
  (mapv #(select-keys % columns) x))
```

マップから鍵の部分集合を取り出す関数が標準で用意されているので、Java 版・Scala 版が書いた「列を選んで新しい特徴量を組み直す」処理が 1 行になります。ただし `select-keys` が返すのはマップなので、**結果の列の順は保証されません**。順が要るところ（線形回帰に渡す行）では、あとで `columns` のベクタの順に並べ直します。

## 9.7 外れ値を検出する

第 1 四分位数（Q1）と第 3 四分位数（Q3）の差を **四分位範囲（IQR）** と呼びます。`Q1 − 1.5 × IQR` より小さい値と、`Q3 + 1.5 × IQR` より大きい値を外れ値とみなします。

```clojure
(deftest 外れ値の検出
  (testing "四分位数の位置が値の間にあれば前後の値から線形補間する"
    (is (close-to? 1.75 (ch/quantile [4.0 1.0 3.0 2.0] 0.25))))
  (testing "第三四分位数からIQRの一点五倍より大きい値を外れ値とする"
    (is (= [false false false false true] (ch/iqr-outliers [1.0 2.0 3.0 4.0 100.0]))))
```

```clojure
(defn quantile
  "分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。"
  [values q]
  (let [sorted (vec (sort values))
        position (* (dec (count sorted)) q)
        lower (long (Math/floor position))
        upper (long (Math/ceil position))]
    (+ (sorted lower) (* (- (sorted upper) (sorted lower)) (- position lower)))))

(defn iqr-outliers
  "第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。"
  ([values] (iqr-outliers values default-k))
  ([values k]
   (let [q1 (quantile values 0.25)
         q3 (quantile values 0.75)
         iqr (- q3 q1)]
     (mapv #(or (< % (- q1 (* k iqr))) (> % (+ q3 (* k iqr)))) values))))
```

Java 版は既定の引数が無いので `iqrOutliers(values)` と `iqrOutliers(values, k)` の 2 つをオーバーロードし、Scala 版・Kotlin 版は既定値を書きました。Clojure には既定引数がありませんが、**引数の数ごとに本体を書く多アリティ関数** があります。`([values] (iqr-outliers values default-k))` の 1 行が、Scala の `k: Double = DefaultK` に当たります。オーバーロードと違って呼び先は同じ名前の 1 つの関数なので、「短いほうが長いほうを呼ぶ」ことがコードに現れます。

外れ値を除く `remove-target-outliers` は、訓練データから正解（価格）が外れ値の行だけを取り除き、テストデータには手を付けません。テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいても評価から外してはいけないからです。

```clojure
(defn remove-target-outliers
  "訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。"
  [split]
  (let [kept (remove (fn [[_ _ outlier?]] outlier?)
                     (map vector (:x-train split) (:t-train split)
                          (iqr-outliers (:t-train split))))]
    (assoc split :x-train (mapv first kept) :t-train (mapv second kept))))
```

Java 版は「残す添字の `List<Integer>` を作り、特徴量と正解をそれぞれ添字で引く」と書きました。Clojure では `map` に 3 つのコレクションを渡すと 3 つずつ組にしてくれるので、特徴量・正解・外れ値かどうかをそろえて `remove` で落とせます。添字が出てこないので、特徴量と正解がずれる心配がありません。

`assoc` は「そのキーだけ差し替えた新しいマップ」を返すので、`:x-test`・`:t-test`・`:columns` はそのまま残ります。Java 版・Scala 版が分割の型のコンストラクターに 4 つの値を並べ直していたのに対し、**変える鍵だけを書けます**。これは動的なマップを分割の表現に選んだことの利点です。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

区切り文字と文字コードを受け取って表を作る `load-delimited` を足します。

```clojure
(defn load-delimited
  "文字コードと区切り文字を指定して読み込み、1 行目を列名にする。
   文字コードを間違えても例外にはならず、読めないバイトは置換文字になる。"
  [file charset separator]
  (with-open [reader (InputStreamReader. (io/input-stream file) (Charset/forName charset))]
    (let [[header & rows] (csv/read-csv reader :separator separator)
          columns (mapv #(keyword (str/replace-first % bom "")) header)]
      {:columns columns
       :rows (mapv #(zipmap columns %) (remove #(every? str/blank? %) rows))})))
```

`clojure.data.csv` の `read-csv` は `:separator` に **文字** を受け取るので、`\tab`・`\,` と書きます（Java 版・Scala 版は正規表現を `Pattern.quote` で囲む必要がありました）。文字コードは `java.io.InputStreamReader` に `Charset` を渡して指定します。`clojure.java.io/reader` にも `:encoding` がありますが、この章では「どの層で文字コードを決めているか」をコードに出したいので、`InputStreamReader` を直に組み立てました。

文字コードを間違えたときの振る舞いは、Java 版・Scala 版と **違いました**。

```clojure
  (testing "Shift_JISのファイルを文字コードを渡して読み込む"
    (let [path (temp-file "weather" "weather_id,weather\n1,晴れ\n" "Shift_JIS")
          table (ch/load-delimited path "Shift_JIS" \,)]
      (is (= ["晴れ"] (mapv :weather (:rows table))))))
  (testing "Shift_JISのファイルをUTF-8として読むと例外を投げずに文字化けする"
    (let [path (temp-file "weather" "weather_id,weather\n1,晴れ\n" "Shift_JIS")
          table (ch/load-delimited path "UTF-8" \,)]
      (is (not= ["晴れ"] (mapv :weather (:rows table))))))
```

Java 版・Scala 版の `Files.readAllLines` は、復号できないバイトがあると `MalformedInputException` を投げました。`InputStreamReader` は既定で **置換文字（U+FFFD）に置き換えて読み進めます**。同じ JVM の上でも、どのクラスで読むかで振る舞いが変わります。Kotlin 版が Kotlin DataFrame で遭遇したのと同じ「黙って文字化けする」側です。

テストは「例外になる」ではなく「読めるが値が違う」を確かめる形になりました。**落ちてくれないほうが、テストで捕まえる価値は高い** と言えます。例外を出したいなら `CharsetDecoder` に `CodingErrorAction/REPORT` を設定して `InputStreamReader` を作りますが、この章では「既定はこうである」ことを記録するほうを選びました。

### マップによる結合と集計

利用者数の表に、天気 ID をキーにして天気の名前を加えます。

```clojure
(defn join-weather
  "天気 ID をキーにしたマップを引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。"
  [bike weather]
  (let [by-id (into {} (map (fn [row] [(get row join-key) row])) (:rows weather))
        added (vec (remove #(= join-key %) (:columns weather)))]
    (when-not (= (count by-id) (count (:rows weather)))
      (throw (IllegalArgumentException. (str (name join-key) " が一意ではありません"))))
    {:columns (into (vec (:columns bike)) added)
     :rows (into [] (keep (fn [row]
                            (when-let [found (get by-id (get row join-key))]
                              (merge row (select-keys found added)))))
                 (:rows bike))}))
```

`keep` は「関数を当てて `nil` でない結果だけを残す」関数です。`when-let` と組み合わせると、「引く・あれば結合する・無ければ捨てる」が 1 つの式になります。Scala 版の `flatMap` + `Option` と同じ働きで、Java 版の `filter(containsKey)` と `map(get)` の 2 段が要りません。

Scala 版と同じ落とし穴もありました。Clojure の `into {}` も、キーが重複すると後の値で静かに上書きします（Java の `Collectors.toMap` は例外を投げます）。「天気 ID が一意でなければ結合の前に気付ける」という Java 版の安全性を保つため、件数を比べて例外にしました。**便利な既定が、必ずしも安全な既定とは限りません。**

天気ごとの平均利用者数は、`group-by` と `sort-by` で求めます。

```clojure
(defn mean-count-by-weather
  "天気ごとの平均利用者数を、多い順に並べて返す。"
  [joined]
  (->> (:rows joined)
       (group-by :weather)
       (mapv (fn [[weather rows]]
               [weather (/ (reduce + (map #(chapter02/number % :cnt) rows)) (count rows))]))
       (sort-by second >)
       vec))
```

Java 版は「`groupingBy` が返す `HashMap` は順序を持たないので、並べ替えてから `LinkedHashMap::new` に集める」と書きました。Clojure 版も Scala 版と同じく、順序が要る結果を **マップではなくベクタの並び** で返します。`[[天気 平均] ...]` なら、順に意味があることが値の形から分かります。`:weather` をキーワードのまま `group-by` に渡せるのは、キーワードが「マップから自分を引く関数」でもあるからです。

## 9.9 特徴量の効果を測る

### Boston データの前処理

`prepare-boston` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

```clojure
(defn prepare-boston
  "CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。"
  [csv-file test-size seed]
  (let [table (chapter02/load-table csv-file)
        crimes (mapv #(chapter02/text % category) (:rows table))
        encoded (encode table category (categories crimes))
        {:keys [columns rows labels]} (chapter02/split-features-and-target encoded target)
        prices (mapv #(Double/parseDouble %) labels)
        split (chapter02/split-train-test rows prices test-size seed)
        means (chapter02/column-means (:x-train split) columns)]
    (assoc split
           :columns columns
           :x-train (chapter02/fill-missing (:x-train split) columns means)
           :x-test (chapter02/fill-missing (:x-test split) columns means))))
```

第 2 章の `split-features-and-target` は `{:columns ... :rows ... :labels ...}` というマップを返すので、`{:keys [columns rows labels]}` で 3 つ同時に受け取れます。Java 版は同じことをするために `FeaturesAndTarget` という record を用意し、Scala 版はタプルを分解しました。

ここで `:columns` を結果に足しているのが、この章で Clojure がいちばん苦労した点です。第 3 章まで（iris の 4 列）は、特徴量のマップの鍵の順がそのまま列の順でした。Clojure のマップは 8 件までは挿入順を保つ配列マップだからです。**Boston のデータは特徴量が 14 列あり、ハッシュマップになるので鍵の順は入れ替わります。** 列の順に意味がある処理（線形回帰に渡す行、表示）は、すべて `:columns` のベクタを引数で受け取る形にしました。

| 場面 | 順の持ち方 |
|------|-----------|
| 表（読み込み直後） | `:columns` のベクタ |
| 特徴量 1 件 | マップ。**順は当てにしない** |
| 標準化の平均・標準偏差 | マップ。並べるときは `:columns` を使う |
| 線形回帰に渡す行 | `columns` の順に並べたベクタ |

「9 件目から実装が変わるコレクション」は Clojure の実装上の最適化ですが、それに気付かずに鍵の順へ依存すると、**列が 8 列までのテストは通り、実データで壊れます**。テストのフィクスチャを小さく作るほど見つかりにくい種類のバグなので、順が要るところは最初から明示しました。

### 線形回帰と決定係数

決定係数を測るために、この章に正規方程式を解く最小の線形回帰を置きました。Tribuo の `DenseMatrix` のコレスキー分解で `XᵀX β = Xᵀt` を解きます。

```clojure
(defn linear-fit
  "先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く（Tribuo のコレスキー分解）。"
  [rows t]
  (let [design (into-array (map (fn [row] (double-array (cons 1.0 row))) rows))
        ^DenseMatrix x (DenseMatrix/createDenseMatrix design)
        transposed (.transpose x)
        factorization (.choleskyFactorization (.matrixMultiply transposed x))]
    (when-not (.isPresent factorization)
      (throw (IllegalArgumentException. "特徴量の列が互いに独立でないため、正規方程式を解けません")))
    (let [target-vector (DenseVector/createDenseVector (double-array t))
          beta (vec (.toArray (.solve (.get factorization)
                                      (.leftMultiply transposed target-vector))))]
      {:intercept (first beta) :weights (vec (rest beta))})))
```

Tribuo は Java のライブラリなので、行列は `double[][]`、`choleskyFactorization()` の戻り値は `java.util.Optional` です。Clojure の不変コレクションとの境目は 3 か所でした。

| 境目 | 橋渡し |
|------|-------|
| ベクタのベクタ → `double[][]` | `(into-array (map (fn [row] (double-array (cons 1.0 row))) rows))` |
| `java.util.Optional` | `.isPresent` で確かめて `.get` |
| `double[]` → ベクタ | `(vec (.toArray ...))` |

`(cons 1.0 row)` で先頭に 1 を足せるので、Java 版の `System.arraycopy` は要りません。`into-array` は最初の要素の型から配列の要素型を決めるので、`double-array` の並びを渡せば `double[][]` になります。Scala 版は `Optional` を `Option` に変換して `getOrElse` で例外にしましたが、Clojure には変換先が無いので `Optional` のまま `isPresent`・`get` を呼びます。**Java の型をそのまま扱う場面では、Clojure は Java に近い書き方になります。**

特徴量の組ごとの評価は `score-feature-set` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `standardizer` を求め、両方を `standardize-all` してから学習します。

```clojure
(defn score-feature-set
  "列から多項式特徴量を作って terms の項を選び、訓練データで標準化してから線形回帰で学習し、決定係数を求める。"
  [split columns terms]
  (let [train (select-columns (expand (:x-train split) columns) terms)
        test (select-columns (expand (:x-test split) columns) terms)
        std (standardizer train terms)
        x-train (to-rows (standardize-all std train) terms)
        x-test (to-rows (standardize-all std test) terms)
        model (linear-fit x-train (:t-train split))]
    {:train (r-squared (:t-train split) (linear-predict model x-train))
     :test (r-squared (:t-test split) (linear-predict model x-test))}))
```

`to-rows` は特徴量のマップを `terms` の順のベクタに直す関数です。ここで **順を決め直している** ので、`select-keys` がマップの順を保たなくても結果は変わりません。決定係数は `{:train ... :test ...}` というマップで返します。Java 版・Scala 版は `Scores` という型を作りましたが、Clojure では鍵の名前が同じ役目を果たします。タプル（`[0.77 0.86]`）にしないのは同じ理由です。**読む人には名前が要ります。**

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

```clojure
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
```

分割を表すマップをテストの中で手で書けるのは、型を宣言していないからです。Java 版・Scala 版は `TrainTestSplit` のコンストラクターを呼びました。**手軽な代わりに、鍵の綴りを間違えても実行するまで分かりません。** `:t-trian` と書いたテストはコンパイルできてしまい、`nil` を数えて落ちます（このとき失敗メッセージは「1.0 と比べたら違った」ではなく `NullPointerException` になります）。clj-kondo は未知のキーワードまでは見てくれないので、この種の間違いは静的解析でも捕まりません。

### 実データで測る

```bash
ML_DATA_DIR=<学習データの置き場> clojure -M:run chapter09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950
  2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628
  交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213
訓練データの PRICE の外れ値: 8 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

「標準化した訓練データの RM」は、標準化した訓練データにもう一度 `standardizer` を当て、その平均と標準偏差を表示しています。

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、ほかの版と同じ値になりました（分割に関係しないため）

**決定係数の 8 つの数値は、Java 版・Scala 版の記事とすべて一致しました。** 第 2 章で書いたとおり、Clojure 版の `split-train-test` は `java.util.Random` と Fisher-Yates のシャッフルで Java 版（`Collections.shuffle`）と同じ並びを作ります。同じ行が訓練データとテストデータに入るので、そのあとの標準化・多項式特徴量・正規方程式まで含めて同じ値になります。Kotlin 版は `kotlin.random.Random(0)` を使うので分け方が違い、2 乗の項でテスト 0.6457 → 0.7975 と別の値になりました。

数値が一致したことには、もう 1 つ意味があります。Java 版は `DoubleStream.sum()`（補正付きの加算）で平均や総和を求めていますが、Clojure 版は `(reduce + ...)` の素朴な加算です。加算の順は同じでも丸めの扱いは同じではありません。それでも表示する 4 桁では差が出ませんでした。**数値が合わないときに「乱数か手順か丸めか」を切り分けられるのは、ほかの版が照合先としてあるからです。**

この出力は `boston-data-test` で固定しています。`clojure.test` にはテストを飛ばす仕組みが無いので、第 2 章・第 3 章と同じく「データが無ければ理由を標準エラーに出して早く戻る」形にしました。

```clojure
(defn- data-file
  "学習データのファイルを返す。無ければ nil（テストはスキップする）。"
  [file-name]
  (let [path (str (dataset/dir) "/" file-name)]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println (str "学習データ " file-name " が配置されていない（gulp data:setup）のでスキップする"))
        nil))))
```

Scala 版の `assume` や JUnit の `assumeTrue` と違い、**スキップしたことはテストの結果に出ません**（「0 件の表明が通った」と数えられます）。理由を標準エラーに出しているのはそのためです。データがある環境では 4 つのテストで実データを確かめ、無い環境でも検査は通ります。

## 9.10 Notebook による探索と可視化

Clojure 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、`cljfmt check src test`・`clj-kondo --lint src test --fail-level warning`・`clojure -M:test`・`clojure -M:coverage` をかけました（第 5 章で整えた検査です）。

- **関数の分配束縛で引数を減らす** — `standardize` は `{:keys [means stds]}` を受け取り、`format-scores` は `{:keys [train test]}` を受け取ります。マップを 1 つ渡すだけで、呼ぶ側は鍵の名前で意図を示せます
- **順が要るところだけ列のベクタを回す** — `standardizer`・`expand`・`score-feature-set`・`run` はすべて `columns` を引数で受け取ります。「マップの鍵の順に頼らない」という方針を、引数の形で守りました
- **`->>` を使うのは段が深いところだけ** — `mean-count-by-weather` だけがスレッディングマクロです。短い入れ子まで `->>` にすると、かえって行数が増えます

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、次の形で公開しています。

| API | 内容 |
|-----|------|
| `(standardizer x columns)` | 指定した列の平均と標準偏差（件数で割る）を `{:columns ... :means {...} :stds {...}}` で返す |
| `(standardize std features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `(standardize-all std x)` | 並びを標準化する |
| `(tribuo-standardize train values)` | Tribuo の標準化（件数から 1 を引く）で比べる |

K-means は距離を使うので、標準偏差の定義（件数で割る）を変えると結果が変わります。Tribuo の `MeanStdDevTransformation` ではなく、この `standardizer` を使うことを第 14 章でも明記します。

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Clojure の TDD で自作し、標準化を Tribuo と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `categories`・`encode` | — | 訓練データとテストデータで列をそろえる |
| 標準化 | `standardizer`・`standardize` | Tribuo の `MeanStdDevTransformation` | 標準偏差の定義がライブラリで違う、分散 0 の列、テストデータの平均を使わない |
| 多項式特徴量 | `expand`・`term-name` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile`・`iqr-outliers` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `load-delimited`・`join-weather` | — | 区切り文字と文字コード、内部結合で消える行、`into {}` の静かな上書き |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。これらの値は Java 版・Scala 版と完全に一致しました。

Clojure 版ならではの学びもありました。

1. **マップの鍵の順は 9 件目で変わる** — 8 件までの配列マップは挿入順を保ち、9 件以上のハッシュマップは保ちません。列が 14 列ある実データで初めて効くので、順が要るところは `columns` のベクタを引数で回す設計にした
2. **型が無い分、名前と位置で示す** — 項は 2 要素ベクタ、分割と決定係数はマップ。`Term`・`Scores`・`TrainTestSplit` のような型は作らないので、`term-name`・`:train`・`:x-train` という名前が契約になる。手軽さと引き換えに、鍵の綴り間違いは実行するまで分からない
3. **`keep` + `when-let` が内部結合になる** — Scala 版の `flatMap` + `Option` と同じ「引く・あれば使う・無ければ捨てる」を 1 つの式で書ける。ただし `nil` は「無い」と「値が nil」を区別しない
4. **Java の API との境目は型ヒントと明示的な変換** — Tribuo の `TransformStatistics`・`DenseMatrix`・`Optional` は、型ヒント・`double-array`・`into-array`・`isPresent` で橋渡しした。境目では Clojure は Java に近い書き方になる
5. **文字コードを間違えても落ちない** — `InputStreamReader` は復号できないバイトを置換文字にして読み進める。Java 版・Scala 版の `Files.readAllLines`（例外）とは既定が違うので、テストは「例外になる」ではなく「値が違う」で書いた

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
