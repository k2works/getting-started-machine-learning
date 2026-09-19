---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Java の TDD で自作し、特徴量の組み合わせごとの決定係数を測って、Tribuo の標準化との違い（不偏標準偏差）を確かめる。"
tags: [article,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T15:57:17Z }
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

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) と対比します。Kotlin 版は Kotlin DataFrame の `convert`・`add`・`innerJoin`・`groupBy` で表を操作しました。Java 版はデータフレームのライブラリを使わず、第 2 章の `Table`・`Row`・`Features` と Stream API で同じことを書きます。

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード（実データで確認） |
|---------|------|----------|------------------------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

文字コードは、ファイルを UTF-8 と Shift_JIS のそれぞれで復号できるかを試して確かめました。`weather.csv` だけが UTF-8 として復号できません。

第 2 章の `Table.load` は「BOM 付きの UTF-8 のカンマ区切り」を読むメソッドです。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します（第 2 章の `Table` は変更しません）。

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
  - [ ] 2 つの列の積（交互作用の項）を加える
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

この章のクラスは `chapter09` パッケージに置き、技法ごとにクラスを分けます。

| クラス | 役割 |
|-------|------|
| `Dummies` | ダミー変数 |
| `Standardizer` | 標準化（record） |
| `TribuoStandardization` | Tribuo の標準化の呼び出し |
| `PolynomialFeatures` | 多項式特徴量 |
| `Outliers` | 外れ値の検出 |
| `DelimitedFiles`・`BikeWeather` | 区切り文字・文字コードを指定した読み込みと、表の結合 |
| `LinearModel`・`Scores`・`Boston` | 正規方程式による線形回帰、決定係数、Boston データの前処理 |

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```java
@Test
@DisplayName("先頭を除いたカテゴリを辞書順に返す")
void categoriesWithoutFirst() {
  assertThat(Dummies.categories(List.of("low", "high", "very_low", "low")))
      .containsExactly("low", "very_low");
}

@Test
@DisplayName("欠損値（空欄）はカテゴリに数えない")
void categoriesIgnoreMissing() {
  assertThat(Dummies.categories(List.of("low", "", "high"))).containsExactly("low");
}
```

Kotlin 版は欠損値を `null` で表し、`filterNotNull()` で除きました。Java 版の `Row` は空欄を空文字列のまま持つので、`isBlank()` で除きます。

```java
/** 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。 */
public static List<String> categories(List<String> values) {
  return values.stream().filter(v -> !v.isBlank()).distinct().sorted().skip(1).toList();
}
```

`distinct().sorted().skip(1)` は Kotlin の `distinct().sorted().drop(1)` とほぼ同じ形です。

### 表に列を加える

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```java
@Test
@DisplayName("カテゴリごとに 0 と 1 の列を作り元の列を取り除く")
void encodesDummies() {
  Table encoded =
      Dummies.encode(crimeTable("low", "high", "very_low"), "CRIME", List.of("low", "very_low"));

  assertThat(encoded.columns()).containsExactly("RM", "CRIME_low", "CRIME_very_low");
  assertThat(encoded.rows()).extracting(r -> r.text("RM")).containsExactly("6.0", "6.0", "6.0");
  assertThat(encoded.rows()).extracting(r -> r.text("CRIME_low")).containsExactly("1", "0", "0");
  assertThat(encoded.rows())
      .extracting(r -> r.text("CRIME_very_low"))
      .containsExactly("0", "0", "1");
}
```

`Table` は変更できない record なので、行ごとにセルの `Map` を写し、元の列を取り除いてダミー変数のセルを加えた新しい `Row` を作ります。

```java
/** 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば "1"、それ以外は "0"。 */
public static Table encode(Table table, String column, List<String> categories) {
  List<String> dummyColumns = categories.stream().map(c -> column + "_" + c).toList();
  List<String> columns =
      Stream.concat(
              table.columns().stream().filter(c -> !c.equals(column)), dummyColumns.stream())
          .toList();
  List<Row> rows = table.rows().stream().map(row -> encodeRow(row, column, categories)).toList();
  return new Table(columns, rows);
}

private static Row encodeRow(Row row, String column, List<String> categories) {
  Map<String, String> cells = new HashMap<>(row.cells());
  String value = cells.remove(column);
  for (String category : categories) {
    cells.put(column + "_" + category, category.equals(value) ? "1" : "0");
  }
  return new Row(cells);
}
```

セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `Preprocessing.splitFeaturesAndTarget`・`columnMeans`・`fillMissing` を、ダミー変数の列にもそのまま使えます。カテゴリを引数で受け取るのは Kotlin 版と同じ理由で、訓練データで決めたカテゴリをテストデータにも当てはめ、列をそろえるためです。カテゴリに無い値の行は、すべての列が `"0"` になることもテストで確かめました。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### 平均と標準偏差を求める

Kotlin 版は `Standardizer` を data class にし、列名から平均・標準偏差への `Map` を持たせました。Java 版は record にします。第 14 章（K-means）でもこのクラスを使うので、第 2 章の `Features` のリストをそのまま受け取れる形にします。

```java
@Test
@DisplayName("訓練データから列ごとの平均と標準偏差を求める")
void fitsMeansAndStds() {
  var standardizer = Standardizer.fit(List.of(row(1, 10), row(2, 10), row(3, 40)));

  assertThat(standardizer.means().get("RM")).isCloseTo(2.0, within(1e-12));
  assertThat(standardizer.means().get("LSTAT")).isCloseTo(20.0, within(1e-12));
  assertThat(standardizer.stds().get("RM")).isCloseTo(Math.sqrt(2.0 / 3), within(1e-12));
  assertThat(standardizer.stds().get("LSTAT")).isCloseTo(Math.sqrt(200.0), within(1e-12));
}
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。scikit-learn の `StandardScaler` と同じ定義にしました。

テストを実行すると、まだ `Standardizer` が無いのでコンパイルで失敗します。

```text
src/test/java/chapter09/StandardizerTest.java:22: エラー: シンボルを見つけられません
    var standardizer = Standardizer.fit(List.of(row(1, 10), row(2, 10), row(3, 40)));
                       ^
  シンボル:   変数 Standardizer
  場所: クラス StandardizerTest
src/test/java/chapter09/StandardizerTest.java:33: エラー: シンボルを見つけられません
    var standardizer = new Standardizer(Map.of("RM", 2.0), Map.of("RM", 0.5));
                           ^
  シンボル:   クラス Standardizer
  場所: クラス StandardizerTest
```

実装は次のとおりです。

```java
public record Standardizer(Map<String, Double> means, Map<String, Double> stds) {
  public Standardizer {
    if (!means.keySet().equals(stds.keySet())) {
      throw new IllegalArgumentException("平均と標準偏差の列が違います");
    }
    means = Collections.unmodifiableMap(new LinkedHashMap<>(means));
    stds = Collections.unmodifiableMap(new LinkedHashMap<>(stds));
  }

  /** 特徴量のすべての列について、平均と標準偏差を求める。 */
  public static Standardizer fit(List<Features> x) {
    if (x.isEmpty()) {
      throw new IllegalArgumentException("特徴量が 1 件もありません");
    }
    Map<String, Double> means = new LinkedHashMap<>();
    Map<String, Double> stds = new LinkedHashMap<>();
    for (String column : x.getFirst().columns()) {
      double[] values = x.stream().mapToDouble(f -> f.value(column)).toArray();
      double mean = mean(values);
      double variance = mean(Arrays.stream(values).map(v -> (v - mean) * (v - mean)).toArray());
      double std = Math.sqrt(variance);
      means.put(column, mean);
      stds.put(column, std == 0 ? 1.0 : std);
    }
    return new Standardizer(means, stds);
  }

  private static double mean(double[] values) {
    return Arrays.stream(values).average().orElseThrow();
  }

  /** 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。 */
  public Features transform(Features features) {
    List<String> columns = features.columns();
    double[] values = features.values();
    for (int i = 0; i < values.length; i++) {
      String column = columns.get(i);
      if (means.containsKey(column)) {
        values[i] = (values[i] - means.get(column)) / stds.get(column);
      }
    }
    return new Features(columns, values);
  }

  /** 特徴量のリストを標準化する。 */
  public List<Features> transform(List<Features> x) {
    return x.stream().map(this::transform).toList();
  }
}
```

Kotlin 版との違いを 3 つ挙げます。

1. **`Map` の写し方** — `Map.copyOf` は変更できない写しを作りますが、順序を保ちません。列の順を保ちたいので、`LinkedHashMap` に写してから `Collections.unmodifiableMap` で包みました。Kotlin の `associateWith` は最初から挿入順の `LinkedHashMap` を返します
2. **標準偏差の計算** — Kotlin 版は Kotlin DataFrame の `std(ddof = 0)` を呼びました。Java 版はライブラリを使わないので、分散（平均との差の 2 乗の平均）の平方根を自分で書きます
3. **配列の写し** — `Features.values()` は配列の写しを返す（第 2 章）ので、`transform` で書き換えても元の特徴量は変わりません

`transform` には 1 件の特徴量を受け取るものと、リストを受け取るものを用意しました。すべて同じ値の列は、標準偏差を 1 にして値が 0 になるようにしています（0 で割って NaN になるのを避けるため）。

### Tribuo の標準化と突き合わせる

Tribuo には `MeanStdDevTransformation` という標準化があります。Java から直接呼べるので、Kotlin 版の橋渡しのコードとほぼ同じ形になります。

```java
/** 訓練データの値から平均と標準偏差を求め、別の値を標準化する。 */
public static List<Double> standardize(List<Double> train, List<Double> values) {
  TransformStatistics statistics = new MeanStdDevTransformation().createStats();
  train.forEach(statistics::observeValue);
  Transformer transformer = statistics.generateTransformer();
  return values.stream().map(transformer::transform).toList();
}
```

まず、自作の標準化と同じ値になるはずだと考えて、そのまま比べるテストを書いてみました。

```java
double own = standardizer.transform(Samples.rm(8.0)).value("RM");

assertThat(own)
    .isCloseTo(
        TribuoStandardization.standardize(train, List.of(8.0)).getFirst(), within(1e-12));
```

```text
    java.lang.AssertionError:
    Expecting actual:
      2.197401062294143
    to be close to:
      1.9030051422496395
    by less than 1.0E-12 but difference was 0.2943959200445035.
```

2 つの値の比は 0.866 で、`√(3/4)` です。訓練データは 4 件なので、Tribuo は件数から 1 を引いた 3 で割る **不偏標準偏差** を使っていることが分かります。Kotlin 版で確かめた結果（ADR 002）と同じです。この事実をテストに残します。

```java
@Test
@DisplayName("Tribuo の MeanStdDevTransformation は件数から 1 を引いて割る標準偏差を使う")
void tribuoUsesSampleStd() {
  double mean = TRAIN.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
  double sumOfSquares = TRAIN.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
  double sampleStd = Math.sqrt(sumOfSquares / (TRAIN.size() - 1));

  List<Double> standardized = TribuoStandardization.standardize(TRAIN, TEST);

  assertThat(standardized.get(0)).isCloseTo((6.2 - mean) / sampleStd, within(1e-12));
  assertThat(standardized.get(1)).isCloseTo((8.0 - mean) / sampleStd, within(1e-12));
}

@Test
@DisplayName("自作の標準化に件数から決まる係数を掛けると Tribuo の値になる")
void ownTimesRatioEqualsTribuo() {
  var standardizer = Standardizer.fit(TRAIN.stream().map(Samples::rm).toList());
  double ratio = Math.sqrt((TRAIN.size() - 1.0) / TRAIN.size());

  List<Double> tribuo = TribuoStandardization.standardize(TRAIN, TEST);

  for (int i = 0; i < TEST.size(); i++) {
    double own = standardizer.transform(Samples.rm(TEST.get(i))).value("RM");
    assertThat(own * ratio).isCloseTo(tribuo.get(i), within(1e-12));
  }
}
```

同じ「標準偏差」でも、ライブラリによって割る数が違います。件数が多ければ差は小さくなりますが、この章の訓練データは 70 件なので、係数は `√(69/70)` ≒ 0.993 です。線形回帰では係数の大きさが変わるだけで予測は変わりませんが、第 14 章の K-means のように距離を使うアルゴリズムでは、どちらの定義を使ったかを記事とコードで明示しておきます。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

部屋数（RM）と価格の関係が直線でなく曲線なら、RM の 2 乗の列を加えると線形回帰でも曲線を表せます。2 つの列の積（交互作用の項）を加えると、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表せます。

```java
@Test
@DisplayName("2 列なら 2 乗の列と 2 つの列の積の列を加える")
void twoColumns() {
  var columns = List.of("RM", "LSTAT");
  var x =
      List.of(
          new Features(columns, new double[] {2, 5}), new Features(columns, new double[] {3, 7}));

  List<Features> features = PolynomialFeatures.expand(x, columns);

  assertThat(features.getFirst().columns())
      .containsExactly("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2");
  assertThat(features).extracting(f -> f.value("RM LSTAT")).containsExactly(10.0, 21.0);
  assertThat(features).extracting(f -> f.value("LSTAT^2")).containsExactly(25.0, 49.0);
}
```

1 列・2 列・3 列と三角測量し、3 列では scikit-learn の `PolynomialFeatures` と同じ並びの 9 列になることを確かめました。列の組は、重複を許して 2 つを選ぶ組を並べて作ります。Kotlin 版は `Pair<String, String>` と別の関数 `termName` を使いましたが、Java 版は組に名前付きの record を使い、項の名前をそのメソッドにしました。

```java
public record Pair(String left, String right) {
  /** 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。 */
  public String name() {
    return left.equals(right) ? left + "^2" : left + " " + right;
  }
}

/** 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。 */
public static List<Pair> pairsWithReplacement(List<String> columns) {
  List<Pair> pairs = new ArrayList<>();
  for (int i = 0; i < columns.size(); i++) {
    for (int j = i; j < columns.size(); j++) {
      pairs.add(new Pair(columns.get(i), columns.get(j)));
    }
  }
  return List.copyOf(pairs);
}
```

`expand` は、指定した列の値の後ろに、組ごとの積を並べた新しい `Features` を作ります。使う項を選ぶ `select` も用意し、「元の特徴量だけ」「2 乗の項を追加」「交互作用の項も追加」を同じ流れで比べられるようにしました。

## 9.7 外れ値を検出する

第 1 四分位数（Q1）と第 3 四分位数（Q3）の差を **四分位範囲（IQR）** と呼びます。`Q1 − 1.5 × IQR` より小さい値と、`Q3 + 1.5 × IQR` より大きい値を外れ値とみなします。

```java
@Test
@DisplayName("四分位数の位置が値の間にあれば前後の値から線形補間する")
void quantileInterpolates() {
  assertThat(Outliers.quantile(List.of(4.0, 1.0, 3.0, 2.0), 0.25)).isCloseTo(1.75, within(1e-12));
}

@Test
@DisplayName("第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする")
void upperOutlier() {
  assertThat(Outliers.iqrOutliers(List.of(1.0, 2.0, 3.0, 4.0, 100.0)))
      .containsExactly(false, false, false, false, true);
}
```

```java
/** 分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。 */
public static double quantile(List<Double> values, double q) {
  List<Double> sorted = values.stream().sorted().toList();
  double position = (sorted.size() - 1) * q;
  int lower = (int) Math.floor(position);
  int upper = (int) Math.ceil(position);
  return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
}

/** 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。 */
public static List<Boolean> iqrOutliers(List<Double> values, double k) {
  double q1 = quantile(values, FIRST_QUARTILE);
  double q3 = quantile(values, THIRD_QUARTILE);
  double iqr = q3 - q1;
  return values.stream().map(v -> v < q1 - k * iqr || v > q3 + k * iqr).toList();
}

/** k を 1.5 にして外れ値を求める。 */
public static List<Boolean> iqrOutliers(List<Double> values) {
  return iqrOutliers(values, DEFAULT_K);
}
```

Kotlin 版は `k: Double = 1.5` と既定の引数で書きました。Java には既定の引数が無いので、引数の少ないメソッドを **オーバーロード** して既定値を渡します。

外れ値を除く `removeTargetOutliers` は、訓練データから正解（価格）が外れ値の行だけを取り除き、テストデータには手を付けません。テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいても評価から外してはいけないからです。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

区切り文字と文字コードを受け取って `Table` を作る `DelimitedFiles.load` を足します。

```java
/** 1 行目を列名として読み込む。文字コードが違えば MalformedInputException を投げる。 */
public static Table load(Path file, Charset charset, String delimiter) throws IOException {
  String separator = Pattern.quote(delimiter);
  List<String> lines = Files.readAllLines(file, charset);
  List<String> columns = List.of(lines.getFirst().split(separator));
  List<Row> rows =
      lines.stream()
          .skip(1)
          .filter(line -> !line.isBlank())
          .map(line -> toRow(columns, line.split(separator, -1)))
          .toList();
  return new Table(columns, rows);
}
```

`String.split` は正規表現を受け取るので、区切り文字を `Pattern.quote` で囲んでおきます（`|` などを区切り文字にしても誤動作しないように）。`bike.tsv` は `"\t"` と UTF-8、`weather.csv` は `","` と `Charset.forName("Shift_JIS")` で読み込みます。

文字コードを間違えたときの振る舞いは、Kotlin 版と Java 版で違います。Kotlin 版の記事にあるとおり、Kotlin DataFrame は UTF-8 として解釈できないバイトを置換文字（U+FFFD）に置き換えて読み込みを続けました。Java の `Files.readAllLines` は置き換えずに `MalformedInputException` を投げます。この違いをテストに残しました。

```java
@Test
@DisplayName("Shift_JIS のファイルを UTF-8 として読むと例外になる")
void shiftJisIsNotUtf8() throws IOException {
  Path csvFile = directory.resolve("weather.csv");
  Files.writeString(csvFile, "weather_id,weather\n1,晴れ\n", Charset.forName("Shift_JIS"));

  org.assertj.core.api.Assertions.assertThatThrownBy(
          () -> DelimitedFiles.load(csvFile, StandardCharsets.UTF_8, ","))
      .isInstanceOf(java.nio.charset.MalformedInputException.class);
}
```

`Files.readAllLines` は、復号できないバイトがあると置き換えずに例外で知らせる、と API ドキュメントに書かれています。文字化けしたデータが黙って入ってこない点では、この読み込み方のほうが安全です。

### Map による結合と集計

利用者数の表に、天気 ID をキーにして天気の名前を加えます。Kotlin 版は `innerJoin` を使いました。Java 版は、天気の表を「天気 ID → 行」の `Map` にしてから、利用者数の行ごとに引きます。

```java
/** 天気 ID をキーにした Map を引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。 */
public static Table joinWeather(Table bike, Table weather) {
  Map<String, Row> byId =
      weather.rows().stream()
          .collect(Collectors.toMap(row -> row.text(KEY), Function.identity()));
  List<String> added = weather.columns().stream().filter(c -> !KEY.equals(c)).toList();
  List<Row> rows =
      bike.rows().stream()
          .filter(row -> byId.containsKey(row.text(KEY)))
          .map(row -> join(row, byId.get(row.text(KEY)), added))
          .toList();
  return new Table(Stream.concat(bike.columns().stream(), added.stream()).toList(), rows);
}
```

天気の表に無い天気 ID の行は `filter` で落とすので、SQL の内部結合と同じ結果になります（テスト「天気の表に無い天気 ID の行は残さない」）。`Collectors.toMap` はキーが重複すると例外を投げるので、天気の表の天気 ID が一意でなければ結合の前に気付けます。`filter(c -> !KEY.equals(c))` と定数を左に置いているのは、PMD の `LiteralsFirstInComparisons` の指摘に従ったためです。

天気ごとの平均利用者数は、`Collectors.groupingBy` と `averagingDouble` で求め、多い順に並べ替えて `LinkedHashMap` に集めます。

```java
/** 天気ごとの平均利用者数を、多い順に並べて返す。 */
public static Map<String, Double> meanCountByWeather(Table joined) {
  Map<String, Double> means =
      joined.rows().stream()
          .collect(
              Collectors.groupingBy(
                  row -> row.text("weather"),
                  Collectors.averagingDouble(row -> row.number("cnt").orElseThrow())));
  return means.entrySet().stream()
      .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
      .collect(
          Collectors.toMap(
              Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
}
```

`groupingBy` が返す `HashMap` は順序を持たないので、並べ替えたあとに `LinkedHashMap::new` を渡して順序を保ちます。`Map.Entry.<String, Double>comparingByValue()` の型引数は、`reversed()` を続けると型推論が効かなくなるため明示しています。

## 9.9 特徴量の効果を測る

### Boston データの前処理

`Boston.prepare` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

Kotlin 版では、欠損のある整数の列（RAD）が `Int?` として読み込まれ、平均値の `Double` を入れられないという型の問題が実データで見つかりました。Java 版の `Row` はセルを文字列で持ち、`number` で読むときに `double` にするので、この問題は起きません。列の型を読み込み時に推論しない代わりに、読むたびに解釈する設計の違いです。

### 線形回帰と決定係数

決定係数を測るために、Kotlin 版と同じく、この章に正規方程式を解く最小の `LinearModel` を置きました。Tribuo の `DenseMatrix` のコレスキー分解で `XᵀX β = Xᵀt` を解きます。

```java
/** 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。 */
public static LinearModel fit(double[][] rows, List<Double> t) {
  double[][] design = new double[rows.length][];
  for (int i = 0; i < rows.length; i++) {
    design[i] = new double[rows[i].length + 1];
    design[i][0] = 1;
    System.arraycopy(rows[i], 0, design[i], 1, rows[i].length);
  }
  DenseMatrix x = DenseMatrix.createDenseMatrix(design);
  DenseMatrix transposed = x.transpose();
  DenseMatrix.CholeskyFactorization cholesky =
      transposed
          .matrixMultiply(x)
          .choleskyFactorization()
          .orElseThrow(() -> new IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません"));
  DenseVector target = DenseVector.createDenseVector(t.stream().mapToDouble(v -> v).toArray());
  double[] beta = cholesky.solve(transposed.leftMultiply(target)).toArray();
  return new LinearModel(beta[0], Arrays.stream(beta).skip(1).boxed().toList());
}
```

`choleskyFactorization()` は `Optional` を返すので、Kotlin 版の `orElseThrow { ... }` と同じく `orElseThrow` で「解けない」ことを例外にします。

特徴量の組ごとの評価は `Boston.scoreFeatureSet` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `Standardizer` を `fit` し、両方を `transform` してから学習します。

```java
public static Scores scoreFeatureSet(
    TrainTestSplit<Features, Double> split, List<String> columns, List<String> terms) {
  List<Features> train =
      PolynomialFeatures.select(PolynomialFeatures.expand(split.xTrain(), columns), terms);
  List<Features> test =
      PolynomialFeatures.select(PolynomialFeatures.expand(split.xTest(), columns), terms);
  Standardizer standardizer = Standardizer.fit(train);
  double[][] xTrain = toRows(standardizer.transform(train));
  double[][] xTest = toRows(standardizer.transform(test));
  LinearModel model = LinearModel.fit(xTrain, split.tTrain());
  return new Scores(
      LinearModel.rSquared(split.tTrain(), model.predict(xTrain)),
      LinearModel.rSquared(split.tTest(), model.predict(xTest)));
}
```

Kotlin 版は訓練とテストの決定係数を `Pair<Double, Double>` で返しましたが、Java 版は `Scores(double train, double test)` という record にしました。`first`・`second` ではなく `train()`・`test()` と読めます。

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

### 実データで測る

`./gradlew runChapter -Pchapter=09` の出力です。

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

「標準化した訓練データの RM」は、標準化した訓練データにもう一度 `Standardizer.fit` を当て、その平均と標準偏差を表示しています。

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、Kotlin 版と同じ値になりました

決定係数の値は Kotlin 版（2 乗の項でテスト 0.6457 → 0.7975）と違います。第 2 章と同じく、Java 版は `java.util.Random(0)` で `Collections.shuffle` し、Kotlin 版は `kotlin.random.Random(0)` で `shuffled` するので、同じシードでも訓練データとテストデータに入る行が違うためです。「2 乗の項で上がる」という結論は同じですが、交互作用の項の効果は、Java 版では Python 版と同じく「下がる」、Kotlin 版では「ほとんど変わらない」になりました。100 件のデータでは、分け方によって結論が揺れる判断もあることが分かります。

この出力は `FeatureEngineeringDataTest` で固定しています。学習データが無い環境では、`assumeTrue` で実データのテストをスキップします。

## 9.10 Notebook による探索と可視化

Java 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、`./gradlew spotlessApply check` で整形と静的解析をかけました。

- **PMD の `LiteralsFirstInComparisons`** — `c.equals(KEY)` を `KEY.equals(c)` に直しました。定数を左に置けば、左辺が `null` でも `NullPointerException` になりません。`Dummies.encode` の `c.equals(column)` は、比べる相手が引数で定数ではないので指摘されません
- **表示の桁数** — `Main` の表示の桁数（4・2・1）を `SCORE_DIGITS`・`MEAN_DIGITS`・`COUNT_DIGITS` の定数にしました
- **標準偏差の確認** — 最初は `Main` の中で平均と標準偏差を計算し直していましたが、`Standardizer.fit` をもう一度当てれば同じことが分かるので、計算の重複を消しました

Kotlin 版が detekt の指摘で技法ごとにファイルを分けたのに対し、Java 版は「1 ファイルに 1 つのトップレベルのクラス」なので、最初から技法ごとのクラスに分かれています。

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、`Standardizer` は次の形で公開しています。

| API | 内容 |
|-----|------|
| `Standardizer.fit(List<Features> x)` | すべての列の平均と標準偏差（件数で割る）を求める |
| `standardizer.transform(List<Features> x)` | リストを標準化する |
| `standardizer.transform(Features features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardizer.means()`・`stds()` | 列名の順を保った、変更できない `Map` |

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Java の TDD で自作し、標準化を Tribuo と突き合わせました。

| 技法 | 自作したクラス・メソッド | 突き合わせたライブラリ | 落とし穴 |
|------|---------------------|-------------------|---------|
| ダミー変数 | `Dummies.categories`・`encode` | — | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | Tribuo の `MeanStdDevTransformation` | 標準偏差の定義がライブラリで違う、分散 0 の列、テストデータの平均を使わない |
| 多項式特徴量 | `PolynomialFeatures.expand`・`pairsWithReplacement` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `Outliers.quantile`・`iqrOutliers` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `DelimitedFiles.load`・`BikeWeather.joinWeather` | — | 区切り文字と文字コード、内部結合で消える行 |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。

Java 版ならではの学びもありました。

1. **データフレームが無くても書ける** — `Map` で結合し、`Collectors.groupingBy` で集計した。順序を保つには `LinkedHashMap` を明示する
2. **文字コードの誤りは例外になる** — `Files.readAllLines` は `MalformedInputException` を投げ、Kotlin DataFrame のように黙って文字化けしない
3. **既定の引数の代わりにオーバーロード** — `iqrOutliers(values)` と `iqrOutliers(values, k)` を用意した
4. **組には名前を付ける** — `Pair` の代わりに `Scores` や `PolynomialFeatures.Pair` の record を使い、項の名前もメソッドにした

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
