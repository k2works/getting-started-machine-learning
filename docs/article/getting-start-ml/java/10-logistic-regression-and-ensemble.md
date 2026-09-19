---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Java の TDD で自作し、interface Classifier とアダプターで共通化して Tribuo と正解率を比べる。"
tags: [article,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T16:00:40Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`interface Classifier` でモデルに共通する操作（`fit` と `predict`）を定義し、どのモデルも同じメソッドで評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Kotlin 版の第 10 章](../kotlin/10-logistic-regression-and-ensemble.md) と対比しながら Java の書き方を見ていきます。注目してほしいのは次の 3 点です。

- Java の `interface` も Kotlin と同じく **実装を宣言したクラスだけ** を受け入れます。第 3 章の `DecisionTree` を変更せずに共通のインターフェースに合わせるため、アダプターを書きます
- Kotlin 版はデータフレームの列を名前で取り出しましたが、Java 版は第 2 章の `Features`（列名と `double` の配列）のリストでデータを持ちます。ロジスティック回帰は `double[]` と添字で、ランダムフォレストの列の選択は `Features` を組み直して書きます
- 第 3 章の `Tree` は sealed interface なので、特徴量の重要度も `switch` のパターンマッチで木をたどります

ライブラリとの突き合わせには、Tribuo の `LogisticRegressionTrainer` と `RandomForestTrainer` を使います。最適化の方法や乱数の使い方が自作と違うので、予測の完全一致は求めず、正解率を比べます（[ADR 005](../../../adr/005-java-ml-libraries.md)）。Kotlin 版で見つけた Tribuo の癖（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）が Java 版でも同じように現れるかも確かめます。

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `Preprocessing.prepareIris` で前処理します。乱数に `java.util.Random` を使うので、訓練データとテストデータに入る行は Kotlin 版と違い、正解率も Kotlin 版とは一致しません（第 2 章）。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じメソッドで評価する
  - [ ] 第 3 章の決定木を変更せずに共通のインターフェースに合わせる
  - [ ] Tribuo のトレーナーも同じメソッドで評価する
- [ ] 実データで Tribuo と正解率を突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。

### 仮実装

Kotlin 版と同じく、**1 サンプル分のスコア**（`double[]`）を受け取る関数にします。Java にはトップレベルの関数が無いので、`LogisticRegression` クラスの static メソッドにします。テストは第 3 章と同じく、`@Nested` の内部クラスで観点ごとにまとめます。

```java
// src/test/java/chapter10/LogisticRegressionTest.java
package chapter10;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LogisticRegressionTest {
  @Nested
  class Softmax {
    @Test
    @DisplayName("値がすべて同じなら確率は均等になる")
    void uniform() {
      assertThat(LogisticRegression.softmax(new double[] {0.0, 0.0, 0.0, 0.0}))
          .containsExactly(0.25, 0.25, 0.25, 0.25);
    }
  }
}
```

Kotlin 版は `DoubleArray` を `toList()` でリストにしてから比べました。AssertJ の `assertThat(double[])` は配列の中身を比べる検証（`containsExactly`）を持っているので、Java 版では変換が要りません。

```text
> Task :compileTestJava
.../src/test/java/chapter10/LogisticRegressionTest.java:15: エラー: シンボルを見つけられません
      assertThat(LogisticRegression.softmax(new double[] {0.0, 0.0, 0.0, 0.0}))
                 ^
  シンボル:   変数 LogisticRegression
  場所: クラス LogisticRegressionTest.Softmax
エラー1個

> Task :compileTestJava FAILED
```

均等な確率を返す仮実装で Green にします。

```java
// src/main/java/chapter10/LogisticRegression.java
package chapter10;

import java.util.Arrays;

/** ソフトマックスと勾配降下法によるロジスティック回帰。 */
public final class LogisticRegression {
  private LogisticRegression() {}

  /** スコアを、合計が 1 になる確率に変換する。 */
  public static double[] softmax(double[] z) {
    double[] probabilities = new double[z.length];
    Arrays.fill(probabilities, 1.0 / z.length);
    return probabilities;
  }
}
```

この時点ではまだ static メソッドしか無いので、第 2 章の `Preprocessing` などと同じく、コンストラクターを private にしてインスタンスを作れないようにしています。

### 三角測量

値の差が `ln 2` なら、確率の比は 2 倍になるはずです。

```java
    @Test
    @DisplayName("値の差が指数の比になる")
    void ratio() {
      double[] probabilities = LogisticRegression.softmax(new double[] {0.0, Math.log(2.0)});

      assertThat(probabilities[0]).isCloseTo(1.0 / 3, within(1e-12));
      assertThat(probabilities[1]).isCloseTo(2.0 / 3, within(1e-12));
    }
```

```text
LogisticRegressionTest > Softmax > 値がすべて同じなら確率は均等になる PASSED
LogisticRegressionTest > Softmax > 値の差が指数の比になる FAILED
    java.lang.AssertionError: 
    Expecting actual:
      0.5
    to be close to:
      0.3333333333333333
    by less than 1.0E-12 but difference was 0.1666666666666667.
    (a difference of exactly 1.0E-12 being considered valid)
2 tests completed, 1 failed
```

定義どおりに一般化します。`Arrays.stream(double[])` は `DoubleStream` を返すので、`map` と `sum` で配列のまま計算できます。

```java
  public static double[] softmax(double[] z) {
    double[] exps = Arrays.stream(z).map(Math::exp).toArray();
    double total = Arrays.stream(exps).sum();
    return Arrays.stream(exps).map(e -> e / total).toArray();
  }
```

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```java
    @Test
    @DisplayName("大きな値でもあふれずに確率を求める")
    void largeValues() {
      assertThat(LogisticRegression.softmax(new double[] {1000.0, 1000.0}))
          .containsExactly(0.5, 0.5);
    }
```

```text
LogisticRegressionTest > Softmax > 大きな値でもあふれずに確率を求める FAILED
    org.opentest4j.AssertionFailedError: 
    Expecting actual:
      [NaN, NaN]
    to contain exactly (and in same order):
      [0.5, 0.5]
    but some elements were not found:
      [0.5, 0.5]
    and others were not expected:
      [NaN, NaN]
LogisticRegressionTest > Softmax > 値がすべて同じなら確率は均等になる PASSED
LogisticRegressionTest > Softmax > 値の差が指数の比になる PASSED
3 tests completed, 1 failed
```

`Math.exp(1000.0)` は `double` で表せる範囲を超えて `Infinity` になり、`Infinity / Infinity` が `NaN`（非数）になりました。Kotlin 版と同じく JVM の浮動小数点数の計算は警告も例外も出さないので、境界の値のテストが無ければ気づけません。

ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで最大値を引いてから `exp` を計算します。`DoubleStream.max()` は空の配列に備えて `OptionalDouble` を返すので、`orElseThrow()` で値を取り出します。

```java
  /** スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。 */
  public static double[] softmax(double[] z) {
    double max = Arrays.stream(z).max().orElseThrow();
    double[] exps = Arrays.stream(z).map(v -> Math.exp(v - max)).toArray();
    double total = Arrays.stream(exps).sum();
    return Arrays.stream(exps).map(e -> e / total).toArray();
  }
```

```text
LogisticRegressionTest > Softmax > 大きな値でもあふれずに確率を求める PASSED
LogisticRegressionTest > Softmax > 値がすべて同じなら確率は均等になる PASSED
LogisticRegressionTest > Softmax > 値の差が指数の比になる PASSED
```

## 10.4 ロジスティック回帰

### 仮実装と三角測量

第 3 章の決定木と同じく `fit` と `predict` を持つクラスにします。テスト用の特徴量は、第 3 章のテストの `Samples.column` と同じ形のヘルパーを、この章のテストのパッケージにも置きました（第 3 章の `Samples` はパッケージプライベートなので、`chapter10` からは使えません）。

```java
// src/test/java/chapter10/Samples.java（抜粋）
  /** 1 列だけの特徴量を値の数だけ作る。 */
  static List<Features> column(String name, double... values) {
    return Arrays.stream(values)
        .mapToObj(v -> new Features(List.of(name), new double[] {v}))
        .toList();
  }
```

```java
  @Nested
  class FitAndPredict {
    @Test
    @DisplayName("1 種類のラベルだけを学習するとそのラベルを予測する")
    void singleLabel() {
      var model =
          new LogisticRegression().fit(column("花弁幅", 0.1, 0.2), List.of("setosa", "setosa"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.9))).containsExactly("setosa", "setosa");
    }
  }
```

コンパイラは、コンストラクターが private であることと、`fit` が無いことの 2 つを指摘しました。

```text
.../src/test/java/chapter10/LogisticRegressionTest.java:44: エラー: LogisticRegression()はLogisticRegressionでprivateアクセスされます
      var model = new LogisticRegression().fit(column("花弁幅", 0.1, 0.2), List.of("setosa", "setosa"));
                  ^
.../src/test/java/chapter10/LogisticRegressionTest.java:44: エラー: シンボルを見つけられません
      var model = new LogisticRegression().fit(column("花弁幅", 0.1, 0.2), List.of("setosa", "setosa"));
                                          ^
  シンボル:   メソッド fit(List<Features>,List<String>)
  場所: クラス LogisticRegression
エラー2個
```

private コンストラクターを消し（Java は、コンストラクターを書かなければ引数なしの公開コンストラクターを用意します）、最初のラベルを返す仮実装にします。

```java
public final class LogisticRegression {
  private String label = "";

  // softmax は省略

  public LogisticRegression fit(List<Features> x, List<String> t) {
    label = t.getFirst();
    return this;
  }

  public List<String> predict(List<Features> x) {
    return Collections.nCopies(x.size(), label);
  }
}
```

`Collections.nCopies(n, 値)` は、同じ値を n 個並べた変更できないリストです。Kotlin 版の `List(n) { label }` に当たります。

2 種類のラベルを境界の左右で予測する例を加えると、仮実装では通りません。

```java
    @Test
    @DisplayName("2 種類のラベルを境界の左右で予測する")
    void twoLabels() {
      var model =
          new LogisticRegression()
              .fit(
                  column("花弁幅", 0.1, 0.2, 0.8, 0.9),
                  List.of("setosa", "setosa", "virginica", "virginica"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.85))).containsExactly("setosa", "virginica");
    }
```

```text
LogisticRegressionTest > FitAndPredict > 1 種類のラベルだけを学習するとそのラベルを予測する PASSED
LogisticRegressionTest > FitAndPredict > 2 種類のラベルを境界の左右で予測する FAILED
    org.opentest4j.AssertionFailedError: 
    Expecting actual:
      ["setosa", "setosa"]
    to contain exactly (and in same order):
      ["setosa", "virginica"]
    but some elements were not found:
      ["virginica"]
    and others were not expected:
      ["setosa"]
```

### 勾配降下法で学習する

重みを少しずつ動かして、予測した確率を正解に近づけます。損失（交差エントロピー）を小さくする方向は、「予測した確率 − 正解」から計算できます。Kotlin 版と同じく、正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を作る代わりに、**正解の品種の確率からだけ 1 を引いて**「確率 − 正解」にします。

損失の記録と学習前の予測のエラーも、ここでまとめて確かめます。

```java
    @Test
    @DisplayName("3 種類のラベルを 2 つの特徴量から予測する")
    void threeLabels() {
      var x =
          Samples.columns(
              "花弁長さ",
              new double[] {0.1, 0.2, 0.5, 0.6, 0.5, 0.6},
              "花弁幅",
              new double[] {0.1, 0.2, 0.1, 0.2, 0.8, 0.9});
      var t = List.of("setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica");

      var model = new LogisticRegression().fit(x, t);

      assertThat(model.predict(x)).isEqualTo(t);
    }

    @Test
    @DisplayName("学習を繰り返すと損失が小さくなる")
    void lossDecreases() {
      var model =
          new LogisticRegression(1.0, 100)
              .fit(
                  column("花弁幅", 0.1, 0.2, 0.8, 0.9),
                  List.of("setosa", "setosa", "virginica", "virginica"));

      assertThat(model.losses()).hasSize(100);
      assertThat(model.losses().getLast()).isLessThan(model.losses().getFirst());
    }

    @Test
    @DisplayName("学習する前に予測するとエラーになる")
    void predictBeforeFit() {
      assertThatThrownBy(() -> new LogisticRegression().predict(column("花弁幅", 0.1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fit で学習してから predict を呼んでください");
    }
```

Kotlin 版は既定引数（`LogisticRegression(epochs = 100)`）で繰り返し回数だけを変えました。Java には既定引数も名前付き引数も無いので、引数なしのコンストラクターが既定値を渡して、2 引数のコンストラクターを呼ぶ形にします（コンストラクターのオーバーロード）。テストでは `new LogisticRegression(1.0, 100)` と、学習率も並べて書く必要があります。

```java
// src/main/java/chapter10/LogisticRegression.java
public final class LogisticRegression {
  private static final double DEFAULT_LEARNING_RATE = 1.0;
  private static final int DEFAULT_EPOCHS = 5000;
  private static final double EPSILON = 1e-12;

  private final double learningRate;
  private final int epochs;
  private List<String> classes = List.of();
  // weights[特徴量][品種]
  private double[][] weights = new double[0][0];
  private double[] bias = new double[0];
  private List<Double> losses = List.of();

  /** 学習率 1.0、繰り返し 5000 回のロジスティック回帰。 */
  public LogisticRegression() {
    this(DEFAULT_LEARNING_RATE, DEFAULT_EPOCHS);
  }

  public LogisticRegression(double learningRate, int epochs) {
    this.learningRate = learningRate;
    this.epochs = epochs;
  }

  // softmax は省略

  /** 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。 */
  public static double crossEntropy(List<double[]> probabilities, int[] targets) {
    return -IntStream.range(0, probabilities.size())
            .mapToDouble(i -> Math.log(probabilities.get(i)[targets[i]] + EPSILON))
            .sum()
        / probabilities.size();
  }

  /** 学習した品種の並び（名前の順）。 */
  public List<String> classes() {
    return classes;
  }

  /** 繰り返しごとの訓練データの損失。 */
  public List<Double> losses() {
    return losses;
  }

  private double[] scores(double[] row) {
    double[] scores = bias.clone();
    for (int f = 0; f < row.length; f++) {
      for (int k = 0; k < scores.length; k++) {
        scores[k] += row[f] * weights[f][k];
      }
    }
    return scores;
  }

  /** バッチ勾配降下法で重みと切片を学習する。 */
  public LogisticRegression fit(List<Features> x, List<String> t) {
    List<double[]> rows = x.stream().map(Features::values).toList();
    classes = t.stream().distinct().sorted().toList();
    int[] targets = t.stream().mapToInt(classes::indexOf).toArray();
    int nFeatures = x.getFirst().columns().size();
    weights = new double[nFeatures][classes.size()];
    bias = new double[classes.size()];
    List<Double> recorded = new ArrayList<>();
    for (int epoch = 0; epoch < epochs; epoch++) {
      List<double[]> probabilities = rows.stream().map(row -> softmax(scores(row))).toList();
      recorded.add(crossEntropy(probabilities, targets));
      // 確率 − 正解（正解の品種だけ 1 を引く）
      List<double[]> errors =
          IntStream.range(0, rows.size())
              .mapToObj(
                  i -> {
                    double[] error = probabilities.get(i).clone();
                    error[targets[i]] -= 1.0;
                    return error;
                  })
              .toList();
      update(rows, errors);
    }
    losses = List.copyOf(recorded);
    return this;
  }

  private void update(List<double[]> rows, List<double[]> errors) {
    int n = rows.size();
    for (int k = 0; k < classes.size(); k++) {
      for (int f = 0; f < weights.length; f++) {
        double gradient = 0.0;
        for (int i = 0; i < n; i++) {
          gradient += rows.get(i)[f] * errors.get(i)[k];
        }
        weights[f][k] -= learningRate * gradient / n;
      }
      double biasGradient = 0.0;
      for (double[] error : errors) {
        biasGradient += error[k];
      }
      bias[k] -= learningRate * biasGradient / n;
    }
  }

  /** スコアが最大の品種を予測する。 */
  public List<String> predict(List<Features> x) {
    if (classes.isEmpty()) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return x.stream().map(features -> classes.get(argmax(scores(features.values())))).toList();
  }

  private static int argmax(double[] values) {
    int best = 0;
    for (int i = 1; i < values.length; i++) {
      if (values[i] > values[best]) {
        best = i;
      }
    }
    return best;
  }
}
```

- 第 2 章の `Features.values()` は配列の写しを返すので、`fit` の最初に 1 回だけ `double[]` のリストに変えておき、繰り返しの中では写しを作りません。Kotlin 版の `AnyFrame.toRows()`（データフレームを行の配列に組み直す拡張関数）は、Java 版では `x.stream().map(Features::values)` の 1 行で済みます。`Features` がもともと行ごとの配列だからです
- `weights` は `double[][]`（配列の配列）で、`weights[特徴量][品種]` の順に添字を付けます。どちらの添字を先にするかは型から分からないので、Kotlin 版と同じくコメントで残しています
- 勾配の和は、Kotlin 版の `sumOf` の代わりに `for` 文で書きました。3 重の添字の計算は、ストリームで書くより `for` 文のほうが式をそのまま読めます。1 回の繰り返しで全サンプルの誤差を先に求めてから重みを更新するので、Python 版・Kotlin 版と同じ **バッチ勾配降下法** です
- Kotlin 版の `also { it[targets[i]] -= 1.0 }` のようなスコープ関数は Java に無いので、ラムダのブロックで `clone()` した配列を書き換えてから返しています
- `predict` では確率を計算せず、スコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、結果は同じです。Kotlin 版の `maxBy` に当たるものが `double[]` には無いので、`argmax` を書きました
- 学習前の予測では、Kotlin 版と同じく第 3 章と同じメッセージの `IllegalStateException` を投げます。Kotlin の `check(条件) { メッセージ }` に当たる標準ライブラリの関数は Java に無いので、`if` と `throw` で書きます
- `losses` は `List.copyOf` で変更できないリストにしてから公開します。Kotlin 版は型（読み取り専用の `List<Double>`）で書き換えを防ぎましたが、Java の `List` には読み取り専用の型が無いので、実行時に変更できないリストを返して防ぎます

```text
LogisticRegressionTest > FitAndPredict > 1 種類のラベルだけを学習するとそのラベルを予測する PASSED
LogisticRegressionTest > FitAndPredict > 2 種類のラベルを境界の左右で予測する PASSED
LogisticRegressionTest > FitAndPredict > 3 種類のラベルを 2 つの特徴量から予測する PASSED
LogisticRegressionTest > FitAndPredict > 学習を繰り返すと損失が小さくなる PASSED
LogisticRegressionTest > FitAndPredict > 学習する前に予測するとエラーになる PASSED
LogisticRegressionTest > Softmax > 大きな値でもあふれずに確率を求める PASSED
LogisticRegressionTest > Softmax > 値がすべて同じなら確率は均等になる PASSED
LogisticRegressionTest > Softmax > 値の差が指数の比になる PASSED
```

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。Tribuo の `RandomForestTrainer` は「分割ごと」に特徴量を選び直すので、仕組みは少し異なります（10.8 節）。

### 多数決とブートストラップ標本

```java
// src/test/java/chapter10/RandomForestTest.java（抜粋）
  @Nested
  class MajorityVote {
    @Test
    @DisplayName("サンプルごとに最も多い予測を選ぶ")
    void mostCommon() {
      var votes =
          List.of(
              List.of("setosa", "virginica"),
              List.of("setosa", "virginica"),
              List.of("versicolor", "setosa"));

      assertThat(RandomForest.majorityVote(votes)).containsExactly("setosa", "virginica");
    }
  }

  @Nested
  class BootstrapSample {
    @Test
    @DisplayName("元のデータと同じ件数の行番号を重複を許して選ぶ")
    void withReplacement() {
      List<Integer> rows = RandomForest.bootstrapSample(100, new Random(0));

      assertThat(rows).hasSize(100).allMatch(row -> row >= 0 && row < 100);
      assertThat(new HashSet<>(rows)).hasSizeLessThan(100);
    }

    @Test
    @DisplayName("同じシードなら同じ行を選ぶ")
    void sameSeed() {
      assertThat(RandomForest.bootstrapSample(10, new Random(42)))
          .isEqualTo(RandomForest.bootstrapSample(10, new Random(42)));
    }
  }
```

`votes` は「木ごとの予測のリスト」です。サンプルごとの多数決に組み替えるだけなので、森そのもののテスト（次の節）と一緒に書き、明白な実装で進めました。最初のコンパイルエラーは、Kotlin 版と同じくクラスが無いことでした。

```text
.../src/test/java/chapter10/RandomForestTest.java:25: エラー: シンボルを見つけられません
      assertThat(RandomForest.majorityVote(votes)).containsExactly("setosa", "virginica");
                 ^
  シンボル:   変数 RandomForest
  場所: クラス RandomForestTest.MajorityVote
.../src/test/java/chapter10/RandomForestTest.java:34: エラー: シンボルを見つけられません
      List<Integer> rows = RandomForest.bootstrapSample(100, new Random(0));
                           ^
  シンボル:   変数 RandomForest
  場所: クラス RandomForestTest.BootstrapSample
```

```java
// src/main/java/chapter10/RandomForest.java（抜粋）
  /** サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。 */
  public static List<String> majorityVote(List<List<String>> votes) {
    return IntStream.range(0, votes.getFirst().size())
        .mapToObj(sample -> mostCommon(votes.stream().map(vote -> vote.get(sample)).toList()))
        .toList();
  }

  private static String mostCommon(List<String> labels) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    labels.forEach(label -> counts.merge(label, 1, Integer::sum));
    String best = labels.getFirst();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > counts.get(best)) {
        best = entry.getKey();
      }
    }
    return best;
  }

  /** 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。 */
  public static List<Integer> bootstrapSample(int size, Random random) {
    return IntStream.range(0, size).mapToObj(i -> random.nextInt(size)).toList();
  }
```

- 件数は第 3 章の `DecisionTrees.majority` と同じく、`LinkedHashMap` と `merge` で数えます。Kotlin 版の `groupingBy { it }.eachCount()` に当たります。`LinkedHashMap` は入れた順を保つので、同数なら先に現れた予測が選ばれます
- `random.nextInt(size)` は 0 以上 `size` 未満の整数を返します。乱数生成器を引数で受け取るのは、森全体で 1 つの生成器を使い回し、シード 1 つで全部の木の乱数を再現できるようにするためです。Kotlin 版の `kotlin.random.Random` とは乱数列が違うので、選ばれる行は Kotlin 版と一致しません

### 森を作る

テスト用の 2 品種 10 件のデータ（がく片幅では分けられず、花弁幅で分けられる架空の値）を `Samples.twoSpeciesX()` と `Samples.twoSpeciesT()` に置き、森のテストを書きます。

```java
  @Nested
  class FitAndPredict {
    @Test
    @DisplayName("指定した数だけ第 3 章の決定木を学習する")
    void numberOfTrees() {
      var model = RandomForest.of(5, 1, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.trees()).hasSize(5);
    }

    @Test
    @DisplayName("各決定木は指定した数の特徴量だけを使う")
    void featuresPerTree() {
      var model = RandomForest.of(5, 1, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.trees()).allMatch(tree -> tree.columns().size() == 1);
    }

    @Test
    @DisplayName("決定木の多数決で予測する")
    void vote() {
      var model = RandomForest.of(25, 2, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.predict(Samples.twoSpeciesNewX())).containsExactly("setosa", "virginica");
    }

    @Test
    @DisplayName("同じシードなら同じ予測になる")
    void sameSeed() {
      var first = RandomForest.of(5, 1, 7).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());
      var second = RandomForest.of(5, 1, 7).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(second.trees())
          .extracting(FittedTree::columns)
          .isEqualTo(first.trees().stream().map(FittedTree::columns).toList());
      assertThat(second.predict(Samples.twoSpeciesX()))
          .isEqualTo(first.predict(Samples.twoSpeciesX()));
    }

    @Test
    @DisplayName("学習する前に予測するとエラーになる")
    void predictBeforeFit() {
      assertThatThrownBy(() -> RandomForest.of(10, 2, 0).predict(Samples.column("花弁幅", 0.1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fit で学習してから predict を呼んでください");
    }
  }
```

Kotlin 版は `RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0)` と名前付き引数で書きました。Java では `new RandomForest(5, 1, -1, 0)` のように数値を並べると、どれが何か読めなくなります。そこで第 3 章の `DecisionTree.unlimited()`・`withMaxDepth(n)` と同じく、名前で意味を表す static ファクトリーメソッド `RandomForest.of`・`RandomForest.withMaxDepth` にしました。

部品（多数決・ブートストラップ標本・第 3 章の決定木）がそろっているので、組み立てるだけの明白な実装で進めます。1 本分の情報は record にまとめます。

```java
// src/main/java/chapter10/FittedTree.java
/** ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。 */
public record FittedTree(List<String> columns, List<Integer> rows, DecisionTree model) {
  public FittedTree {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
  }
}
```

```java
// src/main/java/chapter10/RandomForest.java（抜粋）
public final class RandomForest {
  private static final int UNLIMITED = -1;

  private final int nEstimators;
  private final int maxFeatures;
  private final int maxDepth;
  private final long seed;
  private List<FittedTree> trees = List.of();

  private RandomForest(int nEstimators, int maxFeatures, int maxDepth, long seed) {
    this.nEstimators = nEstimators;
    this.maxFeatures = maxFeatures;
    this.maxDepth = maxDepth;
    this.seed = seed;
  }

  /** 深さを制限しない決定木の森。 */
  public static RandomForest of(int nEstimators, int maxFeatures, long seed) {
    return new RandomForest(nEstimators, maxFeatures, UNLIMITED, seed);
  }

  /** 深さの上限を指定した決定木の森。 */
  public static RandomForest withMaxDepth(
      int nEstimators, int maxFeatures, int maxDepth, long seed) {
    if (maxDepth < 0) {
      throw new IllegalArgumentException("深さの上限は 0 以上にしてください");
    }
    return new RandomForest(nEstimators, maxFeatures, maxDepth, seed);
  }

  // majorityVote・bootstrapSample は省略

  /** 特徴量から、指定した列だけを取り出す。 */
  static Features selectColumns(Features features, List<String> columns) {
    return new Features(columns, columns.stream().mapToDouble(features::value).toArray());
  }

  static List<Features> selectColumns(List<Features> x, List<String> columns) {
    return x.stream().map(features -> selectColumns(features, columns)).toList();
  }

  private DecisionTree newTree() {
    return maxDepth == UNLIMITED ? DecisionTree.unlimited() : DecisionTree.withMaxDepth(maxDepth);
  }

  /** 学習した決定木。 */
  public List<FittedTree> trees() {
    return trees;
  }

  public RandomForest fit(List<Features> x, List<String> t) {
    Random random = new Random(seed);
    List<String> allColumns = x.getFirst().columns();
    List<FittedTree> fitted = new ArrayList<>();
    for (int i = 0; i < nEstimators; i++) {
      List<Integer> rows = bootstrapSample(x.size(), random);
      List<String> shuffled = new ArrayList<>(allColumns);
      Collections.shuffle(shuffled, random);
      List<String> chosen = shuffled.subList(0, maxFeatures);
      // 列の順は元のまま残す
      List<String> columns = allColumns.stream().filter(chosen::contains).toList();
      List<Features> sampleX =
          rows.stream().map(row -> selectColumns(x.get(row), columns)).toList();
      List<String> sampleT = rows.stream().map(t::get).toList();
      fitted.add(new FittedTree(columns, rows, newTree().fit(sampleX, sampleT)));
    }
    trees = List.copyOf(fitted);
    return this;
  }

  public List<String> predict(List<Features> x) {
    if (trees.isEmpty()) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return majorityVote(
        trees.stream()
            .map(tree -> tree.model().predict(selectColumns(x, tree.columns())))
            .toList());
  }
}
```

- Kotlin 版は、データフレームの `select` で列を絞りました。Java 版の `Features` は列名と値の配列なので、`selectColumns` で「指定した列だけの `Features`」を作り直します。第 3 章の決定木は `Features.columns()` の列だけから分割を探すので、絞った列だけで学習します
- Kotlin の `shuffled(random)` は並べ替えた新しいリストを返しますが、Java の `Collections.shuffle(list, random)` は渡したリストそのものを並べ替えます。`allColumns`（`Features` の列名の変更できないリスト）を直接渡すと `UnsupportedOperationException` になるので、`new ArrayList<>(...)` で写してから並べ替えています
- `filter(chosen::contains)` で元の列の順に戻しておくと、同じ組み合わせが同じ並びになります
- ブートストラップ標本の行番号は、特徴量の重要度を計算するときに使うので `rows` にも残します（10.6 節）
- `FittedTree` の `model` は、学習済みの `DecisionTree`（中身を変えられるクラス）です。record は成分を変更できませんが、成分が指すオブジェクトの中身までは守りません。ここでは学習し終えた木を入れるだけなので、そのまま持たせています

```text
RandomForestTest > MajorityVote > サンプルごとに最も多い予測を選ぶ PASSED
RandomForestTest > BootstrapSample > 元のデータと同じ件数の行番号を重複を許して選ぶ PASSED
RandomForestTest > BootstrapSample > 同じシードなら同じ行を選ぶ PASSED
RandomForestTest > FitAndPredict > 指定した数だけ第 3 章の決定木を学習する PASSED
RandomForestTest > FitAndPredict > 各決定木は指定した数の特徴量だけを使う PASSED
RandomForestTest > FitAndPredict > 決定木の多数決で予測する PASSED
RandomForestTest > FitAndPredict > 同じシードなら同じ予測になる PASSED
RandomForestTest > FitAndPredict > 学習する前に予測するとエラーになる PASSED
```

第 3 章の `DecisionTree` には一切手を入れていません。`fit` と `predict` という小さな公開 API で作ってあったので、部品としてそのまま組み込めました。

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.impurity()` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。ジニ不純度は第 3 章の `DecisionTrees.gini` を公開メソッドとして使います。

### 決定木 1 本の重要度

Kotlin 版と同じ 3 つの観点（分割しない木、1 回だけ分割する木、2 回分割する木の重み付け）でテストを書きました。

```java
// src/test/java/chapter10/FeatureImportanceTest.java（抜粋）
    @Test
    @DisplayName("分割しない木はすべての特徴量の重要度が 0")
    void leaf() {
      var x = Samples.columns("がく片幅", new double[] {0.3, 0.5}, "花弁幅", new double[] {0.1, 0.2});

      assertThat(
              FeatureImportance.treeImportances(new Leaf("setosa"), x, List.of("setosa", "setosa")))
          .containsExactly(entry("がく片幅", 0.0), entry("花弁幅", 0.0));
    }

    @Test
    @DisplayName("分割で減った不純度を件数で重み付けして割合にする")
    void weighted() {
      var x =
          Samples.columns(
              "花弁長さ",
              new double[] {0.1, 0.2, 0.3, 0.8, 0.7, 0.9},
              "花弁幅",
              new double[] {0.1, 0.1, 0.1, 0.2, 0.9, 0.9});
      var t = List.of("setosa", "setosa", "setosa", "versicolor", "virginica", "virginica");
      var tree = DecisionTree.unlimited().fit(x, t).tree().orElseThrow();

      var importances = FeatureImportance.treeImportances(tree, x, t);

      assertThat(importances.get("花弁長さ")).isCloseTo(7.0 / 11, within(1e-12));
      assertThat(importances.get("花弁幅")).isCloseTo(4.0 / 11, within(1e-12));
    }
```

最初は、分割しない木の期待値を `containsExactlyEntriesOf(Map.of(...))` で書いて失敗しました。`Map.of` は要素の順を決めないので、「列の順に並んでいること」を比べる相手になりません。`entry` を並べる `containsExactly` に書き直しています。重要度は `LinkedHashMap` で列の順に並べて返すので、`Main` で表示したときも列の順に並びます。

第 3 章の木は `sealed interface Tree permits Leaf, Node` なので、木をたどる処理は `switch` のパターンマッチで書きます。Kotlin 版の `when (tree) { is Leaf -> …; is Node -> … }` に当たります。

```java
// src/main/java/chapter10/FeatureImportance.java
public final class FeatureImportance {
  private FeatureImportance() {}

  /** 1 回の分割で減った不純度（件数で重み付け）。 */
  private record Decrease(String feature, double amount) {}

  private static Stream<Decrease> impurityDecreases(Tree tree, List<Features> x, List<String> t) {
    return switch (tree) {
      case Leaf ignored -> Stream.empty();
      case Node node -> {
        Split split = node.split();
        List<Integer> left =
            IntStream.range(0, x.size())
                .filter(i -> x.get(i).value(split.feature()) <= split.threshold())
                .boxed()
                .toList();
        List<Integer> right =
            IntStream.range(0, x.size()).filter(i -> !left.contains(i)).boxed().toList();
        var here =
            new Decrease(split.feature(), t.size() * (DecisionTrees.gini(t) - split.impurity()));
        yield Stream.concat(
            Stream.of(here),
            Stream.concat(
                impurityDecreases(node.left(), pick(x, left), pick(t, left)),
                impurityDecreases(node.right(), pick(x, right), pick(t, right))));
      }
    };
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** 決定木 1 本の重要度。特徴量の列の順に並べ、合計が 1 になるようにする。 */
  public static Map<String, Double> treeImportances(Tree tree, List<Features> x, List<String> t) {
    Map<String, Double> totals = zeros(x.getFirst().columns());
    impurityDecreases(tree, x, t).forEach(d -> totals.merge(d.feature(), d.amount(), Double::sum));
    return normalize(totals);
  }

  // forestImportances は次の節

  private static Map<String, Double> zeros(List<String> columns) {
    Map<String, Double> zeros = new LinkedHashMap<>();
    columns.forEach(column -> zeros.put(column, 0.0));
    return zeros;
  }

  private static Map<String, Double> normalize(Map<String, Double> totals) {
    double total = totals.values().stream().mapToDouble(Double::doubleValue).sum();
    if (total == 0.0) {
      return totals;
    }
    Map<String, Double> normalized = new LinkedHashMap<>();
    totals.forEach((feature, value) -> normalized.put(feature, value / total));
    return normalized;
  }
}
```

- Kotlin 版は「特徴量と減少量」の組を `Pair<String, Double>` で表しました。Java には標準の組の型が無いので、第 1 章と同じく名前付きの record `Decrease` にしました。クラスの中に置いた private な record は、そのクラスの中だけで使う型です
- Kotlin 版は `List` を `+` でつなぎましたが、Java 版は `Stream.concat` でつないでいます。`switch` の `case` の右辺でブロックを書くときは、値を `yield` で返します
- 分割しない葉の `case` では、変数を使いません。最初は `case Leaf leaf ->` と書き、PMD に「使っていないローカル変数」と指摘されました（10.9 節）

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。木が 1 本なら、その木の重要度と一致するはずです。

```java
    @Test
    @DisplayName("木が 1 本なら学習に使った行でのその木の重要度と一致する")
    void singleTree() {
      // x と t は 2 品種 8 件の架空の値（省略）
      var forest = RandomForest.of(1, 2, 0).fit(x, t);
      FittedTree fitted = forest.trees().getFirst();

      var expected =
          FeatureImportance.treeImportances(
              fitted.model().tree().orElseThrow(),
              fitted.rows().stream().map(x::get).toList(),
              fitted.rows().stream().map(t::get).toList());

      assertThat(FeatureImportance.forestImportances(forest, x, t)).isEqualTo(expected);
    }
```

第 3 章の `DecisionTree.tree()` は、学習前は空の `Optional<Tree>` を返します。Kotlin 版の `checkNotNull(fitted.model.tree)` に当たるのが `orElseThrow()` です。

```java
  /** 木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。 */
  public static Map<String, Double> forestImportances(
      RandomForest forest, List<Features> x, List<String> t) {
    Map<String, Double> totals = zeros(x.getFirst().columns());
    for (FittedTree fitted : forest.trees()) {
      List<Features> sampleX = RandomForest.selectColumns(pick(x, fitted.rows()), fitted.columns());
      Map<String, Double> importances =
          treeImportances(fitted.model().tree().orElseThrow(), sampleX, pick(t, fitted.rows()));
      importances.forEach(
          (feature, value) -> totals.merge(feature, value / forest.trees().size(), Double::sum));
    }
    return normalize(totals);
  }
```

木ごとの重要度には、その木が使った列しか入っていません。Kotlin 版は `it[feature] ?: 0.0` で使わなかった列を 0 として平均しました。Java 版では、先に全部の列を 0 にした `LinkedHashMap` を作り、木ごとの重要度を `merge` で足し込むので、使わなかった列は 0 のまま残ります。

```text
FeatureImportanceTest > ForestImportances > 木が 1 本なら学習に使った行でのその木の重要度と一致する PASSED
FeatureImportanceTest > TreeImportances > 1 回だけ分割する木は分割に使った特徴量の重要度が 1 PASSED
FeatureImportanceTest > TreeImportances > 分割しない木はすべての特徴量の重要度が 0 PASSED
FeatureImportanceTest > TreeImportances > 分割で減った不純度を件数で重み付けして割合にする PASSED
```

## 10.7 モデル共通のインターフェース

### interface で「fit と predict を持つもの」を表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも `fit(x, t)` と `predict(x)` を持っています。これを `interface` で表します。

まず、テスト用の単純なモデル `AlwaysSetosa` で、評価の振る舞いを決めます。

```java
// src/test/java/chapter10/EvaluateTest.java（抜粋）
class EvaluateTest {
  /** どんな特徴量にも setosa と答えるモデル。 */
  private static final class AlwaysSetosa implements Classifier {
    @Override
    public AlwaysSetosa fit(List<Features> x, List<String> t) {
      return this;
    }

    @Override
    public List<String> predict(List<Features> x) {
      return Collections.nCopies(x.size(), "setosa");
    }
  }

  private static TrainTestSplit<Features, String> smallSplit() {
    return new TrainTestSplit<>(
        Samples.column("花弁幅", 0.1, 0.2, 0.8, 0.9),
        Samples.column("花弁幅", 0.15, 0.25),
        List.of("setosa", "setosa", "virginica", "virginica"),
        List.of("setosa", "setosa"));
  }

  @Test
  @DisplayName("学習させてから訓練データとテストデータの正解率を求める")
  void evaluate() {
    assertThat(Score.evaluate(new AlwaysSetosa(), smallSplit())).isEqualTo(new Score(0.5, 1.0));
  }
}
```

```java
// src/main/java/chapter10/Classifier.java
/** fit で学習し、predict でラベルを予測する分類器。 */
public interface Classifier {
  /** 訓練データで学習し、自分自身を返す。 */
  Classifier fit(List<Features> x, List<String> t);

  /** 特徴量ごとのラベルを予測する。 */
  List<String> predict(List<Features> x);
}
```

```java
// src/main/java/chapter10/Score.java
/** 訓練データとテストデータの正解率。 */
public record Score(double train, double test) {
  /** モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。 */
  public static Score evaluate(Classifier model, TrainTestSplit<Features, String> split) {
    model.fit(split.xTrain(), split.tTrain());
    return new Score(
        KinokoTakenoko.accuracy(model.predict(split.xTrain()), split.tTrain()),
        KinokoTakenoko.accuracy(model.predict(split.xTest()), split.tTest()));
  }
}
```

- 正解率は第 1 章の `KinokoTakenoko.accuracy`、分割結果は第 2 章の `TrainTestSplit<Features, String>` を再利用しています
- Kotlin 版はトップレベルの関数 `evaluate` にしましたが、Java にはトップレベルの関数が無いので、結果の型 `Score` の static メソッドにしました。`Score.evaluate(model, split)` と、何を返すかが呼び出しから読めます
- `Score` は record なので、`isEqualTo(new Score(0.5, 1.0))` で 2 つの正解率をまとめて比べられます
- `AlwaysSetosa` の `fit` の戻り値の型は、インターフェースの `Classifier` ではなく `AlwaysSetosa` です。Java も Kotlin と同じく、オーバーライドするメソッドの戻り値を元の型のサブタイプに狭められます（共変戻り値型）

### 3 つのモデルを同じメソッドで評価する

第 3 章の決定木と、この章の 2 つのモデルを `List<Classifier>` に入れて評価します。Kotlin 版と同じ理由（次に述べる名前的部分型）で、第 3 章の決定木は、アダプター `DecisionTreeClassifier` で包む形でテストを書きました。

```java
  @Test
  @DisplayName("第 3 章の決定木と自作のモデルを同じ関数で評価できる")
  void sameFunction() {
    List<Classifier> models =
        List.of(
            DecisionTreeClassifier.withMaxDepth(1),
            new LogisticRegression(),
            RandomForest.of(5, 1, 0));

    var scores = models.stream().map(model -> Score.evaluate(model, smallSplit())).toList();

    assertThat(scores).containsOnly(new Score(1.0, 1.0)).hasSize(3);
  }
```

`LogisticRegression` と `RandomForest` も `fit` と `predict` を持っていますが、`Classifier` を実装すると **宣言していない** ので、`List<Classifier>` には入りません。Java のインターフェースも Kotlin と同じく、名前で型を判定する **名前的部分型**（nominal subtyping）だからです。

この 2 つはこの章で書いたクラスなので、`implements Classifier` を宣言し、`fit` と `predict` に `@Override` を付けます。

```java
public final class LogisticRegression implements Classifier {
  // ...
  @Override
  public LogisticRegression fit(List<Features> x, List<String> t) {
    // ...
  }

  @Override
  public List<String> predict(List<Features> x) {
    // ...
  }
}
```

`@Override` は、Kotlin の `override` と違って付けなくてもコンパイルが通る注釈です。ただし付けておけば、`fit` の名前や引数の型を変えたときに「何もオーバーライドしていない」とコンパイラが知らせます。Error Prone も、`@Override` の付け忘れ（`MissingOverride`）を警告します。本プロジェクトは警告をエラーにしているので、付け忘れはコンパイルエラーとして見つかります。次の節のアダプターの `predict` から `@Override` を外して、確かめました。

```text
.../src/main/java/chapter10/DecisionTreeClassifier.java:31: 警告: [MissingOverride] predict implements method in Classifier; expected @Override
    (see https://errorprone.info/bugpattern/MissingOverride)
エラー: 警告が見つかり-Werrorが指定されました
エラー1個
警告1個
```

第 3 章の `DecisionTree` には手を入れない方針なので、`DecisionTree` を包んで `Classifier` として振る舞わせるクラス（**アダプター**）を書きます。

```java
// src/main/java/chapter10/DecisionTreeClassifier.java
/** 第 3 章の決定木を変更せずに Classifier に合わせるアダプター。 */
public final class DecisionTreeClassifier implements Classifier {
  private final DecisionTree tree;

  private DecisionTreeClassifier(DecisionTree tree) {
    this.tree = tree;
  }

  /** 深さを制限しない決定木。 */
  public static DecisionTreeClassifier unlimited() {
    return new DecisionTreeClassifier(DecisionTree.unlimited());
  }

  /** 深さの上限を指定した決定木。 */
  public static DecisionTreeClassifier withMaxDepth(int maxDepth) {
    return new DecisionTreeClassifier(DecisionTree.withMaxDepth(maxDepth));
  }

  @Override
  public DecisionTreeClassifier fit(List<Features> x, List<String> t) {
    tree.fit(x, t);
    return this;
  }

  @Override
  public List<String> predict(List<Features> x) {
    return tree.predict(x);
  }
}
```

アダプターのファクトリーメソッドは、第 3 章の `DecisionTree.unlimited()`・`withMaxDepth(n)` と同じ名前にそろえました。包む側と包まれる側で作り方が同じなので、読み手は第 3 章の知識のまま使えます。

```text
EvaluateTest > 第 3 章の決定木と自作のモデルを同じ関数で評価できる PASSED
EvaluateTest > 学習させてから訓練データとテストデータの正解率を求める PASSED
```

| 観点 | Python の `Protocol` | Kotlin・Java の `interface` |
|------|--------------------|-----------------------|
| 型が合う条件 | 同じ名前・型のメソッドを持っている（構造的部分型） | 実装を宣言している（名前的部分型） |
| 既存のクラス（第 3 章の決定木） | そのまま入れられる | アダプターで包む |
| 型の確認 | mypy を実行したとき | コンパイルのとき |
| オーバーライドの印 | 無い | Kotlin は `override` が必須、Java は `@Override` が任意（本プロジェクトでは Error Prone で必須にしている） |

## 10.8 Tribuo のトレーナーを同じインターフェースで使う

### Tribuo のトレーナーを Classifier に包む

Tribuo では、学習の設定を持つ **トレーナー**（`Trainer<Label>`）が、データセットから **モデル**（`Model<Label>`）を作ります。トレーナーを受け取り、`fit` でモデルを作って持っておくアダプターを書けば、どのトレーナーも `Classifier` として評価できます。`Features` との変換には、第 3 章の `TribuoTrees.toDataset` と `TribuoTrees.predict` をそのまま使います。

```java
// src/test/java/chapter10/TribuoClassifierTest.java（抜粋）
  @Test
  @DisplayName("Tribuo のロジスティック回帰とランダムフォレストも同じ関数で評価できる")
  void sameFunction() {
    List<Classifier> models =
        List.of(
            new TribuoClassifier(new LogisticRegressionTrainer()),
            TribuoClassifier.randomForest(10, TribuoClassifier.UNLIMITED, 0L));

    var scores = models.stream().map(model -> Score.evaluate(model, twoSpeciesSplit())).toList();

    assertThat(scores).containsOnly(new Score(1.0, 1.0)).hasSize(2);
  }

  @Test
  @DisplayName("学習する前に予測するとエラーになる")
  void predictBeforeFit() {
    assertThatThrownBy(
            () ->
                new TribuoClassifier(new LogisticRegressionTrainer())
                    .predict(Samples.column("花弁幅", 0.1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fit で学習してから predict を呼んでください");
  }
```

```java
// src/main/java/chapter10/TribuoClassifier.java
/** Tribuo のトレーナーを Classifier に合わせるアダプター。 */
public final class TribuoClassifier implements Classifier {
  /** 深さを制限しないことを表す値 */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  // 分割ごとに、半分の特徴量から分割の候補を選ぶ
  private static final float FRACTION_FEATURES_IN_SPLIT = 0.5f;

  private final Trainer<Label> trainer;
  private Model<Label> model;

  public TribuoClassifier(Trainer<Label> trainer) {
    this.trainer = trainer;
  }

  /** ブートストラップ標本で nEstimators 本の CART を学習し、多数決する Tribuo のランダムフォレスト。 */
  public static TribuoClassifier randomForest(int nEstimators, int maxDepth, long seed) {
    var tree = new CARTClassificationTrainer(maxDepth, FRACTION_FEATURES_IN_SPLIT, seed);
    return new TribuoClassifier(
        new RandomForestTrainer<>(tree, new VotingCombiner(), nEstimators, seed));
  }

  @Override
  public TribuoClassifier fit(List<Features> x, List<String> t) {
    model = trainer.train(TribuoTrees.toDataset(x, t));
    return this;
  }

  @Override
  public List<String> predict(List<Features> x) {
    if (model == null) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return TribuoTrees.predict(model, x);
  }
}
```

- `model` は学習前は `null` です。Kotlin 版は型を `Model<Label>?` にしてコンパイラに null の検査を任せましたが、Java の型は null を区別しないので、`predict` で自分で確かめます。第 3 章の `DecisionTree` と同じメッセージの `IllegalStateException` にしました
- `RandomForestTrainer` は、内側の決定木のトレーナー・予測をまとめる方法（多数決の `VotingCombiner`）・木の数・シードを受け取ります。`RandomForestTrainer<>` の `<>`（ダイヤモンド演算子）は、型引数 `Label` を引数から推論させる書き方です。Kotlin 版は型引数を書かずに済みましたが、Java ではジェネリッククラスのコンストラクターに `<>` を付けないと、型引数の無い「raw 型」になり、`-Xlint:all` の警告（本プロジェクトではエラー）になります
- `RandomForestTrainer` は `org.tribuo.common.tree` パッケージにあり、`tribuo-common-tree` というモジュールに入っています。依存に書いた `tribuo-classification-tree` が `tribuo-common-tree` に依存しているので、依存を足さずに使えました（`./gradlew dependencies --configuration compileClasspath` で確かめました）

```text
TribuoClassifierTest > 学習する前に予測するとエラーになる PASSED
TribuoClassifierTest > Tribuo のロジスティック回帰とランダムフォレストも同じ関数で評価できる PASSED
```

### Tribuo の設定と自作との違い

既定の設定は、自作と次の点が違います（Kotlin 版で確かめた内容と同じです）。

| 項目 | 自作 | Tribuo |
|------|------|--------|
| ロジスティック回帰の損失 | 交差エントロピー（ソフトマックス） | `LogMulticlass`（多クラスのロジスティック損失） |
| ロジスティック回帰の最適化 | バッチ勾配降下法（学習率 1.0）、5000 回 | `LogisticRegressionTrainer()` は AdaGrad、ミニバッチの大きさ 1 の確率的勾配降下法、5 エポック、シード 12345 |
| ランダムフォレストで特徴量を選ぶ単位 | 木ごと（2 つ） | 分割ごと（`fractionFeaturesInSplit` で割合を指定。この章では 0.5） |
| 決定木を分割する最小の件数 | 1 件になるまで分ける | `minChildWeight` の既定値 5（重みの合計が 5 未満の節は分割しない） |

`RandomForestTrainer` は、内側の決定木の `fractionFeaturesInSplit` が 1 のままだと、コンストラクターで例外を投げます。Java 版では、これもテストに残しました。

```java
  @Test
  @DisplayName("Tribuo のランダムフォレストは分割ごとに特徴量を絞らない決定木を受け付けない")
  void tribuoRandomForestRequiresFraction() {
    var tree = new CARTClassificationTrainer(Integer.MAX_VALUE);

    assertThatThrownBy(() -> new RandomForestTrainer<>(tree, new VotingCombiner(), 100, 0L))
        .hasMessageContaining("requires that the decision tree innerTrainer have fractional");
  }
```

例外のメッセージは `Property Exception component:'' property:'innerTrainer' - RandomForestTrainer requires that the decision tree innerTrainer have fractional features in split.` でした。

## 10.9 リファクタリング

`./gradlew check` で PMD を実行すると、特徴量の重要度の `switch` が 1 か所指摘されました（パスは `apps/java/` からの相対パスに直しています）。

```text
src/main/java/chapter10/FeatureImportance.java:24:	UnusedLocalVariable:	Avoid unused local variables such as 'leaf'.
```

最初は `case Leaf leaf -> Stream.empty();` と書いていました。型のパターンは変数を宣言しますが、葉では変数を使わないので、PMD は「使っていないローカル変数」とみなします。Java 22 以降なら名前の無い変数 `_` で `case Leaf _ ->` と書けますが、本プロジェクトのツールチェーンは Java 21 なので使えません。PMD の `UnusedLocalVariable` は `ignored` という名前の変数を対象から外すので、`case Leaf ignored ->` に変えて「使わないことを意図している」と名前で示しました。ルールを抑える注釈（`@SuppressWarnings`）は要りません。

Kotlin 版は `is Leaf ->` と、変数を宣言せずに型だけで分岐できるので、この指摘はありませんでした。Kotlin 版の 10.9 節で直したスプレッド演算子（`select(*配列)`）の指摘も、Java 版には現れません。列の選択を `Features` を組み直す `selectColumns` で書いたからです。

## 10.10 実データで突き合わせる

### 実データのテスト

`Preprocessing.prepareIris` で前処理した iris で、自作のモデルと Tribuo の正解率を確かめます。値は、実装を実データで動かして得たものをテストで固定しました（Red を経ていません）。

```java
// src/test/java/chapter10/IrisModelsTest.java（抜粋）
class IrisModelsTest {
  private final Path csvFile = DataDir.dataDir().resolve("iris.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
  }

  private TrainTestSplit<Features, String> irisSplit() throws IOException {
    return Preprocessing.prepareIris(csvFile, 0.3, 0);
  }

  private static TribuoClassifier tribuoLogisticRegression(int epochs) {
    return new TribuoClassifier(
        new LinearSGDTrainer(
            new LogMulticlass(), new AdaGrad(1.0, 0.1), epochs, Trainer.DEFAULT_SEED));
  }

  @Test
  @DisplayName("ロジスティック回帰はテストデータの 45 件中 41 件を正しく分類する")
  void logisticRegression() throws IOException {
    Score score = Score.evaluate(new LogisticRegression(), irisSplit());

    assertThat(score.test()).isCloseTo(41.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("ランダムフォレストは訓練データを分け切りテストデータの 45 件中 42 件を正しく分類する")
  void randomForest() throws IOException {
    Score score = Score.evaluate(RandomForest.of(100, 2, 0), irisSplit());

    assertThat(score.train()).isEqualTo(1.0);
    assertThat(score.test()).isCloseTo(42.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("Tribuo の既定のロジスティック回帰と同じ設定で 5 エポックにすると既定と同じ正解率になる")
  void tribuoDefaultIsFiveEpochs() throws IOException {
    Score defaults =
        Score.evaluate(new TribuoClassifier(new LogisticRegressionTrainer()), irisSplit());

    assertThat(Score.evaluate(tribuoLogisticRegression(5), irisSplit())).isEqualTo(defaults);
  }

  @Test
  @DisplayName("Tribuo のロジスティック回帰は 500 エポックで自作と同じ正解率になる")
  void tribuoFiveHundredEpochs() throws IOException {
    Score tribuo = Score.evaluate(tribuoLogisticRegression(500), irisSplit());

    assertThat(tribuo).isEqualTo(Score.evaluate(new LogisticRegression(), irisSplit()));
  }

  @Test
  @DisplayName("Tribuo のランダムフォレストは最小の重みを 1 にすると訓練データを分け切る")
  void tribuoRandomForestMinChildWeightOne() throws IOException {
    var tree =
        new CARTClassificationTrainer(Integer.MAX_VALUE, 1.0f, 0.0f, 0.5f, new GiniIndex(), 0L);
    var forest =
        new TribuoClassifier(new RandomForestTrainer<>(tree, new VotingCombiner(), 100, 0L));

    Score score = Score.evaluate(forest, irisSplit());

    assertThat(score.train()).isEqualTo(1.0);
    assertThat(score.test()).isCloseTo(42.0 / 45, within(1e-12));
  }
}
```

Tribuo の既定のロジスティック回帰と、既定のランダムフォレストの正解率を固定するテストも、同じファイルに置いています。`LogisticRegressionTrainer()` が `LinearSGDTrainer(new LogMulticlass(), new AdaGrad(1.0, 0.1), 5, Trainer.DEFAULT_SEED)` と同じ設定であることは、Kotlin 版では Tribuo のソースコードを読んで確かめました。Java 版では、両者の正解率が一致することをテスト（`tribuoDefaultIsFiveEpochs`）に残し、エポック数だけを変えて比べる根拠にしています。

### モデルを比べる

`./gradlew runChapter -Pchapter=10` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。

```java
// src/main/java/chapter10/Main.java（抜粋）
  /** 名前とモデル。表示する順に並べる。 */
  public static Map<String, Classifier> models() {
    Map<String, Classifier> models = new LinkedHashMap<>();
    models.put("決定木（深さ " + SHALLOW_DEPTH + "）", DecisionTreeClassifier.withMaxDepth(SHALLOW_DEPTH));
    models.put("ロジスティック回帰", new LogisticRegression());
    models.put(
        "ランダムフォレスト（" + N_ESTIMATORS + " 本）", RandomForest.of(N_ESTIMATORS, MAX_FEATURES, SEED));
    models.put(
        "ランダムフォレスト（" + N_ESTIMATORS + " 本・深さ " + SHALLOW_DEPTH + "）",
        RandomForest.withMaxDepth(N_ESTIMATORS, MAX_FEATURES, SHALLOW_DEPTH, SEED));
    models.put("Tribuo ロジスティック回帰", new TribuoClassifier(new LogisticRegressionTrainer()));
    models.put(
        "Tribuo ランダムフォレスト（" + N_ESTIMATORS + " 本）",
        TribuoClassifier.randomForest(N_ESTIMATORS, TribuoClassifier.UNLIMITED, SEED));
    return models;
  }

  public static void main(String[] args) throws IOException {
    // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
    Logger.getLogger("org.tribuo").setLevel(Level.WARNING);
    TrainTestSplit<Features, String> split =
        Preprocessing.prepareIris(DataDir.dataDir().resolve("iris.csv"), TEST_SIZE, SEED);
    System.out.println("モデル\t訓練データ\tテストデータ");
    models()
        .forEach(
            (name, model) -> {
              Score score = Score.evaluate(model, split);
              System.out.println(name + "\t" + format(score.train()) + "\t" + format(score.test()));
            });

    RandomForest forest =
        RandomForest.of(N_ESTIMATORS, MAX_FEATURES, SEED).fit(split.xTrain(), split.tTrain());
    System.out.println();
    System.out.println("ランダムフォレスト（" + N_ESTIMATORS + " 本）の特徴量の重要度:");
    FeatureImportance.forestImportances(forest, split.xTrain(), split.tTrain())
        .forEach((feature, value) -> System.out.println(feature + "\t" + format(value)));
  }
```

- Kotlin 版は名前とモデルの組を `List<Pair<String, Classifier>>` で並べました。Java には標準の組の型が無いので、入れた順を保つ `LinkedHashMap` を使っています。`Map.forEach` は名前とモデルを 2 つの引数でラムダに渡すので、Kotlin 版の分解宣言 `for ((name, model) in models())` と同じように読めます
- Tribuo は `java.util.logging` で学習の経過を標準エラーに出すので、`org.tribuo` のロガーを警告以上だけに絞っています

```bash
./gradlew runChapter -Pchapter=10
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

この表示は `IrisModelsTest` の `mainPrintsSummary` で固定しています。結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です。分割が違うので数値は Kotlin 版と違いますが、この傾向は Kotlin 版と同じです
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

既定の 5 エポックの結果は、50 エポック以降の結果と違い、テストデータの正解率が 1 件分低くなりました。50 エポック以降は変わらないので、既定の 5 エポックでは学習が落ち着いていないと言えます。500 エポックで、自作（5000 回）と同じ訓練 0.9143・テスト 0.9111 になり、この組み合わせはテストで固定しています。

Kotlin 版では、既定の 5 エポックの訓練データの正解率（0.8000）がテストデータ（0.9111）より低く、学習が足りないことが訓練データの正解率から読み取れました。Java 版の分割では、5 エポックの訓練データの正解率（0.9238）はむしろ 50 エポック以降（0.9143）より高く、訓練データの正解率だけでは学習が足りないと判断できません。エポック数を増やして結果が落ち着くかを見ることで、「既定のエポック数では学習が足りない」という Kotlin 版の結論を Java 版でも確かめられました。

自作のほうも繰り返し回数を変えて確かめました。1000 回では訓練 0.9238・テスト 0.9111、5000 回と 20000 回では訓練 0.9143・テスト 0.9111 でした。テストデータの正解率は変わらないので、既定の繰り返し回数は Python 版・Kotlin 版と同じ 5000 回のままにしています。

**ランダムフォレスト**: Tribuo は訓練データで 0.9905（105 件中 104 件）と分け切っていません。10.8 節の表の `minChildWeight`（既定値 5）を 1.0 にすると、訓練データは 1.0000 になり、テストデータは 0.9556 から 0.9333 に下がりました。重みの合計が 5 未満の節を分割しないという既定の設定が、1 本 1 本の木の深さを抑えていたことを、Java 版の分割でも確かめられました。この分割では、木を深く育てないほうがテストデータの正解率が高く、自作のランダムフォレストで深さを 2 に制限した結果（0.9556）と同じ傾向です。

同じ「ランダムフォレスト」という名前でも、特徴量を選ぶ単位（木ごとか分割ごとか）や、分割を止める条件が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

テストの実行結果です。

```bash
./gradlew test --tests "chapter10.*"
```

第 10 章のテストは 33 件すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent ./gradlew test`）では、実データのテスト 8 件がスキップされ、残りの 25 件が通ります。

## 10.11 Notebook で探索する

Java 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。Java 版の `LogisticRegression.losses()`（繰り返しごとの損失）と `FeatureImportance`（列の順に並んだ重要度）は、Kotlin 版と同じ形のデータを返すので、同じ観点で読めます。分割が違うので、値そのものは Kotlin 版と一致しません。

## 10.12 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のインターフェースで Tribuo と並べて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。JVM は警告を出さないので、境界の値のテストで見つけ、最大値を引く方法で直した
2. **配列と添字による学習** — NumPy の行列計算の代わりに、`double[]` と `for` 文でバッチ勾配降下法を書いた。第 2 章の `Features` が行ごとの配列なので、Kotlin 版のようにデータフレームを行に組み直す手間が無かった
3. **部品の再利用** — 第 3 章の決定木を変更せずに組み合わせ、ブートストラップ標本と特徴量の部分集合でランダムフォレストを作った。1 本分の情報は record にまとめ、既定引数の代わりに名前で意味を表す static ファクトリーメソッドを使った
4. **名前的部分型とアダプター** — Java の `interface` も実装の宣言が必要なので、第 3 章の決定木と Tribuo のトレーナーをアダプターで包み、自作のモデルと同じ `Score.evaluate` で評価した。`@Override` と Error Prone で、宣言の漏れをコンパイル時に見つけられるようにした
5. **設定をそろえて突き合わせる** — Tribuo のロジスティック回帰はエポック数、ランダムフォレストは `minChildWeight` と特徴量を選ぶ単位が自作と違った。Kotlin 版と分割が違っても、同じ設定の違いが同じ向きに効くことを実測し、テストに残した

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
