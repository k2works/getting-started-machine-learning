---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・四分位範囲による外れ値の除去を自作し、特徴量の組み合わせごとに線形回帰の決定係数を比べる。列の多い CSV は型を持たない CsvFile で読み、Shift_JIS の CSV は CodePagesEncodingProvider を登録して読み、Map で表を結合する。ML.NET の NormalizeMeanVariance の既定が平均を引かないことを学習用テストで確かめる。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T08:36:09Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムだけでなく、どんな特徴量を渡すかで大きく変わります。手元の列から、モデルが使いやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、次の 4 つの道具を作り、住宅価格の予測（線形回帰）の決定係数がどう変わるかを確かめます。

- **ダミー変数**: 文字のカテゴリ（`high`・`low` など）を 0 と 1 の列にする
- **標準化**: 列ごとに平均 0・標準偏差 1 にそろえる
- **多項式特徴量**: 2 乗の項や、列どうしの積（交互作用）の項を加える
- **外れ値の除去**: 四分位範囲から外れた価格の行を、訓練データから除く

さらに、2 つの表を共通の列で **結合** して、特徴量を増やします。

[Python 版の第 9 章](../python/09-feature-engineering.md)・[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md)・[TypeScript 版の第 9 章](../typescript/09-feature-engineering.md) と同じ題材で進めます。F# 版では、次の 3 点に注目してください。

- 列の多い CSV を、型プロバイダではなく **型を持たない `CsvFile`** で読む。どちらを使うかの判断
- Shift_JIS の CSV を読むには、.NET では **エンコーディングの登録** が要る
- 表の結合を **`Map`** で書く

## 9.2 題材とデータ

### Boston.csv

第 12 章でも使う、ボストンの地区ごとの住宅価格（`PRICE`）のデータです。`CRIME` だけが文字のカテゴリ（`high`・`low`・`very_low`）で、ほかの 12 列は数値です。いくつかの列に空欄（欠損値）があります。

### bike.tsv と weather.csv

`bike.tsv` は、自転車の貸し出しサービスの 1 日ごとの利用者数（`cnt`）と、天気の番号（`weather_id`）を記録したタブ区切りのファイルです。`weather.csv` は、天気の番号と名前（晴れ・曇り・雨）の対応表で、文字コードが **Shift_JIS** です。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
- [ ] 訓練データの平均と標準偏差で標準化する
  - [ ] 標準偏差が 0 の列は 0 にする
  - [ ] ML.NET の正規化と突き合わせる
- [ ] 2 乗の項と交互作用の項を作る
- [ ] 四分位範囲で外れ値を見つけ、訓練データから除く
- [ ] Shift_JIS の CSV を読み、表を結合する
- [ ] 特徴量の組み合わせごとに決定係数を比べる

## 9.4 カテゴリ値をダミー変数にする

線形回帰は数値しか扱えないので、`CRIME` の `high`・`low`・`very_low` を 0 と 1 の列にします。3 つのカテゴリに 3 列を作ると、「3 列の合計は常に 1」という余分な関係ができてしまうので、先頭のカテゴリの列は作りません（`CRIME_low` も `CRIME_very_low` も 0 なら `high`）。

```fsharp
// tests/MachineLearning.Tests/Chapter09/DummiesTest.fs
[<Fact>]
let ``先頭を除いたカテゴリを辞書順に返す`` () =
    Assert.Equal<string list>([ "low"; "very_low" ], dummyCategories [ "low"; "high"; "very_low"; "low" ])

[<Fact>]
let ``カテゴリに無い値はすべての列が 0 になる`` () =
    Assert.Equal<Map<string, float>>(
        Map.ofList [ "CRIME_low", 0.0; "CRIME_very_low", 0.0 ],
        encodeDummies "CRIME" [ "low"; "very_low" ] "unknown"
    )
```

```fsharp
// src/MachineLearning/Chapter09/Dummies.fs
/// ダミー変数にするカテゴリ。重複を除いて辞書順に並べ、先頭のカテゴリを除く
let dummyCategories (values: string list) : string list =
    values |> List.distinct |> List.sort |> List.tail

/// カテゴリの値を、「列名_カテゴリ」という名前の 0 と 1 の列にする。カテゴリに無い値はすべて 0 にする
let encodeDummies (column: string) (categories: string list) (value: string) : Map<string, float> =
    categories
    |> List.map (fun category -> $"{column}_{category}", (if value = category then 1.0 else 0.0))
    |> Map.ofList
```

カテゴリの一覧を引数で受け取るのは、訓練データで決めたカテゴリをテストデータにも同じように当てはめるためです。テストデータだけに現れた値は、すべての列が 0 になります。

## 9.5 特徴量を標準化する

### 訓練データから平均と標準偏差を求める

列ごとに桁が違うと、正則化（第 12 章）や距離（第 14 章）を使う手法で、桁の大きい列ばかりが効いてしまいます。**標準化** は、(値 − 平均) / 標準偏差 で、列ごとに平均 0・標準偏差 1 にそろえます。

平均と標準偏差は **訓練データだけ** から求め、テストデータにも同じ値で当てはめます（第 2 章のデータリークと同じ理由です）。求める処理（`fitStandardizer`）と当てはめる処理（`transformStandardized`）を分けます。

```fsharp
// tests/MachineLearning.Tests/Chapter09/StandardizerTest.fs
[<Fact>]
let ``訓練データの平均と標準偏差で、指定した列だけを標準化する`` () =
    let train = rows "RM" [ 1.0; 2.0; 3.0 ]
    let standardizer = fitStandardizer [ "RM" ] train

    let test =
        [ Map.ofList [ "RM", 4.0; "LSTAT", 7.0 ] ] |> transformStandardized standardizer

    Assert.Equal(2.0 / sqrt (2.0 / 3.0), test.Head["RM"], 12)
    Assert.Equal(7.0, test.Head["LSTAT"])

[<Fact>]
let ``標準偏差が 0 の列は 0 にする`` () =
    let train = rows "CHAS" [ 1.0; 1.0 ]

    let standardized = train |> transformStandardized (fitStandardizer [ "CHAS" ] train)

    Assert.Equal<float list>([ 0.0; 0.0 ], standardized |> List.map (fun row -> row["CHAS"]))
```

```fsharp
// src/MachineLearning/Chapter09/Standardizer.fs
/// 訓練データから求めた、列ごとの平均と標準偏差（件数で割る母標準偏差）
type Standardizer =
    {
        Means: Map<string, float>
        Stds: Map<string, float>
    }

/// 標準化する列だけを (値 - 平均) / 標準偏差 にする。標準偏差が 0 の列は 0 にする。ほかの列はそのまま残す
let transformStandardized (standardizer: Standardizer) (rows: Map<string, float> list) : Map<string, float> list =
    let standardize column value =
        match Map.tryFind column standardizer.Means with
        | None -> value
        | Some _ when standardizer.Stds[column] = 0.0 -> 0.0
        | Some mean -> (value - mean) / standardizer.Stds[column]

    rows |> List.map (Map.map standardize)
```

- 学習した結果（平均と標準偏差）をレコードで返し、当てはめる関数に渡します。第 3 章の「`fit` が木を返し、`predict` が木を受け取る」と同じ形です
- `Map.tryFind` は、キーが無ければ `None` を返します。標準化の対象でない列は、そのまま残します
- すべて同じ値の列（標準偏差 0）で割ると `NaN` になるので、0 にします。`Some _ when ...` のように、パターンにガードを付けて場合を分けています

### ML.NET の正規化と突き合わせる

ML.NET には、平均と分散で正規化する `NormalizeMeanVariance` があります。[ADR 004](../../../adr/004-fsharp-ml-libraries.md) で、既定のままでは平均を引かないことが分かっていたので、学習用テストで確かめてから突き合わせます。

```fsharp
// tests/MachineLearning.Tests/Chapter09/MlNetNormalizationTest.fs
let values = [ 1.0; 2.0; 3.0; 6.0 ]

[<Fact>]
let ``学習用テスト: NormalizeMeanVariance の既定（fixZero）は平均を引かず、どの値にも同じ数を掛けるだけ`` () =
    let normalized = normalizeMeanVariance true values

    let ratios = List.map2 (/) normalized values

    Assert.NotEqual(0.0, List.average normalized, 5)
    Assert.All(ratios, (fun ratio -> Assert.Equal(ratios.Head, ratio, 5)))

[<Fact>]
let ``fixZero を外すと、自作の標準化と同じ値になる`` () =
    let rows = values |> List.map (fun value -> Map.ofList [ "x", value ])

    let mine =
        rows
        |> transformStandardized (fitStandardizer [ "x" ] rows)
        |> List.map (fun row -> row["x"])

    let library = normalizeMeanVariance false values

    List.iter2 (fun (m: float) (l: float) -> Assert.Equal(m, l, 5)) mine library
```

`fixZero`（既定は `true`）は、「元の 0 を変換後も 0 のままにする」という指定です。0 を保つには平均を引けないので、既定では値を定数倍するだけになり、変換後の平均は 0 になりません。疎なデータ（0 の多いデータ）の 0 を崩さないための既定です。`fixZero = false` にすると、自作の標準化と小数第 5 位まで一致しました（ML.NET は `float32` で計算するので、桁はそこまでです）。

```fsharp
// src/MachineLearning/Chapter09/MlNetNormalization.fs
/// ML.NET の NormalizeMeanVariance で 1 列の値を正規化する。fixZero が true（既定）なら平均を引かない
let normalizeMeanVariance (fixZero: bool) (values: float list) : float list =
    let context = MLContext(seed = 0)

    let data =
        context.Data.LoadFromEnumerable(values |> List.map (fun v -> { Value = float32 v }))

    let transformed =
        context.Transforms.NormalizeMeanVariance("Value", fixZero = fixZero).Fit(data).Transform(data)

    context.Data.CreateEnumerable<ValueRow>(transformed, reuseRowObject = false)
    |> Seq.map (fun row -> float row.Value)
    |> Seq.toList
```

## 9.6 多項式特徴量を作る

住宅価格と部屋数（`RM`）の関係が直線とは限りません。`RM` の 2 乗の列を加えると、線形回帰でも曲がった関係を表せます。列どうしの積（**交互作用**）の項は、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表します。

```fsharp
// tests/MachineLearning.Tests/Chapter09/PolynomialTest.fs
[<Fact>]
let ``同じ列の組も含めて、列の組を重複なく作る`` () =
    Assert.Equal<(string * string) list>([ "a", "a"; "a", "b"; "b", "b" ], pairsWithReplacement [ "a"; "b" ])

[<Fact>]
let ``元の列と 2 次の項の列を持つ行を作る`` () =
    let rows = [ Map.ofList [ "a", 2.0; "b", 3.0; "c", 9.0 ] ]

    Assert.Equal<Map<string, float> list>(
        [ Map.ofList [ "a", 2.0; "b", 3.0; "a^2", 4.0; "a b", 6.0; "b^2", 9.0 ] ],
        polynomialFeatures [ "a"; "b" ] rows
    )
```

```fsharp
// src/MachineLearning/Chapter09/Polynomial.fs
/// 同じ要素どうしの組も含めて、要素の組を重複なく作る（[a; b] なら (a, a)・(a, b)・(b, b)）
let pairsWithReplacement (items: 'T list) : ('T * 'T) list =
    items
    |> List.mapi (fun i left -> items |> List.skip i |> List.map (fun right -> left, right))
    |> List.concat

/// 2 次の項の名前。同じ列なら「列^2」、違う列なら「列 列」
let termName (left: string) (right: string) : string =
    if left = right then $"{left}^2" else $"{left} {right}"

/// 指定した列と、その列の 2 次の項（2 乗と積）だけを持つ行にする
let polynomialFeatures (columns: string list) (rows: Map<string, float> list) : Map<string, float> list =
    let pairs = pairsWithReplacement columns

    rows
    |> List.map (fun row ->
        (columns |> List.map (fun column -> column, row[column]))
        @ (pairs
           |> List.map (fun (left, right) -> termName left right, row[left] * row[right]))
        |> Map.ofList)
```

- `pairsWithReplacement` はジェネリックで、列名以外の組にも使えます。`List.mapi` で位置 `i` を受け取り、`List.skip i` でそれより前の要素を飛ばして、同じ組を 2 回作らないようにします
- 指定しなかった列（テストの `c`）は、結果に残しません。どの列を使うかを呼び出す側が決められます

## 9.7 外れ値を検出する

四分位範囲（IQR）を使って外れ値を見つけます。値を小さい順に並べて 4 等分したときの、1/4 の位置の値（第 1 四分位点）と 3/4 の位置の値（第 3 四分位点）の差が四分位範囲です。第 1 四分位点より「四分位範囲の 1.5 倍」以上小さい値と、第 3 四分位点より 1.5 倍以上大きい値を外れ値とします。

```fsharp
// tests/MachineLearning.Tests/Chapter09/OutliersTest.fs
[<Fact>]
let ``分位点は並べた値の間を線形補間する`` () =
    let values = [ 4.0; 1.0; 3.0; 2.0 ]

    Assert.Equal<float list>([ 1.0; 1.75; 2.5; 4.0 ], [ 0.0; 0.25; 0.5; 1.0 ] |> List.map (quantile values))

[<Fact>]
let ``訓練データだけから正解の外れ値の行を除く`` () =
    let split =
        {
            XTrain = [ 1; 2; 3; 4; 5 ]
            XTest = [ 6 ]
            TTrain = [ 1.0; 2.0; 3.0; 4.0; 100.0 ]
            TTest = [ 1000.0 ]
        }

    let removed = removeTargetOutliers split

    Assert.Equal<int list>([ 1; 2; 3; 4 ], removed.XTrain)
    Assert.Equal<float list>([ 1.0; 2.0; 3.0; 4.0 ], removed.TTrain)
    Assert.Equal<float list>([ 1000.0 ], removed.TTest)
```

```fsharp
// src/MachineLearning/Chapter09/Outliers.fs
/// 第 1 四分位点・第 3 四分位点から、四分位範囲（IQR）の k 倍より外側にある値を外れ値とする
let iqrOutliersWith (k: float) (values: float list) : bool list =
    let q1 = quantile values 0.25
    let q3 = quantile values 0.75
    let iqr = q3 - q1

    values |> List.map (fun value -> value < q1 - k * iqr || value > q3 + k * iqr)

/// k = 1.5 の四分位範囲による外れ値
let iqrOutliers: float list -> bool list = iqrOutliersWith 1.5

/// 訓練データの正解の外れ値の行を、訓練データから除く。テストデータには手を付けない
let removeTargetOutliers (split: TrainTestSplit<'X, float>) : TrainTestSplit<'X, float> =
    let kept =
        List.zip3 split.XTrain split.TTrain (iqrOutliers split.TTrain)
        |> List.filter (fun (_, _, outlier) -> not outlier)

    { split with
        XTrain = kept |> List.map (fun (x, _, _) -> x)
        TTrain = kept |> List.map (fun (_, t, _) -> t)
    }
```

- `iqrOutliers` は、`iqrOutliersWith` に `k = 1.5` だけを渡した **部分適用** です
- `removeTargetOutliers` は、第 2 章の `TrainTestSplit` を受け取って返します。特徴量の型は `'X` のままなので、どんな特徴量の分割にも使えます。テストデータの外れ値は除きません。本番のデータから外れ値を選んで捨てることはできないためです
- `List.zip3` で特徴量・正解・外れ値かどうかを 3 つ組にし、外れ値でない組だけを残してから、`with` で訓練データだけを差し替えます

## 9.8 表を結合して特徴量を増やす

### Shift_JIS の CSV を読む

`weather.csv` は Shift_JIS で書かれています。.NET の標準では UTF-8 などしか使えず、Shift_JIS のようなコードページのエンコーディングは、一度 **登録** してからでないと使えません。

```fsharp
// tests/MachineLearning.Tests/Chapter09/BikeWeatherTest.fs
[<Fact>]
let ``Shift_JIS の CSV を読み込み、天気の番号から名前への Map にする`` () =
    let csvFile = Path.GetTempFileName()
    File.WriteAllText(csvFile, "weather_id,weather\n1,晴れ\n2,曇り\n", shiftJis ())

    Assert.Equal<Map<int, string>>(Map.ofList [ 1, "晴れ"; 2, "曇り" ], loadWeather csvFile)

[<Fact>]
let ``学習用テスト: Shift_JIS のファイルを UTF-8 として読むと文字化けする`` () =
    let csvFile = Path.GetTempFileName()
    File.WriteAllText(csvFile, "晴れ", shiftJis ())

    Assert.NotEqual<string>("晴れ", File.ReadAllText(csvFile, Encoding.UTF8))
```

```fsharp
// src/MachineLearning/Chapter09/BikeWeather.fs
/// Shift_JIS のエンコーディング。.NET では、コードページのエンコーディングを使う前に一度登録が要る
let shiftJis () : Encoding =
    Encoding.RegisterProvider CodePagesEncodingProvider.Instance
    Encoding.GetEncoding "shift_jis"

[<Literal>]
let WeatherSample = "weather_id,weather\n1,sample"

type WeatherCsv = CsvProvider<WeatherSample>

/// Shift_JIS の weather.csv を、天気の番号から名前への Map にする
let loadWeather (csvFile: string) : Map<int, string> =
    use reader = new StreamReader(csvFile, shiftJis ())

    WeatherCsv.Load(reader).Rows
    |> Seq.map (fun row -> row.Weather_id, row.Weather)
    |> Map.ofSeq
```

- `CodePagesEncodingProvider` を登録すると、`Encoding.GetEncoding "shift_jis"` で Shift_JIS を使えるようになります。登録は何度行っても害はありません
- 型プロバイダの `Load` には、ファイルのパスだけでなく、`StreamReader`（文字コードを指定した読み手）も渡せます
- `use` は、ブロックを抜けるときにファイルを閉じる宣言です。C# の `using` に当たります
- タブ区切りの `bike.tsv` は、`CsvProvider<BikeSample, Separators="\t">` のように区切り文字を指定して読みます

### Map で結合する

```fsharp
[<Fact>]
let ``天気の番号で天気の名前を結合し、名前が無い行は除く`` () =
    let joined = joinWeather (Map.ofList [ 1, "晴れ"; 2, "曇り" ]) bike

    Assert.Equal<(string * int) list>([ "晴れ", 100; "曇り", 50; "晴れ", 300 ], joined |> List.map (fun (w, b) -> w, b.Count))
```

```fsharp
/// 天気の番号で天気の名前を結合する。名前の無い番号の行は除く
let joinWeather (weather: Map<int, string>) (bike: BikeDay list) : (string * BikeDay) list =
    bike
    |> List.choose (fun day -> Map.tryFind day.WeatherId weather |> Option.map (fun name -> name, day))

/// 天気ごとの平均利用者数を、多い順に並べる
let meanCountByWeather (joined: (string * BikeDay) list) : (string * float) list =
    joined
    |> List.groupBy fst
    |> List.map (fun (weather, days) -> weather, days |> List.averageBy (fun (_, day) -> float day.Count))
    |> List.sortByDescending snd
```

- 対応表を `Map<int, string>` にしておけば、結合は「番号で名前を引く」だけです。`Map.tryFind` が `option` を返し、`List.choose` が `None`（名前の無い番号）の行を捨てます。SQL の内部結合（INNER JOIN）に当たります
- `List.groupBy` で天気ごとにまとめ、平均をとってから、`List.sortByDescending` で多い順に並べます

## 9.9 特徴量の効果を測る

### 型を持たない CSV で Boston を読む

Boston.csv には 14 列あり、`CRIME` と `PRICE` のほかに 12 列の数値の列があります。いくつかの列には空欄があります。第 12 章では、使う 4 列だけを型プロバイダの `Schema` で指定しました。この章では、`CRIME` と `PRICE` 以外の **すべての数値の列** を特徴量にします。

列を全部 `Schema` に書く代わりに、FSharp.Data の **型を持たない CSV の API**（`CsvFile`）で読みます。型プロバイダは「決まった列をコンパイル時に型で確かめる」のが得意で、`CsvFile` は「列の一覧を実行時に受け取って、まとめて処理する」のが得意です。

```fsharp
// src/MachineLearning/Chapter09/BostonFeatures.fs
/// 空欄なら None、そうでなければ数値
let private parseOptional (text: string) : float option =
    if text = "" then None else Some(float text)

/// Boston.csv を、型を持たない CSV として読む。CRIME をダミー変数にし、PRICE を正解にする。
/// 特徴量の名前は CSV の列の順（ダミー変数は最後）に並べて返す
let loadBostonFeatures (csvFile: string) : string list * (Map<string, float option> * float) list =
    let csv = CsvFile.Load(csvFile).Cache()
    let headers = csv.Headers |> Option.defaultValue [||] |> List.ofArray

    let numericColumns =
        headers |> List.filter (fun header -> header <> Category && header <> Target)

    let rows = csv.Rows |> Seq.toList

    let categories =
        rows |> List.map (fun row -> row.GetColumn Category) |> dummyCategories

    let toFeatures (row: CsvRow) =
        let numeric =
            numericColumns
            |> List.map (fun column -> column, parseOptional (row.GetColumn column))

        let dummies =
            encodeDummies Category categories (row.GetColumn Category)
            |> Map.toList
            |> List.map (fun (name, value) -> name, Some value)

        Map.ofList (numeric @ dummies), float (row.GetColumn Target)

    numericColumns @ (categories |> List.map (fun category -> $"{Category}_{category}")),
    rows |> List.map toFeatures
```

- `row.GetColumn "RM"` は、列名で値を文字列として取り出します。列名の書き間違いは、コンパイル時ではなく実行時に分かります。型プロバイダとの引き換えです
- `csv.Headers` は `string[] option` です。見出しの無い CSV もありうるので `option` になっています
- `Map` はキーの順に並ぶので、CSV の列の順は別のリストで返します

読み込んだ後は、第 2 章の `splitTrainTest`・`columnMeans`・`fillMissing` で分割と補完をします。第 2 章の関数を `float option` の `Map` で書いておいたので、そのまま使えます。

### 特徴量の組み合わせごとに決定係数を測る

```fsharp
/// columns から 2 次の項を作り、terms の列だけを訓練データの平均と標準偏差で標準化して線形回帰し、
/// 訓練データとテストデータの決定係数を返す
let scoreFeatureSet
    (split: TrainTestSplit<Map<string, float>, float>)
    (columns: string list)
    (terms: string list)
    : float * float =
    let select rows =
        polynomialFeatures columns rows
        |> List.map (Map.filter (fun name _ -> List.contains name terms))

    let train = select split.XTrain
    let standardizer = fitStandardizer terms train
    let xTrain = transformStandardized standardizer train
    let xTest = select split.XTest |> transformStandardized standardizer
    let model = fitLinearRegression xTrain split.TTrain

    r2Score split.TTrain (predictLinearRegression model xTrain),
    r2Score split.TTest (predictLinearRegression model xTest)
```

線形回帰は、第 7 章の `fitLinearRegression`・`predictLinearRegression` を、決定係数は第 7 章の `r2Score` をそのまま使います。第 7 章の線形回帰が `Map` の特徴量を受け取るので、作った特徴量をそのまま渡せます。

`RM`・`LSTAT`・`PTRATIO` の 3 列から、次の 3 通りを比べます。

- 元の特徴量（3 列）
- 2 乗の項を追加（6 列）
- 交互作用の項も追加（9 列）

### 実データで測る

```bash
dotnet run --project src/MachineLearning -- chapter09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
ML.NET で正規化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.5723, テスト 0.6970
  2 乗の項を追加（6 列）: 訓練 0.7561, テスト 0.8351
  交互作用の項も追加（9 列）: 訓練 0.7779, テスト 0.8078
訓練データの PRICE の外れ値: 6 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6351, テスト 0.7110
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

- 2 乗の項を加えると、テストデータの決定係数は 0.70 から 0.84 に上がりました。価格と部屋数・低所得者の割合の関係が、直線より曲線に近いことを表しています
- 交互作用の項まで加えると、訓練データの決定係数は上がりましたが、テストデータでは 0.81 に下がりました。100 件のデータに 9 列は多く、訓練データに合わせすぎています（第 12 章の過学習です）
- 価格の外れ値 6 件を訓練データから除くと、テストデータの決定係数は 0.84 から 0.71 に下がりました。外れ値はすべて高額の側（39.8〜50.0）にあり、除いた訓練データでは、高額な物件の傾向を学べなくなります。外れ値は「誤った値」とは限りません。除くかどうかは、値が誤りかどうかを確かめてから判断します
- 表示の最初の「平均 0.00」は、最初は `-0.00` と表示されました。ML.NET は `float32` で計算するので、平均の誤差が 1e-9 より大きく残るためです。0 とみなす幅を 1e-6 に広げました

分割に使う乱数生成器が他の版と違うので、決定係数は他の版と一致しません。天気ごとの平均利用者数は分割に関係しないので、他の版と同じ値です。

最初は、ML.NET の結果の行を `$"... {clean mlNet.Means["RM"]:F2} ..."` と書いて、次のコンパイルエラーになりました。

```text
error FS3373: 補間された文字列が無効です。単一引用符または逐語的文字列リテラルは、単一引用符または逐語的文字列内の補間された式では使用できません。補間式に対して明示的な 'let' バインドを使用するか、外部文字列リテラルとして三重引用符文字列を使用することをご検討ください。
```

`$"..."` の補間式の中に、`"RM"` という文字列リテラルは書けません。外側を三重引用符の `$"""..."""` にすると書けます。

### 実データのテスト

表示をまるごと比べるテストを残しています（学習データが無ければスキップします）。第 9 章のテストは 21 件です。

## 9.10 Notebook で探索する

Notebook は `apps/fsharp/notebooks/chapter09_boston_exploration.ipynb` にあります。Polyglot Notebooks が廃止されていることは、[第 2 章の 2.10 節](02-data-preprocessing-and-triangulation.md) を参照してください。

```fsharp
[ "RM"; "LSTAT" ]
|> List.map (fun column ->
    Chart.Point(x = (split.XTrain |> List.map (fun row -> row[column])), y = split.TTrain, Name = column)
    |> Chart.withXAxisStyle column
    |> Chart.withYAxisStyle "PRICE")
|> Chart.Grid(1, 2)
|> Chart.withTitle "訓練データの特徴量と PRICE"
```

`Chart.Grid(1, 2)` は、複数のグラフを 1 行 2 列に並べます。

- 標準化の前後の `RM` を散布図にすると、点は一直線に並びます。標準化は値を平行移動して拡大・縮小するだけで、並び順や間隔の比は変えません
- `RM` と価格、`LSTAT` と価格の散布図で、関係が直線に近いか、曲がっているかを確かめられます。2 乗の項でテストデータの決定係数が上がった（0.70 から 0.84）ことと見比べてください
- 訓練データの価格を小さい順に並べると、第 1 四分位点は 17.95、第 3 四分位点は 24.8 で、外れ値の 6 件（39.8・43.8・44.8・48.5・50.0・50.0）はすべて高額の側にありました
- 天気ごとの平均利用者数は、晴れ・曇り・雨の順に少なくなり、雨の日は晴れの日の 4 割以下です

グラフの画像は、第 2 章と同じ理由で記事に載せていません。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter09/BikeWeather.fs）</summary>

```fsharp
module MachineLearning.Chapter09.BikeWeather

open System.IO
open System.Text
open FSharp.Data

/// Shift_JIS のエンコーディング。.NET では、コードページのエンコーディングを使う前に一度登録が要る
let shiftJis () : Encoding =
    Encoding.RegisterProvider CodePagesEncodingProvider.Instance
    Encoding.GetEncoding "shift_jis"

/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let BikeSample =
    "dteday\tholiday\tweekday\tworkingday\tweather_id\tcnt\n2011-01-01\t0\t6\t0\t2\t985"

type BikeTsv = CsvProvider<BikeSample, Separators="\t">

[<Literal>]
let WeatherSample = "weather_id,weather\n1,sample"

type WeatherCsv = CsvProvider<WeatherSample>

/// 1 日分の利用者数
type BikeDay =
    {
        Day: string
        WeatherId: int
        Count: int
    }

let loadBike (tsvFile: string) : BikeDay list =
    BikeTsv.Load(tsvFile).Rows
    |> Seq.map (fun row ->
        {
            Day = row.Dteday.ToString "yyyy-MM-dd"
            WeatherId = row.Weather_id
            Count = row.Cnt
        })
    |> Seq.toList

/// Shift_JIS の weather.csv を、天気の番号から名前への Map にする
let loadWeather (csvFile: string) : Map<int, string> =
    use reader = new StreamReader(csvFile, shiftJis ())

    WeatherCsv.Load(reader).Rows
    |> Seq.map (fun row -> row.Weather_id, row.Weather)
    |> Map.ofSeq

/// 天気の番号で天気の名前を結合する。名前の無い番号の行は除く
let joinWeather (weather: Map<int, string>) (bike: BikeDay list) : (string * BikeDay) list =
    bike
    |> List.choose (fun day -> Map.tryFind day.WeatherId weather |> Option.map (fun name -> name, day))

/// 天気ごとの平均利用者数を、多い順に並べる
let meanCountByWeather (joined: (string * BikeDay) list) : (string * float) list =
    joined
    |> List.groupBy fst
    |> List.map (fun (weather, days) -> weather, days |> List.averageBy (fun (_, day) -> float day.Count))
    |> List.sortByDescending snd
```

</details>

## 9.11 まとめ

この章では、手元の列から特徴量を作り、線形回帰の決定係数で効果を確かめました。

1. **学習と適用を分ける** — ダミー変数のカテゴリも、標準化の平均と標準偏差も、訓練データで決めてからテストデータに当てはめた
2. **ライブラリの既定を確かめる** — ML.NET の `NormalizeMeanVariance` は既定では平均を引かない。`fixZero = false` で自作の標準化と一致した
3. **型プロバイダと型を持たない CSV** — 決まった列は型プロバイダで、列の一覧をまとめて扱うときは `CsvFile` で読んだ
4. **エンコーディングと Map による結合** — Shift_JIS は `CodePagesEncodingProvider` を登録してから読み、表の結合は `Map.tryFind` と `List.choose` で書いた
5. **特徴量は増やせばよいわけではない** — 2 乗の項は効いたが、交互作用の項まで加えると過学習した。外れ値も、機械的に除くと性能が下がった

次の章では、ロジスティック回帰と、決定木を組み合わせるアンサンブル学習を扱います。
