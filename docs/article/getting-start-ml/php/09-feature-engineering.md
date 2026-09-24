---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値・表の結合を PHP の TDD で自作し、標準化を Rubix ML の ZScaleStandardizer と突き合わせる。Shift_JIS の CSV を mbstring の CP932 で読み、文字コードを取り違えたときに PHP で何が起きるかを実測する。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
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

標準化は Rubix ML の `ZScaleStandardizer` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Java 版](../java/09-feature-engineering.md)・[Scala 版](../scala/09-feature-engineering.md)・[Clojure 版](../clojure/09-feature-engineering.md)・[Elixir 版](../elixir/09-feature-engineering.md) と数値を対比します。PHP 版はデータフレームのライブラリを使わず、第 2 章で決めた「列名を鍵にした連想配列の並び」で表を持ちます（[ADR 013](../../../adr/013-php-ml-libraries.md)）。

この章で PHP ならではの点は 3 つです。

- **Shift_JIS が標準で読めます。** mbstring が `CP932` を持っているので、`mb_convert_encoding($bytes, 'UTF-8', 'CP932')` の 1 行で済みます。[Elixir 版](../elixir/09-feature-engineering.md) が codepagex という外部ライブラリと設定ファイルへの書き足しを必要としたのとは対照的で、**この章で依存は 1 つも増えません**
- **文字コードを取り違えても、どちら向きでも例外になりません。** Shift_JIS を UTF-8 として読んでも、UTF-8 を CP932 として読んでも、PHP は黙って進みます。Java 版が例外、Clojure 版が置換文字（U+FFFD）だったのとも、Elixir 版（片方向だけ静かに通る）とも違う、4 つめの振る舞いです
- **標準偏差の定義が Rubix ML と一致しました。** JVM 系の版が Tribuo の n−1（標本標準偏差）とずれたところが、Rubix ML の `ZScaleStandardizer` は **n で割る**（母標準偏差）ので、自作の値とそのまま一致します。Elixir 版の Scholar と同じ側です

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード |
|---------|------|----------|-----------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

第 2 章の `Chapter02::loadTable()` は「BOM 付きの UTF-8 のカンマ区切り」を読む関数です。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` のために、この章で区切り文字と文字コードを指定できる読み込みを足します。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] Rubix ML の `ZScaleStandardizer` と突き合わせる
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

置き場は次のとおりです。技法ごとの関数は `GettingStartedMl\Chapter09` の静的メソッドにまとめ、**状態を覚えるもの・値を運ぶものだけを `src/Chapter09/` のクラスに切り出します**。

| ファイル | 中身 |
|---------|------|
| `src/Chapter09.php` | 技法ごとの関数（`categories`・`encode`・`expand`・`quantile`・`joinWeather`・`linearFit` など） |
| `src/Chapter09/Standardizer.php` | 平均と標準偏差を覚える `readonly class` |
| `src/Chapter09/BostonSplit.php` | 分割の結果（列名・訓練・テスト）を持つ `readonly class` |

`BostonSplit` を作ったのには理由があります。第 2 章の `splitTrainTest()` は `array{xTrain: ..., xTest: ..., tTrain: ..., tTest: ...}` という形の連想配列を返しました。この章では列名も一緒に持ち回るので、同じ書き方を続けると PHPDoc の 1 行が 150 文字を超え、渡す関数ごとに同じ形を書き写すことになります。**名前の付いた型にすれば、形は 1 か所にだけ書けば済みます。**

```php
final readonly class BostonSplit
{
    /**
     * @param list<string>               $columns
     * @param list<array<string, float>> $xTrain
     * @param list<array<string, float>> $xTest
     * @param list<float>                $tTrain
     * @param list<float>                $tTest
     */
    public function __construct(
        public array $columns,
        public array $xTrain,
        public array $xTest,
        public array $tTrain,
        public array $tTest,
    ) {
    }
}
```

これは「型の道具が言語本体にあると、設計がどう変わるか」の一例です。[Ruby 版](../ruby/09-feature-engineering.md) は `Data.define` で同じことをしますが、中身の型は書けません。[Elixir 版](../elixir/09-feature-engineering.md) はマップのままで通し、鍵の綴りの間違いは `%{split | key: ...}` の構文に頼って捕まえました。PHP は**書いた形を PHPStan が検査する**ので、連想配列のままでも守れますが、書く量が増えたときにクラスへ移すほうが読みやすくなります。

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

まずテストです。

```php
    #[TestDox('カテゴリは辞書順に並べて先頭を落とす')]
    public function testカテゴリは辞書順に並べて先頭を落とす(): void
    {
        $this->assertSame(
            ['low', 'very_low'],
            Chapter09::categories(['low', 'high', 'very_low', 'high']),
        );
    }

    #[TestDox('空欄はカテゴリに数えない')]
    public function test空欄はカテゴリに数えない(): void
    {
        $this->assertSame(['b'], Chapter09::categories(['a', '', 'b', ' ']));
    }
```

実装です。

```php
    public static function categories(array $values): array
    {
        $found = array_values(array_unique(array_filter(
            $values,
            static fn (string $v): bool => trim($v) !== '',
        )));
        sort($found);

        return array_slice($found, 1);
    }
```

`array_unique` と `array_filter` は **キーを保ちます**。PHP の配列はキーの穴が空いたままでも配列なので、そのままでは `list<string>`（0 から連番のキーを持つ配列）ではありません。`array_values()` で詰め直さないと PHPStan のレベル 9 が通りません。第 2 章で出てきた「添字を書き換えたら `array_values()` で包み直す」と同じ話が、**絞り込みの側でも起きます**。

逆に、`sort()` と `array_slice()` は結果が list になるので、そこに `array_values()` を重ねると PHPStan が「効果がありません」と指摘します。**どの関数がキーを保ち、どれが詰め直すかを、静的解析が覚えさせてくれます。** 動的型付けの言語でここまで教えてくれる道具は珍しく、[Ruby 版](../ruby/09-feature-engineering.md) では `map`・`select`・`uniq` がすべて新しい配列を返すのでこの問題自体がありません。

### 表に列を加える

次に、表からカテゴリの列を取り除き、末尾にダミー変数の列を加えます。

```php
    #[TestDox('ダミー変数の列を末尾に加え、元の列を取り除く')]
    public function testダミー変数の列を末尾に加える(): void
    {
        $table = new Table(
            ['CRIME', 'RM'],
            [
                ['CRIME' => 'high', 'RM' => '6.0'],
                ['CRIME' => 'low', 'RM' => '5.0'],
            ],
        );

        $encoded = Chapter09::encode($table, 'CRIME', ['low']);

        $this->assertSame(['RM', 'CRIME_low'], $encoded->columns);
        $this->assertSame(
            [
                ['RM' => '6.0', 'CRIME_low' => '0'],
                ['RM' => '5.0', 'CRIME_low' => '1'],
            ],
            $encoded->rows,
        );
    }
```

`high` の行は `CRIME_low` が `0` だけになります。辞書順で先頭の `high` の列を作らないので、「どちらの列も 0 なら high」と読むことになります。

実装で大事なのは、**セルを文字列のままにしておく** ことです。

```php
    public static function encode(Table $table, string $column, array $categories): Table
    {
        $dummyColumns = array_map(
            static fn (string $c): string => "{$column}_{$c}",
            $categories,
        );

        $rows = array_map(
            static function (array $row) use ($column, $categories, $dummyColumns): array {
                $value = Chapter02::text($row, $column);
                unset($row[$column]);

                foreach ($categories as $i => $category) {
                    $row[$dummyColumns[$i]] = $category === $value ? '1' : '0';
                }

                return $row;
            },
            $table->rows,
        );

        $columns = array_values(
            array_filter($table->columns, static fn (string $c): bool => $c !== $column),
        );

        return new Table([...$columns, ...$dummyColumns], $rows);
    }
```

`'1'` と `'0'` を文字列で入れているので、第 2 章の `splitFeaturesAndTarget()`・`columnMeans()`・`fillMissing()` をダミー変数の列にもそのまま使えます。数値に変えるのは `fillMissing()` が一括で行う場所だけ、という約束が保たれます。

`$row` はクロージャの引数なので、`unset($row[$column])` しても **呼び出し元の行は変わりません**。PHP の配列は代入で値としてコピーされる（copy-on-write）ためです。参照渡しを明示しない限り、`array_map` に渡した行を書き換えても元の表は壊れません。オブジェクトを渡していたら逆になるので、「表を連想配列で持つ」という第 2 章の決定がここで効いています。

**TODO リスト**:

- [x] カテゴリ値をダミー変数にする

## 9.5 特徴量を標準化する

### 標準化とは

**標準化** は、列ごとに「平均を引いて標準偏差で割る」変換です。単位の違う特徴量（部屋数の 6 と、税率の 300）を同じ物差しに乗せます。

### 平均と標準偏差を覚える

標準化で重要なのは、**平均と標準偏差を訓練データだけから求め、テストデータにも同じ値を使う** ことです。テストデータの平均を使うと、本番では知り得ない情報がモデルに漏れます（リーケージ）。

この「覚えておいて、あとで別のデータに当てる」という形は、関数だけでは表しにくいので `readonly class` にします。

```php
    #[TestDox('標準化した値は平均 0・標準偏差 1 になる')]
    public function test標準化した値は平均0標準偏差1になる(): void
    {
        $x = [['a' => 1.0], ['a' => 2.0], ['a' => 4.0], ['a' => 8.0]];
        $standardized = Standardizer::fit($x, ['a'])->transformAll($x);
        $again = Standardizer::fit($standardized, ['a']);

        $this->assertEqualsWithDelta(0.0, $again->means['a'], 1e-12);
        $this->assertEqualsWithDelta(1.0, $again->stds['a'], 1e-12);
    }
```

「標準化した値をもう一度 `fit` に通すと、平均 0・標準偏差 1 になる」という書き方にしました。期待値をこちらで計算して書き写すより、**性質そのものを確かめる** ほうが、あとで実装を変えても壊れません。

### n で割るか n−1 で割るか

標準偏差には 2 つの定義があります。偏差平方和を件数 n で割る **母標準偏差** と、n−1 で割る **標本標準偏差** です。ライブラリと突き合わせる前に、自作がどちらなのかをテストで固定します。

```php
    #[TestDox('標準偏差は件数 n で割る母標準偏差を使う')]
    public function test標準偏差は件数で割る(): void
    {
        $x = [['a' => 1.0], ['a' => 2.0], ['a' => 4.0], ['a' => 8.0]];
        $std = Standardizer::fit($x, ['a']);

        // 平均 3.75、偏差平方和 28.75。n で割ると 2.6810、n-1 なら 3.0957
        $this->assertEqualsWithDelta(3.75, $std->means['a'], 1e-12);
        $this->assertEqualsWithDelta(sqrt(28.75 / 4), $std->stds['a'], 1e-12);
        $this->assertNotEqualsWithDelta(sqrt(28.75 / 3), $std->stds['a'], 1e-6);
    }
```

**`assertNotEqualsWithDelta` で「そうではない」ほうも書いています。** n で割る実装を n−1 に取り違えたとき、上の行だけでは「値が少し違う」としか分かりませんが、下の行があれば「取り違えた定義」を名指しで示せます。

実装です。

```php
    public static function fit(array $x, array $columns): self
    {
        if ($x === []) {
            throw new InvalidArgumentException('特徴量が 1 件もありません');
        }

        $means = [];
        $stds = [];

        foreach ($columns as $column) {
            $values = array_map(
                static fn (array $features): float => self::value($features, $column),
                $x,
            );
            $mean = array_sum($values) / count($values);
            $variance = array_sum(
                array_map(static fn (float $v): float => ($v - $mean) * ($v - $mean), $values),
            ) / count($values);
            $std = sqrt($variance);

            $means[$column] = $mean;
            $stds[$column] = $std === 0.0 ? 1.0 : $std;
        }

        return new self($columns, $means, $stds);
    }
```

**すべて同じ値の列は標準偏差が 0 になり、割り算があふれます。** そこで標準偏差を 1 に置き換え、標準化した値が 0 になるようにしました。ボストンのデータでは、訓練データの CHAS（川に接しているか）がすべて 0 になる分割があり得ます。

```php
    #[TestDox('すべて同じ値の列は標準化すると 0 になる')]
    public function test同じ値の列は0になる(): void
    {
        $x = [['a' => 3.0], ['a' => 3.0]];

        $this->assertSame(
            [['a' => 0.0], ['a' => 0.0]],
            Standardizer::fit($x, ['a'])->transformAll($x),
        );
    }
```

### Rubix ML の標準化と突き合わせる

Rubix ML の `ZScaleStandardizer` と比べます。

```php
    public static function rubixStandardize(array $train, array $values): array
    {
        $standardizer = new ZScaleStandardizer(true);
        $standardizer->fit(Unlabeled::quick(array_map(static fn (float $v): array => [$v], $train)));

        $samples = array_map(static fn (float $v): array => [$v], $values);
        $standardizer->transform($samples);

        return array_map(
            static function (array $sample): float {
                $value = $sample[0];

                // Rubix ML の標本は mixed の配列なので、数値であることを確かめてから float にする。
                if (!is_int($value) && !is_float($value)) {
                    throw new InvalidArgumentException('標準化した値が数値ではありません');
                }

                return (float) $value;
            },
            $samples,
        );
    }
```

```php
    #[TestDox('Rubix ML の ZScaleStandardizer も n で割る')]
    public function testRubixの標準化と一致する(): void
    {
        $values = [1.0, 2.0, 4.0, 8.0];
        $x = array_map(static fn (float $v): array => ['a' => $v], $values);
        $ours = array_column(Standardizer::fit($x, ['a'])->transformAll($x), 'a');

        $this->assertEqualsWithDelta($ours, Chapter09::rubixStandardize($values, $values), 1e-12);
    }
```

**1e-12 の許容で一致しました。** Rubix ML も n で割る母標準偏差を使っています。Java 版・Scala 版・Clojure 版が Tribuo の n−1 とずれて「自作を使う」と明記しなければならなかったのに対し、PHP 版はどちらを使っても同じ値になります。第 14 章（K-means）で標準化を使うときも、この一致がそのまま効きます。

実装で 1 か所だけ PHP らしい手間がありました。`ZScaleStandardizer::transform()` は **引数を参照で受け取って書き換えます**（戻り値がありません）。Rubix ML の変換器はすべてこの形なので、「変換した結果を受け取る」のではなく「渡した配列が変わっている」と読む必要があります。値を返す自作の `transformAll()` と並べると、副作用のある API と無い API の違いがはっきりします。

もう 1 つ、Rubix ML の標本は PHPStan から見ると `mixed` の配列です。`(float) $sample[0]` と素直に書くとレベル 9 が「mixed は float にキャストできません」と拒みます。**外のライブラリから値が戻ってくる境界が、型の付いた世界の端です。** そこで `is_int`・`is_float` で確かめてから渡すようにしました。これは面倒ですが、「ライブラリが何を返すか分かっていない」という事実を消さずに済みます。

**TODO リスト**:

- [x] 特徴量を標準化する

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

線形回帰は直線しか引けませんが、特徴量に `RM^2` や `RM × LSTAT` を加えれば、曲がった関係も表せます。これを **多項式特徴量** と呼びます。

列の組は、scikit-learn の `PolynomialFeatures` と同じ順（重複を許して 2 つ選ぶ）にそろえます。

```php
    #[TestDox('重複を許して 2 つの列を選ぶ組を作る')]
    public function test2つの列を選ぶ組を作る(): void
    {
        $this->assertSame(
            [['a', 'a'], ['a', 'b'], ['b', 'b']],
            Chapter09::pairsWithReplacement(['a', 'b']),
        );
    }

    #[TestDox('項の名前は 2 乗なら ^2、違う列なら空白でつなぐ')]
    public function test項の名前(): void
    {
        $this->assertSame('a^2', Chapter09::termName('a', 'a'));
        $this->assertSame('a b', Chapter09::termName('a', 'b'));
    }
```

```php
    public static function pairsWithReplacement(array $columns): array
    {
        $pairs = [];

        foreach ($columns as $i => $left) {
            foreach (array_slice($columns, $i) as $right) {
                $pairs[] = [$left, $right];
            }
        }

        return $pairs;
    }

    public static function termName(string $left, string $right): string
    {
        return $left === $right ? "{$left}^2" : "{$left} {$right}";
    }
```

Elixir 版は `term_name({left, left})` と `term_name({left, right})` の 2 つの節に分け、「同じ列どうし」という条件を引数の形で表しました。PHP には引数のパターンマッチが無いので、`$left === $right` の三項演算子で書きます。**分岐が本体に残るぶん、3 行で読み切れます。**

項の名前に `^` と空白を使うので、列名として連想配列の鍵になります。PHP の配列の鍵は任意の文字列なので、Elixir 版が `String.to_atom/1` でアトム表を汚す心配をしたような問題は起きません。

### 展開と選択

```php
    #[TestDox('多項式特徴量は元の列と積の項を持つ')]
    public function test多項式特徴量(): void
    {
        $expanded = Chapter09::expand([['a' => 2.0, 'b' => 3.0, 'c' => 9.0]], ['a', 'b']);

        $this->assertSame(
            [['a' => 2.0, 'b' => 3.0, 'a^2' => 4.0, 'a b' => 6.0, 'b^2' => 9.0]],
            $expanded,
        );
    }
```

指定していない `c` は落ちます。`assertSame` で **鍵の順まで** 確かめています。PHP の連想配列は挿入順を保つので、順が変わったら気づけます（[Elixir 版](../elixir/09-feature-engineering.md) のマップは順を保たないので、この書き方ができませんでした）。

列を選ぶほうも、鍵の順が「指定した順」になるように書きます。

```php
    public static function selectColumns(array $x, array $columns): array
    {
        return array_map(
            static function (array $features) use ($columns): array {
                $selected = [];

                foreach ($columns as $column) {
                    $selected[$column] = self::featureValue($features, $column);
                }

                return $selected;
            },
            $x,
        );
    }
```

`array_intersect_key()` を使うと 1 行で書けますが、**鍵の順が元の配列のまま** になります。標準化と線形回帰に渡すときに列の順が意味を持つので、指定した順に詰め直すほうを選びました。

**TODO リスト**:

- [x] 多項式特徴量を作る

## 9.7 外れ値を検出する

**四分位範囲（IQR）** で外れ値を判定します。第 1 四分位数 Q1 と第 3 四分位数 Q3 を求め、`Q1 - 1.5 × IQR` より小さい値と `Q3 + 1.5 × IQR` より大きい値を外れ値とします。

分位数は、位置が値の間に落ちたら線形補間します（pandas の `quantile` の既定と同じ）。

```php
    #[TestDox('分位数は値の間なら線形補間する')]
    public function test分位数は線形補間する(): void
    {
        $values = [1.0, 2.0, 3.0, 4.0];

        $this->assertEqualsWithDelta(1.75, Chapter09::quantile($values, 0.25), 1e-12);
        $this->assertEqualsWithDelta(2.5, Chapter09::quantile($values, 0.5), 1e-12);
        $this->assertEqualsWithDelta(3.25, Chapter09::quantile($values, 0.75), 1e-12);
    }
```

```php
    public static function quantile(array $values, float $q): float
    {
        if ($values === []) {
            throw new InvalidArgumentException('値が 1 件もありません');
        }

        $sorted = $values;
        sort($sorted);

        $position = (count($sorted) - 1) * $q;
        $lower = (int) floor($position);
        $upper = (int) ceil($position);

        return $sorted[$lower] + ($sorted[$upper] - $sorted[$lower]) * ($position - $lower);
    }
```

`$sorted = $values;` の 1 行が要ります。**PHP の `sort()` は引数を参照で受け取り、その場で並べ替えます。** 引数の `$values` は値としてコピーされているので呼び出し元は壊れませんが、`$values` をそのまま並べ替えると「引数を書き換える関数」に見えてしまいます。別の名前に写してから並べ替えると、「元の並びは触らない」という意図が読む人に伝わります。

訓練データからだけ外れ値の行を取り除きます。

```php
    #[TestDox('訓練データからだけ外れ値の行を取り除く')]
    public function test訓練データからだけ外れ値を取り除く(): void
    {
        $split = new Chapter09\BostonSplit(
            ['a'],
            [['a' => 1.0], ['a' => 2.0], ['a' => 3.0], ['a' => 4.0], ['a' => 5.0]],
            [['a' => 9.0]],
            [1.0, 2.0, 3.0, 4.0, 100.0],
            [100.0],
        );

        $removed = Chapter09::removeTargetOutliers($split);

        $this->assertCount(4, $removed->xTrain);
        $this->assertSame([1.0, 2.0, 3.0, 4.0], $removed->tTrain);
        $this->assertSame([100.0], $removed->tTest);
    }
```

テストデータの 100.0 は残ります。**テストデータは「本番で来るデータ」の代わり** なので、都合の悪い値を取り除いてしまうと評価の意味がなくなります。この非対称は、実データの節でそのまま決定係数に現れます。

**TODO リスト**:

- [x] 外れ値を検出する

## 9.8 表を結合して特徴量を増やす

### タブ区切り

第 2 章の `Csv::parseTable()` は、区切り文字を引数で受け取れるように書いてありました。

```php
    public static function parseTable(string $contents, string $separator = ','): Table
```

なので、タブ区切りは `"\t"` を渡すだけです。**Elixir 版で NimbleCSV の区切り文字がコンパイル時に決まってしまい、タブ用のパーサーをもう 1 つ定義する必要があったのとは対照的です。** PHP の `fgetcsv` は区切り文字を実行時の引数で受け取ります。

```php
    #[TestDox('タブ区切りの表を読める')]
    public function testタブ区切りの表を読める(): void
    {
        $path = $this->file("id\tcnt\n1\t10\n", '.tsv');
        $table = Chapter09::loadDelimited($path, Chapter09::UTF8, "\t");

        $this->assertSame(['id', 'cnt'], $table->columns);
        $this->assertSame([['id' => '1', 'cnt' => '10']], $table->rows);
    }
```

### Shift_JIS

`weather.csv` は Shift_JIS です。PHP では mbstring が `CP932`（Shift_JIS の Windows 拡張）を標準で持っているので、読み込んだバイト列を変換するだけで済みます。

```php
    public static function loadDelimited(string $path, string $encoding, string $separator): Table
    {
        $contents = @file_get_contents($path);

        if ($contents === false) {
            throw new InvalidArgumentException("ファイルを開けません: {$path}");
        }

        $text = $contents;

        if ($encoding !== self::UTF8) {
            // 変換できない文字コード名を渡したときだけ false になる。
            $converted = mb_convert_encoding($contents, self::UTF8, $encoding);

            if (!is_string($converted)) {
                throw new InvalidArgumentException("文字コードを変換できません: {$encoding}");
            }

            $text = $converted;
        }

        return Csv::parseTable($text, $separator);
    }
```

**この章で増えた依存はゼロです。** `composer.json` には何も足していません。Elixir 版が codepagex を追加し、`config/config.exs` に「どの符号化表を組み込むか」を書いたのと比べると、ずいぶん軽く済みました。JVM の言語版の `Charset.forName("Shift_JIS")` に近い手触りです。

テストで Shift_JIS のファイルを用意するために、逆向きの変換も用意します。

```php
    /** 文字列を CP932（Shift_JIS）のバイト列にする。テストでファイルを用意するために使う。 */
    public static function toCp932(string $text): string
    {
        return mb_convert_encoding($text, self::CP932, self::UTF8);
    }
```

```php
    #[TestDox('Shift_JIS の表を CP932 として読める')]
    public function testShiftJISの表を読める(): void
    {
        $path = $this->file(Chapter09::toCp932("天気\n晴れ\n"));
        $table = Chapter09::loadDelimited($path, Chapter09::CP932, ',');

        $this->assertSame(['天気'], $table->columns);
        $this->assertSame([['天気' => '晴れ']], $table->rows);
    }
```

### 取り違えたときに何が起きるか

文字コードは、取り違えたときの振る舞いのほうが大事です。実際に両方向を動かして確かめました。

まず、**読み込む前に「UTF-8 ではない」と判定できます。**

```php
    #[TestDox('UTF-8 かどうかを読み込む前に判定できる')]
    public function testUTF8かどうかを判定できる(): void
    {
        $this->assertFalse(mb_check_encoding(Chapter09::toCp932('天気'), 'UTF-8'));
        $this->assertTrue(mb_check_encoding('天気', 'UTF-8'));
    }
```

`mb_check_encoding()` は、バイト列がその文字コードとして正しいかを返します。`天気` の Shift_JIS は `93 56 8b 43` で、`93` から始まる並びは UTF-8 として不正なので `false` になります。**変換する前にファイルを見分けられる** のは、JVM 系の言語版にも Elixir 版にも無かった道具です。

では、判定せずに取り違えたらどうなるか。

```php
    #[TestDox('Shift_JIS を UTF-8 として読むと例外にならず不正なバイト列が残る')]
    public function testShiftJISをUTF8として読む(): void
    {
        $path = $this->file(Chapter09::toCp932("天気\n晴れ\n"));
        $table = Chapter09::loadDelimited($path, Chapter09::UTF8, ',');

        // 例外も置換文字も出ない。UTF-8 として不正なままの文字列が入る。
        $this->assertFalse(mb_check_encoding($table->columns[0], 'UTF-8'));
        $this->assertSame("\x93\x56\x8b\x43", $table->columns[0]);
    }

    #[TestDox('UTF-8 を CP932 として読むと例外にならず文字化けする')]
    public function testUTF8をCP932として読む(): void
    {
        $path = $this->file("天気\n晴れ\n");
        $table = Chapter09::loadDelimited($path, Chapter09::CP932, ',');

        // 変換は通る。中身は別の文字に化けているだけで、UTF-8 としては正しい。
        $this->assertTrue(mb_check_encoding($table->columns[0], 'UTF-8'));
        $this->assertNotSame('天気', $table->columns[0]);
    }
```

**どちらの向きでも例外になりません。** 片方は不正なバイト列がそのまま残り、もう片方は「UTF-8 としては正しいが意味の違う文字」（`天気` が `螟ｩ豌` に化けます）になります。後者のほうが厄介です。`mb_check_encoding()` が `true` を返すので、**あとからでは取り違えたことに気づけません**。

シリーズ 4 言語目にして、4 通りの振る舞いがそろいました。

| 言語 | Shift_JIS を UTF-8 として読む | UTF-8 を Shift_JIS として読む |
|------|--------------------------|--------------------------|
| Java・Scala・Kotlin | `MalformedInputException` を投げる（`CodingErrorAction.REPORT` のとき） | 同上 |
| Clojure | 置換文字 U+FFFD に置き換える（既定が `REPLACE`） | 同上 |
| Elixir | 不正なバイナリがそのまま入る（例外なし） | codepagex が `{:error, ...}` を返す |
| **PHP** | **不正なバイト列がそのまま入る（例外なし）** | **文字化けした正しい UTF-8 になる（例外なし）** |

**PHP がいちばん静かです。** 引き換えに、`mb_check_encoding()` という「読む前に確かめる」道具が用意されています。**黙って進む言語では、確かめる責任はこちらにある**という設計です。

なお `mb_convert_encoding($bytes, 'UTF-8', 'UTF-8')` で「UTF-8 として不正なバイトを取り除く」洗浄もできます。このとき不正なバイトは `?`（0x3F）に置き換わります。Clojure が使う U+FFFD（REPLACEMENT CHARACTER）ではなく、素の疑問符です。`mb_substitute_character()` の既定値が `63` なのを変えれば `"none"` や `0xFFFD` にもできますが、**プロセス全体の設定**なので、ライブラリの中で触るべきではありません。

### 配列による結合と集計

天気 ID をキーにした配列を作り、自転車の表に天気の列を加えます（内部結合）。

```php
    public static function joinWeather(Table $bike, Table $weather): Table
    {
        $byId = [];

        foreach ($weather->rows as $row) {
            $byId[Chapter02::text($row, self::JOIN_KEY)] = $row;
        }

        if (count($byId) !== count($weather->rows)) {
            throw new InvalidArgumentException(self::JOIN_KEY . ' が一意ではありません');
        }
        // ……
    }
```

**キーが重複すると、PHP の配列は静かに上書きします。** 何も言わずに行が消えるので、件数を比べて気づけるようにしました。Elixir の `Map.new/2` と同じ落とし穴で、同じ対処になります。

```php
    #[TestDox('結合の鍵が一意でなければ失敗する')]
    public function test結合の鍵が一意でなければ失敗する(): void
    {
        $weather = new Table(
            ['weather_id', 'weather'],
            [
                ['weather_id' => '1', 'weather' => '晴れ'],
                ['weather_id' => '1', 'weather' => '雨'],
            ],
        );

        $this->expectException(InvalidArgumentException::class);

        Chapter09::joinWeather(new Table(['weather_id'], []), $weather);
    }
```

天気の表に無い ID の行も、黙って消えます（内部結合なので意図どおりですが、テストで明示しておきます）。

```php
        // 天気の表に無い 9 の行は残らない。
        $this->assertSame(['weather_id', 'cnt', 'weather'], $joined->columns);
        $this->assertCount(2, $joined->rows);
```

集計は、天気ごとに平均を求めて多い順に並べます。

```php
    public static function meanCountByWeather(Table $joined): array
    {
        $counts = [];

        foreach ($joined->rows as $row) {
            $counts[Chapter02::text($row, 'weather')][] = (float) Chapter02::number($row, 'cnt');
        }

        $means = [];

        foreach ($counts as $weather => $values) {
            $means[] = [(string) $weather, array_sum($values) / count($values)];
        }

        usort($means, static fn (array $a, array $b): int => $b[1] <=> $a[1]);

        return $means;
    }
```

`$counts[$key][] = $value` は、その鍵がまだ無ければ空の配列を作ってから追加します（自動活性化）。Elixir の `Enum.group_by/2` にあたる処理を、1 行で書けます。

**返すのは連想配列ではなく、`[名前, 値]` の組の並びです。** 並べ替えた結果を連想配列で返すと、受け取る側が `foreach` の順を信じてよいのか迷います。順に意味がある結果は、順を持つ構造で返します。

`(string) $weather` のキャストが要るのは PHP の癖です。**配列の鍵に数字だけの文字列を入れると、PHP は自動で整数に変換します。** 天気の名前は日本語なので実際には起きませんが、PHPStan は `array-key`（`int|string`）として扱うので、明示しないとレベル 9 が通りません。「起きないはずだが型としては起きうる」ことを言語が教えてくれます。

**TODO リスト**:

- [x] 表を結合して特徴量を増やす

## 9.9 特徴量の効果を測る

### 線形回帰と決定係数

作った特徴量が効いたかを測るために、線形回帰と決定係数を用意します。正規方程式 `XᵀX β = Xᵀt` を、MathPHP の行列で解きます。

```php
    public static function linearFit(array $rows, array $t, array $columns): LinearModel
    {
        if ($rows === []) {
            throw new InvalidArgumentException('特徴量が 1 件もありません');
        }

        $design = MatrixFactory::createNumeric(
            array_map(static fn (array $row): array => [1.0, ...$row], $rows),
        );
        $transposed = $design->transpose();

        try {
            /** @var Vector $solution */
            $solution = $transposed
                ->multiply($design)
                ->solve(new Vector($transposed->vectorMultiply(new Vector($t))->getVector()));
            /** @var list<float> $beta */
            $beta = $solution->getVector();
        } catch (Throwable $error) {
            throw new InvalidArgumentException(
                '特徴量の列が互いに独立でないため、正規方程式を解けません',
                0,
                $error,
            );
        }

        foreach ($beta as $value) {
            if (is_nan($value) || is_infinite($value)) {
                throw new InvalidArgumentException('特徴量の列が互いに独立でないため、正規方程式を解けません');
            }
        }

        return new LinearModel($beta[0], $columns, array_slice($beta, 1));
    }
```

`[1.0, ...$row]` の展開で、先頭に切片用の 1 の列を足します。

**モデルの器は第 7 章の `LinearModel` をそのまま使います。** この章は列名を持たない行列（`list<list<float>>`）を扱うので、最初は「切片と重みだけを持つ器」を別に作りました。しかし中身は第 7 章のものと同じで、**同じ概念に 2 つの名前がある**状態になります。そこで第 7 章の `LinearModel` に「列の順に並んだ値から予測する」口（`predictRow`・`predictRows`）を足し、列名は呼ぶ側から渡す形に寄せました。

```php
    public function predictRow(array $values): float
    {
        if (count($values) !== count($this->columns)) {
            throw new InvalidArgumentException(
                sprintf('特徴量と係数の数が違います: %d と %d', count($values), count($this->columns)),
            );
        }

        $sum = $this->intercept;

        foreach ($values as $index => $value) {
            $sum += $this->coefficients[$index] * $value;
        }

        return $sum;
    }
```

寄せたことで、この章でも列名で係数を読めるようになりました（`$model->coefficient('LSTAT')`）。**「列名を持たない行列で計算する」ことと「モデルが列名を覚えている」ことは両立します。**

**失敗の捕まえ方を 2 段構えにしました。** MathPHP は特異行列に対して例外を投げることもあれば、`NaN` や `Infinity` を含むベクトルを返すこともあります（解法によって変わります）。どちらでも同じ `InvalidArgumentException` になるようにしておかないと、呼ぶ側が 2 通りの失敗を扱う羽目になります。Elixir 版の Nx も「例外を投げずに NaN を返す」側だったので、同じ確認が要りました。

```php
    #[TestDox('互いに独立でない列があれば正規方程式を解けない')]
    public function test独立でない列は解けない(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter09::linearFit([[1.0, 2.0], [2.0, 4.0]], [1.0, 2.0], ['a', 'b']);
    }
```

決定係数は、1 から「残差の 2 乗和 ÷ 平均との差の 2 乗和」を引いた値です。完全に当てれば 1、平均を答えるだけなら 0 になります。

```php
    #[TestDox('完全に当てた予測の決定係数は 1')]
    public function test決定係数は1(): void
    {
        $this->assertEqualsWithDelta(1.0, Chapter09::rSquared([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1e-12);
    }

    #[TestDox('平均を答えるだけの予測の決定係数は 0')]
    public function test決定係数は0(): void
    {
        $this->assertEqualsWithDelta(0.0, Chapter09::rSquared([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12);
    }
```

### 特徴量の組を比べる

特徴量の組ごとに、標準化 → 線形回帰 → 決定係数を通します。

```php
    public static function scoreFeatureSet(BostonSplit $split, array $columns, array $terms): array
    {
        $train = self::selectColumns(self::expand($split->xTrain, $columns), $terms);
        $test = self::selectColumns(self::expand($split->xTest, $columns), $terms);
        $standardizer = Standardizer::fit($train, $terms);
        $xTrain = Standardizer::toRows($standardizer->transformAll($train), $terms);
        $xTest = Standardizer::toRows($standardizer->transformAll($test), $terms);
        $model = self::linearFit($xTrain, $split->tTrain, $terms);

        return [
            'train' => self::rSquared($split->tTrain, $model->predictRows($xTrain)),
            'test' => self::rSquared($split->tTest, $model->predictRows($xTest)),
        ];
    }
```

`Standardizer::fit($train, ...)` と、**訓練データからだけ** 平均と標準偏差を求めているのが肝です。同じ `$standardizer` をテストデータにも当てます。

### 実データで測る

```bash
ML_DATA_DIR=<学習データの置き場> php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter09::run();'
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

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、分割に関係しないのでどの版でも同じ値です

**決定係数の 8 つの数値は、Java 版・Scala 版・Clojure 版・Elixir 版の記事とすべて一致しました。** 第 2 章で `java.util.Random` と同じ線形合同法を自作したので、同じ行が訓練データとテストデータに入ります。そのあとの標準化・多項式特徴量・正規方程式まで含めて、4 桁の表示では差が出ませんでした。

一致したことには、もう 1 つ意味があります。行列の分解は、Java 版・Clojure 版が Tribuo の **コレスキー分解**、Elixir 版が Nx の **LU 分解**、PHP 版が MathPHP の **LU 分解** と、アルゴリズムが 2 種類に分かれています。それでも表示する 4 桁では差が出ません。逆に言えば、一致しなかったときに「乱数か、手順か、分解の方法か、丸めか」を切り分けられるのは、ほかの版が照合先としてあるからです。

Kotlin 版は `kotlin.random.Random(0)` を使うので分け方が違い、別の値になります（[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) を参照）。

この出力は `#[Group('data')]` を付けたテストで固定しています。

```php
    #[Group('data')]
    #[TestDox('実データの特徴量エンジニアリングの結果を表示する')]
    public function test実データの結果を表示する(): void
    {
        if (!Dataset::exists('Boston.csv') || !Dataset::exists('weather.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::dir());
        }

        $output = Chapter09::run();

        $this->assertStringContainsString('訓練データ: 70 件, テストデータ: 30 件', $output);
        $this->assertStringContainsString('標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00', $output);
        $this->assertStringContainsString('2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628', $output);
        $this->assertStringContainsString('訓練データの PRICE の外れ値: 8 件', $output);
        $this->assertStringContainsString('天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3', $output);
    }
```

学習データが無い環境では `markTestSkipped()` で飛ばし、CI では `--exclude-group data` で最初から走らせません。**スキップした件数は PHPUnit の結果に残ります**（`OK, but there were issues! Tests: 52, Skipped: 5.`）ので、Clojure 版が標準エラーに理由を出すしかなかったのと比べて、飛ばしたことが見えます。

**TODO リスト**:

- [x] 特徴量の組み合わせごとに決定係数を比べる

## 9.10 Notebook による探索と可視化

PHP 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、第 5 章・第 6 章で整えた検査（`apps:check:php`）をかけました。PHP-CS-Fixer → PHPStan レベル 9 → PHPUnit → カバレッジの順です。

PHPStan が指摘したのは 5 件で、すべて同じ種類の話でした。

- **`array_values()` を足すべきところと、足すと余計なところ** — `array_filter()` のあとには要り、`array_slice()` のあとには要りません。「この関数はキーを保つ」「この関数は詰め直す」を、実装ごとに覚えるのではなく **静的解析に教わる** 形になりました
- **`mixed` を `float` にキャストできない** — Rubix ML から戻ってきた標本と、`mb_convert_encoding()` の `string|false`。**外のライブラリとの境界が、型の付いた世界の端です**。`is_float()` と `is_string()` で確かめてから中に入れます

カバレッジは `src/Chapter09.php` が 96.75%、`Standardizer` が 97.14% でした。到達しないのは、実データが無い環境で例外を投げる分岐です。

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、次の形で公開しています。

| API | 内容 |
|-----|------|
| `Standardizer::fit($x, $columns)` | 指定した列の平均と標準偏差（n で割る）を覚えた `readonly class` を返す |
| `$standardizer->transform($features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `$standardizer->transformAll($x)` | 並びを標準化する |
| `Standardizer::toRows($x, $columns)` | 特徴量を列の順に並べた数値の行にする |
| `Chapter09::rubixStandardize($train, $values)` | Rubix ML の `ZScaleStandardizer` で比べる |

Rubix ML の `ZScaleStandardizer` も自作も同じ定義（n で割る）なので、第 14 章ではどちらを使っても結果が変わりません。JVM 系の版が「Tribuo ではなく自作を使うこと」を明記しなければならなかったのとは、事情が違います。

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を PHP の TDD で自作し、標準化を Rubix ML と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `categories()`・`encode()` | — | `array_filter()` がキーを保つ、訓練とテストで列をそろえる |
| 標準化 | `Standardizer` | Rubix ML の `ZScaleStandardizer`（一致） | 分散 0 の列、テストデータの平均を使わない、変換器が引数を書き換える |
| 多項式特徴量 | `expand()`・`termName()` | — | 列数が急に増えて過学習しやすい、鍵の順 |
| 外れ値の検出 | `quantile()`・`iqrOutliers()` | — | `sort()` が引数を並べ替える、検出はできても除くかはデータの意味で決める |
| 表の結合 | `loadDelimited()`・`joinWeather()` | — | 文字コードの取り違えが例外にならない、配列の鍵の静かな上書き、数字の鍵の整数化 |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。これらの値は Java 版・Scala 版・Clojure 版・Elixir 版と完全に一致しました。

PHP 版ならではの学びは 6 つです。

1. **Shift_JIS のために依存が増えない** — mbstring が `CP932` を標準で持つ。`composer.json` には何も足さずに済んだ。Elixir 版が codepagex と設定ファイルを必要としたのと正反対で、**ライブラリの成熟度ではなく「標準ライブラリの守備範囲」がここでは効いた**
2. **文字コードの取り違えは、どちら向きでも静かに通る** — Shift_JIS を UTF-8 として読むと不正なバイト列が残り、UTF-8 を CP932 として読むと **UTF-8 としては正しい文字化け** になる。後者は `mb_check_encoding()` でも見抜けない。シリーズで 4 つめの、いちばん静かな振る舞い
3. **ただし「読む前に確かめる」道具はある** — `mb_check_encoding($bytes, 'UTF-8')` で判定できる。**黙って進む言語では、確かめる責任はこちらにある**
4. **Rubix ML の標準偏差は n で割る** — Tribuo（n−1）と違い、scikit-learn・Scholar と同じ定義。自作と一致することと、不偏標準偏差ではないことを両方テストに残した
5. **キーを保つか詰め直すかを静的解析が教える** — `array_filter()` のあとの `array_values()` は要り、`array_slice()` のあとは余計。動的型付けの言語で、配列の「形」をここまで見てくれるのは PHP-CS-Fixer ではなく PHPStan の仕事
6. **境界で `mixed` が入ってくる** — Rubix ML の標本も `mb_convert_encoding()` の戻り値も、型の付いた世界の外から来る。`is_float()`・`is_string()` の確認を書くのは面倒だが、**「ライブラリが何を返すか分かっていない」という事実を消さずに済む**

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。**Rubix ML にはランダムフォレストもあるので、Elixir 版が突き合わせられなかったところを比べられます。**

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
