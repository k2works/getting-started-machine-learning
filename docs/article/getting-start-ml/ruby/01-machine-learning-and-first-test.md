---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を Ruby の TDD で実装して正解率を測る。動的型付けと例外による失敗の表し方を Python 版・Rust 版と対比し、メソッド名の衝突を実行時にしか検出できない例を見る。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T10:55:00Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを Ruby で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。「ルールを人間が書く」とはどういうことかを先に体験しておくと、第 3 章で「ルールをデータから学ばせる」ことの意味がはっきりします。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。Ruby 版では次の 2 つと対比します。1 つは Python 版で、同じ動的型付けのスクリプト言語どうし、書き方がよく似ています。Ruby にも scikit-learn に似た API を持つ Rumale という機械学習ライブラリがあり、第 3 章から使います（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。2 つめは [TypeScript 版](../typescript/01-machine-learning-and-first-test.md) と [Kotlin 版](../kotlin/01-machine-learning-and-first-test.md) で、型の検査があるかないかの違いです。Ruby 版では型注釈（RBS・Steep）を使わず、型の誤りはテストで捕まえます。その代償がこの章のうちに一度、目に見える形で現れます。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```ruby
def predict_by_rule(features)
  features.age_group == KINOKO_AGE_GROUP ? KINOKO : TAKENOKO
end
```

この書き方では、ルールの良し悪しは人間の観察力に依存します。特徴量が 3 つなら何とかなりますが、20 個・100 個になると人間には手に負えません。

機械学習は、この「ルール」をデータから自動で作ります。人間が与えるのは「入力（特徴量）」と「正解（ラベル）」の組で、ルールそのものはアルゴリズムが決めます。第 3 章で決定木を実装すると、「年代が 20 ならきのこ」に相当する分岐が、データから自動で決まる様子を見られます。

| | 従来のプログラミング | 機械学習 |
|---|---|---|
| 人間が書くもの | ルール | データと、学習のさせ方 |
| 出力 | 判定結果 | ルール（モデル）と、それを使った判定結果 |
| 得意なこと | 仕様がはっきりしている問題 | 仕様を言葉にしにくい問題 |
| 説明のしやすさ | コードを読めば分かる | モデルによる（決定木は読める、ニューラルネットは難しい） |

### 機械学習のワークフロー

本シリーズを通して、次の流れを繰り返します。

1. **データを集める・読み込む** — この章で CSV を読み込みます
2. **前処理する** — 欠損値を埋め、訓練データとテストデータに分けます（第 2 章）
3. **モデルを学習させる** — アルゴリズムにルールを作らせます（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率を実装します）
5. **改善する** — 特徴量を足す、別のモデルを試す、正則化する（第 9〜12 章）

この章では 1・4 を、いちばん単純な形で通します。**端から端まで通す**のが目的で、精度は求めません。

### 分類と回帰

教師あり学習は、予測するものの型で 2 つに分かれます。

| 種類 | 予測するもの | 例 | 本シリーズで扱う章 |
|------|------------|-----|-----------------|
| 分類 | どのグループに属するか（離散値） | きのこ派／たけのこ派、アヤメの種類、生存／死亡 | 第 1・3・8・10・11 章 |
| 回帰 | 数値（連続値） | 映画の興行収入、住宅価格 | 第 7・9・12 章 |

この章は分類です。正解ラベルが「きのこ」「たけのこ」の 2 つなので、二値分類にあたります。

## 1.3 題材とデータ

### データの入手と配置

本シリーズの学習データは、書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、リポジトリの `tmp/` に置いてから、リポジトリのルートで次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

`apps/data/sukkiri-ml/` に学習データが配置されます。このディレクトリは `.gitignore` の対象なので、誤ってコミットされることはありません。すべての言語版が同じデータを参照します。

### KvsT.csv

この章で使うのは `KvsT.csv` です。19 人分のデータが次の 4 列で記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| 身長 | 身長（cm） | 整数 |
| 体重 | 体重（kg） | 整数 |
| 年代 | 年代 | 10, 20, 30, 40 のいずれか |
| 派閥 | きのこの山派かたけのこの里派か | `きのこ` または `たけのこ` |

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**BOM については、Ruby では読み方しだいで結果が変わります。** 後で確かめます。

## 1.4 TODO リストの作成

仕様をそのままコードにするには大きすぎるので、まず TODO リストに分解します。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

**TODO リスト**:

- [ ] CSV を読み込む
  - [ ] BOM 付き CSV の 1 行を人物として読み込む
  - [ ] 複数行を行の順に読み込む
- [ ] 特徴量と正解ラベルに分ける
- [ ] ルールで派閥を判定する
  - [ ] 20 代ならきのこ派と判定する
  - [ ] 20 代以外ならたけのこ派と判定する
- [ ] 正解率を計算する
- [ ] 実データで正解率を表示する

## 1.5 開発環境の準備

### プロジェクトの構成

実装は `apps/ruby/` に置きます。Bundler のプロジェクトが 1 つ、その中に章ごとのモジュールを並べる構成です。

```text
apps/ruby/
├── .ruby-version
├── .rubocop.yml
├── .bundle/
│   └── config                  # gem の置き場
├── Gemfile
├── Gemfile.lock
├── Rakefile                    # check・run のタスク
├── lib/
│   ├── getting_started_ml.rb   # モジュールの一覧
│   └── getting_started_ml/
│       ├── dataset.rb          # 学習データの場所
│       └── chapter01.rb        # 第 1 章
└── test/
    ├── test_helper.rb          # SimpleCov と Minitest の準備
    ├── dataset_test.rb
    ├── chapter01_test.rb
    └── kvst_data_test.rb       # 実データを使うテスト
```

`Gemfile` は次のとおりです。

```ruby
# frozen_string_literal: true

source "https://rubygems.org"

ruby ">= 3.3"

gem "csv", "~> 3.3"

group :development, :test do
  gem "minitest", "~> 6.0"
  gem "rake", "~> 13.0"
  gem "rubocop", "~> 1.91", require: false
  gem "simplecov", "~> 1.3", require: false
end
```

`csv` を `Gemfile` に書いているのは、Ruby 3.4 から csv が標準の gem から外れるためです。いまの Ruby 3.3 では書かなくても動きますが、版を上げた日に `require "csv"` が突然失敗しないように、依存を明示しておきます。

`.bundle/config` には gem の置き場を書きます。

```yaml
---
BUNDLE_PATH: "vendor/bundle"
```

これで `bundle install` した gem は `apps/ruby/vendor/bundle` に入り、システムの Ruby を汚しません。Python 版の仮想環境（`.venv`）に近い役割です。

Ruby の開発環境は `nix develop .#ruby` に入ると揃います。Ruby 3.3.10、Bundler 2.7.2 です。**macOS に付属する Ruby 2.6 は対象外です。** `Data.define`（Ruby 3.2 以降）や、ハッシュの値の省略記法（Ruby 3.1 以降）を使うので、2.6 では動きません。`Gemfile` の `ruby ">= 3.3"` が、古い Ruby で `bundle install` したときに止めてくれます。

```bash
nix develop .#ruby
cd apps/ruby
bundle install
```

### 環境確認テスト

いちばん最初に書くのは、環境が動くことを確かめるテストです。学習データの置き場を返すメソッドから始めます。

```ruby
# frozen_string_literal: true

module GettingStartedMl
  # 学習データのディレクトリを求める。
  module Dataset
    # 実データの置き場をテストや CI から差し替えるための環境変数の名前。
    ENV_NAME = "ML_DATA_DIR"

    # 既定の置き場。テストは apps/ruby で走るので、相対パスで apps/data に届く。
    DEFAULT_DIR = File.join("..", "data", "sukkiri-ml")

    # 学習データのディレクトリを返す。環境変数はテストで差し替えられるように引数で受け取る。
    def self.dir(env = ENV)
      value = env[ENV_NAME]
      value.nil? || value.empty? ? DEFAULT_DIR : value
    end
  end
end
```

`env = ENV` が既定値つきの引数です。ふだんは本物の環境変数 `ENV` を読み、テストでは普通の `Hash` を渡して差し替えます。`ENV` と `Hash` は別のクラスですが、どちらも `[]` で値を引けるので、このメソッドから見れば区別が要りません。**型ではなく「そのメソッドに応答するか」で扱う**、いわゆるダックタイピングです。Rust 版では同じことをクロージャ（`impl Fn(&str) -> Option<String>`）の引数で書き、型で宣言しました。

先頭の `# frozen_string_literal: true` は、ファイル内の文字列リテラルを変更不可にする指示です。RuboCop が全ファイルに求めます。

テストは `test/` に別ファイルで書きます。

```ruby
# frozen_string_literal: true

require "test_helper"

class DatasetTest < Minitest::Test
  def test_環境変数が無ければ既定の場所を使う
    assert_equal File.join("..", "data", "sukkiri-ml"), GettingStartedMl::Dataset.dir({})
  end

  def test_環境変数があればその場所を使う
    assert_equal "/tmp/data", GettingStartedMl::Dataset.dir({ "ML_DATA_DIR" => "/tmp/data" })
  end

  def test_環境変数が空なら既定の場所を使う
    assert_equal File.join("..", "data", "sukkiri-ml"), GettingStartedMl::Dataset.dir({ "ML_DATA_DIR" => "" })
  end
end
```

Minitest の約束は 2 つだけです。

1. **`Minitest::Test` を継承したクラスに、`test_` で始まるメソッドを書く。** そのメソッドがテストになります。Python の unittest と同じ流儀です
2. **`assert_equal 期待値, 実際の値` の順に書く。** Rust の `assert_eq!(left, right)` とは順番の慣習が逆です

メソッド名に日本語が使えるのは Rust と同じです。`test_` の後ろを日本語にして、振る舞いをそのまま名前にしています。

`test_helper.rb` は各テストの先頭で読み込む準備のファイルです。

```ruby
# frozen_string_literal: true

require "simplecov"
SimpleCov.start do
  skip "/test/"
  skip "/vendor/"
end

require "tmpdir"
require "minitest/autorun"
require "getting_started_ml"
```

**SimpleCov はテスト対象を読み込むより前に始める**必要があります。後から始めると、それまでに読み込んだファイルの行が計測されません。`skip` はカバレッジの集計から外すパスの指定です（テストコード自身と、`vendor/` の gem）。

## 1.6 ルールで派閥を判定する

TODO リストは CSV の読み込みから始まっていますが、**いちばん中心にある「判定」から**着手します。CSV の読み込みは外側の関心事で、判定の仕様とは独立だからです。

### Red: まだ無いものを呼ぶ

テストを先に書きます。

```ruby
class Chapter01Test < Minitest::Test
  C = GettingStartedMl::Chapter01

  def features(height, weight, age_group)
    C::Features.new(height:, weight:, age_group:)
  end

  def test_二十代はきのこ派と判定する
    assert_equal C::KINOKO, C.predict_by_rule(features(170, 60, 20))
  end
end
```

`Chapter01` も `Features` もまだありません。実行すると、このテストは `NameError` で失敗します。

ここが Rust 版・Kotlin 版との違いです。静的型付け言語の Red はコンパイルエラーで、テストが走る前に止まりました。**Ruby の Red は実行時エラー**で、Python 版と同じく、テストを走らせて初めて「無い」と分かります。未定義の定数を参照しても、ファイルを読み込んだ時点では何も言われません。

`C = GettingStartedMl::Chapter01` と、長いモジュール名に短い別名を付けている点に注目してください。これには理由があります（1.11 節で扱います）。

`features(height:, weight:, age_group:)` の `height:` は、Ruby 3.1 から使えるハッシュの値の省略記法で、`height: height` と同じ意味です。Rust の構造体のフィールドの省略記法と同じ発想です。

### Green: 値オブジェクトとルール

特徴量の型を `Data.define` で作ります。

```ruby
# 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
Person = Data.define(:height, :weight, :age_group, :faction)

# 判定の手がかりになる特徴量。正解ラベルを持たない。
Features = Data.define(:height, :weight, :age_group)
```

`Data.define` は Ruby 3.2 で入った、**不変の値オブジェクト**を作る仕組みです。1 行で次がそろいます。

| 振る舞い | 中身 | ほかの言語版だと |
|---------|------|----------------|
| キーワード引数のコンストラクタ | `Features.new(height: 170, weight: 60, age_group: 20)`。足りない・余計な引数は `ArgumentError` | Kotlin の `data class`、Python の `@dataclass(frozen=True)` |
| 読み取りメソッド | `features.age_group` | 同上 |
| 値による比較 | `==` がフィールドの値で比べる | Rust の `#[derive(PartialEq)]` |
| 不変 | セッターが無い | Rust の既定の不変 |

古くからある `Struct` は、フィールドを後から書き換えられます。特徴量や学習データの行を途中で書き換える理由は無いので、`Data` を選びました。**「引数の個数を間違えたら落ちる」ことが、型の宣言を書かない Ruby では貴重な早期の検査**になります。

ルールの本体は次のとおりです。

```ruby
# 「20 代ならきのこ派」というルールの年代。
KINOKO_AGE_GROUP = 20

# きのこ派の呼び名。
KINOKO = "きのこ"
# たけのこ派の呼び名。
TAKENOKO = "たけのこ"

# 人間が決めたルールで派閥を判定する。
def predict_by_rule(features)
  features.age_group == KINOKO_AGE_GROUP ? KINOKO : TAKENOKO
end
```

TDD の手順としては、まず `KINOKO` を返すだけの仮実装で Green にし、次の三角測量で本実装に進めています。

### 三角測量

年代が 20 以外のすべてでたけのこ派になることを確かめます。

```ruby
def test_二十代以外はたけのこ派と判定する
  [10, 30, 40, 50].each do |age_group|
    assert_equal C::TAKENOKO, C.predict_by_rule(features(170, 60, age_group))
  end
end
```

配列に `each` とブロックを渡して回します。Rust 版の `for age_group in [10, 30, 40, 50]` と同じ形ですが、Ruby では `for` 文よりもブロックを渡すほうが普通です。Minitest の `assert_equal` は失敗しても例外を投げてそのテストを止めるだけなので、どの値で落ちたかはメッセージの期待値と実際の値から読み取ります。

### モジュールの形

章の関数はモジュールにまとめ、`module_function` を付けます。

```ruby
module GettingStartedMl
  # 第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。
  module Chapter01
    # ...定数と Data.define...

    module_function

    def predict_by_rule(features)
      # ...
    end
  end
end
```

`module_function` より後に書いたメソッドは、**`Chapter01.predict_by_rule(...)` とモジュールから直接呼べる関数**になります。クラスを作ってインスタンスを生成するほどではない、状態を持たない関数の集まりに使う書き方です。Python のモジュールの関数、Rust の `pub fn` に近い立ち位置です。この書き方には落とし穴が 1 つあり、1.11 節で踏みます。

## 1.7 失敗を例外で表す

CSV の読み込みに進む前に、**失敗をどう表すか**を決めます。Rust 版では `enum Error` を定義し、失敗しうる関数は `Result<T, Error>` を返しました。Ruby は逆に、失敗は**例外を投げて**表します。新しい例外クラスは作らず、標準の例外を使います。

| 失敗 | Ruby 版 | Rust 版 |
|------|---------|---------|
| 数値として読めない | `ArgumentError` | `Error::NotANumber` |
| 列が無い | `KeyError` | `Error::MissingColumn` |
| 予測と正解ラベルの件数が違う | `ArgumentError` | `Error::LengthMismatch` |
| CSV を開けない | `Errno::ENOENT` など（標準ライブラリが投げる） | `Error::Io` |

`ArgumentError` は「引数の値がおかしい」、`KeyError` は「キーが無い」を表す標準の例外で、`Hash#fetch` が投げるのも `KeyError` です。意味が合う標準の例外があるなら、それを使うほうが読み手に伝わります。

言語ごとの失敗の表し方を並べると、Ruby の位置が分かります。

| 言語版 | 失敗の表し方 | 網羅性の検査 |
|--------|------------|------------|
| Rust | `Result<T, Error>` と `enum Error` | `match` の網羅性をコンパイラが検査する |
| Go | `(値, error)` の多値返却 | されない |
| Java | 検査例外 `throws` | 宣言はあるが、種類の網羅は検査されない |
| Ruby・Python・TypeScript・Kotlin | 例外 | されない |

Ruby では、メソッドがどの例外を投げうるかをどこにも宣言しません。**呼び出し側が知る手段はドキュメントとテストだけ**です。そこで、失敗の種類ごとにテストを書き、「この入力ならこの例外とこのメッセージ」を仕様として固定します（1.8 節）。

## 1.8 CSV を読み込む

### BOM が取り除かれるかどうかは読み方で決まる

本シリーズを通しての「BOM の落とし穴」です。Python 版・Kotlin 版・Java 版・C# 版・Go 版では、BOM 付きの CSV を素直に読むと、先頭の列名が `"\uFEFF身長"` になり、「身長」で引けませんでした。Rust 版では csv クレートが自分で BOM を取り除きました。

Ruby の csv は、**読み方によって結果が変わります**。

| 読み方 | 先頭の列名 |
|--------|-----------|
| `CSV.foreach(path, headers: true)`・`CSV.read(path, headers: true)`（ファイルを開く） | `"身長"`（BOM が取り除かれる） |
| `CSV.parse(File.read(path), headers: true)`（文字列を渡す） | `"\uFEFF身長"`（BOM が残る） |
| `CSV.parse(File.read(path, encoding: "bom\|utf-8"), headers: true)` | `"身長"` |

ファイルを開く読み方なら、csv が BOM を取り除きます。いったん `File.read` で文字列にしてから `CSV.parse` に渡すと、csv から見れば「BOM で始まる文字列」でしかないので、そのまま列名に残ります。文字列から読むなら、ファイルを読む段階で `encoding: "bom|utf-8"` を指定して BOM を落とします。Python 版の `encoding='utf-8-sig'` と同じ役割の指定です。

Rust 版の結論は「どの層が面倒を見てくれるかはライブラリごとに違う」でした。Ruby では**同じライブラリの中でも、入口によって違います**。本章ではファイルを開く `CSV.foreach` を使い、BOM の除去を書きません。そのかわり、その前提をテストで固定します。

```ruby
def test_BOM_付きの_CSV_を列名で読み込む
  Dir.mktmpdir do |dir|
    path = File.join(dir, "people.csv")
    File.write(path, "\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n")

    assert_equal [C::Person.new(height: 170, weight: 60, age_group: 20, faction: C::KINOKO)], C.load_people(path)
  end
end
```

Ruby の二重引用符の文字列では `"\uFEFF"` が BOM の 1 文字になります。BOM の文字をソースに直接書くと目に見えないので、エスケープで書きます。

`Dir.mktmpdir` にブロックを渡すと、一時ディレクトリを作ってブロックに渡し、**ブロックを抜けるときに消してくれます**。後始末を忘れようがない書き方で、Ruby ではファイルやロックなど「開いたら閉じる」ものにブロックを使うのが定石です。

`assert_equal` が `Person` の配列どうしをそのまま比べられるのは、`Data.define` の `==` がフィールドの値で比べるからです。

もし誰かが後で `load_people` を `CSV.parse(File.read(...))` に書き換えたら、このテストは失敗します。ライブラリの親切に頼るときは、**テストで確かめてから頼る**のが安全です。

### 読み込みの実装

```ruby
# CSV を読み込み、列名で値を取り出して人物のリストにする。
# CSV.foreach はファイルを開くときに BOM を取り除くので、ほかの言語版のような前処理は要らない。
def load_people(csv_file)
  CSV.foreach(csv_file, headers: true).map do |row|
    Person.new(
      height: number(row, "身長"),
      weight: number(row, "体重"),
      age_group: number(row, "年代"),
      faction: text(row, "派閥")
    )
  end
end
```

`CSV.foreach` にブロックを渡さずに呼ぶと、行を 1 つずつ返す `Enumerator` が返ります。それに `map` をつなげて、各行を `Person` に変えます。`headers: true` を付けると、1 行目が見出しとして扱われ、各行は列名で値を引ける `CSV::Row` になります。

列の取り出しは 2 段に分けます。

```ruby
# 列名で数値を読む。整数として読めなければ ArgumentError を投げる。
def number(row, column)
  cell = text(row, column)
  Integer(cell, 10)
rescue ArgumentError
  raise ArgumentError, "#{column} を数値として読めません: #{cell}"
end

# 列名でセルを読む。列が無ければ KeyError を投げる。
def text(row, column)
  raise KeyError, "列がありません: #{column}" unless row.headers.include?(column)

  row[column]
end
```

数値の変換に `cell.to_i` ではなく `Integer(cell, 10)` を使っているのが要点です。

- `"高い".to_i` は **`0` を返します**。例外は出ません。身長 0 cm の人物が黙って紛れ込み、正解率がどこかでずれるだけです
- `Integer("高い", 10)` は **`ArgumentError` を投げます**。第 2 引数の `10` は 10 進数として読む指定で、`"010"` のような値を 8 進数と解釈させないためです

`to_i` の寛容さは、スクリプトを手早く書くときには便利ですが、学習データの読み込みでは誤りを隠します。**型の検査が無い言語ほど、入口で値を厳しく弾く**必要があります。

`rescue` を `def` の直下に書くと、メソッド全体を `begin ... rescue ... end` で囲んだのと同じ意味になります。`Integer` が投げた素っ気ないメッセージの `ArgumentError` を受け止め、列名と値を入れたメッセージで投げ直しています。`cell` はメソッドの中の変数なので、`rescue` の中からも見えます。

列が無いときに `row[column]` は `nil` を返すだけで、例外は出ません。放っておくと `Integer(nil, 10)` で別の分かりにくいエラーになるので、`text` で先に列の有無を確かめて `KeyError` を投げます。

失敗の種類ごとにテストを書きます。

```ruby
def test_数値でない値があれば読み込めない
  Dir.mktmpdir do |dir|
    path = File.join(dir, "people.csv")
    File.write(path, "身長,体重,年代,派閥\n高い,60,20,きのこ\n")

    error = assert_raises(ArgumentError) { C.load_people(path) }

    assert_equal "身長 を数値として読めません: 高い", error.message
  end
end

def test_列が無ければ読み込めない
  Dir.mktmpdir do |dir|
    path = File.join(dir, "people.csv")
    File.write(path, "身長,体重,年代\n170,60,20\n")

    error = assert_raises(KeyError) { C.load_people(path) }

    assert_equal "列がありません: 派閥", error.message
  end
end
```

`assert_raises` は、ブロックの中で指定の例外が投げられることを確かめ、その例外を返します。返った例外の `message` まで確かめるのは、Rust 版で `unwrap_err()` の `to_string()` を比べたのと同じ考え方です。Ruby ではメソッドが投げる例外をどこにも宣言しないので、**このテストが事実上の宣言**になります。

## 1.9 特徴量と正解ラベルに分ける

```ruby
# 人物のリストを特徴量と正解ラベルに分ける。
def split_features_and_labels(people)
  features = people.map do |person|
    Features.new(height: person.height, weight: person.weight, age_group: person.age_group)
  end

  [features, people.map(&:faction)]
end
```

戻り値は 2 要素の配列で、呼び出し側は `x, t = split_features_and_labels(people)` と多重代入で受けます。Python 版のタプルのアンパックとほぼ同じ見た目です。

`people.map(&:faction)` は `people.map { |person| person.faction }` の短縮形です。`&:faction` はシンボルをブロックに変える書き方で、「各要素の `faction` メソッドを呼ぶ」という意味になります。

Rust 版では、ここで「借りるか、渡すか」「どこで `clone()` するか」を決める必要がありました。Ruby では配列もオブジェクトも参照で渡るので、その判断はありません。そのかわり、**呼び出した先で配列を書き換えられても止める仕組みも無い**ので、書き換えないことは約束事になります。`Data.define` で作った `Person` と `Features` が不変なのは、その約束を型の側で少しでも担保するためです。

テストを書きます。

```ruby
def test_人物のリストを特徴量と正解ラベルに分ける
  people = [
    C::Person.new(height: 170, weight: 60, age_group: 20, faction: C::KINOKO),
    C::Person.new(height: 160, weight: 50, age_group: 30, faction: C::TAKENOKO)
  ]

  x, t = C.split_features_and_labels(people)

  assert_equal [features(170, 60, 20), features(160, 50, 30)], x
  assert_equal [C::KINOKO, C::TAKENOKO], t
end
```

## 1.10 正解率を計算する

```ruby
# 予測が正解ラベルと一致した割合を返す。件数が違えば ArgumentError を投げる。
def accuracy(predictions, labels)
  unless predictions.size == labels.size
    raise ArgumentError, "予測と正解ラベルの件数が違います: #{predictions.size} と #{labels.size}"
  end

  predictions.zip(labels).count { |prediction, label| prediction == label }.fdiv(labels.size)
end
```

`zip` で 2 つの配列を組にし、`count` にブロックを渡して一致した組を数え、`fdiv` で割ります。Rust 版の `iter().zip().filter().count()` とほぼ同じ流れを、1 行で書けます。

`fdiv` は**浮動小数点数として割る**メソッドです。Ruby の `14 / 19` は整数の割り算で `0` になります。Rust 版では `correct as f64` と変換を必ず明示させられて、この事故が起きない仕組みでした。Ruby では型が教えてくれないので、`fdiv` を選ぶことと、テストで確かめることの 2 つで防ぎます。

テストは 3 つ書きます。

```ruby
def test_全部当たれば正解率は一になる
  assert_in_delta 1.0, C.accuracy([C::KINOKO, C::TAKENOKO], [C::KINOKO, C::TAKENOKO])
end

def test_半分当たれば正解率は零点五になる
  assert_in_delta 0.5, C.accuracy([C::KINOKO, C::KINOKO], [C::KINOKO, C::TAKENOKO])
end

def test_件数が違えば正解率を求められない
  error = assert_raises(ArgumentError) { C.accuracy([C::KINOKO], [C::KINOKO, C::TAKENOKO]) }

  assert_equal "予測と正解ラベルの件数が違います: 1 と 2", error.message
end
```

浮動小数点数の比較には `assert_in_delta`（許容誤差つきの比較）を使います。1.0 と 0.5 は厳密に表せる値ですが、第 2 章以降で誤差の出る値を比べることを見越して、最初から許容誤差つきで書いておきます。

## 1.11 実データで正解率を表示する

### データが無ければスキップする

実データを使うテストは別のファイルに置きます。

```ruby
# frozen_string_literal: true

require "test_helper"

# 実データ（KvsT.csv）を使うテスト。学習データが無ければスキップする。
class KvsTDataTest < Minitest::Test
  # module_function のモジュールを include すると、章の run が Minitest の run を上書きするので、
  # include せずに定数と関数をモジュールから呼ぶ。
  C = GettingStartedMl::Chapter01

  def csv_file
    path = File.join(GettingStartedMl::Dataset.dir, "KvsT.csv")
    skip "学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def test_実データを読み込んで正解率を求める
    people = C.load_people(csv_file)

    assert_equal 19, people.size

    x, t = C.split_features_and_labels(people)
    predictions = x.map { |features| C.predict_by_rule(features) }

    assert_in_delta 14.0 / 19, C.accuracy(predictions, t), 1e-12
  end
end
```

Minitest には **`skip`** があります。呼ぶとそのテストはそこで止まり、失敗でも成功でもなく「スキップ」として数えられます。Rust 版では標準のテストにスキップが無く、早く戻って成功扱いにするしかありませんでした。Ruby 版では、結果の行にスキップの件数がそのまま出ます（1.12 節）。

`csv_file` の中で `skip` しているので、データが無ければ `load_people` まで進みません。テストの本体は「データがある」前提で素直に書けます。

期待値を `14.0 / 19` と書いているのは、「19 人中 14 人が当たる」という意味を残すためです。`14.0` と小数点を付けているので、ここは浮動小数点数の割り算になります。

### 詰まった点: `include` したら Minitest が動かなくなった

コメントにある「include せずに」には、実際に詰まった経緯があります。

最初は、テストクラスで `include GettingStartedMl::Chapter01` として、`C.` を付けずに `predict_by_rule(...)` と呼べるようにしていました。定数も `KINOKO` とそのまま書けて、見た目はすっきりします。ところが、章を実行する `run` メソッドを `Chapter01` に足したところで、テストが 1 本も走らなくなりました。

```text
private method `run' called for an instance of KvsTDataTest (NoMethodError)
```

原因は `module_function` の仕組みにあります。`module_function` は、各メソッドを「モジュールから直接呼べる関数」にすると同時に、**プライベートなインスタンスメソッドとしても残します**。そのモジュールを `include` すると、`run` がプライベートなインスタンスメソッドとしてテストクラスに入ります。

一方、Minitest は各テストを `Minitest::Test#run` というメソッドで実行します。`include` したモジュールは継承の探索順でスーパークラスより手前に入るので、**章の `run` が Minitest の `run` を上書き**しました。Minitest がテストを走らせようと `run` を呼ぶと、プライベートな章の `run` に当たって `NoMethodError` になったわけです。

直し方は、`include` をやめて `C = GettingStartedMl::Chapter01` と別名を付け、`C.predict_by_rule(...)`・`C::KINOKO` とモジュール越しに呼ぶことです。モジュールの関数はテストクラスに混ざらないので、名前が衝突しません。

これは**動的型付け言語の代償が目に見えた例**です。

- Kotlin 版や Rust 版なら、同じ名前で別の意味のメソッドを持ち込めば、コンパイルの段階で重複や可視性の矛盾として止まります
- Ruby では、`include` もメソッドの定義も実行時に起こることなので、**メソッド名の衝突は実行するまで検出できません**。しかも壊れたのは `run` を足した章のコードではなく、それと無関係なテストの仕組みの側でした

テストを書いていたから、`run` を足した直後に気づけました。型の検査を持たない Ruby 版にとって、テストは仕様の記録であると同時に、**コンパイラが担うはずだった検査の代わり**でもあります。型注釈（RBS・Steep）を入れれば一部は静的に捕まえられますが、本シリーズでは使いません。動的型付けのまま、何をテストで捕まえなければならないかを体験することが、Ruby 版の対比の軸だからです。

### 結果を表示する

```ruby
# 実データでルールによる判定の正解率を表示する。
def run(out = $stdout)
  people = load_people(File.join(Dataset.dir, "KvsT.csv"))
  x, t = split_features_and_labels(people)
  predictions = x.map { |features| predict_by_rule(features) }

  out.puts "データ件数: #{people.size}"
  out.puts format("ルールによる判定の正解率: %.4f", accuracy(predictions, t))
end
```

書き出し先を引数 `out` にして、既定値を標準出力 `$stdout` にしています。Rust 版の `&mut impl std::io::Write` と同じ設計ですが、Ruby では `puts` に応答するものなら何でも渡せます。テストでは `StringIO` を渡せば、出力を文字列として受け取れます。ここもダックタイピングです。

章を選んで実行するのは Rake のタスクです。

```ruby
desc "章を選んで実行する（例: rake run[chapter01]）"
task :run, [:chapter] do |_task, args|
  $LOAD_PATH.unshift File.expand_path("lib", __dir__)
  require "getting_started_ml"

  GettingStartedMl.const_get(args.fetch(:chapter).capitalize).run
end
```

`"chapter01".capitalize` で `"Chapter01"` にし、`const_get` でその名前のモジュールを引いて `run` を呼びます。文字列からモジュールを引けるのは動的言語ならではで、章が増えてもタスクを書き換える必要がありません。Rust 版では `match` に章を 1 つずつ並べました。

実行します。

```text
$ bundle exec rake 'run[chapter01]'
データ件数: 19
ルールによる判定の正解率: 0.7368
```

`run[chapter01]` を引用符で囲んでいるのは、zsh が `[...]` をファイル名のパターンとして展開しようとするためです。

**正解率 0.7368** は、ほかの言語版と同じ値です。19 人中 14 人を正しく判定できました。乱数を使わない処理なので、言語が違っても値は一致します（第 2 章では、ここが崩れます）。

## 1.12 リファクタリング

### 整形と静的解析

RuboCop の設定は次のとおりです。

```yaml
AllCops:
  TargetRubyVersion: 3.3
  NewCops: enable
  SuggestExtensions: false
  Exclude:
    - "vendor/**/*"

# テストのメソッド名に日本語を使うので、ASCII 以外の識別子を許す。
Naming/AsciiIdentifiers:
  Enabled: false

Style/StringLiterals:
  EnforcedStyle: double_quotes

# テストのメソッド名は日本語で振る舞いを書くので、snake_case を求めない。
Naming/MethodName:
  Exclude:
    - "test/**/*"
```

既定のままだと、日本語のテストメソッド名が 2 つのルールに引っかかります。

- `Naming/AsciiIdentifiers` は識別子に ASCII 以外の文字を使うことを咎めます。日本語のメソッド名を許すため、無効にしました
- `Naming/MethodName` はメソッド名に snake_case を求めます。`test_BOM_付きの_CSV_を列名で読み込む` のような名前が引っかかるので、`test/` の中だけ除外しました。`lib/` の中は snake_case のままです

`NewCops: enable` は、RuboCop の版を上げたときに増えた新しいルールを有効にする指定です。書かないと、新しいルールごとに「有効にするか決めてください」という警告が大量に出ます。`vendor/` を除外しているのは、`vendor/bundle` に入った gem のコードまで検査しないためです。

わざと崩すとどうなるかを確かめました。使っていない変数を足すと `Lint/UselessAssignment`、文字列を単引用符で書くと `Style/StringLiterals` が検出され、`rake check` が失敗します。**`rake check` は RuboCop → テストの順なので、RuboCop が失敗するとテストまで進みません。** 警告を残したまま先へ進めないのは、Rust 版の `-D warnings` と同じ考え方です。

RuboCop は、1.11 節の `run` の衝突は検出できません。`include` とメソッドの定義は、どちらもそれだけ見れば正しい Ruby だからです。静的解析で捕まえられるのは「書き方」までで、「実行したら何と何がぶつかるか」はテストの領分です。

### まとめて検査する

`Rakefile` の検査のタスクは次のとおりです。

```ruby
Minitest::TestTask.create(:test) do |task|
  task.libs << "lib" << "test"
  task.test_globs = ["test/**/*_test.rb"]
end

RuboCop::RakeTask.new(:rubocop)

desc "整形・静的解析・テスト（カバレッジつき）をまとめて実行する"
task check: %i[rubocop test]

task default: :check
```

`task check: %i[rubocop test]` は、`check` が `rubocop` と `test` に依存するという宣言で、書いた順に実行されます。`%i[...]` はシンボルの配列を作る書き方です。

```bash
bundle exec rake check
```

`bundle exec` を付けるのは、`Gemfile.lock` に書かれた版の gem で実行するためです。付けないと、たまたま入っている別の版の RuboCop や Minitest が使われることがあります。

### カバレッジ

学習データがある状態で `bundle exec rake check` を実行すると、テストは 13 runs・21 assertions・0 skips で全部通り、SimpleCov の行カバレッジは 40 / 45 行（88.88%）でした。

学習データを外す（`ML_DATA_DIR=/nonexist bundle exec rake check`）と、実データのテストがスキップされ、13 runs・19 assertions・1 skips になります。

| 学習データ | runs | assertions | skips |
|-----------|------|-----------|-------|
| あり | 13 | 21 | 0 |
| なし | 13 | 19 | 1 |

**スキップが 1 件として数えられています。** Rust 版では、データが無くても実データのテストが成功として数えられてしまうため、本当に走ったかをカバレッジの差で確かめる必要がありました。Minitest では結果の行を見るだけで分かります。アサーションの件数が 21 から 19 に減っているのも、実データのテストの 2 つのアサーションが走らなかった分です。

カバレッジは行単位で、SimpleCov が `coverage/` に HTML の報告を書き出します。`test_helper.rb` で `test/` と `vendor/` を集計から外しているので、数字は `lib/` のコードだけの割合です。

## 1.13 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。Ruby に固有の論点は次のとおりです。

1. **Red は実行時エラー** — Python 版と同じく、無いものを呼んだことはテストを走らせて初めて分かる。Rust 版・Kotlin 版のようにコンパイルでは止まらない
2. **失敗は標準の例外で表す** — `ArgumentError`・`KeyError` を投げ、どの例外を投げうるかは `assert_raises` のテストで固定する。数値の変換は `to_i` ではなく `Integer(cell, 10)` で、不正な値を黙って 0 にしない
3. **`Data.define` で値オブジェクトを作る** — 不変で、`==` が値で比べ、引数の過不足を `ArgumentError` で止める。型を宣言しない言語での貴重な早期の検査
4. **BOM は読み方しだい** — `CSV.foreach`・`CSV.read` なら取り除かれ、`CSV.parse(File.read(...))` では残る。前提はテストで固定する
5. **メソッド名の衝突は実行するまで分からない** — `module_function` のモジュールを `include` したら、章の `run` が `Minitest::Test#run` を上書きした。型の検査を持たない Ruby では、テストがコンパイラの代わりを担う
6. **スキップがある** — 実データのテストは Minitest の `skip` で飛ばし、結果の行にスキップの件数が出る

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。そして、**乱数の実装が言語ごとに違う**ことが、ここではじめて数値の不一致として現れます。
