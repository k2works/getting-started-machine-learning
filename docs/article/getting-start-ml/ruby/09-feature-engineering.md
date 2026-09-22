---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Ruby の TDD で自作し、Rumale の StandardScaler・PolynomialFeatures と突き合わせる。Rumale の標準化は標本標準偏差（n − 1）で割り、scikit-learn と定義が違うことを実測する。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:20:00Z }
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

標準化は Rumale の `StandardScaler`、多項式特徴量は `PolynomialFeatures` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Rust 版](../rust/09-feature-engineering.md) と対比します。Ruby 版で拾う論点は次の 3 つです。

- **「標準化」の定義がライブラリで違う**。Rumale の `StandardScaler` は件数 − 1 で割る標本標準偏差を使い、scikit-learn（件数で割る母標準偏差）と一致しません。名前が同じでも、突き合わせるまで定義は分かりません
- **組み合わせは標準ライブラリにある**。scikit-learn の `PolynomialFeatures` と同じ順の組は、`Array#repeated_combination` がそのまま返します
- **文字コードは読み込みの引数で指定する**。`CSV.read` に `encoding: "Shift_JIS:UTF-8"` を渡すと、Shift_JIS のファイルを UTF-8 の文字列として読めます。指定を忘れると `CSV::InvalidEncodingError` で止まり、Go 版のように黙って文字が化けることはありません

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード（実データで確認） |
|---------|------|----------|------------------------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

`Boston.csv` は第 2 章の `Table.load` でそのまま読めます。`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します（第 2 章の `Table` は変更しません）。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] Rumale の `StandardScaler` と突き合わせる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項と交互作用の項を加える
  - [ ] Rumale の `PolynomialFeatures` と突き合わせる
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

この章のコードは `lib/getting_started_ml/chapter09/` に置き、技法ごとにファイルを分けます。どのファイルも `module GettingStartedMl::Chapter09` を開き直して関数を足すので、呼び出し側は `Chapter09.encode`・`Chapter09.expand` のように、ファイルを意識せずに呼べます。

| ファイル | 役割 |
|---------|------|
| `dummies.rb` | ダミー変数 |
| `standardizer.rb` | 標準化 |
| `rumale_scaler.rb` | Rumale の標準化の呼び出し |
| `polynomial.rb` | 多項式特徴量 |
| `outliers.rb` | 外れ値の検出 |
| `bike_weather.rb` | 区切り文字・文字コードを指定した読み込みと、表の結合 |
| `linear.rb`・`boston.rb` | 正規方程式による線形回帰、決定係数、Boston データの前処理 |

Rust 版は、この章で「文字コードで読めない」「正規方程式を解けない」という新しい失敗が出てくるので、章の `enum Error` を作りました。Ruby 版は第 1 章から失敗を例外で表しているので、足すのは「正規方程式を解けない」を表す `SingularError` の 1 つだけです。文字コードの失敗は csv gem の `CSV::InvalidEncodingError` がそのまま上がり、無い列の失敗は第 2 章の `KeyError` がそのまま上がります。**包み直さずに素通しするのが、例外の言語の既定の書き方です。**

## 9.4 カテゴリ値をダミー変数にする

### Red: まだ無いモジュールを呼ぶ

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```ruby
def test_先頭を除いたカテゴリを辞書順に返す
  assert_equal %w[low very_low], C.categories(%w[low high very_low low])
end

def test_欠損値はカテゴリに数えない
  assert_equal %w[low], C.categories(["low", "", "high"])
end
```

実行すると、まだ `Chapter09` が無いので、テストのクラスを読み込んだところで止まります。

```text
test/chapter09_test.rb:6:in `<class:Chapter09Test>': uninitialized constant GettingStartedMl::Chapter09 (NameError)

  C = GettingStartedMl::Chapter09
                      ^^^^^^^^^^^
Did you mean?  GettingStartedMl::Chapter02
               GettingStartedMl::Chapter01
               GettingStartedMl::Chapter03
```

Rust 版の `error[E0433]` はコンパイルの段階で止まりましたが、Ruby 版は **テストファイルを読み込んで定数を評価した瞬間** に止まります。どちらも「まだ無い」ことを教えてくれますが、Ruby の `Did you mean?` は似た名前の候補を並べてくれます。

### Green: カテゴリを求めて列を加える

```ruby
# 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す
# （pandas の get_dummies(drop_first: True) と同じ）。
def categories(values)
  values.map(&:strip).reject(&:empty?).uniq.sort.drop(1)
end
```

Rust 版は「並べ替えてから `dedup`（隣り合う重複だけを消す）」という順序に依存する書き方でした。Ruby の `uniq` は **離れた重複も消す** ので、`uniq` と `sort` の順はどちらでも結果が変わりません。

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```ruby
# 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
# 値が一致すれば "1"、それ以外は "0"。元の表は変えずに新しい表を返す。
def encode(table, column, categories)
  dummy_columns = categories.map { |category| "#{column}_#{category}" }
  rows = table.rows.map { |row| Chapter02::Row.new(encode_cells(row, column, categories)) }

  Chapter02::Table.new(columns: (table.columns - [column]) + dummy_columns, rows:)
end

# 1 行のセルから列を除き、ダミー変数のセルを加える。
def encode_cells(row, column, categories)
  value = row.text(column).strip
  dummies = categories.to_h { |category| ["#{column}_#{category}", value == category ? "1" : "0"] }

  row.cells.except(column).merge(dummies)
end
```

`Hash#except` と `Hash#merge` はどちらも **新しい `Hash` を返し**、元の行のセルを変えません。Rust 版は「借用している限り書き換えられない」ことをコンパイラが保証しましたが、Ruby では「新しいものを返すメソッドだけを使う」という書き方の約束で守ります。約束が守られていることは、テストで確かめます。

```ruby
def test_ダミー変数にしても元の表は変わらない
  table = crime_table(%w[low])
  C.encode(table, "CRIME", %w[low])

  assert_equal %w[RM CRIME], table.columns
  assert_equal "low", table.rows.first.text("CRIME")
end
```

セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `split_features_and_target`・`column_means`・`fill_missing` を、ダミー変数の列にもそのまま使えます。

Rumale にも `Preprocessing::OneHotEncoder` がありますが、受け取るのは **整数のカテゴリ番号** で、`drop_first` に当たる指定もありません。`Numo::Int32[0, 2, 1]` を渡すと `[[1.0, 0.0, 0.0], [0.0, 0.0, 1.0], [0.0, 1.0, 0.0]]` のように全カテゴリの列を返しました。文字列のカテゴリを列名つきで扱いたいこの章では、自作のほうが素直です。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### 列ごとの平均と標準偏差

```ruby
def test_訓練データから列ごとの平均と標準偏差を求める
  standardizer = C::Standardizer.fit([row(1.0, 10.0), row(2.0, 10.0), row(3.0, 40.0)])

  assert_in_delta 2.0, standardizer.mean("RM"), 1e-12
  assert_in_delta 20.0, standardizer.mean("LSTAT"), 1e-12
  # 件数（3）で割る標準偏差（母標準偏差）。scikit-learn の StandardScaler と同じ定義
  assert_in_delta Math.sqrt(2.0 / 3), standardizer.std("RM"), 1e-12
  assert_in_delta Math.sqrt(200.0), standardizer.std("LSTAT"), 1e-12
end
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。

`Standardizer` は、第 2 章の `Features` と同じく `Data.define` で作ります。`fit` はクラスメソッドにして、「訓練データから作る」ことを名前で表します。

```ruby
Standardizer = Data.define(:columns, :means, :stds) do
  # 特徴量のすべての列について、平均と標準偏差を求める。
  def self.fit(x)
    raise ArgumentError, "特徴量が 1 件もありません" if x.empty?

    columns = x.first.columns
    stats = columns.map { |column| Chapter09.mean_and_std(x.map { |features| features.value(column) }) }

    new(columns:, means: stats.map(&:first), stds: stats.map(&:last))
  end

  # …mean(column)・std(column)・transform(x)・transform_one(features)
end
```

```ruby
# 平均と、件数で割る標準偏差の組。標準偏差が 0 なら 1 に置き換える。
def mean_and_std(values)
  mean = values.sum / values.size
  std = Math.sqrt(values.sum { |value| (value - mean)**2 } / values.size)

  [mean, std.zero? ? 1.0 : std]
end
```

分散 0 の列（すべて同じ値）は標準偏差を 1 に置き換えます。そうしないと 0 で割ります。Ruby の `Float` を 0.0 で割ると `NaN` か `Infinity` になり、**例外は出ません**（`Integer` を 0 で割ったときだけ `ZeroDivisionError`）。ここは自分で気づいてテストを書くしかありません。

```ruby
def test_すべて同じ値の列は標準化すると0になる
  standardizer = C::Standardizer.fit([row(1.0, 5.0), row(3.0, 5.0)])

  assert_in_delta 1.0, standardizer.std("LSTAT"), 1e-12
  assert_in_delta 0.0, standardizer.transform_one(row(2.0, 5.0)).value("LSTAT"), 1e-12
end
```

`transform_one` は、第 2 章の `Features` の `with(values:)` で値だけを差し替えた新しい特徴量を返します。`Data` の `with` は、指定したメンバーだけを変えた複製を作るメソッドで、Rust の構造体更新記法（`..元の値`）に当たります。

### Rumale の標準化と突き合わせる

Rumale の `Preprocessing::StandardScaler` と比べます。ADR 010 では「母標準偏差か標本標準偏差かを実測で確かめる」としていたので、定義の違いが出る小さなデータを選びました。

```ruby
# 訓練データの値から平均と標準偏差を求め、別の値を標準化する。
# Rumale は行列を受け取るので、1 列の行列にしてから渡す。
def standardize(train, values)
  scaler = Rumale::Preprocessing::StandardScaler.new.fit(column(train))

  scaler.transform(column(values)).to_a.flatten
end

# 値の並びを 1 列の行列にする。
def column(values)
  Numo::DFloat.cast(values.map { |value| [value] })
end
```

`[1.0, 2.0, 3.0, 4.0]` で学習して `1.0` を標準化すると、母標準偏差なら `(1 − 2.5) / √1.25 ≈ −1.3416`、標本標準偏差なら `(1 − 2.5) / √(5/3) ≈ −1.1619` になります。実測の値は **−1.161895003862225** でした。

```ruby
def test_Rumale_の標準化は標本標準偏差で割る
  # 平均 2.5、標本標準偏差 sqrt(5/3)。母標準偏差なら sqrt(1.25) になる
  scaled = C::RumaleScaler.standardize([1.0, 2.0, 3.0, 4.0], [1.0])

  assert_in_delta((1.0 - 2.5) / Math.sqrt(5.0 / 3), scaled.first, 1e-12)
end
```

**Rumale の `StandardScaler` は標本標準偏差（n − 1 で割る）で、scikit-learn・linfa とは定義が違います。** Rumale 2.2.0 のソースを読むと、`fit` は `@std_vec = x.stddev(0)` と Numo の `stddev` を呼んでいるだけでした。Numo の `stddev` は `Numo::DFloat[1.0, 2.0, 3.0, 4.0].stddev` が `1.2909944487358056`（= √(5/3)）を返す、標本標準偏差です。**Rumale が選んだというより、行列ライブラリの既定がそのまま出ている** わけです。

実データでは、自作の値に `√((n − 1) / n)` を掛けると、Rumale の値と 70 件すべてが 10⁻¹² より近くなりました。`test/boston_features_test.rb` で確かめています。

```ruby
# Rumale の StandardScaler は Numo の stddev（件数 − 1 で割る標本標準偏差）を使うので、
# 自作（件数で割る母標準偏差）とは sqrt((n − 1) / n) 倍ずれる
def test_Rumale_の標準化は自作と標本標準偏差の分だけずれる
  x_train = split.x_train
  rm = rm_values(x_train)
  ours = rm_values(C::Standardizer.fit(x_train).transform(x_train))
  ratio = Math.sqrt((rm.size - 1).fdiv(rm.size))

  assert_all_close(ours.map { |value| value * ratio }, C::RumaleScaler.standardize(rm, rm))
end
```

| 言語版 | ライブラリ | 標準偏差の定義 | 自作と一致するか |
|-------|-----------|--------------|----------------|
| Ruby | Rumale `Preprocessing::StandardScaler`（Numo の `stddev`） | 標本標準偏差（n − 1） | **しない**（√((n − 1)/n) 倍ずれる） |
| Python | scikit-learn `StandardScaler` | 母標準偏差（n） | 一致する |
| Rust | linfa-preprocessing `LinearScaler::standard()` | 母標準偏差（n） | 一致する |
| Java | Tribuo `MeanStdDevTransformation` | 標本標準偏差（n − 1） | しない |
| Go | gonum `stat.StdDev` | 標本標準偏差（n − 1） | しない |

Rumale は scikit-learn に似た API（`fit`・`transform`・`fit_transform`）を持ちますが、**API が似ていることと、中身の定義が同じことは別の話** です。この章では自作の `Standardizer`（母標準偏差）を最終の実装にし、Rumale の標準化は比較のためだけに使います。訓練データ 70 件では、Rumale で標準化した値の（件数で割った）標準偏差は 0.9928 になり、1 からわずかにずれます（9.9 節の出力）。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

同じ列を 2 回掛ければ 2 乗の項、違う列どうしを掛ければ **交互作用の項** です。列の組は「重複を許して 2 つ選ぶ」組み合わせで、scikit-learn の `PolynomialFeatures` と同じ順に並べます。

```ruby
def test_重複を許して二つの列を選ぶ組を並べる
  names = C.pairs_with_replacement(%w[RM LSTAT PTRATIO]).map(&:name)

  assert_equal ["RM^2", "RM LSTAT", "RM PTRATIO", "LSTAT^2", "LSTAT PTRATIO", "PTRATIO^2"], names
end
```

Rust 版は二重ループで組を作りましたが、Ruby には **`Array#repeated_combination` がちょうどこの順で組を返す** ので、ループを書く必要がありません。

```ruby
# 2 つの列の組。左と右が同じなら 2 乗の項を表す。
Pair = Data.define(:left, :right) do
  # 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"・"RM LSTAT"）にする。
  def name
    left == right ? "#{left}^2" : "#{left} #{right}"
  end

  # 項の値。2 つの列の値の積。
  def value(features)
    features.value(left) * features.value(right)
  end
end

# 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。
# Array#repeated_combination が、ちょうどこの順で組を返す。
def pairs_with_replacement(columns)
  columns.repeated_combination(2).map { |left, right| Pair.new(left:, right:) }
end
```

`expand` で作った項から、使う項だけを `select` で選びます。無い項を選ぶと第 2 章の `Features#value` の `KeyError` がそのまま上がります。

```ruby
def test_無い列は選べない
  error = assert_raises(KeyError) { C.select([row(2.0, 3.0)], ["RM^2"]) }

  assert_equal "列がありません: RM^2", error.message
end
```

日本語のテスト名は `test_` のあとに続くので、`test_2乗の項…` と数字から書くこともできます。ただ、ほかの言語版のテスト名（Rust 版の `二乗の項…` は識別子が数字で始められないため）とそろえて、`二乗` と書きました。

### Rumale の多項式特徴量と突き合わせる

Rumale には `Preprocessing::PolynomialFeatures` もあります。

```ruby
def test_Rumale_の多項式特徴量と同じ値になる
  ours = C.expand([row(2.0, 3.0)], %w[RM LSTAT]).first.values
  theirs = Rumale::Preprocessing::PolynomialFeatures.new(degree: 2).fit_transform(Numo::DFloat[[2.0, 3.0]])

  # Rumale は先頭に定数項（1）の列を置き、そのあとは scikit-learn と同じ順に並べる
  assert_equal [1.0, *ours], theirs.to_a.first
end
```

Rumale の出力は `[1.0, 2.0, 3.0, 4.0, 6.0, 9.0]` で、先頭の定数項を除けば自作と同じ並びでした。scikit-learn の `PolynomialFeatures` も既定で定数項の列（`include_bias=True`）を付けるので、ここは scikit-learn と同じ振る舞いです。Rumale の出力は列名を持たない行列なので、「RM^2 だけを選ぶ」には列の位置を自分で数える必要があります。列名で項を選びたいこの章では、自作の `expand` と `select` を使います。

## 9.7 外れ値を検出する

**四分位範囲（IQR）** は、第 3 四分位数 Q3 と第 1 四分位数 Q1 の差です。Q1 − 1.5 × IQR より小さい値と、Q3 + 1.5 × IQR より大きい値を外れ値とみなします。

```ruby
# 分位数を求める。位置が値の間にあれば前後の値から線形補間する
# （pandas の quantile の既定と同じ）。
def quantile(values, ratio)
  raise ArgumentError, "値が 1 件もありません" if values.empty?

  sorted = values.sort
  position = (sorted.size - 1) * ratio
  lower = sorted[position.floor]

  lower + ((sorted[position.ceil] - lower) * (position - position.floor))
end

# 四分位範囲（IQR）の factor 倍より外側にある値を true にした並びを返す。
def iqr_outliers(values, factor = DEFAULT_K)
  q1 = quantile(values, 0.25)
  q3 = quantile(values, 0.75)
  range = (q1 - (factor * (q3 - q1)))..(q3 + (factor * (q3 - q1)))

  values.map { |value| !range.cover?(value) }
end
```

Rust 版は、`f64` が `Ord` を実装していないため `sort_by(f64::total_cmp)` と書く必要がありました。Ruby の `Array#sort` は `Float` の並びをそのまま並べ替えます。その代わり `NaN` が混ざると、`[1.0, Float::NAN, 0.5].sort` は `ArgumentError`（comparison of Float with NaN failed）になります。**型で先に知らせるか、実行して初めて知るか** の違いです。

範囲は `Range` で表し、`cover?` で内側かどうかを判定しました。「下限以上かつ上限以下」を 1 つの値として持てるので、条件式を 2 つ並べるより読み違えにくくなります。

引数名は `q`・`k` ではなく `ratio`・`factor` にしました。RuboCop の `Naming/MethodParameterName` が 3 文字未満の引数名を咎めるためです。機械学習の慣例の `x`・`t`・`y` だけは `.rubocop.yml` で許していますが、それ以外は意味の分かる名前にそろえます。

外れ値を **訓練データからだけ** 取り除く関数も足します。テストデータは現実に来るデータなので、外れ値を含んだまま評価します。

```ruby
# 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
def remove_target_outliers(split)
  outliers = iqr_outliers(split.t_train)
  kept = split.x_train.zip(split.t_train).reject.with_index { |_, index| outliers[index] }
  x_train, t_train = kept.transpose

  split.with(x_train: x_train || [], t_train: t_train || [])
end
```

`reject.with_index` は、ブロックに添字も渡して除外する書き方です。Rust 版の「残す行番号を集めてから引き直す」という 2 段の処理が 1 行になります。`transpose` は空の配列に対して `[]` を返し、多重代入すると両方 `nil` になるので、`|| []` で空の並びに戻しています（第 2 章の `to_split` と同じ扱い）。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

区切り文字と文字コードを引数で受け取る読み込みを書きます。

```ruby
# Shift_JIS のファイルを読み、UTF-8 の文字列にする指定。
SHIFT_JIS = "Shift_JIS:UTF-8"
# UTF-8 のファイルを読む指定。
UTF8 = "UTF-8"

# 1 行目を列名として読み込む。文字コードが合わなければ CSV::InvalidEncodingError になる。
def load_table(file, col_sep: ",", encoding: UTF8)
  csv = CSV.read(file, headers: true, col_sep:, encoding:)
  rows = csv.map { |record| Chapter02::Row.new(record.to_h.transform_values(&:to_s)) }

  Chapter02::Table.new(columns: csv.headers, rows:)
end
```

`"Shift_JIS:UTF-8"` は「外部の文字コード : 内部の文字コード」という Ruby の書き方で、**Shift_JIS のバイト列を読みながら UTF-8 の文字列に変換する** 指定です。Rust 版は文字コードを `enum Encoding` にして、打ち間違いをコンパイルエラーにしました。Ruby 版は文字列を定数に置いて、打ち間違いを「未定義の定数」の `NameError` にします。定数の名前を打ち間違えれば実行時に止まります。ところが、**定数の中身を打ち間違えても止まりません**。`encoding: "Shift_JlS:UTF-8"`（I を小文字の l にした）で ASCII だけのファイルを読んでみると、csv gem は `warning: Unsupported encoding Shift_JlS ignored` という警告を出しただけで、指定を無視して読み進めました。文字コードの名前を 1 か所の定数にまとめ、Shift_JIS の文字を含む架空のデータでテストしておくのは、この「警告だけで先へ進む」振る舞いに備えるためです。

### UTF-8 として読むと例外になる

テストでは、`"...".encode("Shift_JIS")` で作った架空の 2 行を一時ファイルに書き、読み込みを確かめます。

```ruby
def weather_csv
  "weather_id,weather\n1,晴れ\n2,雨\n".encode("Shift_JIS")
end

def test_shift_jisのファイルを読み込む
  with_file("weather.csv", weather_csv) do |path|
    table = C.load_table(path, encoding: C::SHIFT_JIS)

    assert_equal(%w[晴れ 雨], table.rows.map { |row| row.text("weather") })
  end
end

def test_shift_jisのファイルはutf8として読めない
  with_file("weather.csv", weather_csv) do |path|
    assert_raises(CSV::InvalidEncodingError) { C.load_table(path) }
  end
end
```

実データの `weather.csv` でも同じことを確かめました。`File.read` は例外を出さずに読めますが、中身の文字列は `valid_encoding?` が `false` を返します。**Ruby の文字列は「UTF-8 と名乗っているが、中身は UTF-8 として正しくない」状態を持てる** ので、文字列を作った時点では気づけません。csv gem が行を解析する段階で `CSV::InvalidEncodingError` を投げて、初めて止まります。

```ruby
def test_weather_csvはutf8として読めない
  path = data_file("weather.csv")

  refute File.read(path).valid_encoding?
  assert_raises(CSV::InvalidEncodingError) { C.load_table(path) }
  assert_equal 3, C.load_weather(path).rows.size
end
```

| 言語版 | Shift_JIS を UTF-8 として読むと |
|-------|------------------------------|
| Ruby | `File.read` は読めるが `valid_encoding?` が `false`。`CSV.read` は `CSV::InvalidEncodingError` を投げる |
| Rust | `String::from_utf8` が `Err`。`String` は正しい UTF-8 であることを型で保証する |
| Java | `Files.readAllLines` が `MalformedInputException` を投げる |
| Go | `golang.org/x/text/encoding/japanese` が黙って U+FFFD に置き換える（失敗にならない） |

Rust は「正しくない UTF-8 の `String` は作れない」ので、読んだ瞬間に分かります。Ruby は「正しくない文字列も作れる」ので、**それを使う処理（ここでは CSV の解析）まで気づくのが遅れます**。今回は csv gem が検査してくれましたが、`File.read` した文字列を自分で `split` するような書き方なら、化けたまま先へ進みます。

### 対応表を作って結合する

天気 ID をキーにした `Hash` を作り、自転車の表を 1 行ずつ引きます（内部結合）。天気の表に無い ID の行は残しません。

```ruby
# 天気 ID をキーにして、自転車の表に天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。
def join_weather(bike, weather)
  by_id = weather.rows.to_h { |row| [row.text(WEATHER_KEY), row] }
  rows = bike.rows.filter_map { |row| join_row(row, by_id[row.text(WEATHER_KEY)]) }

  Chapter02::Table.new(columns: bike.columns + (weather.columns - [WEATHER_KEY]), rows:)
end

# 自転車の行に天気の行のセルを加える。天気の行が無ければ nil を返す。
def join_row(row, found)
  Chapter02::Row.new(row.cells.merge(found.cells.except(WEATHER_KEY))) if found
end
```

`filter_map` は「変換して、`nil`・`false` になったものを捨てる」メソッドです。`join_row` が「相手が無ければ `nil`」を返すので、変換と内部結合の絞り込みが 1 回の走査で済みます。

天気ごとの平均は、`group_by` で集めてから多い順に並べます。

```ruby
# 天気ごとの平均利用者数を、多い順に並べる。
def mean_count_by_weather(joined)
  joined.rows.group_by { |row| row.text("weather") }
        .map { |weather, rows| [weather, rows.sum { |row| row.number("cnt") } / rows.size] }
        .sort_by { |_weather, mean| -mean }
end
```

Rust 版は `HashMap` の順が定まらないため `Vec<(String, f64, usize)>` に自分で集計しました。Ruby の `Hash`（と `group_by` の結果）は **挿入した順を保つ** ので、そのまま集計に使えます。

## 9.9 特徴量の効果を測る

### Boston データの前処理

`prepare_boston` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

```ruby
def prepare_boston(csv_file, test_size:, seed:)
  columns, rows, prices = boston_features_and_prices(Chapter02::Table.load(csv_file))
  split = Chapter02.split_train_test(rows, prices, test_size:, seed:)
  means = Chapter02.column_means(split.x_train, columns)

  split.with(x_train: Chapter02.fill_missing(split.x_train, columns, means),
             x_test: Chapter02.fill_missing(split.x_test, columns, means))
end

# CRIME をダミー変数にしてから、特徴量の列・行・価格に分ける。価格は数値に読み直す。
def boston_features_and_prices(table)
  crimes = table.rows.map { |row| row.text(BOSTON_CATEGORY) }
  encoded = encode(table, BOSTON_CATEGORY, categories(crimes))
  columns, rows, labels = Chapter02.split_features_and_target(encoded, BOSTON_TARGET)

  [columns, rows, labels.map { |label| Float(label) }]
end
```

第 2 章の `split_features_and_target` は正解ラベルを文字列で返すので、`PRICE` は `Float(label)` で数値に読み直します。第 1 章と同じく、`to_f` ではなく `Float()` を使うので、数値として読めない値は `ArgumentError` で止まります（`"abc".to_f` は黙って `0.0` を返します）。第 2 章の `split_train_test` は正解ラベルの型を問わないので、数値の正解にもそのまま使えます。

### 線形回帰と決定係数

決定係数を測るために、Rust 版と同じく、この章に正規方程式を解く最小の `LinearModel` を置きました。第 7 章の重回帰とは独立に、この章だけで完結させています。行列の演算は `Array` の配列で書き、コレスキー分解と前進・後退代入も自作しました。Ruby の `matrix` は Ruby 3.1 から標準の gem から外れて `Gemfile` に書く必要があり、Rumale が依存する Numo には連立方程式を解く関数がありません（`numo-linalg` という別の gem が要る）。依存を増やさないことを選びました。

```ruby
LinearModel = Data.define(:intercept, :weights) do
  # 先頭に切片の列（すべて 1）を足し、正規方程式 XᵀX β = Xᵀt を解く。
  def self.fit(rows, t)
    design = rows.map { |row| [1.0, *row] }
    transposed = design.transpose
    normal = transposed.map { |left| transposed.map { |right| Chapter09.dot(left, right) } }
    beta = Chapter09.solve_cholesky(normal, transposed.map { |column| Chapter09.dot(column, t) })

    new(intercept: beta.first, weights: beta.drop(1))
  end

  # 並びを予測する。
  def predict(rows)
    rows.map { |row| intercept + Chapter09.dot(weights, row) }
  end
end
```

`XᵀX` の (i, j) 成分は「i 列目と j 列目の内積」なので、転置した行（= 元の列）どうしの内積を並べれば作れます。

```ruby
# L の (row, col) 成分。
def cholesky_entry(matrix, lower, row, col)
  rest = matrix[row][col] - dot(lower[row].first(col), lower[col].first(col))
  return rest / lower[col][col] unless row == col
  raise SingularError if rest <= 0.0

  Math.sqrt(rest)
end

# Lᵀ x = y を後ろから解く。Lᵀ の行と列を逆順にすると下三角になるので、前から解いて逆順に戻す。
def backward_substitution(lower, vector)
  reversed = lower.transpose.reverse.map(&:reverse)

  forward_substitution(reversed, vector.reverse).reverse
end
```

後退代入は、最初は添字で後ろから回すループで書きましたが、RuboCop の `Metrics/AbcSize`（上限 17）を超えました。上限は緩めずに、「上三角の行と列を逆順にすると下三角になる」ことを使って、前進代入の関数を使い回す形に直しました。**添字の計算が消え、後退代入が前進代入の言い換えであることがコードに現れます。**

対角が 0 以下になれば「列が互いに独立でない」として `SingularError` を投げます。

```ruby
def test_同じ値の列が二つあれば解けない
  error = assert_raises(C::SingularError) { C::LinearModel.fit([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]], [1.0, 2.0, 3.0]) }

  assert_equal "特徴量の列が互いに独立でないため、正規方程式を解けません", error.message
end
```

```ruby
# 正規方程式を解けない（列が互いに独立でない）ときの失敗。
class SingularError < StandardError
  def initialize(message = "特徴量の列が互いに独立でないため、正規方程式を解けません")
    super
  end
end
```

既定のメッセージを `initialize` の既定引数にしておくと、`raise SingularError` とクラスだけを書けば済みます。`super` を括弧なしで呼ぶと、受け取った引数をそのまま親に渡します。

特徴量の組ごとの評価は `score_feature_set` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `Standardizer` を `fit` し、両方を `transform` してから学習します。

```ruby
def score_feature_set(split, columns, terms)
  x_train, x_test = standardized_terms(split, columns, terms)
  model = LinearModel.fit(x_train, split.t_train)

  Scores.new(train: r_squared(split.t_train, model.predict(x_train)),
             test: r_squared(split.t_test, model.predict(x_test)))
end

# 訓練データとテストデータで項を作って選び、訓練データの平均と標準偏差で標準化した値の並びにする。
def standardized_terms(split, columns, terms)
  train, test = [split.x_train, split.x_test].map { |x| select(expand(x, columns), terms) }
  standardizer = Standardizer.fit(train)

  [train, test].map { |x| standardizer.transform(x).map(&:values) }
end
```

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

### 実データで測る

```bash
bundle exec rake 'run[chapter09]'
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
Rumale で標準化した RM: 平均 0.00, 標準偏差 0.9928
決定係数:
  元の特徴量（3 列）: 訓練 0.6643, テスト 0.2932
  2 乗の項を追加（6 列）: 訓練 0.8242, テスト 0.4875
  交互作用の項も追加（9 列）: 訓練 0.8302, テスト 0.4413
訓練データの PRICE の外れ値: 9 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.7633, テスト 0.6437
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

「標準化した訓練データの RM」は、標準化した訓練データにもう一度自作の `Standardizer.fit` を当て、その平均と標準偏差を表示しています。「Rumale で標準化した RM」は、Rumale で標準化した値に同じことをしたもので、標準偏差が 0.9928（= √(69/70)）になります。

- **2 乗の項** を加えると、テストデータの決定係数が 0.2932 から 0.4875 に上がりました。向きは Rust 版（0.5239 → 0.6804）と同じです
- **交互作用の項** も加えると、訓練データは上がり（0.8242 → 0.8302）、テストデータは下がりました（0.4875 → 0.4413）。Rust 版はテストデータもわずかに上がり、Java 版は下がりました。100 件のデータでは、分け方によって結論が揺れる判断です
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.4875 から 0.6437 に **上がりました**。Rust 版（0.6804 → 0.5882）・Java 版・Kotlin 版はどれも下がっており、**向きが逆** です。Ruby 版の分割では訓練データの外れ値が 9 件（Rust 版は 4 件）と多く、外れ値が学習を大きく引っ張っていたと読めます。テストデータ 30 件では、外れ値の扱いの良し悪しも分割しだいで変わります
- **天気ごとの平均利用者数** は Rust 版・Java 版・Go 版と同じ値になりました。集計は乱数を使わないので、言語をまたいで一致します

決定係数の値がほかの言語版と違うのは、第 2 章と同じく乱数の違いです。Ruby 版は `Random.new(0)` と `Array#shuffle(random:)` で並べ替えるので、同じシードでも訓練データとテストデータに入る行がほかの言語版と違います。**言語版をまたいで比べられるのは「向き」であって「値」ではなく、その向きすら、この小さなデータでは分割で変わることがあります。**

この出力は `test/boston_features_test.rb` で固定しています。学習データが無い環境では、Minitest の `skip` でスキップします。

## 9.10 Notebook による探索と可視化

Ruby 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから `bundle exec rake check` をかけると、RuboCop が 21 件を指摘しました。上限は緩めずに、次のように直しました。

- **`Metrics/AbcSize`（上限 17）** — `run`・`prepare_boston`・`score_feature_set`・`join_weather` などが 17.8〜20.0 で超えました。`run` は表示する行を返す小さなメソッド（`split_lines`・`standardized_rm`・`scores_lines`・`weather_line`）に分け、`prepare_boston` はダミー変数から価格の読み直しまでを `boston_features_and_prices` に、`score_feature_set` は項の作成と標準化を `standardized_terms` に切り出しました。どれも「名前を付けられるまとまり」で切っているので、分けたあとのほうが読みやすくなります
- **`Naming/MethodParameterName`** — コレスキー分解の `a`・`b`・`i`・`j`、分位数の `q`、倍率の `k` が 3 文字未満で咎められました。`matrix`・`vector`・`row`・`col`・`ratio`・`factor` にしました
- **`Metrics/ClassLength`（上限 100 行）** — テストのクラスが 184 行になりました。技法ごとに `chapter09_test.rb`（ダミー変数・標準化・多項式特徴量）、`chapter09_regression_test.rb`（外れ値・線形回帰・決定係数）、`chapter09_tables_test.rb`（読み込み・結合・Boston の前処理）の 3 つに分けました
- **`Style/Documentation`** — `module Chapter09` を開き直すファイルのうち、コメントを付けていない 2 つが咎められました。ファイルごとに「第 9 章の何か」を 1 行書きました

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、`Standardizer` は次の形で公開しています。

| API | 内容 |
|-----|------|
| `Standardizer.fit(x)` | すべての列の平均と標準偏差（件数で割る）を求める |
| `standardizer.transform(x)` | 並びを標準化する |
| `standardizer.transform_one(features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardizer.mean(column)`・`std(column)` | 列名で平均・標準偏差を読む |

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Ruby の TDD で自作し、標準化と多項式特徴量を Rumale と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `categories`・`encode` | —（Rumale の `OneHotEncoder` は整数のカテゴリ番号だけを受け取る） | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | Rumale の `StandardScaler` | **Rumale は標本標準偏差**（Numo の `stddev`）。分散 0 の列。テストデータの平均を使わない |
| 多項式特徴量 | `pairs_with_replacement`・`expand`・`select` | Rumale の `PolynomialFeatures`（先頭の定数項を除いて一致） | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile`・`iqr_outliers` | — | `NaN` が混ざると `sort` が実行時に失敗する |
| 表の結合 | `load_table`・`join_weather` | csv gem | 区切り文字と文字コード、内部結合で消える行 |

Ruby らしさが出たのは次の 3 点です。

1. **ライブラリの定義は突き合わせるまで分からない** — Rumale は scikit-learn に似た API を持つが、標準化は Numo の `stddev` をそのまま使った標本標準偏差だった。API の見た目の近さは定義の同一性を保証しない
2. **標準ライブラリの語彙で書ける** — 組み合わせは `repeated_combination`、除外は `reject.with_index`、結合は `filter_map`、集計は `group_by`。Rust 版で二重ループや添字で書いたところが、名前のついたメソッドになる
3. **文字列は正しくない中身も持てる** — Shift_JIS のファイルを UTF-8 として `File.read` しても例外にならず、csv gem の解析で初めて `CSV::InvalidEncodingError` になる。Rust の `String` のように型が保証してくれない分、文字コードは読み込みの引数で明示する

次の章では、ロジスティック回帰とランダムフォレストを自作し、ダックタイピングでモデルを共通化して Rumale と並べます。
