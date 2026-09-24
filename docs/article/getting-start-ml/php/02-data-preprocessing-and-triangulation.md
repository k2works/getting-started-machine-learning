---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "アヤメのデータを読み込み、欠損値を平均値で補完して訓練データとテストデータに分ける。java.util.Random と同じ線形合同法を PHP で自作し、整数が溢れると float に化けるという PHP 固有の落とし穴を越えて JVM の言語版と並びをそろえる。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

この章では、機械学習の前に必ず必要になる**前処理**を実装します。題材はアヤメ（iris）のデータで、150 件の測定値から品種を当てる問題です。第 3 章で決定木を学習させるための土台を作ります。

この章の山場は**乱数生成器を自作すること**です。PHP の `mt_rand` はほかの言語版と並びが合わないので、`java.util.Random` と同じ 48 ビットの線形合同法を書きます。そしてそこで、**PHP の整数は溢れると float に化ける**という、この言語ならではの落とし穴に正面からぶつかります。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と同じ題材・同じ TODO リストで進めます。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | PHP 版での読み方 |
|----|------|----------------|
| がく片長さ | がく片の長さ | `Chapter02::number($row, 'がく片長さ')` → `?float` |
| がく片幅 | がく片の幅 | `Chapter02::number($row, 'がく片幅')` → `?float` |
| 花弁長さ | 花弁の長さ | `Chapter02::number($row, '花弁長さ')` → `?float` |
| 花弁幅 | 花弁の幅 | `Chapter02::number($row, '花弁幅')` → `?float` |
| 種類 | 品種（3 種類が 50 件ずつ） | `Chapter02::text($row, '種類')` → `string` |

値は 0.0〜1.0 に収まる形で配布されています。特徴量の 4 列には合わせて 7 件の欠損値があります。PHP には「値があるかもしれない」を表す専用の型がないので、**無いことは `null` で表します**。

| 言語 | 欠損値の表し方 | 値を取り出す |
|------|--------------|------------|
| **PHP** | **`?float`（`null` を許す型）** | **`??`・`array_filter`・`=== null`** |
| Ruby・Python | `nil`／`None` | `\|\|`・`or`・`compact`／`is None` |
| Elixir・Clojure | `nil` | `\|\|`・`Enum.reject(&is_nil/1)`・`keep` |
| Scala・F# | `Option[Double]`／`float option` | `getOrElse`・`map`・`match` |
| Rust | `Option<f64>` | `unwrap_or`・`?`・`match` |

PHP の `?float` は Scala の `Option[Double]` ほど強くありません。`null` を取り出す前に検査したかどうかを**型が強制しない**からです。ただし PHPStan のレベル 9 は `?float` を `float` として使おうとすると指摘してくれるので、**静的解析が Option の役割を肩代わりします**。実行時に守るのは型宣言、null の扱いを守るのは静的解析——第 1 章で見た二層構造がここでも効きます。

### 訓練データとテストデータ

学習に使うデータでそのまま性能を測ると、「答えを覚えただけ」のモデルを高く評価してしまいます。そこでデータを 2 つに分けます。この章では 150 件を訓練データ 105 件・テストデータ 45 件に分けます。

### なぜ乱数を自作するのか

分割の前に並べ替えます。並べ替えには乱数が要ります。PHP には乱数の選択肢がいくつかあります。

| 選択肢 | 特徴 |
|--------|------|
| `mt_rand`（`mt_srand` でシードを指定） | メルセンヌ・ツイスタ。**ほかの言語版と並びが合わない** |
| `random_int` | 暗号論的に安全だが**シードを指定できない**ので再現しない |
| `Random\Randomizer`（PHP 8.2 から） | エンジンを選べるが、`java.util.Random` のエンジンは無い |
| **Java 版・Kotlin 版・Scala 版・Clojure 版・Elixir 版** | **シード 0 で `0..9` を並べ替えると `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`** |

**どれもほかの言語版と一致しません。** 一致しなければ訓練データに入る 105 行が変わり、そこから求まる平均値も、第 3 章の決定木の境界も変わります。章をまたいだ突き合わせができなくなります。

そこで PHP 版は、[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) と同じく**`java.util.Random` と同じ乱数生成器を自作します**。`java.util.Random` は乱数の作り方が仕様として文書に書かれていて、48 ビットの線形合同法という、写し取れる程度に小さなアルゴリズムです。

## 2.3 開発環境の準備

第 1 章で作った `apps/php/` にファイルを足します。

```text
apps/php/
├── src/
│   ├── Chapter01.php
│   ├── Chapter02.php   # この章
│   ├── Csv.php         # この章（列名つきの読み込み）
│   ├── Dataset.php
│   ├── Features.php
│   ├── Person.php
│   ├── Random.php      # この章（自作の乱数）
│   └── Table.php       # この章（列の並びと行）
└── tests/
    ├── Chapter01Test.php
    ├── Chapter02Test.php
    ├── CsvTest.php
    ├── DatasetTest.php
    └── RandomTest.php
```

**乱数も CSV も自作・標準の機能で済むので、この章では依存が 1 つも増えません。**

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] 乱数生成器を自作する
  - [ ] シードから状態を作る
  - [ ] 範囲を指定して整数を 1 つ返す
  - [ ] Fisher-Yates で並べ替える
  - [ ] シード 0 の並びが JVM の言語版と一致する
- [ ] 表を読み込む
  - [ ] 数値の列を読む。空欄は欠損値にする
  - [ ] 数値として読めない値・列の不足を弾く
  - [ ] 列ごとに欠損値の数を数える
- [ ] 平均値で欠損値を補完する
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
- [ ] 前処理をまとめる
- [ ] 実データで前処理の結果を表示する

## 2.5 乱数生成器を自作する

### テストファースト: 性質から挟み撃ちにする

いきなり「JVM と同じ並びになること」をテストにはできません。まだ何も書いていないので、期待する並びが分かりません。そこで、**どんな実装でも満たすべき性質**から書きます。

```php
#[TestDox('範囲の中の値だけを返す')]
public function test範囲の中の値だけを返す(): void
{
    $random = new Random(7);

    for ($i = 0; $i < 1000; ++$i) {
        $value = $random->nextInt(10);

        $this->assertGreaterThanOrEqual(0, $value);
        $this->assertLessThan(10, $value);
    }
}

#[TestDox('同じシードなら同じ並びになる')]
public function test同じシードなら同じ並びになる(): void
{
    $this->assertSame(Random::shuffle(range(0, 20), 123), Random::shuffle(range(0, 20), 123));
}
```

この 2 つは「乱数として当たり前のこと」です。これらを満たしたうえで、最後に JVM との一致を確かめます。

### Green: 48 ビットの線形合同法

`java.util.Random` の仕様はこうです。状態は 48 ビットの整数で、次の状態は

```text
state = (state * 0x5DEECE66D + 0xB) mod 2^48
```

で求めます。素直に書くとこうなります。

```php
// これは動かない。後で直す。
private function nextBits(int $bits): int
{
    $this->state = ($this->state * self::MULTIPLIER + self::INCREMENT) & self::MASK;

    return $this->state >> (48 - $bits);
}
```

動かしてみます。

```text
Deprecated: Implicit conversion from float 4.0930922089437614E+24 to int loses precision
  in src/Random.php on line 83
3, 6, 5, 1, 4, 8, 7, 9, 2, 0
```

### この章いちばんの落とし穴: 整数が溢れると float に化ける

`state` は 48 ビット、乗数 `0x5DEECE66D` は 35 ビットです。掛けると **83 ビット**になります。PHP の整数は 64 ビットなので、当然そのままでは入りません。

問題は、**そのとき PHP が何をするか**です。

| 言語 | 64 ビットを超えたとき |
|------|-------------------|
| **PHP** | **float に暗黙変換される（精度を失う）** |
| Java・Kotlin・Scala・C# | 折り返す（上位ビットが捨てられる） |
| Elixir・Clojure・Ruby・Python | 多倍長整数になる（正確なまま） |
| Rust | デバッグビルドではパニック、リリースでは折り返す |
| Go | 折り返す |

**PHP はこの 3 つのどれとも違う、4 つめの振る舞いをします。** 折り返しもせず、多倍長にもならず、**float になって下位のビットを静かに捨てる**のです。線形合同法は下位ビットの情報が次の状態を決めるので、これは致命的です。

しかも、**例外ではなく非推奨の警告**で通り過ぎます。第 1 章で `phpunit.xml` に `failOnDeprecation="true"` を入れておいたおかげで、テストがこれを失敗として教えてくれました。入れていなければ「なぜか並びが合わない」という形でしか現れず、原因にたどり着くのに時間がかかったはずです。

Java のように折り返してほしいなら、自分で 48 ビットに収める必要があります。

```php
/**
 * 48 ビットの状態を 1 つ進める。
 *
 * state（48 ビット）に MULTIPLIER（35 ビット）を素直に掛けると 83 ビットになり、
 * PHP では 64 ビットを超えた整数が float に化けて精度を失う（Java のように
 * 折り返さない）。そこで state を上下 24 ビットに分け、それぞれ 59 ビットに
 * 収まる掛け算にしてから 48 ビットで足し合わせる。
 */
private static function advance(int $state): int
{
    $high = $state >> 24;
    $low = $state & 0xFFFFFF;

    $result = ($high * self::MULTIPLIER) & 0xFFFFFF;
    $result = ($result << 24) + $low * self::MULTIPLIER + self::INCREMENT;

    return $result & self::MASK;
}
```

上位 24 ビットと下位 24 ビットに分けると、どちらも `24 + 35 = 59` ビットに収まります。上位側の積は最終的に 48 ビットでマスクされるので、24 ビットぶんだけ残して左に 24 ビットずらせば足ります。

**筆算の繰り上がりをそのままコードにしたもの**だと思ってください。掛け算を桁ごとに分けるのは、多倍長整数の実装がやっていることそのものです。PHP には多倍長整数が言語に無い（GMP か bcmath の拡張が要る）ので、48 ビットぶんだけ自分で用意した、ということになります。

### 2 の冪のときだけ別式

`java.util.Random` の `nextInt(bound)` には、`bound` が 2 の冪のときだけ通る別の経路があります。

```php
if (($bound & -$bound) === $bound) {
    return ($bound * $this->nextBits(31)) >> 31;
}
```

`$bound & -$bound` が `$bound` に等しいのは、立っているビットが 1 つだけのとき——つまり 2 の冪のときです。この場合は剰余を取らず、上位ビットをそのまま使います。剰余だと下位ビットに偏りが出るためです。

**ここを省くと並びが合いません。** 「写し取る」とはそういうことで、仕様の細部まで合わせないと目的（JVM と同じ数列）を達成できません。

### 剰余の偏りを避ける棄却

2 の冪でない場合は剰余を取りますが、そのままでは偏ります。31 ビットの乱数（0 から 2^31−1）を 10 で割った余りは、0〜7 が 1 回ずつ多く出ます。`java.util.Random` は**偏りを生む値を捨てて引き直す**ことで解決しています。

```php
do {
    $bits = $this->nextBits(31);
    $value = $bits % $bound;
} while ($bits - $value + ($bound - 1) >= 0x80000000);

return $value;
```

条件が成り立つのは、最後の「端数の区間」に入ったときだけです。10 の場合、2^31 を 10 で割ると 6 余るので、引き直しが起きるのは約 4.7 億回に 6 回。まず起きませんが、**起きたときに偏らないことが保証される**のが大事です。

### Fisher-Yates

```php
public static function shuffle(array $items, int $seed): array
{
    $random = new self($seed);

    for ($i = count($items) - 1; $i >= 1; --$i) {
        $j = $random->nextInt($i + 1);
        [$items[$i], $items[$j]] = [$items[$j], $items[$i]];
    }

    return array_values($items);
}
```

`[$items[$i], $items[$j]] = [$items[$j], $items[$i]]` は分割代入による交換です。一時変数が要りません。

**`$items` を書き換えているのに元の配列が変わらない**点に注目してください。PHP の配列は代入・引数渡しのときに値としてコピーされます（実際には書き込むまでコピーを遅らせる仕組みですが、見え方は値渡しです）。オブジェクトが参照で渡るのとは逆です。これは Ruby や Python の配列（参照渡し）と違うところで、テストでも確かめておきます。

```php
#[TestDox('並べ替えは元の配列を変えない')]
public function test並べ替えは元の配列を変えない(): void
{
    $items = range(0, 9);
    Random::shuffle($items, 0);

    $this->assertSame(range(0, 9), $items);
}
```

最後の `array_values()` は、PHPStan のレベル 9 のためです。添字を書き換えた配列は「0 から連番である」ことを静的解析が保証できなくなるので、`list<T>` を返すと宣言した以上は包み直す必要があります。

### 山場のテスト: JVM の言語版と一致する

```php
#[TestDox('Fisher-Yates の並べ替えがほかの言語版と一致する')]
public function testFisherYatesの並べ替えが一致する(): void
{
    // Java 版・Elixir 版と同じ並び。
    $this->assertSame([4, 8, 9, 6, 3, 5, 2, 1, 7, 0], Random::shuffle(range(0, 9), 0));
}
```

```text
OK
```

**通りました。** JVM を持たない PHP から、`java.util.Random` とビット単位で同じ数列が出ています。[Java 版](../java/02-data-preprocessing-and-triangulation.md)・[Kotlin 版](../kotlin/02-data-preprocessing-and-triangulation.md)・[Scala 版](../scala/02-data-preprocessing-and-triangulation.md)・[Clojure 版](../clojure/02-data-preprocessing-and-triangulation.md)・[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) と同じ並びです。

乱数生成器は魔法ではなく、**48 ビットの漸化式と、剰余の偏りへの手当てだけ**でできています。

## 2.6 表を読み込む

### 列の並びを持ち回る

第 1 章では 1 行を `Person` という `readonly class` にしましたが、この章では列が増えたり減ったりするので、**列名と値の連想配列**で扱います。

```php
/** 列名の並びと行のリストを持つ表。 */
final readonly class Table
{
    /**
     * @param list<string>                $columns
     * @param list<array<string, string>> $rows
     */
    public function __construct(
        public array $columns,
        public array $rows,
    ) {
    }
}
```

`$columns` を別に持っているのは、**列の順を保つため**です。PHP の連想配列は挿入順を保つので、実は `$rows[0]` のキーの順を見れば列の順は分かります。[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) は「マップがキーの順を保たない」ために列の並びを別に持たざるをえませんでしたが、PHP ではそうではありません。

それでも別に持つことにしました。理由は 2 つあります。1 つは、行が 0 件のとき列が分からなくなるからです。もう 1 つは、**「列の並び」という概念をコードに書いておくほうが、後から列を足したり外したりする章（第 8・9 章）で扱いやすい**からです。言語が許すからといって暗黙に頼らない、という判断です。

### 数値の列を読む

```php
/**
 * 数値の列を読む。空欄なら null を返す。数値として読めなければ失敗する。
 *
 * PHP には Option が無いので、欠損値は null で表す。
 *
 * @param array<string, string> $row
 */
public static function number(array $row, string $column): ?float
{
    $cell = trim(self::text($row, $column));

    if ($cell === '') {
        return null;
    }

    if (!is_numeric($cell)) {
        throw new InvalidArgumentException("{$column} を数値として読めません: {$cell}");
    }

    return (float) $cell;
}
```

第 1 章では `filter_var(..., FILTER_VALIDATE_INT)` を使いましたが、ここは小数なので `is_numeric` にしています。**`is_numeric` は `'1e3'`（指数表記）も `' 1.5'`・`'1.5 '`（前後の空白）も真とします**。データの素性を考えると許して構いませんが、「何を通すか」を知らずに使うと、後で説明できない値が混ざります。

### 空欄を「無い」として扱う

```php
#[TestDox('空欄の数値は null になる')]
public function test空欄の数値はnullになる(): void
{
    $this->assertNull(Chapter02::number(['a' => ' '], 'a'));
    $this->assertSame(1.5, Chapter02::number(['a' => '1.5'], 'a'));
}
```

**空白だけの欄も欠損値として扱います。** CSV では「空欄」と「空白 1 文字」が見分けられないので、`trim` してから判定します。

### 列ごとの欠損値の数

```php
/**
 * 列ごとに欠損値の数を数える。列の順は表の列の順のまま。
 *
 * @return array<string, int>
 */
public static function countMissing(Table $table): array
{
    $counts = [];

    foreach ($table->columns as $column) {
        $counts[$column] = count(
            array_filter($table->rows, static fn (array $row): bool => self::isMissing($row, $column)),
        );
    }

    return $counts;
}
```

`array_filter` は条件に合う要素だけを残します。**ただし添字は保たれます**（`[0 => ..., 3 => ...]` のようになる）ので、数えるだけならよいのですが、そのあと `list` として使うなら `array_values` が要ります。これは PHP の配列が「連想配列と順序つきリストを兼ねている」ことの副作用で、PHPStan がしつこく指摘してくる点でもあります。

## 2.7 平均値で欠損値を補完する

### 平均値を求める

```php
foreach ($columns as $column) {
    $values = [];

    foreach ($rows as $row) {
        $value = self::number($row, $column);

        if ($value !== null) {
            $values[] = $value;
        }
    }

    if ($values === []) {
        throw new InvalidArgumentException("値がすべて空欄です: {$column}");
    }

    $means[$column] = array_sum($values) / count($values);
}
```

`if ($value !== null)` と書いている点に注目してください。**`if ($value)` と書いてはいけません。**

### `??` と `||` の落とし穴: 0.0 は偽である

PHP で「無ければ既定値」を書く方法は 2 つあります。

```php
$value = $row[$column] ?? $default;   // null のときだけ $default
$value = $row[$column] ?: $default;   // falsy のとき $default（0・0.0・''・[] も含む）
```

**アヤメのデータは 0.0〜1.0 に正規化されているので、`0.0` という正当な値が出てきます。** `?:` や `if ($value)` で書くと、**0.0 が「無い」と判定されて平均値に置き換わります**。テストは通るかもしれません。おかしな結果になるのは、たまたま 0.0 が現れた列だけだからです。

| 書き方 | `null` | `0.0` | `''` |
|--------|--------|-------|------|
| `?? $default` | 既定値 | **そのまま** | **そのまま** |
| `?: $default` | 既定値 | **既定値（誤り）** | **既定値（誤り）** |
| `if ($value !== null)` | 無い | **有る** | **有る** |

[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) は `||` で同じ落とし穴を踏みました（Elixir の `||` は `nil` と `false` だけを偽とするので、こちらは 0.0 では踏みません。代わりに「補完する値が `nil` のとき黙って `nil` を返す」という別の形で現れました）。Ruby も `||` は `nil` と `false` だけです。**PHP と JavaScript だけが、0 と空文字列まで偽として扱います。**

この章では `?? `も `?:` も使わず、`!== null` で明示的に書きました。**短く書ける機能があっても、意味が正確なほうを選びます。**

### 補完して特徴量にする

```php
foreach ($columns as $column) {
    $value = self::number($row, $column);

    if ($value === null) {
        if (!array_key_exists($column, $fillValues)) {
            throw new InvalidArgumentException("補完する値がありません: {$column}");
        }

        $value = $fillValues[$column];
    }

    $filled[$column] = $value;
}
```

補完する値が無いときも `??` で済ませずに例外にします。`$fillValues[$column] ?? 0.0` と書くと、**列名を間違えたときに黙って 0.0 で埋まります**。前処理の間違いは、モデルの精度が少し落ちるという形でしか現れないので、気づけません。

## 2.8 訓練データとテストデータに分ける

### 対応を崩さないこと

特徴量とラベルを別々に並べ替えると、対応が崩れます。**組にしてから並べ替えます**。

```php
$pairs = [];

foreach ($x as $i => $row) {
    $pairs[] = [$row, $t[$i]];
}

$shuffled = Random::shuffle($pairs, $seed);
```

これをテストで確かめます。

```php
#[TestDox('分割しても特徴量とラベルの組が崩れない')]
public function test分割しても組が崩れない(): void
{
    $x = array_map(static fn (int $i): array => ['a' => (string) $i], range(1, 10));
    $t = array_map(static fn (int $i): string => "t{$i}", range(1, 10));

    $split = Chapter02::splitTrainTest($x, $t, 0.3, 0);

    foreach ($split['xTrain'] as $i => $row) {
        $this->assertSame('t' . $row['a'], $split['tTrain'][$i]);
    }
}
```

**特徴量の値とラベルの名前を対応させておく**のがこつです。`['a' => '3']` には必ず `'t3'` が付くようにしておけば、並び順がどうなっても対応の崩れだけを検出できます。これは三角測量の一種です。「正しい並び」を書き下すのではなく、「崩れていないこと」を性質として書いています。

### 割合は切り上げる

```php
$trainCount = count($shuffled) - (int) ceil(count($shuffled) * $testSize);
```

```php
#[TestDox('テストデータの割合は切り上げる')]
public function testテストデータの割合は切り上げる(): void
{
    // 10 件の 25% は 2.5 なので、切り上げて 3 件がテストデータになる。
    $this->assertCount(7, $split['xTrain']);
    $this->assertCount(3, $split['xTest']);
}
```

切り上げにするのは scikit-learn の `train_test_split` に合わせたものです。**150 件を 30% で分けると 45 件と 105 件**になります。

`ceil` が `float` を返すので `(int)` で受けています。PHP の `/` が常に float を返すのと同じで、**整数として使いたいところでは明示的に戻す**必要があります。`declare(strict_types=1)` があるので、戻し忘れれば `TypeError` で止まります。

## 2.9 前処理をまとめる

```php
public static function prepareIris(string $path, float $testSize, int $seed): array
{
    [$columns, $rows, $labels] = self::splitFeaturesAndTarget(self::loadTable($path), self::TARGET);
    $split = self::splitTrainTest($rows, $labels, $testSize, $seed);
    $means = self::columnMeans($split['xTrain'], $columns);

    return [
        'xTrain' => self::fillMissing($split['xTrain'], $columns, $means),
        'xTest' => self::fillMissing($split['xTest'], $columns, $means),
        'tTrain' => $split['tTrain'],
        'tTest' => $split['tTest'],
    ];
}
```

順番が大事です。**分割してから、訓練データだけで平均値を求め、それでテストデータも補完します。**

先に全体の平均で補完してから分割すると、テストデータの情報が訓練データに混ざります（**リーク**）。評価が甘くなり、本番で通用しないモデルを「よい」と判断してしまいます。

```php
#[TestDox('補完に使う平均値は訓練データだけから求める')]
public function test補完に使う平均値は訓練データだけから求める(): void
{
    // 訓練データだけの平均（1 と 3 の平均＝2.0）で補完し、テストデータの 5 は混ぜない。
    $train = [['a' => '1'], ['a' => '3']];
    $means = Chapter02::columnMeans($train, ['a']);

    $this->assertEqualsWithDelta(['a' => 2.0], $means, 1e-9);
    $this->assertEqualsWithDelta(
        [['a' => 2.0]],
        Chapter02::fillMissing([['a' => '']], ['a'], $means),
        1e-9,
    );
}
```

戻り値を `array{xTrain: ..., xTest: ..., tTrain: ..., tTest: ...}` という形で型に書いてあるので、使う側で `$split['xTrian']` と綴りを間違えれば PHPStan が見つけます。**連想配列を返しても、キーの綴りは静的に守れます。**

## 2.10 実データで前処理の結果を表示する

```php
#[Group('data')]
#[TestDox('実データの前処理がほかの言語版と一致する')]
public function test実データの前処理が一致する(): void
{
    if (!Dataset::exists('iris.csv')) {
        $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
    }

    $path = Dataset::path('iris.csv');
    $table = Chapter02::loadTable($path);

    $this->assertCount(150, $table->rows);
    $this->assertSame(
        ['がく片長さ' => 2, 'がく片幅' => 1, '花弁長さ' => 2, '花弁幅' => 2, '種類' => 0],
        Chapter02::countMissing($table),
    );

    $split = Chapter02::prepareIris($path, 0.3, 0);

    $this->assertCount(105, $split['xTrain']);
    $this->assertCount(45, $split['xTest']);

    // Java 版・Scala 版・Clojure 版・Elixir 版と一致する。
    $values = array_column($split['xTrain'], 'がく片長さ');
    $this->assertEqualsWithDelta(
        0.4215384615384616,
        array_sum($values) / count($values),
        1e-15,
    );
}
```

実行します。

```bash
php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter02::run();'
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

**訓練データのがく片長さの平均は 0.4215384615384616 で、[Java 版](../java/02-data-preprocessing-and-triangulation.md)・[Scala 版](../scala/02-data-preprocessing-and-triangulation.md)・[Clojure 版](../clojure/02-data-preprocessing-and-triangulation.md)・[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) と 1e-15 まで一致しました。**

同じ 105 行が訓練データに入り、同じ順で足し合わされている、ということです。自作の乱数が効いています。

## 2.11 可視化について

PHP 版には Notebook による探索と可視化の節を設けません。グラフは [Python 版](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版](../kotlin/02-data-preprocessing-and-triangulation.md) の可視化の節を参照してください。

## 2.12 まとめ

この章では前処理を TDD で実装し、訓練データの平均値をほかの 5 言語版と一致させました。PHP に固有の論点は次のとおりです。

1. **整数は溢れると float に化ける** — Java のように折り返さず、Elixir のように多倍長にもならず、**静かに精度を失う**。しかも例外ではなく非推奨の警告。`failOnDeprecation="true"` が気づかせてくれた
2. **48 ビットの掛け算は桁を分けて書く** — 上位 24 ビットと下位 24 ビットに分ければ、どちらも 59 ビットに収まる。筆算の繰り上がりをコードにしたもの
3. **欠損値は `?float` で表し、`!== null` で判定する** — **`?:` や `if ($value)` は 0.0 を「無い」と判定する**。PHP と JavaScript だけが 0 と空文字列を偽とするので、正規化されたデータでは必ず踏む
4. **「無ければ既定値」を安易に書かない** — `$fillValues[$column] ?? 0.0` は、列名を間違えたときに黙って 0.0 で埋める。前処理の間違いは精度がわずかに落ちる形でしか現れない
5. **配列は値としてコピーされる** — `shuffle` の中で書き換えても元の配列は変わらない。オブジェクトが参照で渡るのと逆で、Ruby・Python とも違う
6. **`array_filter` は添字を保つ** — `list<T>` として使うなら `array_values` で包み直す。PHPStan のレベル 9 はこれを見逃さない
7. **連想配列を返してもキーの綴りは守れる** — `array{xTrain: ..., xTest: ...}` と型に書けば、`$split['xTrian']` を静的解析が見つける
8. **`is_numeric` が何を通すかを知って使う** — `'1e3'` も `' 1.5'` も真になる

**TODO リスト（この章の完了時点）**:

- [x] 乱数生成器を自作する
- [x] 表を読み込む
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 前処理をまとめる
- [x] 実データで前処理の結果を表示する

次の章では、いよいよ機械学習のアルゴリズムを実装します。ジニ不純度による決定木を自作し、**Rubix ML の `ClassificationTree` と突き合わせます**。[Elixir 版](../elixir/03-decision-tree-and-obvious-implementation.md) は Scholar に決定木が無いために突き合わせる相手がいませんでしたが、PHP 版にはあります。同じ第 3 波の 2 つの版が、ここで正反対の姿を見せます。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
