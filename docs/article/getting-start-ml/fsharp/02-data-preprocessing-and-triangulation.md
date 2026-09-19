---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "欠損値を含む iris データを FSharp.Data の型プロバイダ（CsvProvider）で読み込み、空欄を option で表して訓練データの平均値で補完する。System.Random のシードと Fisher–Yates のシャッフルで、ジェネリックな分割を TDD で実装し、Polyglot Notebooks で探索する。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T04:51:25Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md)・[TypeScript 版の第 2 章](../typescript/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。F# 版では次の 3 点に注目してください。

- **型プロバイダ** — FSharp.Data の `CsvProvider` が、サンプルの CSV から列の名前と型を持つ型をコンパイル時に作る
- **`option` による欠損値** — 空欄を `None` で表し、補完の前（`float option`）と後（`float`）を型で区別する
- **不変なリストと純粋な関数** — シャッフルも分割も、元のリストを変えずに新しいリストを返す

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | F# 版での型 |
|----|------|------------|
| がく片長さ | がく片の長さ | `float option` |
| がく片幅 | がく片の幅 | `float option` |
| 花弁長さ | 花弁の長さ | `float option` |
| 花弁幅 | 花弁の幅 | `float option` |
| 種類 | 品種（3 種類が 50 件ずつ） | `string` |

特徴量の 4 列には合わせて 7 件の欠損値があります。

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### 他の言語の版と数値が変わる理由

分け方の手順（シード付きでシャッフルし、テストデータの件数を切り上げる）は他の言語の版と同じですが、乱数生成器が違うので、どの行がテストデータに入るかは一致しません。件数は同じ 105 件と 45 件ですが、補完に使う平均値や、第 3 章以降の正解率などの数値は変わります。

## 2.3 開発環境の準備

CSV の読み込みに [FSharp.Data](https://fsprojects.github.io/FSharp.Data/) 8.2.0 を使います。第 1 章と同じく、版は `Directory.Packages.props` にまとめて書きます（中央パッケージ管理。第 5 章で扱います）。

```xml
<!-- Directory.Packages.props -->
    <PackageVersion Include="FSharp.Data" Version="8.2.0" />
```

```xml
<!-- src/MachineLearning/MachineLearning.fsproj -->
  <ItemGroup>
    <PackageReference Include="FSharp.Data" />
  </ItemGroup>
```

プロジェクトファイルの `PackageReference` には版を書きません。`dotnet restore` を実行すると、依存関係の依存関係まで含めた正確な版が `packages.lock.json` に記録されます。

```bash
dotnet restore
```

第 1 章では CSV を自分で分割しましたが、値に引用符や改行が含まれる CSV まで正しく読むのは簡単ではありません。この章からはライブラリに任せます。選定の理由は [ADR 004](../../../adr/004-fsharp-ml-libraries.md) を参照してください。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] 空欄を欠損値（`None`）として読み込む
- [ ] 列ごとの欠損値の数を数える
- [ ] 列ごとの平均値を求める
- [ ] 欠損値を補完する
- [ ] シード付きで並べ替える
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合（切り上げ）どおりの件数に分ける
  - [ ] すべての行を重複なくどちらかに入れる
  - [ ] 特徴量と正解ラベルの対応を保つ
  - [ ] 同じシードなら同じ分け方になる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

他の言語の版にある「特徴量と正解ラベルに分ける」は、F# 版では読み込みの型（2.5 節）に含めます。

## 2.5 型プロバイダで CSV を読み込む

### 型プロバイダとは

第 1 章では、列名から列の位置を探し、文字列を `float` や `int` に変換するコードを自分で書きました。**型プロバイダ** は、この「データの形に合わせた型」をコンパイラが作る仕組みです。

```fsharp
type IrisCsv = CsvProvider<"サンプルの CSV">
```

`CsvProvider` はサンプルの CSV を **コンパイル時に** 読み、1 行目の列名をプロパティ名に、値から推論した型をプロパティの型にした `IrisCsv` を作ります。列名を書き間違えると、実行するまでもなくコンパイルエラーになります。

型の元にするサンプルは、実際の `iris.csv` ではなく、同じ列を持つ **架空の値** で書きます。学習データはライセンスの都合でリポジトリにコミットできないので、CI にもありません。実データを型の元にすると、CI でコンパイルそのものができなくなります。

```fsharp
[<Literal>]
let IrisSample = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n0.1,0.2,,0.4,Iris-sample"
```

`[<Literal>]` は、値をコンパイル時の定数にする属性です。型プロバイダの引数には定数しか渡せません。

### 学習用テストで型の推論を確かめる

`CsvProvider` が空欄をどの型で表すかは、使う前には分かりません。ライブラリの振る舞いを確かめるテスト（学習用テスト）を書きます。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```fsharp
// tests/MachineLearning.Tests/Chapter02/IrisPreprocessingTest.fs
/// 学習用テスト: Schema を指定しないときに型プロバイダが推論する型
type NoSchemaCsv = CsvProvider<IrisSample>

[<Fact>]
let ``学習用テスト: Schema を指定しないと、空欄の無い列は decimal、空欄のある列は string と推論される`` () =
    let row = NoSchemaCsv.GetSample().Rows |> Seq.head

    Assert.IsType<decimal>(box row.がく片長さ) |> ignore
    Assert.IsType<string>(box row.花弁長さ) |> ignore
    Assert.Equal("", row.花弁長さ)
```

- `row.がく片長さ` は、型プロバイダが作ったプロパティです。日本語の列名がそのままプロパティ名になります
- `GetSample()` は、型の元にしたサンプルそのものを読み込みます

このテストは通ります。推論の結果は、どちらも欠損値の扱いに向いていません。

- 小数を含む列は `float` ではなく `decimal`（10 進数の小数）と推論された
- サンプルで空欄にした `花弁長さ` の列は、数値ではなく `string` と推論され、空欄は `""` になった

`decimal` のままでは、平均値などの計算のたびに `float` への変換が要ります。また、欠損値が `""` の文字列では、値が欠けていることを型で表せません。そこで、推論に任せず、`Schema` で列の型を指定します。

### Red: 空欄を None として読み込む

読み込んだ行は、特徴量を **列名から値への `Map`** にし、正解ラベルと組にしたレコード `IrisRow` で表します。

```fsharp
[<Fact>]
let ``BOM 付き CSV を読み込み、空欄を None にする`` () =
    let csvFile = writeCsv "0.2,0.6,,0.1,Iris-setosa\n"

    let rows = loadIris csvFile

    Assert.Equal<IrisRow list>(
        [
            {
                Features = Map.ofList [ "がく片長さ", Some 0.2; "がく片幅", Some 0.6; "花弁長さ", None; "花弁幅", Some 0.1 ]
                Species = "Iris-setosa"
            }
        ],
        rows
    )
```

`writeCsv` は第 1 章と同じく、BOM 付きの UTF-8 で一時ファイルを書くテスト用の関数です。

```fsharp
/// BOM 付きの UTF-8 で、ヘッダーと rows を書いた一時ファイルのパスを返す
let writeCsv (rows: string) : string =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("iris-").FullName, "iris.csv")

    File.WriteAllText(csvFile, "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n" + rows, UTF8Encoding(true))
    csvFile
```

テストのファイルを `MachineLearning.Tests.fsproj` の `Compile` に加えて実行すると、コンパイルエラーになります。

```text
error FS0039: 名前空間 'Chapter02' が定義されていません。
error FS0039: 値またはコンストラクター 'loadIris' が定義されていません。
```

### Green: Schema で列の型を指定する

```fsharp
// src/MachineLearning/Chapter02/IrisPreprocessing.fs
module MachineLearning.Chapter02.IrisPreprocessing

open FSharp.Data

/// 型プロバイダが列の名前と型を知るためのサンプル。
/// 学習データはコミットできないので、同じ列を持つ架空の値で書く。
[<Literal>]
let IrisSample = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n0.1,0.2,,0.4,Iris-sample"

/// 空欄がありうる数値の列は float option として読む
type IrisCsv = CsvProvider<IrisSample, Schema="float option,float option,float option,float option,string">

let FeatureNames = [ "がく片長さ"; "がく片幅"; "花弁長さ"; "花弁幅" ]

[<Literal>]
let Target = "種類"

/// 特徴量（列名から値への Map）と正解ラベルの組
type IrisRow =
    {
        Features: Map<string, float option>
        Species: string
    }

let loadIris (csvFile: string) : IrisRow list =
    IrisCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            Features = Map.ofList [ "がく片長さ", row.がく片長さ; "がく片幅", row.がく片幅; "花弁長さ", row.花弁長さ; "花弁幅", row.花弁幅 ]
            Species = row.種類
        })
    |> Seq.toList
```

- `Schema` は、列の型を左から順にカンマ区切りで指定します。数値の 4 列を `float option` にすると、空欄は `None`、値があれば `Some 0.2` のように読み込まれます
- `IrisCsv.Load(csvFile)` は、型の元にしたサンプルとは別の、実行時に渡したファイルを読みます。型はサンプルから、値はファイルから来ます。BOM は読み込みの際に取り除かれます
- `row.がく片長さ` の型は `float option` です。列名を `row.がく片長` と書き間違えると、コンパイルエラーになります
- 実装のファイルも `MachineLearning.fsproj` の `Compile` に加えます。第 1 章で見たとおり、F# ではファイルの順番に意味があります。`IrisPreprocessing.fs` は、この後で作る `Random.fs` の後ろに置きます

```text
テストの実行の概要: 成功!
```

### なぜ Map にするのか

型プロバイダの行（`IrisCsv.Row`）は、列ごとに型の付いたプロパティを持っています。それでも `Map<string, float option>` に移し替えるのは、この後の関数を **iris 以外のデータにも使える** ようにするためです。

- 欠損値の数・平均値・補完は、どの列にも同じ処理をします。列名をキーにした `Map` なら、列の数や名前によらない 1 つの関数で書けます
- 第 3 章の決定木は、「どの列のどの値で分けるか」を探します。列を名前で選べる形のほうが扱いやすくなります

型プロバイダの型の安全さは、CSV の形を確かめる入り口（`loadIris`）で使い、その先は汎用の形で扱う、という分担です。

## 2.6 平均値で欠損値を補完する

### 列ごとの欠損値の数と平均値

テストでは、列名を短い `a`・`b` にしたデータを使います。関数が iris の列名に依存していないことを、テストの側からも示せます。

```fsharp
[<Fact>]
let ``列ごとに欠損値を数える`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", None ]
            Map.ofList [ "a", Some 3.0; "b", Some 2.0 ]
        ]

    Assert.Equal<Map<string, int>>(Map.ofList [ "a", 1; "b", 2 ], countMissing rows)

[<Fact>]
let ``列ごとに欠損値を除いた平均を求める`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", Some 4.0 ]
            Map.ofList [ "a", Some 3.0; "b", Some 2.0 ]
        ]

    Assert.Equal<Map<string, float>>(Map.ofList [ "a", 2.0; "b", 3.0 ], columnMeans rows)
```

```text
error FS0039: 値またはコンストラクター 'countMissing' が定義されていません。
error FS0039: 値またはコンストラクター 'columnMeans' が定義されていません。
```

どちらも「列ごとに値を集めて、集計する」という同じ形をしています。この形を `byColumn` という関数にし、集計の部分だけを引数で渡します。

```fsharp
/// 行の列名の一覧（1 行目の列名を使う）
let private columnsOf (rows: Map<string, 'V> list) : string list =
    match rows with
    | [] -> []
    | first :: _ -> first |> Map.keys |> Seq.toList

/// 列ごとに値を集計して、列名から集計結果への Map を返す
let private byColumn (rows: Map<string, 'V> list) (aggregate: 'V list -> 'R) : Map<string, 'R> =
    columnsOf rows
    |> List.map (fun column -> column, rows |> List.map (fun row -> row[column]) |> aggregate)
    |> Map.ofList

let countMissing (rows: Map<string, 'V option> list) : Map<string, int> =
    byColumn rows (List.filter Option.isNone >> List.length)

let columnMeans (rows: Map<string, float option> list) : Map<string, float> =
    byColumn rows (List.choose id >> List.average)
```

- `'V` や `'R` は **型パラメーター**（ジェネリック）です。`countMissing` は `'V option` を受け取るので、`float option` でも `string option` でも欠損値を数えられます
- `List.filter Option.isNone >> List.length` の `>>` は **関数の合成** です。「`None` だけを残す」関数と「数える」関数をつないで、「`None` の数を数える」関数を 1 つ作っています。第 8 章の前処理パイプラインでも使います
- `List.choose id` は、`Some` の中身だけを取り出し、`None` を捨てます。`float option list` が `float list` になるので、続く `List.average` で平均値を求められます。TypeScript 版の「`filter` で `null` を取り除くと型も絞り込まれる」に当たる処理が、F# では `option` を外す関数そのものです
- `columnsOf` の `match` は、空のリストと「先頭の要素と残り」の 2 通りに分けています。`rows |> List.head` と書くと、空のリストで例外になります

### 補完する

補完に使う値は引数で受け取ります。

```fsharp
[<Fact>]
let ``欠損値を列ごとの値で補完する`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", Some 4.0 ]
        ]

    let filled = fillMissing (Map.ofList [ "a", 9.0; "b", 8.0 ]) rows

    Assert.Equal<Map<string, float> list>([ features [ "a", 1.0; "b", 8.0 ]; features [ "a", 9.0; "b", 4.0 ] ], filled)
```

`features` は、`(string * float) list` から `Map<string, float>` を作るテスト用の関数です。

```fsharp
let fillMissing (values: Map<string, float>) (rows: Map<string, float option> list) : Map<string, float> list =
    rows
    |> List.map (Map.map (fun column value -> value |> Option.defaultValue values[column]))
```

- 戻り値の型は `Map<string, float> list` です。引数の `float option` が、補完の後は `float` になることを型で表しています。第 3 章の決定木は `float` の特徴量を受け取るので、補完していないデータを渡すとコンパイルエラーになります
- `Option.defaultValue` は、`Some` なら中身を、`None` なら指定した値を返します
- 引数の順番は「補完に使う値」「補完する行」です。F# では、変わりにくい引数を先に、処理の対象を最後に置くと、`rows |> fillMissing means` のようにパイプラインでつなげます
- F# の `Map` とリストは不変です。`Map.map` も `List.map` も新しい値を作るので、TypeScript 版にあった「元の配列は変更しない」のテストは要りません。変更できないことを型が保証しています

```text
テストの実行の概要: 成功!
```

## 2.7 訓練データとテストデータに分ける

### シード付きのシャッフル

データを分ける前に、行の順番をシード付きで並べ替えます。.NET の `System.Random` は、コンストラクターにシードを渡すと、同じシードから同じ乱数列を返します。TypeScript 版のように乱数生成器を自分で作る必要はありません。

```fsharp
// tests/MachineLearning.Tests/Chapter02/RandomTest.fs
module MachineLearning.Tests.Chapter02.RandomTest

open Xunit
open MachineLearning.Chapter02.Random

let items = [ 0..9 ]

[<Fact>]
let ``要素を失わずに並べ替えたリストを返す`` () =
    let shuffled = shuffle 0 items

    Assert.Equal<int list>(items, List.sort shuffled)
    Assert.NotEqual<int list>(items, shuffled)

[<Fact>]
let ``同じシードなら同じ順に並べ替える`` () =
    Assert.Equal<int list>(shuffle 42 items, shuffle 42 items)

[<Fact>]
let ``シードが違えば違う順に並べ替える`` () =
    Assert.NotEqual<int list>(shuffle 0 items, shuffle 1 items)

[<Fact>]
let ``空のリストと 1 要素のリストはそのまま返す`` () =
    Assert.Equal<int list>([], shuffle 0 [])
    Assert.Equal<int list>([ 7 ], shuffle 0 [ 7 ])
```

`shuffle` は、`Random` のオブジェクトではなく **シードの整数** を受け取る関数にします。同じ引数なら必ず同じ結果を返す **純粋な関数** になり、テストでも使う側でも扱いやすくなります。

```text
error FS0039: 名前空間 'Chapter02' が定義されていません。
error FS0039: 値またはコンストラクター 'shuffle' が定義されていません。
```

実装は **Fisher–Yates のシャッフル** です。末尾から順に「それより前のどこか」と入れ替えていく方法で、すべての並べ方が同じ確率で出ます。

```fsharp
// src/MachineLearning/Chapter02/Random.fs
module MachineLearning.Chapter02.Random

open System

/// シードを使って Fisher–Yates のシャッフルで並べ替えたリストを返す。
/// 同じシードなら同じ順になる。元のリストは変更されない（F# のリストは不変）。
let shuffle (seed: int) (items: 'T list) : 'T list =
    let random = Random seed
    let array = List.toArray items

    for i in array.Length - 1 .. -1 .. 1 do
        let j = random.Next(i + 1)
        let tmp = array[i]
        array[i] <- array[j]
        array[j] <- tmp

    List.ofArray array
```

- 入れ替えは、リストをいったん配列（可変）に写してから行います。F# のリストは不変なので、要素を入れ替えることができません
- 可変な配列は関数の中だけで使い、外にはリストを返します。関数の外から見れば、同じ引数に同じ結果を返す純粋な関数のままです
- `random.Next(i + 1)` は、0 以上 `i + 1` 未満の整数を返します
- `for i in array.Length - 1 .. -1 .. 1` は、`array.Length - 1` から 1 まで 1 ずつ減らすループです

`Random.fs` を `MachineLearning.fsproj` の `Compile` に加えて実行すると、`shuffle` が無いという誤りは消えましたが、最後のテストの `shuffle 0 []` の行にだけ、別のコンパイルエラーが残りました。

```text
error FS0041: このプログラム ポイントよりも前の型情報に基づいて、メソッド 'Equal' の固有のオーバーロードを決定することができませんでした。型の注釈が必要な場合があります。既知の型の引数: 'a list * 'b list
```

空のリスト `[]` には、要素の型の手がかりがありません。`shuffle` はどんな要素の型にも使えるジェネリックな関数なので、`shuffle 0 []` の戻り値の型も決まらず、xUnit の `Assert.Equal` のどのオーバーロードを使うかを選べないのです。空のリストに型を注釈して、要素の型を伝えます。

```fsharp
    Assert.Equal<int list>([], shuffle 0 ([]: int list))
```

```text
テストの実行の概要: 成功!
  合計: 20
  失敗: 0
  成功: 20
```

シード 0 で `[0..9]` を並べ替えると、次の順になりました。

```text
[0; 4; 5; 8; 2; 1; 3; 6; 9; 7]
```

.NET 6 以降の `Random` は、シードを渡さないと新しいアルゴリズム（xoshiro256**）を使いますが、シードを渡すと以前の .NET と同じ乱数列を返すアルゴリズムを使います。シード付きの乱数列が .NET の版によって変わるかどうかは、第 4 章で扱います。

### 分割の件数を三角測量する

分割は、特徴量の型 `'X` と正解ラベルの型 `'T` を型パラメーターに持つジェネリックな関数にします。第 7 章からは、正解ラベルが文字列ではなく数値（価格や興行収入）になるためです。

```fsharp
[<Fact>]
let ``テストデータの割合を切り上げた件数で訓練データとテストデータに分ける`` () =
    let x = [ 0..9 ]
    let t = x |> List.map (fun i -> $"label{i}")

    let split = splitTrainTest 0.3 0 x t

    Assert.Equal(7, split.XTrain.Length)
    Assert.Equal(3, split.XTest.Length)
```

このテストだけなら、「先頭の 7 件を訓練データにする」という仮実装でも通ります。件数と割合の違うテストを加えて、件数の計算を一般化させます（三角測量）。

```fsharp
[<Fact>]
let ``件数と割合が変わっても割合どおりの件数に分ける`` () =
    let x = [ 0..19 ]

    let split = splitTrainTest 0.25 0 x x

    Assert.Equal(15, split.XTrain.Length)
    Assert.Equal(5, split.XTest.Length)
```

テストの件数は、どちらも「テストデータの件数を切り上げる」規則で決まります。10 件の 3 割は 3 件、20 件の 2 割 5 分は 5 件です。

最初は、テストの割合に 0.25 と 10 件の組み合わせを使っていました。ところが 10 件の 2 割 5 分は 2.5 件で、切り上げると 3 件、つまり 0.3 のときと同じ 7 件と 3 件になります。これでは「先頭の 7 件」の仮実装も通ってしまい、三角測量になりません。件数も変えて、別の答えになる例にしました。

### 分け方の性質をテストする

件数だけでなく、分け方の性質もテストします。

```fsharp
[<Fact>]
let ``すべての行を重複なく訓練データとテストデータのどちらかに入れる`` () =
    let x = [ 0..9 ]

    let split = splitTrainTest 0.3 0 x x

    Assert.Equal<int list>(x, List.sort (split.XTrain @ split.XTest))

[<Fact>]
let ``分けた後も特徴量と正解ラベルの組み合わせを保つ`` () =
    let x = [ 0..9 ]
    let t = x |> List.map (fun i -> $"label{i}")

    let split = splitTrainTest 0.3 0 x t

    Assert.Equal<string list>(split.XTrain |> List.map (fun i -> $"label{i}"), split.TTrain)
    Assert.Equal<string list>(split.XTest |> List.map (fun i -> $"label{i}"), split.TTest)

[<Fact>]
let ``同じシードなら同じ分け方になる`` () =
    let x = [ 0..9 ]

    Assert.Equal(splitTrainTest 0.3 42 x x, splitTrainTest 0.3 42 x x)
```

- `@` は、2 つのリストをつなげる演算子です
- 最後のテストは、分割の結果をまるごと `Assert.Equal` で比べています。F# のレコードは、フィールドの値が等しければ等しいとみなされる（構造的等価性）ので、フィールドを 1 つずつ比べる必要はありません

### Green: 組にしてからシャッフルする

```fsharp
type TrainTestSplit<'X, 'T> =
    {
        XTrain: 'X list
        XTest: 'X list
        TTrain: 'T list
        TTest: 'T list
    }

/// シードで並べ替えた順に、テストデータの割合（切り上げ）を除いた先頭を訓練データにする
let splitTrainTest (testSize: float) (seed: int) (x: 'X list) (t: 'T list) : TrainTestSplit<'X, 'T> =
    let pairs = List.zip x t |> shuffle seed
    let nTrain = pairs.Length - int (ceil (float pairs.Length * testSize))
    let train, test = List.splitAt nTrain pairs

    {
        XTrain = train |> List.map fst
        XTest = test |> List.map fst
        TTrain = train |> List.map snd
        TTest = test |> List.map snd
    }
```

- TypeScript 版・Kotlin 版は、行の位置（0〜9）をシャッフルし、同じ位置の並びで特徴量と正解ラベルを取り出していました。F# 版では、`List.zip` で特徴量と正解ラベルを **組（タプル）** にしてからシャッフルします。組のまま動くので、対応は崩れようがありません
- `List.splitAt nTrain` は、先頭の `nTrain` 件と残りの 2 つのリストを返します。`let train, test = ...` で、組を 2 つの名前に分けて受け取ります
- `fst` と `snd` は、組の 1 番目と 2 番目を取り出す関数です
- `ceil` は `float` を受け取るので、件数を `float` に変換してから掛け、切り上げてから `int` に戻します。F# は `int` と `float` を自動では変換しません

```text
テストの実行の概要: 成功!
```

## 2.8 前処理をまとめる

読み込み、分割、補完を 1 つの関数にまとめます。補完に使う平均値を訓練データだけから求めていることを、テストで確かめます。

```fsharp
[<Fact>]
let ``訓練データの平均で訓練データとテストデータの欠損値を補完する`` () =
    // 1・2 行目のがく片長さを欠損にし、残りは互いに違う値にする
    let observed = [ 3..10 ] |> List.map (fun i -> float (i * i) + 0.5)

    let rows =
        [ ""; "" ] @ (observed |> List.map string)
        |> List.map (fun length -> $"{length},0.5,0.3,0.1,Iris-setosa")
        |> String.concat "\n"

    let split = prepareIris (writeCsv rows) 0.3 0

    let lengths (rows: Map<string, float> list) =
        rows |> List.map (fun row -> row["がく片長さ"])

    let isObserved value = List.contains value observed
    let filled = lengths (split.XTrain @ split.XTest) |> List.filter (isObserved >> not)
    let trainMean = lengths split.XTrain |> List.filter isObserved |> List.average

    Assert.Equal(2, filled.Length)
    Assert.All(filled, (fun value -> Assert.Equal(trainMean, value, 12)))
```

- 10 行のうち 2 行の `がく片長さ` を空欄にし、残りの 8 行には互いに違う値（9.5、16.5、…）を入れます
- 分割の後、元に無かった値（`filled`）が補完された値です。どれも「訓練データに入った、元からある値」の平均（`trainMean`）と等しいことを確かめます。テストデータの値まで含めた平均で補完していれば、このテストは失敗します
- `Assert.Equal(trainMean, value, 12)` の 3 つ目の引数は、小数第 12 位までで比べる指定です

```fsharp
/// 読み込み、訓練データとテストデータに分け、訓練データの平均で欠損値を補完する
let prepareIris (csvFile: string) (testSize: float) (seed: int) : TrainTestSplit<Map<string, float>, string> =
    let rows = loadIris csvFile
    let x = rows |> List.map (fun row -> row.Features)
    let t = rows |> List.map (fun row -> row.Species)
    let split = splitTrainTest testSize seed x t
    let means = columnMeans split.XTrain

    {
        XTrain = fillMissing means split.XTrain
        XTest = fillMissing means split.XTest
        TTrain = split.TTrain
        TTest = split.TTest
    }
```

戻り値の型 `TrainTestSplit<Map<string, float>, string>` は、「特徴量に欠損値が残っておらず、正解ラベルは文字列」の分割を表します。分割の直後（`split`）は `TrainTestSplit<Map<string, float option>, string>` で、補完の前と後で型が違うため、新しいレコードを作って返しています。

## 2.9 実データで前処理の結果を表示する

### 実データのテスト

第 1 章と同じく、学習データが無ければスキップするテストを書きます。表示の内容もテストで先に決めます。

```fsharp
// tests/MachineLearning.Tests/Chapter02/IrisDataTest.fs
module MachineLearning.Tests.Chapter02.IrisDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing

let csvFile = Path.Combine(dataDir (), "iris.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ iris.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``実データから 150 件を読み込む`` () =
    requireData ()

    Assert.Equal(150, (loadIris csvFile).Length)

[<Fact>]
let ``実行するとデータ件数・欠損値の数・分割の件数を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter02.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 150"
            "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0"
            "訓練データ: 105 件, テストデータ: 45 件"
            "補完後の欠損値の数: 訓練データ 0, テストデータ 0"
        ],
        lines
    )
```

```text
error FS0039: 値、コンストラクター、名前空間、または型 'Main' が定義されていません。
```

### 結果を表示する

```fsharp
// src/MachineLearning/Chapter02/Main.fs
module MachineLearning.Chapter02.Main

open System
open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing

[<Literal>]
let TestSize = 0.3

[<Literal>]
let Seed = 0

let private formatCounts (counts: (string * int) list) : string =
    counts
    |> List.map (fun (column, count) -> $"{column}={count}")
    |> String.concat ", "

/// 補完後の値は float なので None は入らない。数値として欠けている NaN の数を数える
let private totalMissing (rows: Map<string, float> list) : int =
    rows |> List.sumBy (Map.filter (fun _ value -> Double.IsNaN value) >> Map.count)

/// iris.csv を読み込み、欠損値の数と、分割・補完の結果を表示する
let run (print: string -> unit) : unit =
    let csvFile = Path.Combine(dataDir (), "iris.csv")
    let rows = loadIris csvFile
    let missing = rows |> List.map (fun row -> row.Features) |> countMissing

    let missingSpecies =
        rows |> List.filter (fun row -> row.Species = "") |> List.length

    let split = prepareIris csvFile TestSize Seed
    print $"データ件数: {rows.Length}"

    let counts =
        (FeatureNames |> List.map (fun column -> column, missing[column]))
        @ [ Target, missingSpecies ]

    print $"欠損値の数: {formatCounts counts}"
    print $"訓練データ: {split.XTrain.Length} 件, テストデータ: {split.XTest.Length} 件"
    print $"補完後の欠損値の数: 訓練データ {totalMissing split.XTrain}, テストデータ {totalMissing split.XTest}"
```

- `Map` はキーの順（文字列の順）に並ぶので、CSV の列の順ではありません。表示では `FeatureNames` の順に取り出しています
- 補完後の特徴量の型は `Map<string, float>` なので、`None` はもう入りえません。型が「欠損値が無い」ことを保証しています。それでも他の言語の版と同じ表示にそろえるため、`float` のまま欠けている値（`NaN`）の数を数えて表示します

コマンドラインから章を選んで実行できるように、`Program.fs` は章の名前から `run` への対応表にしました。

```fsharp
// src/MachineLearning/Program.fs
module MachineLearning.Program

/// コマンドライン引数の章の名前から、その章の run への対応
let chapters: Map<string, (string -> unit) -> unit> =
    Map.ofList [ "chapter01", Chapter01.Main.run; "chapter02", Chapter02.Main.run ]

[<EntryPoint>]
let main argv =
    match argv with
    | [| name |] when chapters.ContainsKey name ->
        chapters[name](printfn "%s")
        0
    | _ ->
        let names = chapters.Keys |> String.concat " | "
        eprintfn $"使い方: dotnet run --project src/MachineLearning -- ({names})"
        1
```

`| [| name |] when ...` の `when` は、パターンに条件を加える **ガード** です。引数が 1 つで、かつ対応表にある章の名前のときだけ実行します。

```bash
dotnet run --project src/MachineLearning -- chapter02
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
補完後の欠損値の数: 訓練データ 0, テストデータ 0
```

```bash
dotnet test
```

```text
テストの実行の概要: 成功!
  合計: 33
  失敗: 0
  成功: 33
```

第 2 章のテストは 17 件です。データが無い環境では、第 1 章と第 2 章の実データのテストがスキップされます。

```bash
ML_DATA_DIR=/nonexistent dotnet test
```

```text
テストの実行の概要: 成功!
  合計: 33
  成功: 28
  スキップ済み: 5
```

スキップされた 5 件は、第 1 章の 3 件と第 2 章の 2 件です。

### コードスタイルを整える

```bash
dotnet fantomas .
dotnet fsharplint lint MachineLearning.sln
```

```text
========== Summary: 0 warnings ==========
```

## 2.10 Notebook で探索する

### Polyglot Notebooks を用意する

F# 版の探索と可視化には、Polyglot Notebooks（.NET Interactive の F# カーネル）と [Plotly.NET](https://plotly.net/) を使います。

> Polyglot Notebooks と .NET Interactive は 2026 年に廃止され、機能追加もバグ修正もされなくなりました（[dotnet/interactive#4163](https://github.com/dotnet/interactive/issues/4163)）。この記事は `Microsoft.dotnet-interactive` 1.0.712001、Plotly.NET 5.1.0、Plotly.NET.Interactive 5.0.0 で動作を確かめています。将来の VS Code や .NET SDK の更新で動かなくなる可能性があります。Notebook は探索と可視化だけに使い、記事の数値はすべてプロジェクトのコードとテストから求めているので、Notebook が動かなくなっても本文の内容には影響しません。

Notebook は `apps/dotnet/notebooks/chapter02_iris_exploration.ipynb` にあります。VS Code の Polyglot Notebooks の拡張機能で開くか、Jupyter のカーネルとして登録して開きます。事前に `dotnet build` でプロジェクトをビルドしておきます。

```fsharp
#r "nuget: FSharp.Data, 8.2.0"
#r "nuget: Plotly.NET, 5.1.0"
#r "nuget: Plotly.NET.Interactive, 5.0.0"
#r "../src/MachineLearning/bin/Debug/net10.0/MachineLearning.dll"
```

- `#r "nuget: ..."` は、Notebook に NuGet のパッケージを読み込む命令です。プロジェクトと同じ版を指定します
- 最後の行で、ビルドしたプロジェクトの DLL を読み込みます。他の言語の版と同じく、Notebook ではテスト済みの関数を呼ぶだけにします
- `FSharp.Data` も読み込むのは、`loadIris` が型プロバイダの型を使っているためです

```fsharp
open System.IO
open Plotly.NET
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing

let irisCsv = Path.Combine(dataDir (), "iris.csv")
let rows = loadIris irisCsv
rows.Length
```

Kotlin 版では、Notebook の実行場所に合わせて学習データの既定の場所をずらしていました。F# 版の `dataDir` は、第 1 章で既定の場所を `__SOURCE_DIRECTORY__`（ソースファイルのある場所）から求めるようにしたので、Notebook から呼んでもそのまま `apps/data/sukkiri-ml` を指します。

### 欠損値の分布

```fsharp
let missing = rows |> List.map (fun row -> row.Features) |> countMissing

Chart.Column(values = (FeatureNames |> List.map (fun column -> missing[column])), Keys = FeatureNames)
|> Chart.withTitle "列ごとの欠損値の数"
|> Chart.withYAxisStyle "欠損値の数"
```

Plotly.NET のグラフも、`|>` でタイトルや軸の設定を順につなげて組み立てます。棒グラフで見ると、欠損値は特徴量の 4 列に 1〜2 件ずつ散らばっていて、特定の列に偏っていません。

### 品種ごとの特徴量

```fsharp
rows
|> List.countBy (fun row -> row.Species)
|> List.map (fun (species, count) -> {| 種類 = species; 件数 = count |})
|> List.toArray
```

品種はどれも 50 件ずつです。

- `{| 種類 = species; 件数 = count |}` は **匿名レコード** です。型を宣言せずにその場でレコードを作れます
- 匿名レコードの配列にしているのは、Notebook で表として表示させるためです。F# のリストをそのまま表示すると、.NET Interactive はリストの内部の構造（`Head` や `Tail`）まで展開してしまい、読みにくい表示になりました

```fsharp
let split = prepareIris irisCsv 0.3 0
let train = List.zip split.XTrain split.TTrain

train
|> List.groupBy snd
|> List.map (fun (species, group) ->
    Chart.Point(
        x = (group |> List.map (fun (features, _) -> features["花弁長さ"])),
        y = (group |> List.map (fun (features, _) -> features["花弁幅"])),
        Name = species
    ))
|> Chart.combine
|> Chart.withTitle "訓練データの花弁の長さと幅"
|> Chart.withXAxisStyle "花弁長さ"
|> Chart.withYAxisStyle "花弁幅"
```

品種ごとに散布図を 1 つずつ作り、`Chart.combine` で重ねて 1 つのグラフにしています。探索には訓練データだけを使います。

品種ごとの平均値（小数第 3 位で四捨五入）は次のとおりです。

| 種類 | がく片長さ | がく片幅 | 花弁長さ | 花弁幅 |
|------|-----------|---------|---------|-------|
| Iris-setosa | 0.217 | 0.622 | 0.242 | 0.062 |
| Iris-versicolor | 0.467 | 0.321 | 0.508 | 0.512 |
| Iris-virginica | 0.626 | 0.427 | 0.729 | 0.785 |

散布図と平均値から、`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さいことが分かります。訓練データに入った行が他の言語の版と違うので値は少し異なりますが、読み取れる傾向は同じです。

グラフの画像は記事に載せていません。配布データの点をそのまま描いたグラフは、データの再配布に当たるおそれがあるためです。

### 画面なしで Notebook を実行する

Notebook の動作確認は、VS Code を使わずに Jupyter からも行えます。`dotnet interactive jupyter install` で F# のカーネルを Jupyter に登録しておくと、`nbconvert` で Notebook を実行し、結果を別のファイルに書き出せます。

```bash
jupyter nbconvert --to notebook --execute chapter02_iris_exploration.ipynb --output /tmp/chapter02_out.ipynb
```

### 出力セルを消してからコミットする

Notebook の出力セルにはデータが残るので、コミットの前に消します。Python 版と同じ nbstripout で、Polyglot Notebooks の形式（`polyglot_notebook` のメタデータ）を保ったまま出力を消せます。

```bash
nbstripout apps/dotnet/notebooks/chapter02_iris_exploration.ipynb
```

出力の除去をタスクや CI に組み込む方法は、第 6 章で扱います。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter02/IrisPreprocessing.fs）</summary>

```fsharp
module MachineLearning.Chapter02.IrisPreprocessing

open FSharp.Data
open MachineLearning.Chapter02.Random

/// 型プロバイダが列の名前と型を知るためのサンプル。
/// 学習データはコミットできないので、同じ列を持つ架空の値で書く。
[<Literal>]
let IrisSample = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n0.1,0.2,,0.4,Iris-sample"

/// 空欄がありうる数値の列は float option として読む
type IrisCsv = CsvProvider<IrisSample, Schema="float option,float option,float option,float option,string">

let FeatureNames = [ "がく片長さ"; "がく片幅"; "花弁長さ"; "花弁幅" ]

[<Literal>]
let Target = "種類"

/// 特徴量（列名から値への Map）と正解ラベルの組
type IrisRow =
    {
        Features: Map<string, float option>
        Species: string
    }

let loadIris (csvFile: string) : IrisRow list =
    IrisCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            Features = Map.ofList [ "がく片長さ", row.がく片長さ; "がく片幅", row.がく片幅; "花弁長さ", row.花弁長さ; "花弁幅", row.花弁幅 ]
            Species = row.種類
        })
    |> Seq.toList

/// 行の列名の一覧（1 行目の列名を使う）
let private columnsOf (rows: Map<string, 'V> list) : string list =
    match rows with
    | [] -> []
    | first :: _ -> first |> Map.keys |> Seq.toList

/// 列ごとに値を集計して、列名から集計結果への Map を返す
let private byColumn (rows: Map<string, 'V> list) (aggregate: 'V list -> 'R) : Map<string, 'R> =
    columnsOf rows
    |> List.map (fun column -> column, rows |> List.map (fun row -> row[column]) |> aggregate)
    |> Map.ofList

let countMissing (rows: Map<string, 'V option> list) : Map<string, int> =
    byColumn rows (List.filter Option.isNone >> List.length)

let columnMeans (rows: Map<string, float option> list) : Map<string, float> =
    byColumn rows (List.choose id >> List.average)

let fillMissing (values: Map<string, float>) (rows: Map<string, float option> list) : Map<string, float> list =
    rows
    |> List.map (Map.map (fun column value -> value |> Option.defaultValue values[column]))

type TrainTestSplit<'X, 'T> =
    {
        XTrain: 'X list
        XTest: 'X list
        TTrain: 'T list
        TTest: 'T list
    }

/// シードで並べ替えた順に、テストデータの割合（切り上げ）を除いた先頭を訓練データにする
let splitTrainTest (testSize: float) (seed: int) (x: 'X list) (t: 'T list) : TrainTestSplit<'X, 'T> =
    let pairs = List.zip x t |> shuffle seed
    let nTrain = pairs.Length - int (ceil (float pairs.Length * testSize))
    let train, test = List.splitAt nTrain pairs

    {
        XTrain = train |> List.map fst
        XTest = test |> List.map fst
        TTrain = train |> List.map snd
        TTest = test |> List.map snd
    }

/// 読み込み、訓練データとテストデータに分け、訓練データの平均で欠損値を補完する
let prepareIris (csvFile: string) (testSize: float) (seed: int) : TrainTestSplit<Map<string, float>, string> =
    let rows = loadIris csvFile
    let x = rows |> List.map (fun row -> row.Features)
    let t = rows |> List.map (fun row -> row.Species)
    let split = splitTrainTest testSize seed x t
    let means = columnMeans split.XTrain

    {
        XTrain = fillMissing means split.XTrain
        XTest = fillMissing means split.XTest
        TTrain = split.TTrain
        TTest = split.TTest
    }
```

</details>

## 2.11 まとめ

この章では、欠損値を含むデータを型プロバイダで読み込み、訓練データとテストデータに分けました。

1. **型プロバイダ** — `CsvProvider` がサンプルの CSV から型を作る。学習データはコミットできないので、架空の値のサンプルを型の元にした
2. **学習用テスト** — 型の推論に任せると、小数の列は `decimal`、空欄のある列は `string` になることを見つけ、`Schema` で `float option` を指定した
3. **`option` で欠損値を表す** — 補完の前は `float option`、後は `float` とし、`List.choose` や `Option.defaultValue` で `option` を外した。補完の後に欠損値が残らないことを型が保証する
4. **不変なデータと純粋な関数** — シャッフルは関数の中だけで配列を使い、外には新しいリストを返した。特徴量と正解ラベルは組にしてから並べ替えた
5. **三角測量** — 件数と割合を変えたテストで件数の計算を一般化した。同じ答えになる例では三角測量にならないことにも気をつけた

次の章では、ジニ不純度を使う決定木を判別共用体で自作し、ML.NET の決定木と結果を突き合わせます。
