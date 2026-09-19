---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "データフレームのライブラリを使わず、自作の Table・Row と欠損値を持てない Features で iris データを前処理し、java.util.Random による訓練・テストデータ分割を Java の TDD で実装する。"
tags: [article,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T15:52:03Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。Python 版は pandas、Kotlin 版は Kotlin DataFrame を使いましたが、Java 版は **データフレームのライブラリを使わず**、小さな表の型を自分で作ります。その型の設計（欠損値をどう表すか、補完の前と後をどう区別するか）に注目してください。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Java 版での読み方 |
|----|------|-----------------|
| がく片長さ | がく片の長さ | `row.number("がく片長さ")` → `OptionalDouble` |
| がく片幅 | がく片の幅 | `row.number("がく片幅")` → `OptionalDouble` |
| 花弁長さ | 花弁の長さ | `row.number("花弁長さ")` → `OptionalDouble` |
| 花弁幅 | 花弁の幅 | `row.number("花弁幅")` → `OptionalDouble` |
| 種類 | 品種（3 種類が 50 件ずつ） | `row.text("種類")` → `String` |

特徴量の 4 列には合わせて 7 件の欠損値があります。Kotlin DataFrame は欠損値を含む列を `Double?` として読み込みましたが、Java 版では空欄を **空の `OptionalDouble`** として呼び出し側に見せます。

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### Python 版・Kotlin 版と数値が変わる理由

分け方の手順（シード付きでシャッフルし、テストデータの件数を切り上げる）は Python 版・Kotlin 版と同じです。ただし乱数生成器が違います。Java 版は標準ライブラリの `java.util.Random` と `Collections.shuffle` を使うので、NumPy とも Kotlin の `kotlin.random.Random` とも乱数列が異なり、どの行がテストデータに入るかは一致しません。件数は同じ 105 件と 45 件ですが、第 3 章以降の正解率などの数値は Java 版の実測値になります。

## 2.3 開発環境の準備

この章では依存を追加しません。CSV の読み込みも前処理も、第 1 章と同じ標準ライブラリ（`java.nio.file`・Stream API・コレクション）で書きます。

Kotlin 版は Kotlin DataFrame を使いましたが、Java 版ではデータフレームのライブラリを使わず、record とコレクション・Stream API でデータを表すと決めました。表の操作をライブラリに任せず自分で書くことで、欠損値の表し方や補完の前後の区別を、型の設計として考えられるからです。ライブラリの選定理由は [ADR 005](../../../adr/005-java-ml-libraries.md) を参照してください。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] BOM 付き CSV の列名に BOM が残らない
  - [ ] 空欄を欠損値（空の `OptionalDouble`）として読み込む
  - [ ] 行末の空欄も欠損値として読み込む
- [ ] 列ごとの欠損値の数を数える
- [ ] 列ごとの平均値を求める
- [ ] 欠損値を補完する
  - [ ] 列ごとに指定した値で補完する
  - [ ] 元のデータは変更しない
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合どおりの件数に分ける
  - [ ] すべての行を重複なくどちらかに入れる
  - [ ] 特徴量と正解ラベルの対応を保つ
  - [ ] 同じシードなら同じ分け方になる
  - [ ] シードが違えば違う分け方になる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

Kotlin 版の TODO リストに「行末の空欄も欠損値として読み込む」を加えています。自分で CSV を分割するので、ライブラリが面倒を見てくれていた落とし穴を自分で踏むことになるためです（2.5 節）。

## 2.5 自作の表で読み込む

### データの表し方を決める

データフレームのライブラリを使わない代わりに、次の 3 つの型を作ります。

| 型 | 持つもの | 役割 |
|----|---------|------|
| `Table` | 列名の並びと `Row` のリスト | 読み込んだ CSV 全体 |
| `Row` | セルの文字列（列名 → 文字列の `Map`） | 1 行分。`number` と `text` で読み出す |
| `Features` | 列名の並びと `double` の配列 | 補完が済んだ 1 行分の特徴量。欠損値を持てない |

`Row` はセルを **文字列のまま** 持ち、読み出すときに `number`（数値として読む。空欄なら空の `OptionalDouble`）と `text`（文字列として読む）を使い分けます。iris 専用の record（`IrisRow(double sepalLength, ...)`）を作る案もありましたが、第 7 章以降では列の違う CSV を何種類も読むので、列名で引く形のほうが章をまたいで使い回せます。正解ラベルのような文字列の列も同じ `Row` で持てます。

`Row` と `Features` を分けたのは、**補完する前と後を型で区別する** ためです。`Row` の数値は欠損しうるので `OptionalDouble` で返りますが、`Features` の値は `double` の配列で、欠損値を入れる場所がありません。補完する前の行をうっかりモデルに渡すと、コンパイルエラーになります。Kotlin 版では `Double?`（null を取りうる）と `Double` の区別がこれに当たり、Kotlin DataFrame では列の型が補完の前後で変わりました。

### テストファースト

読み込みのテストを書きます。第 1 章と同じく、`@TempDir` の一時ディレクトリに架空の値の CSV を作ります。

```java
// src/test/java/chapter02/TableTest.java
class TableTest {
  private static final String HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

  @TempDir Path directory;

  private Path writeCsv(String rows) throws IOException {
    return Files.writeString(directory.resolve("iris.csv"), HEADER + rows, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("BOM 付き CSV を読み込むと列名に BOM が残らない")
  void stripsBom() throws IOException {
    Table table = Table.load(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

    assertThat(table.columns()).containsExactly("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類");
  }

  @Test
  @DisplayName("空欄は欠損値として読み込む")
  void blankIsMissing() throws IOException {
    Table table = Table.load(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"));

    Row row = table.rows().getFirst();
    assertThat(row.number("がく片幅")).isEmpty();
    assertThat(row.number("がく片長さ")).hasValue(0.1);
    assertThat(row.text("種類")).isEqualTo("Iris-setosa");
  }
}
```

AssertJ は `OptionalDouble` にも対応していて、`isEmpty()`（値が無い）と `hasValue(0.1)`（値が 0.1）で確かめられます。`Table` と `Row` がまだ無いので、第 1 章と同じく「シンボルを見つけられません」のコンパイルエラーになります（Red）。

### Green: 明白な実装

第 1 章の `loadPeople` と同じ手順（BOM を取り除き、ヘッダー行で列名を決め、各行を分割する）なので、明白な実装で書きます。

```java
// src/main/java/chapter02/Row.java
/** CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。 */
public record Row(Map<String, String> cells) {
  public Row {
    cells = Map.copyOf(cells);
  }

  /** 数値の列を読む。空欄なら空の OptionalDouble を返す。 */
  public OptionalDouble number(String column) {
    String cell = text(column);
    return cell.isBlank() ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(cell));
  }

  /** 文字列の列を読む。 */
  public String text(String column) {
    String cell = cells.get(column);
    if (cell == null) {
      throw new IllegalArgumentException("列がありません: " + column);
    }
    return cell;
  }

  /** セルが空欄かどうか。 */
  public boolean isMissing(String column) {
    return text(column).isBlank();
  }
}
```

- `public Row { ... }` は record の **コンパクトコンストラクタ** です。引数の並びを書かずに、成分に代入する前の検査や変換を書けます。ここでは `Map.copyOf` で受け取った `Map` を変更できない写しに置き換えています。呼び出し側が後から元の `Map` を書き換えても、`Row` は影響を受けません（`TableTest` の「Row は受け取った Map を写して持ち、元の Map の変更の影響を受けない」で確かめています）
- `OptionalDouble` は、`double` の値があるかもしれないし無いかもしれないことを表す型です。`Optional<Double>` と違い、`double` を箱（`Double`）に包まずに持てます。Kotlin の `Double?` に当たりますが、`null` ではなく「空の `OptionalDouble`」という値で欠損を表すので、呼び出し側は値を取り出す前に有無を考えざるを得ません
- 無い列を読むと、列名を含むメッセージの例外にしました。第 1 章では `Map.get` の `null` がそのまま `NullPointerException` になり、どの列名だったか分かりませんでした。その反省をテスト「無い列を読み出すと列名を示すエラーになる」で約束しています

```java
// src/main/java/chapter02/Table.java
/** 列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。 */
public record Table(List<String> columns, List<Row> rows) {
  private static final String BOM = "\uFEFF";

  public Table {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
  }

  /** BOM 付きの UTF-8 の CSV を読み込む。 */
  public static Table load(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
    List<String> columns = List.of(stripBom(lines.getFirst()).split(","));
    List<Row> rows =
        lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> toRow(columns, line))
            .toList();
    return new Table(columns, rows);
  }

  private static Row toRow(List<String> columns, String line) {
    // 上限に -1 を渡すと、行末の空欄も空文字列として残る
    String[] values = line.split(",", -1);
    Map<String, String> cells = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      cells.put(columns.get(i), values[i]);
    }
    return new Row(cells);
  }

  private static String stripBom(String line) {
    return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
  }
}
```

`Table` も record にして、コンパクトコンストラクタで `List.copyOf` の写しを持ちます。Kotlin DataFrame の操作がすべて元のデータフレームを変更しなかったのと同じく、`Table` も一度作ったら変更できません。

```text
TableTest > BOM 付き CSV を読み込むと列名に BOM が残らない PASSED
TableTest > 空欄は欠損値として読み込む PASSED
```

### 行末の空欄の落とし穴

`toRow` の `line.split(",", -1)` の `-1` には理由があります。行の最後の列が空欄のテストを見てください。

```java
  @Test
  @DisplayName("行の最後の列が空欄でも欠損値として読み込む")
  void trailingBlankIsMissing() throws IOException {
    Table table = Table.load(writeCsv("0.1,0.2,0.3,,\n"));

    assertThat(table.rows().getFirst().number("花弁幅")).isEmpty();
    assertThat(table.rows().getFirst().text("種類")).isEmpty();
  }
```

`String.split(",")` は、末尾に続く空の文字列を **捨てます**。`"0.1,0.2,0.3,,"` は 5 列のつもりでも、`split(",")` では 3 つの要素しか返りません。上限の引数に負の数を渡すと、末尾の空の文字列も残ります。

この落とし穴には、テストより先に Error Prone が気づきます。`-1` を外してコンパイルすると、次のように止まりました。

```text
Table.java:36: 警告: [StringSplitter] String.split(String) has surprising behavior
    String[] values = line.split(",");
                                ^
    (see https://errorprone.info/bugpattern/StringSplitter)
  Did you mean 'List<String> values = Splitter.on(',').splitToList(line);'?
エラー: 警告が見つかり-Werrorが指定されました
```

提案されている `Splitter` は Guava（Google の Java のライブラリ）のクラスで、本シリーズでは依存に加えていません。標準ライブラリのまま「末尾の空欄も残す」という意図をはっきりさせるために、`split(",", -1)` とコメントで書きました。

確かめのために、この警告だけを抑えてテストを実行すると、次のように失敗します。

```text
TableTest > 行の最後の列が空欄でも欠損値として読み込む FAILED
    java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3
```

3 つしか要素の無い配列の 4 番目（`花弁幅`）を読もうとして失敗しています。Kotlin の `split` は末尾の空の文字列を捨てないので、Kotlin 版（Kotlin DataFrame が読み込む）ではこの問題は現れませんでした。

### 列ごとの欠損値の数

```java
  @Test
  @DisplayName("列ごとの欠損値の数を列の順に数える")
  void countsMissing() {
    Table table =
        new Table(
            List.of("がく片長さ", "がく片幅", "種類"),
            List.of(
                new Row(Map.of("がく片長さ", "0.1", "がく片幅", "0.2", "種類", "Iris-setosa")),
                new Row(Map.of("がく片長さ", "", "がく片幅", "0.3", "種類", "Iris-setosa")),
                new Row(Map.of("がく片長さ", "", "がく片幅", "", "種類", "Iris-virginica"))));

    assertThat(table.countMissing())
        .containsExactly(Map.entry("がく片長さ", 2), Map.entry("がく片幅", 1), Map.entry("種類", 0));
  }
```

```java
  /** 列ごとの欠損値の数を、列の順に並べて返す。 */
  public Map<String, Integer> countMissing() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String column : columns) {
      counts.put(column, (int) rows.stream().filter(row -> row.isMissing(column)).count());
    }
    return counts;
  }
```

`containsExactly` は `Map` の **順序** まで確かめます。`HashMap` は要素の順序を保証しないので、表示の順を列の順にそろえるために、入れた順を保つ `LinkedHashMap` を使います。Kotlin の `associate` が返す `Map` は入れた順を保つので、Kotlin 版ではこの区別を意識しませんでした。

| 観点 | 第 1 章の自作の読み込み | Kotlin DataFrame | Java 版の `Table` |
|------|----------------------|------------------|-----------------|
| BOM | 自分で取り除く | 取り除く | 自分で取り除く |
| 値の型 | 読み込むときに `int` に変換 | 列ごとに推定する | 文字列のまま持ち、読むときに `number`・`text` を選ぶ |
| 空欄 | （無い前提） | `null` | 空の `OptionalDouble` |
| 行末の空欄 | （無い前提） | 空欄として読む | `split(",", -1)` で残す |

## 2.6 平均値で欠損値を補完する

### 平均値を求める

```java
// src/test/java/chapter02/PreprocessingTest.java
class PreprocessingTest {
  private static Row row(String sepalLength, String sepalWidth) {
    return new Row(Map.of("がく片長さ", sepalLength, "がく片幅", sepalWidth));
  }

  @Nested
  class ColumnMeans {
    @Test
    @DisplayName("欠損値を除いて列ごとの平均値を求める")
    void ignoresMissing() {
      var rows = List.of(row("0.1", "0.2"), row("", "0.4"), row("0.3", "0.9"));

      Map<String, Double> means = Preprocessing.columnMeans(rows, List.of("がく片長さ", "がく片幅"));

      assertThat(means.get("がく片長さ")).isCloseTo(0.2, within(1e-12));
      assertThat(means.get("がく片幅")).isCloseTo(0.5, within(1e-12));
    }
  }
}
```

```java
  /** 欠損値を除いて、列ごとの平均値を求める。 */
  public static Map<String, Double> columnMeans(List<Row> rows, List<String> columns) {
    Map<String, Double> means = new LinkedHashMap<>();
    for (String column : columns) {
      OptionalDouble mean =
          rows.stream()
              .map(row -> row.number(column))
              .filter(OptionalDouble::isPresent)
              .mapToDouble(OptionalDouble::getAsDouble)
              .average();
      means.put(column, mean.orElseThrow());
    }
    return means;
  }
```

- `filter(OptionalDouble::isPresent)` で欠損値を除き、`mapToDouble(OptionalDouble::getAsDouble)` で値を取り出します。Kotlin 版の `filterIsInstance<Number>()` に当たります
- `average()` の戻り値も `OptionalDouble` です。値が 1 つも無い列の平均は求められないので、空になります。ここでは `orElseThrow()` で「平均を求められない列がある」ことを例外にしています

### 補完して特徴量にする

補完に使う値は引数で受け取ります。補完の結果は `Row` ではなく `Features` です。

```java
  @Nested
  class FillMissing {
    @Test
    @DisplayName("欠損値を列ごとに指定した値で補完して特徴量にする")
    void fillsWithGivenValues() {
      var rows = List.of(row("0.1", ""), row("", "0.4"));
      var columns = List.of("がく片長さ", "がく片幅");

      List<Features> filled =
          Preprocessing.fillMissing(rows, columns, Map.of("がく片長さ", 0.2, "がく片幅", 0.5));

      assertThat(filled)
          .containsExactly(
              new Features(columns, new double[] {0.1, 0.5}),
              new Features(columns, new double[] {0.2, 0.4}));
    }

    @Test
    @DisplayName("元の行は変更しない")
    void keepsOriginalRows() {
      var rows = List.of(row("", "0.2"));

      Preprocessing.fillMissing(rows, List.of("がく片長さ", "がく片幅"), Map.of("がく片長さ", 0.2));

      assertThat(rows.getFirst().isMissing("がく片長さ")).isTrue();
    }
  }
```

```java
  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  public static List<Features> fillMissing(
      List<Row> rows, List<String> columns, Map<String, Double> values) {
    return rows.stream()
        .map(
            row ->
                new Features(
                    columns,
                    columns.stream()
                        .mapToDouble(
                            column -> row.number(column).orElseGet(() -> fillValue(values, column)))
                        .toArray()))
        .toList();
  }

  private static double fillValue(Map<String, Double> values, String column) {
    Double value = values.get(column);
    if (value == null) {
      throw new IllegalArgumentException("補完する値がありません: " + column);
    }
    return value;
  }
```

`row.number(column).orElseGet(...)` は、値があればその値を、空なら補完する値を返します。Kotlin DataFrame の `fillNulls(列).with { 値 }` に当たる処理を、1 つのセルごとに書いています。`Row` は変更できないので、「元の行は変更しない」はこの設計から自然に満たされます。

### Features を record にしなかった理由

`Features` は、最初は `Row` や `Table` と同じく record で書こうとしました。

```java
public record Features(List<String> columns, double[] values) {
```

これをコンパイルすると、Error Prone が止めます。

```text
Features.java:6: 警告: [ArrayRecordComponent] Record components should not be arrays.
public record Features(List<String> columns, double[] values) {
                                                      ^
    (see https://errorprone.info/bugpattern/ArrayRecordComponent)
エラー: 警告が見つかり-Werrorが指定されました
```

record が自動で作る `equals` は、成分を `equals` で比べます。配列の `equals` は **中身ではなく同じ配列かどうか** を比べるので、同じ値を持つ 2 つの `Features` が等しくなりません。上の `fillsWithGivenValues` のように、期待値を `new Features(columns, new double[] {0.1, 0.5})` と書いて比べるテストが成り立たなくなります。さらに、record は受け取った配列をそのまま持つので、呼び出し側が後から配列を書き換えると `Features` の値も変わってしまいます。

`double` の配列にこだわるのは、第 3 章で Tribuo の `ArrayExample`（特徴量名の配列と `double` の配列を受け取る）にそのまま渡せるからです。そこで record をやめて、配列を写して持ち、`equals`・`hashCode`・`toString` を自分で書くクラスにしました。

```java
// src/main/java/chapter02/Features.java
public final class Features {
  private final List<String> columns;
  private final double[] values;

  public Features(List<String> columns, double[] values) {
    if (columns.size() != values.length) {
      throw new IllegalArgumentException("列名と値の数が違います");
    }
    this.columns = List.copyOf(columns);
    this.values = values.clone();
  }

  /** 値の配列の写しを返す。返した配列を変えても、この特徴量は変わらない。 */
  public double[] values() {
    return values.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Features that
        && columns.equals(that.columns)
        && Arrays.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return 31 * columns.hashCode() + Arrays.hashCode(values);
  }
```

- 受け取るときも返すときも `clone()` で写しを作るので、外から配列を書き換えられません
- `equals` は `Arrays.equals` で配列の中身を比べます。`other instanceof Features that` は **パターンマッチの `instanceof`**（Java 16 以降）で、型の検査と変数 `that` への代入を 1 度に書けます
- `equals` を書いたら `hashCode` も中身で計算します。等しいオブジェクトは同じハッシュ値を持つ、という約束を守るためです

record なら 1 行で済んだものが数十行になりました。その代わり、「写しを持つ」「中身で比べる」という約束をテストで固定しています。

```java
  @Nested
  class FeaturesRecord {
    @Test
    @DisplayName("値の配列を写して持ち、渡した配列を後から変えても影響を受けない")
    void copiesValues() {
      double[] values = {0.1, 0.2};
      Features features = new Features(List.of("a", "b"), values);

      values[0] = 9.9;

      assertThat(features.value("a")).isEqualTo(0.1);
    }

    @Test
    @DisplayName("列名と値が同じなら等しい")
    void equalByValue() {
      assertThat(new Features(List.of("a"), new double[] {0.1}))
          .isEqualTo(new Features(List.of("a"), new double[] {0.1}))
          .hasSameHashCodeAs(new Features(List.of("a"), new double[] {0.1}));
    }
  }
```

## 2.7 特徴量と正解ラベルに分ける

```java
  @Nested
  class SplitFeaturesAndTarget {
    @Test
    @DisplayName("特徴量の列と正解ラベルの列に分ける")
    void splitsColumns() {
      Table table =
          new Table(
              List.of("がく片長さ", "花弁幅", "種類"),
              List.of(
                  new Row(Map.of("がく片長さ", "0.1", "花弁幅", "0.4", "種類", "Iris-setosa")),
                  new Row(Map.of("がく片長さ", "0.5", "花弁幅", "0.8", "種類", "Iris-virginica"))));

      FeaturesAndTarget split = Preprocessing.splitFeaturesAndTarget(table, "種類");

      assertThat(split.columns()).containsExactly("がく片長さ", "花弁幅");
      assertThat(split.rows()).isEqualTo(table.rows());
      assertThat(split.target()).containsExactly("Iris-setosa", "Iris-virginica");
    }
  }
```

```java
// src/main/java/chapter02/FeaturesAndTarget.java
/** 特徴量の列名・補完する前の行・正解ラベル。同じ位置の行と正解ラベルが同じ事例を表す。 */
public record FeaturesAndTarget(List<String> columns, List<Row> rows, List<String> target) {
  public FeaturesAndTarget {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
    target = List.copyOf(target);
  }
}
```

```java
  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  public static FeaturesAndTarget splitFeaturesAndTarget(Table table, String target) {
    List<String> columns = table.columns().stream().filter(c -> !c.equals(target)).toList();
    List<String> labels = table.rows().stream().map(row -> row.text(target)).toList();
    return new FeaturesAndTarget(columns, table.rows(), labels);
  }
```

Kotlin 版は `df.remove(target)` で正解ラベルの列を除いたデータフレームを作り、`Pair` で返しました。Java 版の `Row` は列名で引く形なので、行から列を消す必要はありません。「どの列を特徴量として読むか」を列名のリスト `columns` で持ち、行はそのまま渡します。第 1 章と同じく `Pair` の代わりに名前付きの record を使っています。

## 2.8 訓練データとテストデータに分ける

### 分割結果を表す型と仮実装

```java
// src/main/java/chapter02/TrainTestSplit.java
/** 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。 */
public record TrainTestSplit<X, T>(List<X> xTrain, List<X> xTest, List<T> tTrain, List<T> tTest) {
```

Kotlin 版は特徴量をデータフレームに固定し、正解ラベルだけを型引数 `T` にしました。Java 版は特徴量の型も型引数 `X` にしています。2.9 節で見るように、補完する前の `Row` のまま分けてから、訓練データの平均値で補完して `Features` にするためです。

0 から始まる番号を振ったデータで、件数だけを確かめます。

```java
  @Nested
  class SplitTrainTest {
    private final List<Integer> x = IntStream.range(0, 10).boxed().toList();
    private final List<String> t = x.stream().map(i -> "label" + i).toList();

    @Test
    @DisplayName("テストデータの割合どおりの件数に分ける")
    void splitsByRatio() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      assertThat(List.of(split.xTrain().size(), split.xTest().size())).containsExactly(7, 3);
      assertThat(List.of(split.tTrain().size(), split.tTest().size())).containsExactly(7, 3);
    }
  }
```

`splitTrainTest` を **ジェネリックメソッド** にしたので、テストでは特徴量に `Integer` の番号をそのまま使えます。Kotlin 版はテスト用に 1 列のデータフレームを作りましたが、Java 版ではリストだけで済みます。

仮実装では、先頭の 7 件を訓練データにします。

```java
  public static <X, T> TrainTestSplit<X, T> splitTrainTest(
      List<X> x, List<T> t, double testSize, long seed) {
    List<Integer> positions = new ArrayList<>(IntStream.range(0, x.size()).boxed().toList());
    int nTrain = 7;
    List<Integer> train = positions.subList(0, nTrain);
    List<Integer> test = positions.subList(nTrain, positions.size());
    return new TrainTestSplit<>(pick(x, train), pick(x, test), pick(t, train), pick(t, test));
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }
```

`pick` は、行の位置のリストで要素を取り出します。Kotlin の `slice` に当たるメソッドは Java の `List` に無いので、自分で書きます。

### 三角測量: 件数を一般化する

```java
    @Test
    @DisplayName("件数が変わってもテストデータの割合どおりに分ける")
    void splitsOtherSizes() {
      var twenty = IntStream.range(0, 20).boxed().toList();

      var split = Preprocessing.splitTrainTest(twenty, twenty, 0.25, 0);

      assertThat(List.of(split.xTrain().size(), split.xTest().size())).containsExactly(15, 5);
    }
```

```text
PreprocessingTest > SplitTrainTest > 件数が変わってもテストデータの割合どおりに分ける FAILED
    org.opentest4j.AssertionFailedError: 
    Expecting actual:
      [7, 13]
    to contain exactly (and in same order):
      [15, 5]
    but some elements were not found:
      [15, 5]
    and others were not expected:
      [7, 13]
```

2 つ目の例で、7 がベタ書きの値だったことが露わになりました。テストデータの件数を切り上げて求めます。

```java
    int nTrain = x.size() - (int) Math.ceil(x.size() * testSize);
```

`Math.ceil` の戻り値は `double` なので、`(int)` で整数に変換してから引きます。

### 三角測量: 並び順に頼らない分け方にする

Kotlin 版と同じく、分け方の性質を表すテストを加えます。

```java
    @Test
    @DisplayName("すべての行を重複なく訓練データとテストデータのどちらかに入れる")
    void coversAllRowsOnce() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      Set<Integer> train = new HashSet<>(split.xTrain());
      Set<Integer> test = new HashSet<>(split.xTest());
      assertThat(train).doesNotContainAnyElementsOf(test);
      Set<Integer> all = new HashSet<>(train);
      all.addAll(test);
      assertThat(all).containsExactlyInAnyOrderElementsOf(x);
    }

    @Test
    @DisplayName("特徴量と正解ラベルの対応を保ったまま分ける")
    void keepsPairs() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      assertThat(split.tTrain()).isEqualTo(split.xTrain().stream().map(i -> "label" + i).toList());
      assertThat(split.tTest()).isEqualTo(split.xTest().stream().map(i -> "label" + i).toList());
    }

    @Test
    @DisplayName("同じシードなら同じ分け方になる")
    void sameSeedSameSplit() {
      assertThat(Preprocessing.splitTrainTest(x, t, 0.3, 42).tTest())
          .isEqualTo(Preprocessing.splitTrainTest(x, t, 0.3, 42).tTest());
    }

    @Test
    @DisplayName("シードが違えば違う分け方になる")
    void differentSeedDifferentSplit() {
      assertThat(Preprocessing.splitTrainTest(x, t, 0.3, 0).tTest())
          .isNotEqualTo(Preprocessing.splitTrainTest(x, t, 0.3, 1).tTest());
    }
```

Java の標準ライブラリには集合の和・積の演算子が無いので、`HashSet` を作って AssertJ の `doesNotContainAnyElementsOf`（共通の要素が無い）と `containsExactlyInAnyOrderElementsOf`（順序を問わず過不足なく含む）で確かめます。

```text
PreprocessingTest > SplitTrainTest > 特徴量と正解ラベルの対応を保ったまま分ける PASSED
PreprocessingTest > SplitTrainTest > 件数が変わってもテストデータの割合どおりに分ける PASSED
PreprocessingTest > SplitTrainTest > すべての行を重複なく訓練データとテストデータのどちらかに入れる PASSED
PreprocessingTest > SplitTrainTest > テストデータの割合どおりの件数に分ける PASSED
PreprocessingTest > SplitTrainTest > シードが違えば違う分け方になる FAILED
    java.lang.AssertionError: 
    Expecting actual:
      ["label7", "label8", "label9"]
    not to be equal to:
      ["label7", "label8", "label9"]
PreprocessingTest > SplitTrainTest > 同じシードなら同じ分け方になる PASSED
PreprocessingTest > SplitTrainTest > 数値の正解ラベルも特徴量との対応を保ったまま分ける PASSED
7 tests completed, 1 failed
```

Python 版・Kotlin 版と同じく、先頭から順に分けるだけでは「シードが違えば違う分け方になる」だけが失敗します。どのシードでも最後の 3 件がテストデータになるためです。

### Green: シード付きの乱数で並べ替える

```java
    List<Integer> positions = new ArrayList<>(IntStream.range(0, x.size()).boxed().toList());
    Collections.shuffle(positions, new Random(seed));
    int nTrain = x.size() - (int) Math.ceil(x.size() * testSize);
```

- `java.util.Random(seed)` は、シードが同じなら同じ乱数列を返す生成器です。`Collections.shuffle` にこれを渡すと、再現性のあるシャッフルになります
- `Collections.shuffle` はリストをその場で並べ替えます。`toList()` が返すリストは変更できないので、`new ArrayList<>(...)` で変更できるリストに写してから並べ替えます。Kotlin の `shuffled` は新しいリストを返しましたが、Java の `shuffle` は引数のリストそのものを書き換えます
- シードの型は `long` です。Kotlin 版の `seed: Int` と違いますが、`java.util.Random` のコンストラクタが `long` を受け取るのに合わせました

`java.util.Random` の乱数列は、NumPy とも `kotlin.random.Random` とも違います。同じシード 0 でも、テストデータに入る行は Python 版・Kotlin 版と一致しません。

第 7 章からは、価格のような数値の正解ラベルも同じメソッドで分けます。Kotlin 版は第 7 章に入る前に型引数を足しましたが、Java 版は最初から `<X, T>` にしたので、数値の正解ラベルのテストも実装を変えずに通ります。

```java
    @Test
    @DisplayName("数値の正解ラベルも特徴量との対応を保ったまま分ける")
    void numericLabels() {
      List<Double> numeric = x.stream().map(i -> i * 0.5).toList();

      var split = Preprocessing.splitTrainTest(x, numeric, 0.3, 0);

      assertThat(split.tTest()).isEqualTo(split.xTest().stream().map(i -> i * 0.5).toList());
    }
```

## 2.9 前処理をまとめる

```java
  @Nested
  class PrepareIris {
    @TempDir Path directory;

    @Test
    @DisplayName("訓練データとテストデータのどちらにも欠損値が残らない")
    void noMissingAfterPreparation() throws IOException {
      Path csvFile =
          Files.writeString(
              directory.resolve("iris.csv"),
              """
              \uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類
              0.1,,0.3,0.4,Iris-setosa
              0.2,0.3,,0.5,Iris-setosa
              ,0.4,0.5,0.6,Iris-virginica
              0.4,0.5,0.6,,Iris-virginica
              """,
              StandardCharsets.UTF_8);

      TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, 0.5, 0);

      assertThat(split.xTrain()).hasSize(2);
      assertThat(split.xTest()).hasSize(2);
      assertThat(split.xTrain()).allSatisfy(f -> assertThat(f.values()).doesNotContain(Double.NaN));
      assertThat(split.xTrain().getFirst().columns())
          .containsExactly("がく片長さ", "がく片幅", "花弁長さ", "花弁幅");
    }
  }
```

`"""` で囲んだ **テキストブロック**（Java 15 以降）は、改行を含む文字列を見た目どおりに書けます。すべての行に共通する先頭の空白は取り除かれます。テキストブロックの中でも `\uFEFF` のようなエスケープは使えます。Kotlin 版は `+` で連結していましたが、Java 版では Error Prone が連結をテキストブロックに書き換えるよう促します（2.10 節）。

```java
  /** 正解ラベルの列 */
  public static final String TARGET = "種類";

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  public static TrainTestSplit<Features, String> prepareIris(
      Path csvFile, double testSize, long seed) throws IOException {
    FeaturesAndTarget data = splitFeaturesAndTarget(Table.load(csvFile), TARGET);
    TrainTestSplit<Row, String> split = splitTrainTest(data.rows(), data.target(), testSize, seed);
    Map<String, Double> means = columnMeans(split.xTrain(), data.columns());
    return new TrainTestSplit<>(
        fillMissing(split.xTrain(), data.columns(), means),
        fillMissing(split.xTest(), data.columns(), means),
        split.tTrain(),
        split.tTest());
  }
```

型の流れを追うと、補完の前後の区別がはっきり見えます。

1. `splitTrainTest` は `Row`（欠損しうる）のまま分けて `TrainTestSplit<Row, String>` を返す
2. `columnMeans` は訓練データの `Row` だけから平均値を求める
3. `fillMissing` がその平均値で訓練データとテストデータの両方を補完し、`TrainTestSplit<Features, String>`（欠損値を持てない）にする

戻り値の型が `TrainTestSplit<Features, String>` なので、`prepareIris` を通ったデータに欠損値が残っていないことは型が保証します。Kotlin 版の `split.copy(xTrain = ..., xTest = ...)` に当たる書き方は record に無いので、新しい `TrainTestSplit` を作っています。

`prepareIris` と実データのテスト・`Main` は、関数を組み合わせるだけなので、実装を書いてから実データで出力を確かめ、テストで固定しました。これらは Red を経ていません。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

```java
// src/test/java/chapter02/IrisDataTest.java
class IrisDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("iris.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("実データの列ごとの欠損値の数を数える")
  void countsMissing() throws IOException {
    assertThat(Table.load(csvFile).countMissing())
        .containsExactly(
            Map.entry("がく片長さ", 2),
            Map.entry("がく片幅", 1),
            Map.entry("花弁長さ", 2),
            Map.entry("花弁幅", 2),
            Map.entry("種類", 0));
  }

  @Test
  @DisplayName("実データを 105 件と 45 件に分けて欠損値を補完する")
  void splitsAndFills() throws IOException {
    TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, 0.3, 0);

    assertThat(split.xTrain()).hasSize(105);
    assertThat(split.xTest()).hasSize(45);
    assertThat(split.tTrain()).hasSize(105);
    assertThat(split.tTest()).hasSize(45);
  }

  @Test
  @DisplayName("実行すると前処理の結果を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 150
            欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
            訓練データ: 105 件, テストデータ: 45 件
            特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
            """);
  }
}
```

Kotlin 版の実データのテストは、補完した後の欠損値の数が 0 であることも確かめていました。Java 版の `Features` は欠損値を持てないので、その検査は型が代わりに担います。数えようにも、`Features` には欠損値を数える方法がありません。

表示のテストの期待値は、最初は Kotlin 版と同じく `+` で連結して書いていました。

```java
            "データ件数: 150\n"
                + "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n"
                + "訓練データ: 105 件, テストデータ: 45 件\n"
                + "特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅\n");
```

これを Error Prone が指摘します。

```text
IrisDataTest.java:54: 警告: [StringConcatToTextBlock] This string literal can be written more clearly as a text block
            "データ件数: 150\n"
            ^
    (see https://errorprone.info/bugpattern/StringConcatToTextBlock)
  Did you mean '"""'?
エラー: 警告が見つかり-Werrorが指定されました
```

テストのコードにも Error Prone が効いているので、指摘に従ってテキストブロックに書き換えました。期待する表示が、実際の表示と同じ見た目で読めるようになります。

### 結果を表示する

```java
// src/main/java/chapter02/Main.java
public final class Main {
  private static final double TEST_SIZE = 0.3;
  private static final long SEED = 0;

  private Main() {}

  public static void main(String[] args) throws IOException {
    Path csvFile = DataDir.dataDir().resolve("iris.csv");
    Table table = Table.load(csvFile);
    TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, TEST_SIZE, SEED);
    System.out.println("データ件数: " + table.rows().size());
    System.out.println("欠損値の数: " + formatCounts(table.countMissing()));
    System.out.println(
        "訓練データ: " + split.xTrain().size() + " 件, テストデータ: " + split.xTest().size() + " 件");
    System.out.println("特徴量: " + String.join(", ", split.xTrain().getFirst().columns()));
  }

  private static String formatCounts(Map<String, Integer> counts) {
    return counts.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining(", "));
  }
}
```

Kotlin 版の最後の行は「補完後の欠損値の数: 訓練データ 0, テストデータ 0」でした。前述のとおり `Features` は欠損値を持てないので、Java 版では代わりに、補完した特徴量の列を表示しています。

```bash
./gradlew runChapter -Pchapter=02
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

件数と欠損値の数は乱数に関係しないので、Python 版・Kotlin 版と同じです。

```bash
./gradlew test --tests "chapter02.*"
```

第 2 章のテストは 24 件すべて通ります。データが無い環境では、実データのテスト 3 件がスキップされます。

```text
IrisDataTest > 実行すると前処理の結果を表示する SKIPPED
IrisDataTest > 実データの列ごとの欠損値の数を数える SKIPPED
IrisDataTest > 実データを 105 件と 45 件に分けて欠損値を補完する SKIPPED
```

**TODO リスト**:

- [x] iris.csv を読み込む
  - [x] BOM 付き CSV の列名に BOM が残らない
  - [x] 空欄を欠損値（空の `OptionalDouble`）として読み込む
  - [x] 行末の空欄も欠損値として読み込む
- [x] 列ごとの欠損値の数を数える
- [x] 列ごとの平均値を求める
- [x] 欠損値を補完する
  - [x] 列ごとに指定した値で補完する
  - [x] 元のデータは変更しない
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
  - [x] テストデータの割合どおりの件数に分ける
  - [x] すべての行を重複なくどちらかに入れる
  - [x] 特徴量と正解ラベルの対応を保つ
  - [x] 同じシードなら同じ分け方になる
  - [x] シードが違えば違う分け方になる
- [x] 訓練データの平均値で両方を補完する
- [x] 実データで前処理の結果を表示する

## 2.11 リファクタリング

この章では、Error Prone の指摘に従って直したことが 3 つありました。

| 指摘 | 最初に書いたもの | 直したもの |
|------|---------------|-----------|
| `ArrayRecordComponent` | `record Features(..., double[] values)` | 配列を写して持ち、中身で比べるクラス |
| `StringSplitter` | `line.split(",")` | `line.split(",", -1)` と理由のコメント |
| `StringConcatToTextBlock` | テストの期待値を `+` で連結 | テキストブロック |

どれも、コンパイルが止まったので気づけたものです。特に `ArrayRecordComponent` と `StringSplitter` は、テストでも見つけられますが、テストを書く前にコンパイラが教えてくれました。Kotlin 版で ktlint が整形の観点から書き方を直させたのに対して、Error Prone は「その書き方はバグになりやすい」という観点で止めます。

```bash
./gradlew spotlessApply
./gradlew check
```

```text
BUILD SUCCESSFUL
```

## 2.12 可視化について

Java 版には Notebook の節を設けません。欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

<details>
<summary>この章の完成コード（src/main/java/chapter02/Preprocessing.java）</summary>

```java
package chapter02;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.IntStream;

/** アヤメのデータの前処理。 */
public final class Preprocessing {
  /** 正解ラベルの列 */
  public static final String TARGET = "種類";

  private Preprocessing() {}

  /** 欠損値を除いて、列ごとの平均値を求める。 */
  public static Map<String, Double> columnMeans(List<Row> rows, List<String> columns) {
    Map<String, Double> means = new LinkedHashMap<>();
    for (String column : columns) {
      OptionalDouble mean =
          rows.stream()
              .map(row -> row.number(column))
              .filter(OptionalDouble::isPresent)
              .mapToDouble(OptionalDouble::getAsDouble)
              .average();
      means.put(column, mean.orElseThrow());
    }
    return means;
  }

  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  public static List<Features> fillMissing(
      List<Row> rows, List<String> columns, Map<String, Double> values) {
    return rows.stream()
        .map(
            row ->
                new Features(
                    columns,
                    columns.stream()
                        .mapToDouble(
                            column -> row.number(column).orElseGet(() -> fillValue(values, column)))
                        .toArray()))
        .toList();
  }

  private static double fillValue(Map<String, Double> values, String column) {
    Double value = values.get(column);
    if (value == null) {
      throw new IllegalArgumentException("補完する値がありません: " + column);
    }
    return value;
  }

  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  public static FeaturesAndTarget splitFeaturesAndTarget(Table table, String target) {
    List<String> columns = table.columns().stream().filter(c -> !c.equals(target)).toList();
    List<String> labels = table.rows().stream().map(row -> row.text(target)).toList();
    return new FeaturesAndTarget(columns, table.rows(), labels);
  }

  /** シード付きの乱数で行を並べ替え、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。 */
  public static <X, T> TrainTestSplit<X, T> splitTrainTest(
      List<X> x, List<T> t, double testSize, long seed) {
    if (x.size() != t.size()) {
      throw new IllegalArgumentException("特徴量と正解ラベルの件数が違います");
    }
    List<Integer> positions = new ArrayList<>(IntStream.range(0, x.size()).boxed().toList());
    Collections.shuffle(positions, new Random(seed));
    int nTrain = x.size() - (int) Math.ceil(x.size() * testSize);
    List<Integer> train = positions.subList(0, nTrain);
    List<Integer> test = positions.subList(nTrain, positions.size());
    return new TrainTestSplit<>(pick(x, train), pick(x, test), pick(t, train), pick(t, test));
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  public static TrainTestSplit<Features, String> prepareIris(
      Path csvFile, double testSize, long seed) throws IOException {
    FeaturesAndTarget data = splitFeaturesAndTarget(Table.load(csvFile), TARGET);
    TrainTestSplit<Row, String> split = splitTrainTest(data.rows(), data.target(), testSize, seed);
    Map<String, Double> means = columnMeans(split.xTrain(), data.columns());
    return new TrainTestSplit<>(
        fillMissing(split.xTrain(), data.columns(), means),
        fillMissing(split.xTest(), data.columns(), means),
        split.tTrain(),
        split.tTest());
  }
}
```

</details>

<details>
<summary>この章の完成コード（src/main/java/chapter02/Features.java）</summary>

```java
package chapter02;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。
 *
 * <p>値は double の配列で持つ。record の成分に配列を使うと既定の equals が配列の中身ではなく参照を比べる（Error Prone の
 * ArrayRecordComponent）ので、record ではなくクラスにして、配列を写して持ち、equals・hashCode・toString を中身で比べる。
 */
public final class Features {
  private final List<String> columns;
  private final double[] values;

  public Features(List<String> columns, double[] values) {
    if (columns.size() != values.length) {
      throw new IllegalArgumentException("列名と値の数が違います");
    }
    this.columns = List.copyOf(columns);
    this.values = values.clone();
  }

  /** 列名の並び。 */
  public List<String> columns() {
    return columns;
  }

  /** 値の配列の写しを返す。返した配列を変えても、この特徴量は変わらない。 */
  public double[] values() {
    return values.clone();
  }

  /** 列名で値を読む。 */
  public double value(String column) {
    int index = columns.indexOf(column);
    if (index < 0) {
      throw new IllegalArgumentException("列がありません: " + column);
    }
    return values[index];
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Features that
        && columns.equals(that.columns)
        && Arrays.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return 31 * columns.hashCode() + Arrays.hashCode(values);
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder("Features[");
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        text.append(", ");
      }
      text.append(columns.get(i)).append('=').append(String.format(Locale.ROOT, "%s", values[i]));
    }
    return text.append(']').toString();
  }
}
```

</details>

`Table.java`・`Row.java`・`FeaturesAndTarget.java`・`TrainTestSplit.java` は、本文に載せたものが完成版です。

## 2.13 まとめ

この章では、データフレームのライブラリを使わずに、欠損値を含むデータを前処理し、訓練データとテストデータに分けました。

1. **小さな表を自作する** — セルの文字列を持つ `Row` と、列名で引く `number`・`text` で、章をまたいで使い回せる表にした
2. **欠損値を値で表す** — 空欄は `null` ではなく空の `OptionalDouble` にし、読む側に有無を考えさせた
3. **補完の前後を型で分ける** — 欠損しうる `Row` と、欠損値を持てない `Features` を分け、`prepareIris` の戻り値の型で「欠損値が残っていない」ことを保証した
4. **record の限界** — 配列を持つ record は中身で比べられないので、`Features` はクラスにして写しと `equals` を自分で書いた
5. **三角測量と再現性** — 件数と分け方の性質のテストを重ね、`java.util.Random(seed)` と `Collections.shuffle` による再現性のあるシャッフルに一般化した
6. **Error Prone が落とし穴を先に教える** — 配列の record、`split` の末尾の空欄、文字列の連結を、テストより先にコンパイラが指摘した

次の章では、sealed interface と record で決定木を自作し、JVM の機械学習ライブラリ Tribuo の決定木と結果を突き合わせます。
