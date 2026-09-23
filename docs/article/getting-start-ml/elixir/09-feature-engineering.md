---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Elixir の TDD で自作し、特徴量の組み合わせごとの決定係数を測って、Scholar の StandardScaler と標準偏差の定義が一致すること、そして Erlang/Elixir の標準では Shift_JIS が読めず codepagex が要ることを確かめる。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
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

標準化は Scholar の `Scholar.Preprocessing.StandardScaler` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Java 版](../java/09-feature-engineering.md)・[Scala 版](../scala/09-feature-engineering.md)・[Clojure 版](../clojure/09-feature-engineering.md) と数値を対比します。Elixir 版はデータフレームのライブラリを使わず、第 2 章で決めた「列名のアトムをキーにしたマップの並び」で表を持ちます（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。

この章で Elixir ならではの点は 3 つです。

- **Erlang/Elixir の標準では Shift_JIS が読めません。** JVM の言語版は `Charset.forName("Shift_JIS")` で済んだところが、Elixir では codepagex という外部ライブラリと、どの符号化表を組み込むかの設定が要ります。この章がシリーズで初めて「文字コードのために依存を増やす」章になります
- **文字コードを間違えても例外になりません。** Shift_JIS のファイルを UTF-8 として読むと、Elixir のバイナリはそのまま素通りし、UTF-8 として **不正な文字列** が値に入ります。逆に UTF-8 のファイルを CP932 として読むと、codepagex が `{:error, "Invalid bytes for encoding"}` を返します
- **標準偏差の定義が Scholar と一致しました。** Java 版・Scala 版・Clojure 版が Tribuo と突き合わせたときは「Tribuo は n−1 で割る」というずれが出ましたが、Scholar の `StandardScaler` は **n で割る**（母標準偏差）ので、自作の値とそのまま一致します

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード |
|---------|------|----------|-----------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

第 2 章の `load_table/1` は「BOM 付きの UTF-8 のカンマ区切り」を読む関数です。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] Scholar の `StandardScaler` と突き合わせる
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

Java 版・Scala 版は技法ごとにファイルを分けましたが、Elixir 版は 1 つのモジュール `GettingStartedMl.Chapter09` にまとめ、`##` で始まるコメントで節に区切ります。関数がモジュールの中で名前とアリティで区別されるので、`Dummies.encode` と `PolynomialFeatures.expand` のようにモジュール名で修飾する必要がありません。

| 節 | 関数 |
|----|------|
| カテゴリ値をダミー変数にする | `categories/1`・`encode/3` |
| 標準化 | `standardizer/2`・`standardize/2`・`standardize_all/2`・`scholar_standardize/2` |
| 多項式特徴量 | `pairs_with_replacement/1`・`term_name/1`・`expanded_columns/1`・`expand/2`・`select_columns/2` |
| 外れ値 | `quantile/2`・`iqr_outliers/2`・`remove_target_outliers/1` |
| 表の読み込みと結合 | `load_delimited/3`・`join_weather/2`・`mean_count_by_weather/1` |
| 線形回帰 | `linear_fit/2`・`linear_predict/2`・`r_squared/2` |
| ボストンの住宅価格 | `prepare_boston/3`・`score_feature_set/3`・`run/0` |

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```elixir
    test "先頭を除いたカテゴリを辞書順に返す" do
      assert C.categories(["low", "high", "very_low", "low"]) == ["low", "very_low"]
    end

    test "欠損値は数えない" do
      assert C.categories(["low", "", "high"]) == ["low"]
    end
```

第 2 章の行は空欄を空文字列のまま持つので、`String.trim/1` で除きます。

```elixir
  def categories(values) do
    values
    |> Enum.reject(&(String.trim(&1) == ""))
    |> Enum.uniq()
    |> Enum.sort()
    |> Enum.drop(1)
  end
```

Clojure 版は `(vec (rest (sort (distinct (remove str/blank? values)))))` と内側から外側へ読む形でした。Elixir はパイプライン演算子が言語の中心にあるので、**左から右へ読む形が既定** です。Java 版の `stream().filter(...).distinct().sorted().skip(1).toList()`、Scala 版の `values.filter(...).distinct.sorted.drop(1)` と並びまで同じになります。メソッドチェーンと違うのは、`Enum.reject/2` などが「コレクションを第 1 引数に取る普通の関数」であり、パイプがその第 1 引数に値を差し込んでいるだけ、という点です。

### 表に列を加える

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```elixir
    test "カテゴリごとに零と一の列を作り元の列を取り除く" do
      table = %{
        columns: [:RM, :CRIME],
        rows: [
          %{RM: "6.0", CRIME: "low"},
          %{RM: "6.0", CRIME: "high"},
          %{RM: "6.0", CRIME: "very_low"}
        ]
      }

      encoded = C.encode(table, :CRIME, ["low", "very_low"])

      assert encoded.columns == [:RM, :CRIME_low, :CRIME_very_low]
      assert Enum.map(encoded.rows, & &1[:CRIME_low]) == ["1", "0", "0"]
      assert Enum.map(encoded.rows, & &1[:CRIME_very_low]) == ["0", "0", "1"]
      assert Enum.all?(encoded.rows, &(not is_map_key(&1, :CRIME)))
    end
```

```elixir
  def encode(%{columns: columns, rows: rows}, column, categories) do
    dummy_columns = Enum.map(categories, &dummy_column(column, &1))

    %{
      columns: Enum.reject(columns, &(&1 == column)) ++ dummy_columns,
      rows:
        Enum.map(rows, fn row ->
          value = Map.get(row, column)

          categories
          |> Enum.zip(dummy_columns)
          |> Enum.reduce(Map.delete(row, column), fn {c, name}, acc ->
            Map.put(acc, name, if(c == value, do: "1", else: "0"))
          end)
        end)
    }
  end
```

引数の `%{columns: columns, rows: rows}` は、マップを受け取りながらその中身を取り出す **パターンマッチ** です。Clojure 版の `{:keys [columns rows]}` と同じ働きですが、Elixir では「この形のマップでなければ関数節に入らない」という条件にもなります。`:columns` を持たないマップを渡すと、関数の本体ではなく **呼び出しの時点で** `FunctionClauseError` になります。動的型付けでも、引数の形だけは関数の入口で確かめられます。

Java 版は `new HashMap<>(row.cells())` と写してから `remove`・`put` で書き換えました。Elixir のマップは不変なので、`Map.delete/2` が「その列を除いた新しいマップ」を返し、`Map.put/3` が「そこに対を足した新しいマップ」を返します。元の行が変わらないことをテストで確かめる必要すらありません。

列名は **アトム** にしました。`:CRIME_low` のような名前は `String.to_atom/1` で作ります。

```elixir
  defp dummy_column(column, category_value), do: String.to_atom("#{column}_#{category_value}")
```

`String.to_atom/1` はアトム表に新しい原子を登録します。アトムはガベージコレクトされず、既定の上限（約 100 万）を超えると VM が落ちるので、**外から来る文字列を無制限にアトムにするのは事故のもと** です。ここではカテゴリの種類が 3 つしかないと分かっているので使いました。列名がデータ由来である以上、Elixir ではこの判断を毎回します。Clojure の `keyword` やキーワードのインターンには同じ制約が無いので、ここは Elixir 特有の注意点です。

セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `split_features_and_target/2`・`column_means/2`・`fill_missing/3` を、ダミー変数の列にもそのまま使えます。カテゴリを引数で受け取るのは Java 版・Scala 版と同じ理由で、訓練データで決めたカテゴリをテストデータにも当てはめ、列をそろえるためです。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### 平均と標準偏差を求める

第 14 章（K-means）でもこの関数を使うので、第 2 章の特徴量（列名のアトムから浮動小数点数へのマップ）をそのまま受け取れる形にします。

```elixir
    test "訓練データから列ごとの平均と標準偏差を求める" do
      std =
        C.standardizer([rm_lstat(1, 10), rm_lstat(2, 10), rm_lstat(3, 40)], rm_lstat_columns())

      assert_in_delta std.means[:RM], 2.0, @delta
      assert_in_delta std.means[:LSTAT], 20.0, @delta
      assert_in_delta std.stds[:RM], :math.sqrt(2.0 / 3), @delta
      assert_in_delta std.stds[:LSTAT], :math.sqrt(200.0), @delta
    end

    test "値がすべて同じ列の標準偏差は一にして零を返す" do
      std = C.standardizer([rm_lstat(5, 1), rm_lstat(5, 2)], rm_lstat_columns())

      assert_in_delta std.stds[:RM], 1.0, @delta
      assert_in_delta C.standardize(std, rm_lstat(5, 1))[:RM], 0.0, @delta
    end
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。scikit-learn の `StandardScaler` と同じ定義にしました。

```elixir
  def standardizer(x, columns)

  def standardizer([], _columns), do: raise(ArgumentError, "特徴量が 1 件もありません")

  def standardizer(x, columns) do
    stats =
      Map.new(columns, fn column ->
        values = Enum.map(x, &Map.fetch!(&1, column))
        mean = Enum.sum(values) / length(values)
        variance = Enum.sum(Enum.map(values, &((&1 - mean) * (&1 - mean)))) / length(values)
        std = :math.sqrt(variance)
        {column, {mean, if(std == 0.0, do: 1.0, else: std)}}
      end)

    %{
      columns: columns,
      means: Map.new(stats, fn {column, {mean, _}} -> {column, mean} end),
      stds: Map.new(stats, fn {column, {_, std}} -> {column, std} end)
    }
  end
```

Java 版・Scala 版・Clojure 版との違いを 3 つ挙げます。

1. **「1 件も無い」を関数節で弾く** — `def standardizer([], _columns), do: raise(...)` という節を先に置くと、空リストはこちらに入ります。Clojure 版の `(when (empty? x) (throw ...))` にあたる検査が、本体の中の `if` ではなく **引数のパターン** になりました。`def standardizer(x, columns)` という本体の無い宣言（関数ヘッダー）を先頭に置いているのは、複数の節にまたがるドキュメントをそこに書くためです
2. **順序は型ではなく引数で持つ** — Java 版は順序を保つために `LinkedHashMap` を、Scala 版は `SeqMap` を使いました。Elixir のマップは **キーの順を保ちません**（32 件を超えるとハッシュマップの実装に変わり、それ以下でもキーの項順に並びます）。だから「どの列があるか」ではなく「列がどの順か」を表したいときは、マップの鍵の順に頼らず `columns` のリストを引数で受け取り、`:columns` として結果にも残します
3. **列ごとの統計を 1 回で作る** — 平均と標準偏差を別々のループで作らず、`{平均, 標準偏差}` のタプルを 1 回作ってから 2 つのマップに分けます

標準化する側は `case` でマップを引きます。

```elixir
  def standardize(%{means: means, stds: stds}, features) do
    Map.new(features, fn {column, value} ->
      case Map.fetch(means, column) do
        {:ok, mean} -> {column, (value - mean) / Map.fetch!(stds, column)}
        :error -> {column, value}
      end
    end)
  end
```

Clojure 版は `if-let` で「`nil` でなければ」と書きました。Elixir の `Map.fetch/2` は `{:ok, 値}` か `:error` を返すので、**「無い」と「値が nil である」を取り違えません**。Rust の `Option`・Scala の `Option` と同じ区別が、型ではなくタグ付きタプルという値の形で得られます。`||` や `if` で書くと `nil` を持つ列と区別できなくなるので、この章では `Map.fetch/2` と `Map.fetch!/2` を使い分けました。

### Scholar の標準化と突き合わせる

Scholar には `Scholar.Preprocessing.StandardScaler` があります。`fit/1` が平均と標準偏差を持つ構造体を返し、`transform/2` が別のテンソルを標準化します。

```elixir
  def scholar_standardize(train, values) do
    scaler = train |> to_column() |> StandardScaler.fit()

    scaler
    |> StandardScaler.transform(to_column(values))
    |> Nx.to_flat_list()
  end
```

```elixir
  defp to_column(values), do: Nx.tensor(Enum.map(values, &[&1 * 1.0]), type: :f64)
```

`type: :f64` を明示しているのが要点です。Nx の既定は **単精度（f32）** なので、これを書かないと標準化した値が 7 桁目からずれ、自作の値と `1.0e-9` の精度では一致しません。NumPy が既定で倍精度なのとちょうど裏返しで、Elixir 版ではあらゆるテンソルにこれを書きます（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。`&1 * 1.0` は整数が混ざっても浮動小数点数にするための小細工です。

さて、自作の標準化と同じ値になるかを確かめました。

```elixir
    test "ScholarのStandardScalerは件数で割る標準偏差を使う" do
      train = [6.2, 5.8, 7.1, 6.5]
      values = [6.2, 8.0]
      std = C.standardizer(Enum.map(train, &%{RM: &1}), [:RM])
      expected = Enum.map(values, &C.standardize(std, %{RM: &1})[:RM])

      Enum.zip(expected, C.scholar_standardize(train, values))
      |> Enum.each(fn {mine, theirs} -> assert_in_delta mine, theirs, @delta end)
    end

    test "件数から一を引いて割る標準偏差とは一致しない" do
      train = [6.2, 5.8, 7.1, 6.5]
      mean = Enum.sum(train) / length(train)

      sample_std =
        :math.sqrt(Enum.sum(Enum.map(train, &((&1 - mean) * (&1 - mean)))) / (length(train) - 1))

      [first | _] = C.scholar_standardize(train, [8.0])
      refute_in_delta first, (8.0 - mean) / sample_std, 1.0e-6
    end
```

**一致しました。** `[6.2, 5.8, 7.1, 6.5]` の平均は 6.4、Scholar が返す標準偏差は `0.4743416490252568` で、これは `√(0.9/4)` すなわち **件数 4 で割る母標準偏差** です。件数から 1 を引いて割る不偏標準偏差なら `√(0.9/3) = 0.5477…` になるはずなので、はっきり区別できます。

ここがほかの版と結果が分かれたところです。

| 版 | ライブラリ | 標準偏差の定義 | 自作と一致するか |
|----|-----------|--------------|----------------|
| Kotlin・Java・Scala・Clojure | Tribuo の `MeanStdDevTransformation` | n − 1 で割る（不偏） | しない（比は `√((n−1)/n)`） |
| Python | scikit-learn の `StandardScaler` | n で割る（母） | する |
| **Elixir** | **Scholar の `StandardScaler`** | **n で割る（母）** | **する** |

Scholar は scikit-learn を手本にしているので、定義もそちらにそろっています。JVM 系の 4 つの版が「同じ標準偏差でもライブラリによって割る数が違う」という落とし穴を報告したのに対し、Elixir 版は **一致することを確かめるテスト** と **不偏標準偏差ではないことを確かめるテスト** の 2 本で、その定義を固定しました。1 本目だけだと「たまたま近い」場合に気付けないので、2 本目で反対側からも押さえています。

第 14 章の K-means のように距離を使うアルゴリズムでは、どちらの定義を使ったかで結果が変わります。Elixir 版は自作も Scholar も n で割るので、その心配がありません。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

部屋数（RM）と価格の関係が直線でなく曲線なら、RM の 2 乗の列を加えると線形回帰でも曲線を表せます。2 つの列の積（交互作用の項）を加えると、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表せます。

```elixir
    test "二列なら二乗の列と二つの列の積の列を加える" do
      x = [rm_lstat(2, 5), rm_lstat(3, 7)]
      expanded = C.expand(x, rm_lstat_columns())

      assert C.expanded_columns(rm_lstat_columns()) ==
               [:RM, :LSTAT, :"RM^2", :"RM LSTAT", :"LSTAT^2"]

      assert Enum.map(expanded, & &1[:"RM LSTAT"]) == [10.0, 21.0]
      assert Enum.map(expanded, & &1[:"LSTAT^2"]) == [25.0, 49.0]
    end

    test "三列ならscikit-learnと同じ順の九列になる" do
      assert Enum.map(C.expanded_columns([:RM, :LSTAT, :PTRATIO]), &to_string/1) ==
               [
                 "RM",
                 "LSTAT",
                 "PTRATIO",
                 "RM^2",
                 "RM LSTAT",
                 "RM PTRATIO",
                 "LSTAT^2",
                 "LSTAT PTRATIO",
                 "PTRATIO^2"
               ]
    end
```

1 列・2 列・3 列と三角測量し、3 列では scikit-learn の `PolynomialFeatures` と同じ並びの 9 列になることを確かめました。

Java 版は `Pair` という record に、Scala 版は `Term` という case class に、項を表す型を用意しました。Elixir では **2 要素のタプル** を項とし、名前を作る関数を分けます。

```elixir
  def pairs_with_replacement(columns) do
    indexed = Enum.with_index(columns)

    for {left, i} <- indexed, {right, j} <- indexed, j >= i, do: {left, right}
  end

  @doc ~S(項の名前。scikit-learn の get_feature_names_out と同じ形（:"RM^2"・:"RM LSTAT"）にする。)
  def term_name({left, left}), do: String.to_atom("#{left}^2")
  def term_name({left, right}), do: String.to_atom("#{left} #{right}")
```

`term_name/1` の 2 つの節が、この章でいちばん Elixir らしい書き方です。**1 つ目の節は `{left, left}` と同じ変数を 2 回書いています。** Elixir のパターンマッチでは、同じ変数が 2 回現れると「その 2 つが等しいこと」が条件になります。つまり `{:RM, :RM}` は 1 つ目の節に、`{:RM, :LSTAT}` は 2 つ目の節に入ります。Clojure 版は `(if (= left right) ...)` と本体で分岐しましたが、Elixir では **分岐が関数の入口に出ています**。`if` が 1 つ減ったぶん、「2 つの場合がある」ことがコードの形として見えます。

`for` の内包表記は `i` の外側と `j` の内側という並びで、Java 版の二重の `for` 文と生成される順も同じです。`j >= i` はフィルターで、内包表記の中に条件を並べるだけで絞り込めます。

項の名前は `:"RM^2"`・`:"RM LSTAT"` のような **引用符付きのアトム** になります。`:RM^2` とは書けず（`^` は演算子）、`RM LSTAT` には空白が入るので、どちらも引用符が要ります。テストでもこの形で書いています。「アトムは名前であって識別子ではない」ことがはっきり出るところです。

```elixir
  def expand(x, columns) do
    pairs = pairs_with_replacement(columns)

    Enum.map(x, fn features ->
      terms =
        Map.new(pairs, fn {left, right} = pair ->
          {term_name(pair), Map.fetch!(features, left) * Map.fetch!(features, right)}
        end)

      Map.merge(Map.take(features, columns), terms)
    end)
  end
```

Java 版は結果の `double[]` を確保して添字で埋めましたが、Elixir では「元の列のマップ」に「項のマップ」を `Map.merge/2` で足すだけです。添字の計算（`values[columns.size() + k]`）が消えるので、ずれる余地がありません。`{left, right} = pair` は、分解しつつ元のタプルも `pair` として受け取る書き方です（Clojure の `:as` にあたります）。

使う項を選ぶ `select_columns/2` は `Map.take/2` の言い換えで済みました。

```elixir
  def select_columns(x, columns), do: Enum.map(x, &Map.take(&1, columns))
```

ただし `Map.take/2` が返すのはマップなので、**結果の列の順は保証されません**。順が要るところ（線形回帰に渡す行）では、あとで `columns` のリストの順に並べ直します。

## 9.7 外れ値を検出する

第 1 四分位数（Q1）と第 3 四分位数（Q3）の差を **四分位範囲（IQR）** と呼びます。`Q1 − 1.5 × IQR` より小さい値と、`Q3 + 1.5 × IQR` より大きい値を外れ値とみなします。

```elixir
    test "四分位数の位置が値の間にあれば前後の値から線形補間する" do
      assert_in_delta C.quantile([4.0, 1.0, 3.0, 2.0], 0.25), 1.75, @delta
    end

    test "第三四分位数からIQRの一点五倍より大きい値を外れ値とする" do
      assert C.iqr_outliers([1.0, 2.0, 3.0, 4.0, 100.0]) == [false, false, false, false, true]
    end
```

```elixir
  def quantile(values, q) do
    sorted = Enum.sort(values)
    position = (length(sorted) - 1) * q
    lower = trunc(Float.floor(position))
    upper = trunc(Float.ceil(position))
    at_lower = Enum.at(sorted, lower)

    at_lower + (Enum.at(sorted, upper) - at_lower) * (position - lower)
  end

  @doc "第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。"
  def iqr_outliers(values, k \\ @default_k) do
    q1 = quantile(values, 0.25)
    q3 = quantile(values, 0.75)
    iqr = q3 - q1

    Enum.map(values, &(&1 < q1 - k * iqr or &1 > q3 + k * iqr))
  end
```

Java 版は既定の引数が無いので `iqrOutliers(values)` と `iqrOutliers(values, k)` の 2 つをオーバーロードし、Clojure 版は多アリティ関数で書きました。Elixir には `\\` という **既定引数** の記法があり、`iqr_outliers/1` と `iqr_outliers/2` の両方が自動で定義されます。Scala 版・Kotlin 版の `k: Double = DefaultK` にそのまま対応します。オーバーロードと違って本体は 1 つです。

外れ値を除く `remove_target_outliers/1` は、訓練データから正解（価格）が外れ値の行だけを取り除き、テストデータには手を付けません。テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいても評価から外してはいけないからです。

```elixir
  def remove_target_outliers(split) do
    kept =
      [split.x_train, split.t_train, iqr_outliers(split.t_train)]
      |> Enum.zip()
      |> Enum.reject(fn {_features, _value, outlier?} -> outlier? end)

    %{
      split
      | x_train: Enum.map(kept, &elem(&1, 0)),
        t_train: Enum.map(kept, &elem(&1, 1))
    }
  end
```

Java 版は「残す添字の `List<Integer>` を作り、特徴量と正解をそれぞれ添字で引く」と書きました。Elixir の `Enum.zip/1` は **リストのリスト** を受け取って、対応する要素を 1 つのタプルにまとめてくれるので、特徴量・正解・外れ値かどうかをそろえて `Enum.reject/2` で落とせます。添字が出てこないので、特徴量と正解がずれる心配がありません。

`%{split | x_train: ..., t_train: ...}` は **更新構文** で、「そのキーだけ差し替えた新しいマップ」を返します。`:x_test`・`:t_test`・`:columns` はそのまま残ります。しかもこの構文は、**既に存在するキーしか書けません**。`%{split | x_trian: ...}` と綴りを間違えると `KeyError` になります。`Map.put/3` なら新しいキーとして静かに追加されてしまうので、「既存のキーを更新するつもり」を構文で示せるのは、動的型付けの言語では貴重な安全網です。

## 9.8 表を結合して特徴量を増やす

### タブ区切り

区切り文字を選べるようにするため、第 1 章から使っている `GettingStartedMl.Csv` に口を足しました。

```elixir
  NimbleCSV.define(GettingStartedMl.Csv.Parser, separator: ",", escape: "\"")
  NimbleCSV.define(GettingStartedMl.Csv.TabParser, separator: "\t", escape: "\"")
```

```elixir
  def parse_table(contents, separator \\ ",") do
    [header | rows] = parser(separator).parse_string(contents, skip_headers: false)
    columns = header |> strip_bom() |> Enum.map(&String.to_atom/1)

    rows =
      rows
      |> Enum.reject(fn row -> Enum.all?(row, &(String.trim(&1) == "")) end)
      |> Enum.map(fn row -> columns |> Enum.zip(row) |> Map.new() end)

    {columns, rows}
  end

  defp parser(","), do: Parser
  defp parser("\t"), do: TabParser
  defp parser(separator), do: raise(ArgumentError, "区切り文字に対応していません: #{separator}")
```

ここが JVM 系の版といちばん違うところです。`clojure.data.csv` は `:separator` に文字を実行時に渡せましたが、**NimbleCSV のパーサーはマクロで生成されます**。`NimbleCSV.define/2` は区切り文字ごとに専用のモジュールをコンパイル時に作るので、区切り文字を実行時の引数にはできません。そこで「使う区切り文字ぶんだけモジュールを定義しておき、実行時にはどのモジュールを呼ぶかを選ぶ」形にしました。`parser(",")` が返すのは **モジュール名というアトム** で、`parser(separator).parse_string(...)` はそのアトムに対する動的なモジュール呼び出しです。

未対応の区切り文字は 3 つ目の節で `raise` します。パターンマッチで「対応している 2 つ」を先に並べ、残りを落とす形なので、対応表が関数の形そのものになります。

### Shift_JIS

**Erlang/Elixir の標準ライブラリは Shift_JIS を読めません。** 文字列はすべて UTF-8 のバイナリという前提で作られていて、`String` のどの関数にも文字コードを渡す口がありません。`:unicode` モジュールも UTF-8・UTF-16・UTF-32 と Latin-1 しか扱いません。JVM の言語版が `Charset.forName("Shift_JIS")` の 1 行で済んだところに、Elixir では外部ライブラリ（codepagex）が要ります（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。

しかも codepagex は、既定では一部の符号化表しか組み込みません。使いたい表を設定に書く必要があります。

```elixir
import Config

# codepagex は既定で一部の符号化表しか組み込まない。
# 第 9 章が読む Shift_JIS（CP932）の CSV のために、明示して組み込む。
config :codepagex, :encodings, ["VENDORS/MICSFT/WINDOWS/CP932"]
```

この設定を忘れると、コンパイルは通り、実行時に `{:error, "Unknown encoding ..."}` が返ります。**符号化表がコンパイル時に決まる** のは、NimbleCSV の区切り文字と同じ構図です。Elixir では「速さのためにマクロで固める」設計がライブラリの側にもよく現れ、そのぶん設定がコンパイル時に移ります。

読み込みはこうなりました。

```elixir
  def load_delimited(path, encoding, separator) do
    {columns, rows} = path |> File.read!() |> decode(encoding) |> Csv.parse_table(separator)

    %{columns: columns, rows: rows}
  end
```

```elixir
  defp decode(binary, :utf8), do: binary

  defp decode(binary, :cp932) do
    case Codepagex.to_string(binary, @cp932) do
      {:ok, text} -> text
      {:error, reason} -> raise ArgumentError, "CP932 として読めません: #{reason}"
    end
  end
```

`decode(binary, :utf8)` が **何もしない** のが目を引きます。Elixir の文字列は UTF-8 のバイナリそのものなので、UTF-8 として読むとは「バイト列をそのまま使う」ことだからです。ここから、文字コードを間違えたときの振る舞いが出てきます。

```elixir
    test "Shift_JISのファイルを文字コードを渡して読み込む" do
      path = temp_file("weather", C.to_cp932("weather_id,weather\n1,晴れ\n"))
      table = C.load_delimited(path, :cp932, ",")

      assert Enum.map(table.rows, & &1.weather) == ["晴れ"]
    end

    test "Shift_JISのファイルをUTF-8として読むと例外を投げずに壊れた文字列になる" do
      path = temp_file("weather", C.to_cp932("weather_id,weather\n1,晴れ\n"))
      table = C.load_delimited(path, :utf8, ",")

      [%{weather: weather}] = table.rows
      refute weather == "晴れ"
      refute String.valid?(weather)
    end

    test "UTF-8のファイルをShift_JISとして読むと失敗する" do
      path = temp_file("weather", "weather_id,weather\n1,晴れ\n")

      assert_raise ArgumentError, fn -> C.load_delimited(path, :cp932, ",") end
    end
```

3 本のテストが、3 通りの結末を記録しています。

| 読むもの | 指定した文字コード | 結果 |
|---------|-----------------|------|
| Shift_JIS | `:cp932` | 正しく読める |
| Shift_JIS | `:utf8` | **例外なし。** `String.valid?/1` が `false` になる不正な文字列が値に入る |
| UTF-8 | `:cp932` | codepagex が `{:error, "Invalid bytes for encoding"}` を返し、`ArgumentError` になる |

2 行目が要注意です。Java 版・Scala 版の `Files.readAllLines` は `MalformedInputException` を投げ、Clojure 版の `InputStreamReader` は置換文字（U+FFFD）に置き換えて読み進めました。**Elixir はそのどちらでもなく、バイト列を素通りさせます。** `晴れ` の CP932 表現は `<<144, 176, 130, 234>>` で、これは UTF-8 としては不正なバイト列ですが、Elixir のバイナリとしては何の問題もなく扱えます。文字化けすら起こらず、ただ「UTF-8 ではないバイナリ」が値として流れていきます。

こういう値は、そのままファイルに書き戻したり比較したりする分には動いてしまい、`String.length/1` や `IO.puts/1` に渡したところで初めて壊れます。つまり **失敗が読み込みから遠いところまで遅れます**。テストが「例外になる」ではなく「`String.valid?/1` が `false` になる」で書かれているのはそのためで、**落ちてくれないほうが、テストで捕まえる価値は高い** と言えます。

なお 3 行目は、JVM の版と違って codepagex が **明示的なエラーを返してくれた** 例です。文字コードの取り違えは 2 方向あり、片方は静かに通り、もう片方は止まる。この非対称も記録しておく価値があります。

### マップによる結合と集計

利用者数の表に、天気 ID をキーにして天気の名前を加えます。

```elixir
  def join_weather(bike, weather) do
    by_id = Map.new(weather.rows, &{Map.fetch!(&1, @join_key), &1})
    added = Enum.reject(weather.columns, &(&1 == @join_key))

    if map_size(by_id) != length(weather.rows) do
      raise ArgumentError, "#{@join_key} が一意ではありません"
    end

    %{
      columns: bike.columns ++ added,
      rows:
        Enum.flat_map(bike.rows, fn row ->
          case Map.fetch(by_id, Map.fetch!(row, @join_key)) do
            {:ok, found} -> [Map.merge(row, Map.take(found, added))]
            :error -> []
          end
        end)
    }
  end
```

`Enum.flat_map/2` で「あれば 1 要素のリスト、無ければ空リスト」を返すと、内部結合になります。Clojure 版の `keep` + `when-let`、Scala 版の `flatMap` + `Option` と同じ働きで、Java 版の `filter(containsKey)` と `map(get)` の 2 段が要りません。

Scala 版・Clojure 版と同じ落とし穴もありました。Elixir の `Map.new/2` も、キーが重複すると後の値で静かに上書きします（Java の `Collectors.toMap` は例外を投げます）。「天気 ID が一意でなければ結合の前に気付ける」という Java 版の安全性を保つため、件数を比べて例外にしました。**便利な既定が、必ずしも安全な既定とは限りません。**

天気ごとの平均利用者数は、`Enum.group_by/2` と `Enum.sort_by/3` で求めます。

```elixir
  def mean_count_by_weather(joined) do
    joined.rows
    |> Enum.group_by(& &1.weather)
    |> Enum.map(fn {weather, rows} ->
      {weather, Enum.sum(Enum.map(rows, &Chapter02.number(&1, :cnt))) / length(rows)}
    end)
    |> Enum.sort_by(&elem(&1, 1), :desc)
  end
```

Java 版は「`groupingBy` が返す `HashMap` は順序を持たないので、並べ替えてから `LinkedHashMap::new` に集める」と書きました。Elixir 版も Scala 版・Clojure 版と同じく、順序が要る結果を **マップではなくタプルのリスト** で返します。`[{天気, 平均}, ...]` なら、順に意味があることが値の形から分かります。`Enum.sort_by/3` の第 3 引数に `:desc` を渡せるので、比較関数を書かずに降順にできます。

## 9.9 特徴量の効果を測る

### Boston データの前処理

`prepare_boston/3` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

```elixir
  def prepare_boston(path, test_size, seed) do
    table = Chapter02.load_table(path)
    crimes = Enum.map(table.rows, &Chapter02.text(&1, @category))
    encoded = encode(table, @category, categories(crimes))

    %{columns: columns, rows: rows, labels: labels} =
      Chapter02.split_features_and_target(encoded, @target)

    prices = Enum.map(labels, &Chapter02.number(%{@target => &1}, @target))
    split = Chapter02.split_train_test(rows, prices, test_size, seed)
    means = Chapter02.column_means(split.x_train, columns)

    split
    |> Map.put(:columns, columns)
    |> Map.put(:x_train, Chapter02.fill_missing(split.x_train, columns, means))
    |> Map.put(:x_test, Chapter02.fill_missing(split.x_test, columns, means))
  end
```

`%{columns: columns, rows: rows, labels: labels} = ...` は、右辺のマップから 3 つの値を同時に取り出す **マッチ演算子** です。Java 版は同じことをするために `FeaturesAndTarget` という record を用意し、Scala 版はタプルを分解しました。Elixir では代入の左辺がパターンなので、値を取り出す構文と分岐の構文が同じものになっています。

ここで `:columns` を結果に足しているのが、この章で Elixir がいちばん気を使った点です。第 3 章まで（iris の 4 列）は、特徴量のマップの鍵の順がそのまま列の順のように見えていました。実際には **Elixir のマップはキーの順を保証しません**。小さなマップではキーの項順（アトムなら生成順）に並び、32 件を超えると実装が変わります。どちらにせよ「挿入した順」ではありません。列の順に意味がある処理（線形回帰に渡す行、表示）は、すべて `:columns` のリストを引数で受け取る形にしました。

| 場面 | 順の持ち方 |
|------|-----------|
| 表（読み込み直後） | `:columns` のリスト |
| 特徴量 1 件 | マップ。**順は当てにしない** |
| 標準化の平均・標準偏差 | マップ。並べるときは `:columns` を使う |
| 線形回帰に渡す行 | `columns` の順に並べたリスト |

「マップの順に頼らない」は Clojure 版が「9 件目から実装が変わる」として学んだのと同じ教訓ですが、Elixir では **件数に関わらず最初から挿入順ではありません**。むしろ気付きやすいとも言えます。それでも、列が 4 列のうちは「たまたま合っている」ことがあるので、順が要るところは最初から明示しました。

### 線形回帰と決定係数

決定係数を測るために、この章に正規方程式を解く最小の線形回帰を置きました。Nx の `Nx.LinAlg.solve/2` で `XᵀX β = Xᵀt` を解きます。

```elixir
  def linear_fit(rows, t) do
    x = Nx.tensor(Enum.map(rows, &[1.0 | &1]), type: :f64)
    transposed = Nx.transpose(x)

    beta =
      transposed
      |> Nx.dot(x)
      |> Nx.LinAlg.solve(Nx.dot(transposed, Nx.tensor(t, type: :f64)))
      |> Nx.to_flat_list()

    unless Enum.all?(beta, &is_float/1) do
      raise ArgumentError, "特徴量の列が互いに独立でないため、正規方程式を解けません"
    end

    [intercept | weights] = beta
    %{intercept: intercept, weights: weights}
  end
```

`[1.0 | &1]` で先頭に 1 を足せるので、Java 版の `System.arraycopy` は要りません。Clojure 版は Tribuo の `DenseMatrix` と `java.util.Optional` を橋渡ししましたが、Nx はテンソルを値として扱うので、変換は `Nx.tensor/2` と `Nx.to_flat_list/1` の 2 か所だけです。

**`Nx.LinAlg.solve/2` は列が独立でなくても例外を投げません。** Tribuo のコレスキー分解が `Optional.empty()` を返したのに対し、Nx は LU 分解を進めて `NaN` や `Infinity` を含む答えを返します。そこで `Nx.to_flat_list/1` の結果を検査しています。Nx は `NaN` を `:nan`、無限大を `:infinity`・`:neg_infinity` という **アトム** にして返すので、`is_float/1` で弾けば「まともな数値か」を 1 行で確かめられます。浮動小数点の特別な値をアトムで表すのは Elixir らしい設計で、`Float.nan?` のような専用の述語を覚えなくても、普段のパターンマッチと同じ道具で扱えます。

```elixir
    test "同じ値の列が二つあれば失敗する" do
      assert_raise ArgumentError, fn ->
        C.linear_fit([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]], [3.0, 5.0, 7.0])
      end
    end
```

特徴量の組ごとの評価は `score_feature_set/3` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `standardizer/2` を求め、両方を `standardize_all/2` してから学習します。

```elixir
  def score_feature_set(split, columns, terms) do
    train = split.x_train |> expand(columns) |> select_columns(terms)
    test = split.x_test |> expand(columns) |> select_columns(terms)
    std = standardizer(train, terms)
    x_train = std |> standardize_all(train) |> to_rows(terms)
    x_test = std |> standardize_all(test) |> to_rows(terms)
    model = linear_fit(x_train, split.t_train)

    %{
      train: r_squared(split.t_train, linear_predict(model, x_train)),
      test: r_squared(split.t_test, linear_predict(model, x_test))
    }
  end
```

`to_rows/2` は特徴量のマップを `terms` の順のリストに直す関数です。ここで **順を決め直している** ので、`Map.take/2` がマップの順を保たなくても結果は変わりません。決定係数は `%{train: ..., test: ...}` というマップで返します。Java 版・Scala 版は `Scores` という型を作りましたが、Elixir では鍵の名前が同じ役目を果たします。タプル（`{0.77, 0.86}`）にしないのは同じ理由です。**読む人には名前が要ります。**

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

```elixir
    defp squared_split do
      rms = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0]

      %{
        columns: [:RM],
        x_train: Enum.map(rms, &%{RM: &1}),
        t_train: Enum.map(rms, &price/1),
        x_test: Enum.map([1.5, 2.5], &%{RM: &1}),
        t_test: Enum.map([1.5, 2.5], &price/1)
      }
    end

    test "元の列だけでは当てきれない" do
      assert C.score_feature_set(squared_split(), [:RM], [:RM]).train < 0.99
    end

    test "二乗の項を加えると訓練データもテストデータも当てきる" do
      scores = C.score_feature_set(squared_split(), [:RM], [:RM, :"RM^2"])

      assert_in_delta scores.train, 1.0, @delta
      assert_in_delta scores.test, 1.0, @delta
    end
```

分割を表すマップをテストの中で手で書けるのは、型を宣言していないからです。Java 版・Scala 版は `TrainTestSplit` のコンストラクターを呼びました。**手軽な代わりに、鍵の綴りを間違えても実行するまで分かりません。** `t_trian:` と書いたテストはコンパイルでき、`score_feature_set/3` の中で `split.t_train` を引いたときに初めて `KeyError` になります。ただし `split.t_train` のようなドット記法は、キーが無ければ必ず `KeyError` を投げます（`split[:t_train]` なら `nil` が返って、もっと遠くで壊れます）。**この章では分割やモデルの取り出しをすべてドット記法か `Map.fetch!/2` で書き、綴りの間違いをその場で落とすようにしました。**

### 実データで測る

```bash
ML_DATA_DIR=<学習データの置き場> mix run -e 'GettingStartedMl.Chapter09.run()'
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

「標準化した訓練データの RM」は、標準化した訓練データにもう一度 `standardizer/2` を当て、その平均と標準偏差を表示しています。

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、ほかの版と同じ値になりました（分割に関係しないため）

**決定係数の 8 つの数値は、Java 版・Scala 版・Clojure 版の記事とすべて一致しました。** 第 2 章で書いたとおり、Elixir 版の `split_train_test/4` は `java.util.Random` と同じ線形合同法（自作）と Fisher-Yates のシャッフルで Java 版（`Collections.shuffle`）と同じ並びを作ります。同じ行が訓練データとテストデータに入るので、そのあとの標準化・多項式特徴量・正規方程式まで含めて同じ値になります。Kotlin 版は `kotlin.random.Random(0)` を使うので分け方が違い、2 乗の項でテスト 0.6457 → 0.7975 と別の値になりました。

数値が一致したことには、もう 1 つ意味があります。行列の分解は、Java 版・Clojure 版が Tribuo の **コレスキー分解**、Elixir 版が Nx の **LU 分解** と、アルゴリズムそのものが違います。それでも表示する 4 桁では差が出ませんでした。逆に言えば、一致しなかったときに「乱数か、手順か、分解の方法か、丸めか」を切り分けられるのは、ほかの版が照合先としてあるからです。

この出力は `chapter09_test.exs` の `@tag :data` を付けたテストで固定しています。

```elixir
    @tag :data
    test "実行するとほかの言語版と同じ決定係数を表示する" do
      assert ExUnit.CaptureIO.capture_io(&C.run/0) == """
             訓練データ: 70 件, テストデータ: 30 件
```

学習データが無い環境では、`test/test_helper.exs` がタグを除外します。

```elixir
# 実データ（書籍の購入者だけが使える）が無い環境では、:data のテストを外す。
exclude = if File.dir?(GettingStartedMl.Dataset.dir()), do: [], else: [:data]
```

Clojure 版は「`clojure.test` にはテストを飛ばす仕組みが無い」ので標準エラーに理由を出す形にしましたが、ExUnit にはタグによる除外があり、**何件を外したかが結果の行に出ます**（`101 tests, 0 failures, 7 excluded`）。スキップが結果に残るぶん、Scala 版の `assume` や JUnit の `assumeTrue` に近い扱いです。

## 9.10 Livebook による探索と可視化

Elixir 版では Livebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、`mix format --check-formatted`・`mix compile --warnings-as-errors`・`mix credo --strict`・`mix test --cover` をかけました（第 5 章で整えた検査です）。Credo が指摘したのは 3 種類でした。

- **`Scholar.Preprocessing.StandardScaler` を毎回フルネームで書いていた** — `alias Scholar.Preprocessing.StandardScaler` をモジュールの先頭に置いて `StandardScaler.fit/1` と書くようにしました。Credo の「Nested modules could be aliased at the top of the invoking module」は、名前空間の深いライブラリを使うときによく出ます
- **ドキュメント文字列の中に引用符が 4 つあった** — `:"RM^2"` のようなアトムを説明しようとすると、`"` をエスケープする羽目になります。`@doc ~S(...)` というシギルに変えて、エスケープを消しました
- **関数の入れ子が深すぎた** — 勾配の計算（第 10 章）で `Enum.zip_with` を 3 重にしていたところを、名前の付いた小さな関数に切り出しました

`mix test --cover` の総計は 90.6% でした。`mix.exs` の `test_coverage: [ignore_modules: [...]]` に、`NimbleCSV.define/2` が生成するパーサーのモジュールを並べています。この章でタブ区切りのパーサーを足したので、`GettingStartedMl.Csv.TabParser` も加えました。**自分が書いていないコードをカバレッジの分母に入れない** という判断です。

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、次の形で公開しています。

| API | 内容 |
|-----|------|
| `standardizer(x, columns)` | 指定した列の平均と標準偏差（件数で割る）を `%{columns: ..., means: ..., stds: ...}` で返す |
| `standardize(std, features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardize_all(std, x)` | 並びを標準化する |
| `scholar_standardize(train, values)` | Scholar の `StandardScaler` で比べる |

Scholar の `StandardScaler` も自作も同じ定義（n で割る）なので、第 14 章ではどちらを使っても結果が変わりません。JVM 系の版が「Tribuo ではなく自作を使うこと」を明記しなければならなかったのとは、事情が違います。

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Elixir の TDD で自作し、標準化を Scholar と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `categories/1`・`encode/3` | — | 訓練データとテストデータで列をそろえる、`String.to_atom/1` のアトム表 |
| 標準化 | `standardizer/2`・`standardize/2` | Scholar の `StandardScaler`（一致） | 分散 0 の列、テストデータの平均を使わない、Nx の既定が f32 |
| 多項式特徴量 | `expand/2`・`term_name/1` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile/2`・`iqr_outliers/2` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `load_delimited/3`・`join_weather/2` | — | 区切り文字がコンパイル時に決まる、Shift_JIS が標準で読めない、内部結合で消える行、`Map.new/2` の静かな上書き |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。これらの値は Java 版・Scala 版・Clojure 版と完全に一致しました。

Elixir 版ならではの学びもありました。

1. **Shift_JIS は標準では読めない** — Erlang/Elixir の文字列は UTF-8 のバイナリという前提で作られていて、文字コードを渡す口がどこにもない。codepagex を入れ、`config/config.exs` にどの符号化表を組み込むかを書く必要がある。シリーズで初めて「文字コードのためにライブラリを増やした」章になった
2. **文字コードの取り違えは片方向だけ静かに通る** — Shift_JIS を UTF-8 として読むと、例外も置換文字も出ず、`String.valid?/1` が `false` になるバイナリがそのまま流れる。逆向き（UTF-8 を CP932 として読む）は codepagex がエラーを返す。Java 版（例外）・Clojure 版（置換文字）とも違う 3 つめの振る舞い
3. **Scholar の標準偏差は n で割る** — Tribuo（n−1）と違い、scikit-learn と同じ定義。自作と一致することと、不偏標準偏差ではないことを両方テストに残した
4. **パターンマッチが分岐を関数の入口へ押し出す** — `term_name({left, left})` が「同じ列どうし」を、`standardizer([], _columns)` が「1 件も無い」を、`decode(binary, :utf8)` が「変換しない」を、それぞれ本体の `if` ではなく引数の形で表す
5. **マクロで固まるものはコンパイル時に決まる** — NimbleCSV の区切り文字も codepagex の符号化表も、実行時の引数にはできない。「使うぶんだけ先に定義して、実行時はどれを呼ぶかだけ選ぶ」形に落ち着いた
6. **`%{split | key: ...}` は存在しないキーを弾く** — 型が無い言語で、綴りの間違いを構文で捕まえられる数少ない場所。`Map.put/3` なら静かに通ってしまう

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
