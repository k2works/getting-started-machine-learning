---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "アヤメのデータを読み込み、欠損値を平均値で補完して訓練データとテストデータに分ける。欠損値を nil で表し、Data.define の値オブジェクトで結果を比べる Ruby の書き方を Python 版・Rust 版と対比し、Array#shuffle の並びが言語版ごとに違うことを確かめる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:15:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

この章では、アヤメ（iris）のデータを読み込み、欠損値を平均値で補完して、訓練データとテストデータに分けます。機械学習の本番はまだ先ですが、**データを整えるところが実務では最も時間を食う**ところです。

テスト駆動開発の技法としては、**三角測量**を扱います。1 つのテストだけでは仮実装（定数を返す）で通ってしまうとき、2 つめ・3 つめのテストを足して実装を一般化へ追い込む技法です。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と同じ題材・同じ TODO リストで進めます。Ruby 版では、同じ動的型付けの Python 版と、型で欠損値を表した [Rust 版](../rust/02-data-preprocessing-and-triangulation.md) と対比します。この章で Ruby らしさが出るのは次の 4 つです。

1. **欠損値を `nil` で表す** — Rust 版が `Option<f64>` で型に書いた部分を、Ruby は値で表す。`filter_map` と `||` で欠損値を扱える
2. **`Data.define` の値オブジェクトをそのまま比べる** — 特徴量も分割の結果も `assert_equal` で丸ごと比べられる
3. **`Hash` は挿入の順を保つ** — Rust 版・Go 版が列の順を別に持った理由が、Ruby には無い
4. **`Array#shuffle(random: Random.new(0))` の並びはほかの言語版と違う** — 件数は一致するが、訓練データの平均値は一致しない

## 2.2 題材とデータ

### iris.csv

`iris.csv` は 150 件のアヤメのデータです。

| 列 | 意味 | 値 |
|----|------|-----|
| がく片長さ | がく片の長さ（正規化済み） | 0.0〜1.0 の小数 |
| がく片幅 | がく片の幅（正規化済み） | 0.0〜1.0 の小数 |
| 花弁長さ | 花弁の長さ（正規化済み） | 0.0〜1.0 の小数 |
| 花弁幅 | 花弁の幅（正規化済み） | 0.0〜1.0 の小数 |
| 種類 | アヤメの品種 | `Iris-setosa`・`Iris-versicolor`・`Iris-virginica` |

第 1 章の `KvsT.csv` と違って、**いくつかのセルが空欄**です。数えると、がく片長さが 2 件、がく片幅が 1 件、花弁長さが 2 件、花弁幅が 2 件でした（種類は欠けていません）。この空欄をどう扱うかがこの章の主題の 1 つです。

### 訓練データとテストデータ

学習に使ったデータで正解率を測っても、意味のある数字は得られません。丸暗記したモデルが満点を取るだけだからです。そこで、データを 2 つに分けます。

- **訓練データ**（train）— モデルに学習させるためのデータ。この章では 105 件
- **テストデータ**（test）— 学習に使わず、評価だけに使うデータ。この章では 45 件

150 件を 7:3 に分けた結果です。分け方は乱数で決めます。ここで、**シードを固定しても言語ごとに結果が変わる**という問題が出てきます。

### ほかの言語版と数値が変わる理由

第 1 章の正解率 0.7368 は、ほかの言語版と一致しました。乱数を使わなかったからです。この章からは一致しません。

Ruby の `Random` はメルセンヌ・ツイスタ（MT19937）で、`Array#shuffle` はそれを使って並べ替えます。Rust 版の `rand` 0.8 の `StdRng` とは別の擬似乱数の作り方なので、同じシード 0 を渡しても並びが違います。

| 言語版 | `0..9` をシード 0 で並べ替えた結果 |
|--------|------------------------------|
| Ruby（`Array#shuffle(random: Random.new(0))`） | `[2, 8, 4, 9, 1, 6, 7, 3, 0, 5]` |
| Rust（`rand` 0.8 の `StdRng`） | `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]` |

**一致するのは件数だけ**です。訓練データ 105 件・テストデータ 45 件という分け方は同じでも、どの行が訓練側に入るかが違うので、この後で求める平均値も、第 3 章の正解率も一致しません。

これは不具合ではありません。「乱数で分ける」と決めた時点で、再現性はシードと乱数生成器の実装の組み合わせに依存します。**言語をまたいで同じ数値を出したければ、乱数生成器そのものを移植する**しかありません。本シリーズはそこまではせず、**一致しないことを記事とテストに明記する**方針を取ります。実装の `test/iris_data_test.rb` にも、その理由をコメントとして残しました。

## 2.3 開発環境の準備

第 1 章と同じく `nix develop .#ruby` に入り、`apps/ruby` で作業します。この章では `chapter02` を足します。

```text
apps/ruby/
├── lib/
│   ├── getting_started_ml.rb
│   └── getting_started_ml/
│       ├── dataset.rb
│       ├── chapter01.rb
│       ├── chapter02.rb             # 章のまとめ役（run と表示）
│       └── chapter02/
│           ├── table.rb             # CSV の表と行
│           └── preprocessing.rb     # 補完と分割
└── test/
    ├── chapter02_test.rb
    └── iris_data_test.rb            # 実データを使うテスト
```

第 1 章は `chapter01.rb` の 1 ファイルで足りましたが、この章からはファイルを分けます。`chapter02.rb` が 2 つのファイルを読み込み、どのファイルも同じ `module Chapter02` を開いて中身を足します。

```ruby
require_relative "chapter02/table"
require_relative "chapter02/preprocessing"

module GettingStartedMl
  # 第 2 章: データの前処理。表の読み込み・欠損値の補完・訓練データとテストデータへの分割。
  module Chapter02
    # ...
  end
end
```

Ruby のモジュールは**何度でも開き直せます**。`table.rb` で `Row` と `Table` を、`preprocessing.rb` で `Features` と前処理の関数を、それぞれ同じ `Chapter02` に足しています。Rust 版は親モジュールで `pub mod table;` と子を宣言し、公開する名前を `pub use` で並べました。Ruby には「この章の外から使ってよいのはこれだけ」を 1 か所で宣言する仕組みが無く、読み込んだものはすべて見えます。

`require_relative` は、そのファイルからの相対パスで読み込む指定です。`$LOAD_PATH` に頼らないので、どこから実行しても同じファイルが読まれます。

依存の gem はこの章では増えません。CSV も乱数も、第 1 章までに入っているものと Ruby の標準で足ります。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] CSV を表として読み込む
  - [ ] 数値の列を読む
  - [ ] 空欄の列は欠損値になる
  - [ ] 数値として読めない列は失敗する
  - [ ] 列が無ければ失敗する
- [ ] 列ごとに欠損値を数える
- [ ] 平均値で欠損値を補完する
  - [ ] 欠損値を除いて列ごとの平均値を求める
  - [ ] 値がすべて空欄なら平均値を求められない
  - [ ] 欠損値を平均値で補完する
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合で分ける
  - [ ] 同じシードなら同じ並びになる
  - [ ] 並べ替えても要素は変わらない
  - [ ] 件数が違えば分けられない
- [ ] 実データで前処理の結果を表示する

## 2.5 自作の表で読み込む

### データの表し方を決める

第 1 章では `Person` という「列が決まった値オブジェクト」に読み込みました。この章では列の数が増え、欠損値も入ります。しかも第 4 章以降でデータセットが変わるたびに型を書き直すのは辛いので、**列名で引ける汎用の表**を作ります。

Python 版は pandas の `DataFrame` を使う部分です。Ruby にもデータフレームの gem（Daru・Red Arrow など）はありますが、本シリーズでは使いません（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。学習の題材として、前処理の中身を自分の手で書くためです。

表し方はこう決めました。

| 対象 | 表し方 | 理由 |
|------|-------|------|
| 1 行 | `class Row`（セルの文字列の `Hash` を持つ） | 列名でセルを引く。値は文字列のままで、読むときに解釈する |
| 表 | `Table = Data.define(:columns, :rows)` | 列名の並びと行の並びを持つ |
| 補完後の特徴量 | `Features = Data.define(:columns, :values)` | 欠損値を持たない値として分ける。値で比べられる |
| 分割の結果 | `TrainTestSplit = Data.define(:x_train, :x_test, :t_train, :t_test)` | 4 つの配列に名前を付けて持つ |
| 欠損値 | `nil` | Ruby には `Option` が無いので、値が無いことは `nil` で表す |

Rust 版では、`HashMap` が反復の順を保証しないために、表が列の順を `Vec<String>` で別に持つ必要がありました。**Ruby の `Hash` は挿入した順を保ちます**（Ruby 1.9 から言語の仕様です）。この章では `CSV::Row#headers` から列の並びを取り出して `Table` に持たせていますが、それは順を守るためではなく、「表の列」を `Hash` のキーから推し量らずに明示しておくためです。後で見る `count_missing` の結果が列の順で出るのは、`Hash` の性質のおかげです。

### テストファースト

まずテストから書きます。この章のテストはすべて**架空の値**で書きます。学習データの行は書籍の配布データなので、記事にもテストにも書きません。

```ruby
class Chapter02Test < Minitest::Test
  C = GettingStartedMl::Chapter02

  def row(cells)
    C::Row.new(cells)
  end

  def test_数値の列を読む
    assert_in_delta 5.1, row("がく片長さ" => "5.1").number("がく片長さ")
  end

  def test_空欄の列は欠損値になる
    cells = row("がく片長さ" => "")

    assert_nil cells.number("がく片長さ")
    assert cells.missing?("がく片長さ")
  end
end
```

第 1 章で詰まった経緯のとおり、章のモジュールは `include` せずに `C = GettingStartedMl::Chapter02` と別名を付けて呼びます。

ヘルパーの `row` は `Hash` を受け取るだけです。`row("がく片長さ" => "5.1")` の `=>` の並びは、メソッドの最後の引数が `Hash` のときに `{}` を省略できる書き方です。Rust 版は `&[(&str, &str)]` を受け取って `HashMap<String, String>` を組み立てるヘルパーが要りましたが、Ruby では**ハッシュのリテラルがそのまま行になります**。

`missing?` の末尾の `?` は、真偽値を返すメソッドに付ける Ruby の慣習です。`is_missing` と書くより短く、呼ぶ側では `cells.missing?("がく片長さ")` と問いかけの形で読めます。

### Red: 失敗を確認する

`Row` はまだありません。テストを走らせると、`C::Row` を参照したところで `NameError` になります。

第 1 章と同じく、**Ruby の Red は実行時エラー**です。Rust 版では、`Row` が無いだけで、それを `use` しているほかのファイルまで一緒にコンパイルエラーになりました。Ruby では、テストを走らせてその行に来たときに初めて「無い」と分かります。定数の参照はその行を実行したときに解決されるので、`C::Row` をメソッドの中で参照しているテストだけが `Error` として数えられます。ただし、クラスの本体に直接書いた `C = GettingStartedMl::Chapter02` は読み込んだ時点で実行されるので、`Chapter02` というモジュールそのものが無ければ、テストのファイルを読み込む段階で止まります。

### Green: 欠損値を nil で表す

```ruby
# CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。
class Row
  attr_reader :cells

  def initialize(cells)
    @cells = cells
  end

  # 数値の列を読む。空欄なら nil を返す。数値として読めなければ ArgumentError を投げる。
  def number(column)
    cell = text(column).strip
    return nil if cell.empty?

    Float(cell)
  rescue ArgumentError
    raise ArgumentError, "#{column} を数値として読めません: #{cell}"
  end

  # 文字列の列を読む。列が無ければ KeyError を投げる。
  def text(column)
    cells.fetch(column) { raise KeyError, "列がありません: #{column}" }
  end

  # セルが空欄かどうかを返す。
  def missing?(column)
    text(column).strip.empty?
  end

  def ==(other)
    other.is_a?(Row) && cells == other.cells
  end
end
```

`number` の戻り値は 3 通りです。

| 結果 | 意味 |
|------|------|
| `5.1` などの `Float` | 読めた |
| `nil` | 読めた。空欄だった（欠損値） |
| 例外（`ArgumentError`・`KeyError`） | 読めなかった。数値として解釈できないか、列が無い |

Rust 版はこの 3 つを `Result<Option<f64>>` という入れ子の型で表しました。Ruby では、**値が無いことは `nil`、失敗は例外**と、別の仕組みに分かれます。戻り値を見ても `nil` が返りうることは書かれていないので、それを伝えるのはメソッドのコメントとテストです。`test_空欄の列は欠損値になる` の `assert_nil` が、事実上の型宣言になっています。

| 言語版 | 欠損値の表し方 | 失敗の表し方 |
|--------|--------------|------------|
| Ruby | `nil` | 例外 |
| Python | `NaN`（pandas） | 例外 |
| Rust | `Option<f64>` の `None` | `Result` の `Err` |
| Go | 多値返却の `(value, ok)` で `ok == false` | 多値返却の `error` |

数値の変換は、第 1 章の `Integer(cell, 10)` と同じ考え方で `Float(cell)` にしています。`"たくさん".to_f` は `0.0` を返して黙って欠損値でも失敗でもない値になりますが、`Float("たくさん")` は `ArgumentError` を投げます。

`text` の `cells.fetch(column) { ... }` は、キーが無いときにブロックを実行する書き方です。ブロックを渡さない `fetch` も `KeyError` を投げますが、メッセージを日本語にそろえるためにブロックで投げ直しています。

`strip` を挟んでいるのは、CSV のセルに余分な空白が入っていても空欄として扱えるようにするためです。

### `==` を自分で書いた理由

`Row` だけ `Data.define` ではなく普通のクラスにして、`==` を自分で定義しています。`Row` は `Table` の行として比べられるだけなので、「セルの `Hash` が等しければ等しい」とすれば足ります。普通のクラスの `==` は既定では「同じオブジェクトかどうか」で比べるので、書かなければ中身が同じ行どうしでも等しくなりません。

注意が 1 つあります。**`==` だけを定義し、`eql?` と `hash` は定義していません**。`Row` を `Hash` のキーにしたり、`uniq` で重複を除いたりすると、中身が同じ行でも別物として扱われます。この章ではそういう使い方をしないので問題になりませんが、Rust の `#[derive(PartialEq, Eq, Hash)]` のように「比べ方」と「ハッシュ値」がそろって付くわけではない、という違いは覚えておいてください。

### 三角測量: 失敗のテスト

失敗の 2 つもテストに書きます。

```ruby
def test_数値として読めない列は失敗する
  error = assert_raises(ArgumentError) { row("がく片長さ" => "たくさん").number("がく片長さ") }

  assert_equal "がく片長さ を数値として読めません: たくさん", error.message
end

def test_列が無ければ失敗する
  error = assert_raises(KeyError) { row("がく片長さ" => "5.1").text("花弁幅") }

  assert_equal "列がありません: 花弁幅", error.message
end
```

`number` の `rescue` は、`Float` が投げた `ArgumentError` だけを受け止めて、列名と値を入れたメッセージで投げ直します。`text` が投げる `KeyError` は `ArgumentError` ではないので、`rescue` を素通りしてそのまま呼び出し側へ届きます。**どの例外を受け止めるかをクラスで選べる**ので、「列が無い」と「数値でない」が混ざりません。

### 表を読み込む

```ruby
# CSV の表。列の順と行を持つ。
Table = Data.define(:columns, :rows) do
  # CSV を読み込んで表にする。CSV.read はファイルを開くときに BOM を取り除く。
  # 空欄は nil になるので、欠損値を空文字列にそろえてから行にする。
  def self.load(csv_file)
    csv = CSV.read(csv_file, headers: true)
    rows = csv.map { |record| Row.new(record.to_h.transform_values(&:to_s)) }

    new(columns: csv.headers, rows:)
  end

  # 列ごとに欠損値の数を数える。Hash は挿入の順を保つので、列の順は表の列の順のまま。
  def count_missing
    columns.to_h { |column| [column, rows.count { |row| row.missing?(column) }] }
  end
end
```

`Data.define` にブロックを渡すと、そのブロックの中でメソッドを足せます。`def self.load` はクラスメソッド（`Table.load(...)` と呼ぶ）で、`count_missing` はインスタンスメソッドです。

`CSV.read` は第 1 章の `CSV.foreach` と同じく**ファイルを開く読み方**なので、先頭の BOM を取り除きます。第 1 章で確かめた「文字列から読むと BOM が残る」という落とし穴は、ここでは踏みません。

### csv が空欄を nil にする

コメントにある「空欄は nil になる」は、この章で確かめた csv の振る舞いです。`headers: true` で読むと、`a,b` の見出しに続く `1,` の行は `{"a"=>"1", "b"=>nil}` になります。**空欄のセルは空文字列ではなく `nil`** です。

そのまま `Row` に入れると、`text(column).strip` が `nil` に対する `strip` で `NoMethodError` になります。そこで `transform_values(&:to_s)` で値をすべて文字列にし、`nil` を `""` にそろえてから行にしています（`nil.to_s` は `""`）。

csv が `nil` を返すことを知っていれば、`Row` の側で `cell.nil? || cell.strip.empty?` と書く手もありました。読み込みの入口で空文字列にそろえておけば、`Row` の中では「セルは必ず文字列」と考えてよくなり、`nil` の検査が 1 か所に集まります。**`nil` を持ち込む場所を狭くする**のが、`Option` を持たない言語で `NoMethodError` を避けるいちばんの手です。

### 列ごとの欠損値の数

`count_missing` は `Hash` を返します。`columns.to_h { |column| [column, 数] }` は、ブロックが返した `[キー, 値]` の組から `Hash` を作ります。テストは `Hash` をそのまま比べます。

```ruby
def test_列ごとに欠損値を数える
  table = C::Table.new(
    columns: %w[がく片長さ 種類],
    rows: [
      row("がく片長さ" => "5.1", "種類" => "Iris-setosa"),
      row("がく片長さ" => "", "種類" => "Iris-setosa"),
      row("がく片長さ" => "", "種類" => "Iris-virginica")
    ]
  )

  assert_equal({ "がく片長さ" => 2, "種類" => 0 }, table.count_missing)
end
```

`assert_equal({ ... }, ...)` の丸括弧は省略できません。`assert_equal { ... }` と書くと、Ruby は `{ ... }` を `Hash` ではなく**ブロック**として読むからです。第 1 章では配列を比べていたので気になりませんでしたが、`Hash` のリテラルを最初の引数に書くときの小さな落とし穴です。

Rust 版では、欠損値の数を `Vec<Missing>`（列名と数の構造体のベクタ）で返しました。`HashMap` にすると順が崩れるからです。Ruby では `Hash` にしても列の順が保たれるので、専用の型を作らずに済みました。表示のときも、そのまま列の順で並びます。

`%w[がく片長さ 種類]` は、空白で区切った単語から文字列の配列を作る書き方です。`["がく片長さ", "種類"]` と同じ意味です。

## 2.6 平均値で欠損値を補完する

### 欠損値をどう扱うか

選択肢は 3 つあります。

| やり方 | 長所 | 短所 |
|--------|------|------|
| 欠損のある行を捨てる | 簡単 | データが減る。偏るかもしれない |
| 平均値で埋める | データが減らない | 分散が小さくなる |
| 予測して埋める | 精度が良いこともある | 手間がかかる。過学習の危険 |

150 件のうち 7 件が欠けているだけなので行を捨てても良さそうですが、本シリーズは Python 版に合わせて**平均値で埋める**やり方を採ります。

### 平均値を求める

```ruby
# 欠損値を除いて、列ごとの平均値を求める。
def column_means(rows, columns)
  columns.to_h do |column|
    values = rows.filter_map { |row| row.number(column) }
    raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

    [column, values.sum / values.size]
  end
end
```

`filter_map` がこの章でいちばん Ruby らしい 1 行です。ブロックの結果が `nil`（または `false`）なら捨て、それ以外なら残します。`row.number(column)` は欠損値のとき `nil` を返すので、**欠損値を飛ばすことと数値を取り出すことが 1 回で済みます**。Rust 版では `if let Some(value) = row.number(column)?` で同じことを書きました。`nil` を「値が無い」の印にしておいたことが、ここで効いています。

`values.sum / values.size` は、`Float` の配列の合計を `Integer` の件数で割っています。`Float` を `Integer` で割ると `Float` になるので、第 1 章で気を付けた「整数の割り算で 0 になる」事故は起きません。値が必ず `Float` なのは、`number` が `Float(cell)` で読んでいるからです。

テストは、欠損値を挟んだ 3 件で書きます。

```ruby
def test_欠損値を除いて列ごとの平均値を求める
  rows = [row("がく片長さ" => "1.0"), row("がく片長さ" => ""), row("がく片長さ" => "3.0")]

  assert_equal({ "がく片長さ" => 2.0 }, C.column_means(rows, ["がく片長さ"]))
end
```

欠損値を 0 として数えてしまうと、平均は `4.0 / 3` になります。**欠損値を分母からも外していること**を、この 1 件が確かめています。

値がすべて空欄の列は、平均値を求められません。

```ruby
def test_値がすべて空欄なら平均値を求められない
  error = assert_raises(ArgumentError) { C.column_means([row("がく片長さ" => "")], ["がく片長さ"]) }

  assert_equal "値がすべて空欄です: がく片長さ", error.message
end
```

`values.empty?` の検査を外すと、`[].sum / 0` は `0.0 / 0` ではなく `0 / 0`（`[].sum` は整数の `0`）になり、`ZeroDivisionError` が飛びます。例外の種類もメッセージも、原因を伝えません。**分母が 0 になる前に、意味のあるメッセージで止める**のがこの検査の役目です。

### 補完して特徴量にする

補完した結果は `Row` ではなく `Features` にします。**`Features` は欠損値を持たないことを前提にした値**です。

```ruby
# 欠損値を持たない特徴量。列名と値を同じ順で持つ。
Features = Data.define(:columns, :values) do
  def initialize(columns:, values:)
    raise ArgumentError, "件数が違います: #{columns.size} と #{values.size}" unless columns.size == values.size

    super
  end

  # 列名で値を読む。
  def value(column)
    index = columns.index(column)
    raise KeyError, "列がありません: #{column}" if index.nil?

    values[index]
  end
end
```

`Data.define` の `initialize` を上書きして、列名と値の数をそろえる検査を入れています。`super` を引数なしで呼ぶと、受け取ったキーワード引数をそのまま親に渡します。**作るときに検査しておけば、`Features` を受け取る側は「数がそろっている」と考えてよくなります**。

```ruby
def test_列名と値の数が合わなければ特徴量を作れない
  error = assert_raises(ArgumentError) { C::Features.new(columns: ["がく片長さ"], values: [5.1, 0.2]) }

  assert_equal "件数が違います: 1 と 2", error.message
end
```

Rust 版は `Row::number` が `Result<Option<f64>>`、`Features::value` が `Result<f64>` で、**`Option` が型から消える**ことで「ここから先に欠損値は無い」を表しました。Ruby の `Features` は `values` に `nil` を入れること自体は止めません。欠損値が無いことを保証しているのは、`Features` を作るのが `fill_missing` だけだ、という約束と、次のテストです。

補完の本体はこうです。

```ruby
# 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。
def fill_missing(rows, columns, fill_values)
  rows.map do |row|
    values = columns.map do |column|
      row.number(column) || fill_values.fetch(column) { raise KeyError, "補完する値がありません: #{column}" }
    end

    Features.new(columns:, values:)
  end
end
```

`row.number(column) || 補完する値` の `||` が、「値があればそれ、`nil` なら右側」を表します。Rust 版の `match row.number(column)? { Some(value) => value, None => ... }` が、Ruby では演算子 1 つになりました。

`||` の右側は、左側が `nil` か `false` のときだけ評価されます。`fill_values.fetch` のブロックで投げる `KeyError` も、欠損値に当たったときにしか起きません。補完する値が無くても、欠損値の無い列なら失敗しないということで、この振る舞いもテストで固定しています。

```ruby
def test_補完する値が無ければ失敗する
  error = assert_raises(KeyError) { C.fill_missing([row("がく片長さ" => "")], ["がく片長さ"], {}) }

  assert_equal "補完する値がありません: がく片長さ", error.message
end
```

**`||` には落とし穴もあります**。`0.0` は Ruby では真なので、値が `0.0` のセルが補完値に置き換わることはありません（Python の `or` なら `0.0` は偽で置き換わります）。一方で、もし `number` が `false` を返すことがあれば、それも補完されてしまいます。`number` は `Float` か `nil` しか返さないので、ここでは `||` で足ります。

`rows.map` で新しい配列を返し、元の行は変えません。Rust 版は `rows: &[Row]`（不変の借用）という引数の型が「書き換えない」ことを保証しました。Ruby には同じ保証が無く、`fill_missing` の中で `row.cells` を書き換えることもできてしまいます。書き換えないのは約束事で、コメントの「元の行は変えない」がその約束です。

### 値オブジェクトをそのまま比べる

補完のテストは、`Features` の配列をそのまま比べます。

```ruby
def test_欠損値を平均値で補完する
  rows = [row("がく片長さ" => "1.0"), row("がく片長さ" => "")]

  filled = C.fill_missing(rows, ["がく片長さ"], { "がく片長さ" => 2.0 })

  assert_equal [C::Features.new(columns: ["がく片長さ"], values: [1.0]),
                C::Features.new(columns: ["がく片長さ"], values: [2.0])], filled
end
```

`Features` が配列を 2 つ持つのに、`assert_equal` がそのまま通ります。`Data.define` の `==` はフィールドごとに `==` で比べ、`Array#==` は要素ごとに `==` で比べるからです。

| 言語版 | 配列を含む値の比較 | 型の検査 |
|--------|-----------------|---------|
| Ruby | `Data.define` の `==` | なし（違う型どうしでも比べられ、`false` になるだけ） |
| Python | `@dataclass` の `==` | なし |
| Rust | `#[derive(PartialEq)]` の `==` | あり（型が違えば比較自体が書けない） |
| Go | `reflect.DeepEqual` | なし |

Rust 版では、`f64` を持つ `Features` に `Eq` を付けられない、という区別まで型が教えてくれました。Ruby の `==` にはその区別がありません。`Float::NAN == Float::NAN` は `false` なので、`NaN` を含む `Features` は自分自身と等しくない、という事情は同じですが、それを知らせてくれる仕組みは無く、知っておくしかありません。

## 2.7 特徴量と正解ラベルに分ける

```ruby
# アヤメのデータの正解ラベルの列。
TARGET = "種類"

# 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
def split_features_and_target(table, target)
  [table.columns - [target], table.rows, table.rows.map { |row| row.text(target) }]
end
```

`table.columns - [target]` は、配列の**差**です。`target` を除いた列名の配列が、元の順のまま返ります。Rust 版は `.iter().filter(|column| column.as_str() != target).cloned().collect()` と書き、`&&String` と `&str` をそろえるために `as_str()` が要りました。

戻り値は 3 要素の配列で、呼び出し側は多重代入で受けます。

```ruby
columns, rows, labels = C.split_features_and_target(table, C::TARGET)
```

`table.rows` は複製せずにそのまま返しています。Ruby では配列は参照で渡るので、呼び出し側の `rows` と `table.rows` は同じ配列です。Rust 版は `table.rows.clone()` で複製しました。どちらが良いかではなく、**Ruby では複製するかどうかを選ぶ場面そのものが少ない**という違いです。そのかわり、呼び出し側が `rows` を書き換えれば `table` の中身も変わります。

## 2.8 訓練データとテストデータに分ける

### 分割結果を値オブジェクトで表す

分割の結果は 4 つの配列になります。まとめる値を作ります。

```ruby
# 訓練データとテストデータ。
TrainTestSplit = Data.define(:x_train, :x_test, :t_train, :t_test)
```

Rust 版は `TrainTestSplit<X, T>` と型引数を 2 つ取りました。分割する前は `TrainTestSplit<Row, String>`、補完した後は `TrainTestSplit<Features, String>` と、同じ型を中身を変えて使い回すためです。**Ruby は動的型付けなので、ジェネリクスが要りません**。`x_train` に `Row` の配列を入れても `Features` の配列を入れても、`TrainTestSplit` から見れば同じです。そのかわり、「今どちらが入っているか」は型ではなく変数名と文脈で読み取ることになります。

### 仮実装

最初のテストは 1 つだけです。

```ruby
def test_テストデータの割合で分ける
  x = (0..9).to_a

  split = C.split_train_test(x, x.map(&:to_s), test_size: 0.3, seed: 0)

  assert_equal [7, 3, 7, 3], [split.x_train.size, split.x_test.size, split.t_train.size, split.t_test.size]
end
```

4 つの件数を配列にまとめて 1 回で比べています。落ちたときに 4 つの値がまとめて表示されるので、どれがずれたかが一目で分かります。

`test_size:` と `seed:` はキーワード引数です。`split_train_test(x, t, 0.3, 0)` と位置で渡すと、`0.3` と `0` のどちらが割合でどちらがシードか、呼ぶ側では読めません。**Ruby にはキーワード引数があるので、意味を名前で書けます**。Rust には名前付き引数が無く、Rust 版は `split_train_test(&x, &t, 0.3, 0)` と位置で渡していました。

このテストは、先頭 7 件と末尾 3 件に切るだけの**仮実装**で通ります。

```ruby
# 仮実装：先頭から 7 件と残り 3 件に切る
def split_train_test(x, t, test_size:, seed:)
  TrainTestSplit.new(x_train: x.first(7), x_test: x.drop(7), t_train: t.first(7), t_test: t.drop(7))
end
```

**7 という定数が埋め込まれています。** ここから三角測量で追い込みます。

### 三角測量: 件数を一般化する

件数が違うデータを渡したら止まるべきです。テストを足します。

```ruby
def test_特徴量と正解ラベルの件数が違えば分けられない
  error = assert_raises(ArgumentError) { C.split_train_test([1, 2], [1], test_size: 0.3, seed: 0) }

  assert_equal "件数が違います: 2 と 1", error.message
end
```

仮実装は `[1, 2].first(7)` を黙って `[1, 2]` にし、例外を投げません。Rust 版では同じ場面で `x[..7]` が範囲外の添字でパニックしましたが、**Ruby の `first` は足りなければあるだけ返します**。ここでも Ruby の寛容さが「失敗しないこと」として現れ、Red は `assert_raises` の「例外が投げられなかった」という失敗になります。

件数の検査と、割合からの計算を入れます。

```ruby
raise ArgumentError, "件数が違います: #{x.size} と #{t.size}" unless x.size == t.size

train = shuffled.first(shuffled.size - (shuffled.size * test_size).ceil)
```

`ceil`（切り上げ）にしているのは Python 版の `train_test_split` に合わせたためです。150 件の 0.3 は 45.0 なので切り上げても 45 件ですが、端数の出る件数では 1 件ずれます。**どちらに寄せるかを決めて、全言語版でそろえる**必要がある部分です。

`shuffled.size * test_size` は `Integer` と `Float` の掛け算で `Float` になり、`ceil` で `Integer` に戻ります。Rust 版では `as f64` と `as usize` の 2 回の変換を明示し、桁落ちを承知していることを `#[allow(...)]` で書き残しました。Ruby は数値の型を自動で広げるので、変換を書く場所がありません。

### 三角測量: 並び順に頼らない分け方にする

件数が正しくても、**先頭から切る**実装のままです。アヤメのデータは種類ごとに並んでいるので、先頭 105 件を訓練データにすると `Iris-virginica` がほとんど訓練データに入りません。並べ替えが要ります。

並べ替えのテストを先に書きます。

```ruby
def test_同じシードなら同じ並びになる
  items = (0..9).to_a

  assert_equal C.shuffle(items, 0), C.shuffle(items, 0)
  refute_equal C.shuffle(items, 0), C.shuffle(items, 1)
end

def test_並べ替えても要素は変わらず元の配列も変わらない
  items = (0..9).to_a

  assert_equal items, C.shuffle(items, 0).sort
  assert_equal (0..9).to_a, items
end
```

このテストは**特定の並びを期待していません**。「同じシードなら再現する」「違うシードなら変わる」「要素は増えも減りもしない」「元の配列は変わらない」の 4 つだけを言っています。特定の並びを期待するテストは、乱数生成器の実装が変われば落ちます。ここでは**性質を確かめるテスト**にしました。

`refute_equal` は `assert_equal` の否定です。Minitest では否定の検査に `refute_` の名前が付きます（`refute_nil`・`refute_includes` など）。「違うシードなら変わる」は厳密には危うく、たまたま同じ並びになる可能性はあります（10 件の並べ替えなので 1/10! 程度）。実際にシード 0 と 1 で違うことを確かめたうえで、この形にしています。

4 つめの「元の配列は変わらない」は、Ruby では書いておく価値があります。Ruby の配列には `shuffle`（新しい配列を返す）と `shuffle!`（その場で並べ替える）の 2 つがあり、**`!` が 1 文字違うだけで呼び出し側のデータが壊れます**。Rust 版は引数を `&[E]`（不変の借用）で受け取った時点で、書き換えないことがコンパイラに保証されていました。Ruby ではテストで保証します。

### Green: シード付きの乱数で並べ替える

```ruby
# シードを使って並べ替える。元の配列は変えない。
def shuffle(items, seed)
  items.shuffle(random: Random.new(seed))
end
```

1 行です。Rust 版は Fisher-Yates のシャッフルを自分で書き、ほかの言語版と同じアルゴリズムであることを明示しました。Ruby の `Array#shuffle` も内部は Fisher-Yates ですが、ここでは**標準のメソッドに乱数生成器を渡す**だけにしています。`random:` に `Random` のインスタンスを渡すと、グローバルな乱数ではなくその生成器を使うので、シードで並びが決まります。

`Random.new(seed)` を呼び出しのたびに作っているのが要点です。生成器を使い回すと、2 回目の呼び出しは 1 回目の続きの乱数列を使うので、同じシードでも並びが変わります。**シードから毎回作り直すので、同じ引数なら同じ結果**になります。

### 特徴量と正解ラベルの対応を保つ

並べ替えるとき、特徴量とラベルの対応が崩れてはいけません。組にしてから並べ替え、分けてから組をほどきます。

```ruby
# 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
def split_train_test(x, t, test_size:, seed:)
  raise ArgumentError, "件数が違います: #{x.size} と #{t.size}" unless x.size == t.size

  shuffled = shuffle(x.zip(t), seed)
  train = shuffled.first(shuffled.size - (shuffled.size * test_size).ceil)

  to_split(train, shuffled.drop(train.size))
end

# （特徴量, 正解ラベル）の組の並びを、訓練データとテストデータに組み直す。
def to_split(train, test)
  x_train, t_train = train.transpose
  x_test, t_test = test.transpose

  TrainTestSplit.new(x_train: x_train || [], x_test: x_test || [], t_train: t_train || [], t_test: t_test || [])
end
```

`x.zip(t)` で `[[特徴量, ラベル], ...]` の組の配列を作り、`shuffle` はその組の中身を知らずに並べ替えます。分けた後の `train.transpose` は、組の配列を**転置**して `[[特徴量...], [ラベル...]]` に戻します。`zip` と `transpose` が互いに逆の操作になっている、という形です。

`x_train || []` が要るのは、`[].transpose` が `[]` を返すからです。空の配列を多重代入すると `x_train` も `t_train` も `nil` になります。テストデータが 0 件になるような割合（`test_size: 0.0`）でも `TrainTestSplit` の中身が配列であるように、`nil` を空の配列に置き換えています。**`nil` が紛れ込む場所を、ここでも入口で塞いでいます**。

`to_split` を別のメソッドにしたのは、RuboCop の `Metrics/AbcSize` のためです。代入（Assignment）・メソッド呼び出し（Branch）・条件（Condition）の数から計算する複雑さの指標で、既定の上限は 17 です。上限に収めるために、「分ける」と「組み直す」の 2 つに分けました。静的解析に言われて分けたメソッドですが、名前を付けてみると、それぞれが 1 つのことだけをしています。

対応が崩れないことも、テストで確かめます。

```ruby
def test_分けても特徴量と正解ラベルの対応は崩れない
  x = (0..9).to_a

  split = C.split_train_test(x, x.map(&:to_s), test_size: 0.3, seed: 0)

  assert_equal split.x_train.map(&:to_s), split.t_train
  assert_equal split.x_test.map(&:to_s), split.t_test
end
```

ラベルを「特徴量を文字列にしたもの」にしておけば、並べ替えた後でも `x_train.map(&:to_s)` と `t_train` が一致するはずです。**特定の並びを書かずに、対応だけを検査できます**。

## 2.9 前処理をまとめる

ここまでの部品を 1 つのメソッドにまとめます。

```ruby
# iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
def prepare_iris(csv_file, test_size:, seed:)
  table = Table.load(csv_file)
  columns, rows, labels = split_features_and_target(table, TARGET)
  split = split_train_test(rows, labels, test_size:, seed:)
  means = column_means(split.x_train, columns)

  split.with(x_train: fill_missing(split.x_train, columns, means),
             x_test: fill_missing(split.x_test, columns, means))
end
```

**順序が大事です。** 「分割してから補完する」のであって、逆ではありません。

平均値を求めるのは `split.x_train`、つまり**訓練データだけ**です。テストデータの補完にも訓練データの平均値を使います。全件の平均値で先に補完してしまうと、テストデータの情報が訓練データの前処理に混ざります。これを**データ漏洩**（data leakage）と呼びます。テストデータは「まだ見たことのないデータ」のつもりで評価するものなので、その中身が学習側に漏れると、評価が実力より良く出ます。

最後の `split.with(...)` は、`Data` のインスタンスの**一部のフィールドだけを差し替えた新しいインスタンス**を返します。元の `split` は変わりません（`Data` のインスタンスは凍結されていて、書き換えられません）。`t_train` と `t_test` は元のまま引き継がれ、`x_train` と `x_test` だけが `Row` の配列から `Features` の配列に置き換わります。

Rust 版では、`TrainTestSplit<Row, String>` から `TrainTestSplit<Features, String>` への変換が戻り値の型に現れ、「欠損値を持ちうる行から、持てない特徴量への変換が分割の後にある」ことが型で読めました。Ruby の `with` は、同じ `TrainTestSplit` のまま中身の種類だけが変わります。**変換が起きたことは、コードの順序を読んで知る**しかありません。

`test_size:`・`seed:` をそのまま `split_train_test` に渡している `test_size:, seed:` は、第 1 章で見たハッシュの値の省略記法です。`test_size: test_size, seed: seed` と同じ意味になります。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

実データを使うテストは `test/iris_data_test.rb` に置きます。第 1 章と同じく、データが無ければ `skip` します。

```ruby
# 実データ（iris.csv）を使うテスト。学習データが無ければスキップする。
class IrisDataTest < Minitest::Test
  C = GettingStartedMl::Chapter02

  def csv_file
    path = File.join(GettingStartedMl::Dataset.dir, "iris.csv")
    skip "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def test_実データの列ごとの欠損値の数を数える
    table = C::Table.load(csv_file)

    assert_equal 150, table.rows.size
    assert_equal({ "がく片長さ" => 2, "がく片幅" => 1, "花弁長さ" => 2, "花弁幅" => 2, "種類" => 0 }, table.count_missing)
  end

  def test_実データを百五件と四十五件に分けて欠損値を補完する
    split = C.prepare_iris(csv_file, test_size: 0.3, seed: 0)

    assert_equal [105, 45, 105, 45], [split.x_train.size, split.x_test.size, split.t_train.size, split.t_test.size]
    assert(split.x_train.all? { |features| features.values.none?(&:nil?) })
  end
end
```

件数の 105 と 45 は、**ほかの言語版と一致します**。分け方（どの行がどちらに入るか）は違いますが、150 件を 7:3 に切り上げで分ければ件数は同じだからです。欠損値の数も一致します。これはデータそのものの性質なので、乱数とは無関係です。

2 つめのテストの最後の行は、補完した後の特徴量に `nil` が 1 つも残っていないことを確かめています。Rust 版では `Features` の値が `Vec<f64>` なので、`None` が残ることは型の上でありえませんでした。Ruby では、**型が保証しない「欠損値が残っていない」を、実データのテストで確かめます**。

平均値のほうは一致しません。訓練データに入る行が違うからです。ここも記録として残します。

```ruby
# Ruby の Random（MT19937）と Array#shuffle の並びは、Python 版・Rust 版などと違うので、
# 分かれる行と平均値はほかの言語版と一致しない（件数だけ一致する）
def test_訓練データの平均値は乱数の分け方で決まる
  columns, rows, labels = C.split_features_and_target(C::Table.load(csv_file), C::TARGET)
  split = C.split_train_test(rows, labels, test_size: 0.3, seed: 0)
  means = C.column_means(split.x_train, columns)

  { "がく片長さ" => 0.4202912621359223, "がく片幅" => 0.4314285714285714,
    "花弁長さ" => 0.49640776699029127, "花弁幅" => 0.45796116504854373 }.each do |column, want|
    assert_in_delta want, means[column], 1e-12, column
  end
end
```

Rust 版の同じテストでは、がく片長さの平均値は 0.40628571428571436 でした。Ruby 版は 0.4202912621359223 です。**同じデータ・同じシード 0・同じ 7:3 でも、乱数生成器が違えば平均値が違う**、という事実をこのテストが記録しています。Ruby の版を上げて `Array#shuffle` の実装が変われば、このテストが落ち、そのとき「なぜ落ちたか」をコメントが教えてくれます。

`assert_in_delta` の 4 つめの引数は、失敗したときに表示するメッセージです。`Hash` を回すテストでは、落ちたときにどの列かが分からないと困るので、列名を渡しています。平均値は割り算の結果なので、`assert_equal` ではなく許容誤差 `1e-12` で比べています。

### 結果を表示する

```ruby
module Chapter02
  # テストデータの割合。
  TEST_SIZE = 0.3
  # 分割の乱数のシード。
  SEED = 0

  # アヤメのデータの前処理の結果を表示する。
  def self.run(out = $stdout)
    csv_file = File.join(Dataset.dir, "iris.csv")
    table = Table.load(csv_file)

    out.puts "データ件数: #{table.rows.size}"
    out.puts "欠損値の数: #{format_missing(table.count_missing)}"
    out.puts summary(prepare_iris(csv_file, test_size: TEST_SIZE, seed: SEED))
  end

  # 分割の件数と特徴量の列を表示用の 2 行にする。
  def self.summary(split)
    ["訓練データ: #{split.x_train.size} 件, テストデータ: #{split.x_test.size} 件",
     "特徴量: #{split.x_train.first.columns.join(', ')}"]
  end

  # 列ごとの欠損値の数を「列=数」の並びにする。
  def self.format_missing(counts)
    counts.map { |column, count| "#{column}=#{count}" }.join(", ")
  end
end
```

`run`・`summary`・`format_missing` は `module_function` ではなく `def self.` で定義しています。`def self.run` はモジュールから直接呼べる関数（特異メソッド）だけを作り、プライベートなインスタンスメソッドは作りません。第 1 章で `run` が Minitest の `run` と衝突したのは、`module_function` がインスタンスメソッドも残すからでした。`def self.` なら、たとえ誰かがこのモジュールを `include` しても `run` は混ざりません。

`out.puts` に配列を渡すと、要素を 1 行ずつ出力します。`summary` が 2 要素の配列を返しているので、`out.puts summary(...)` の 1 回で 2 行になります。

`counts.map { |column, count| ... }` は、`Hash` の `map` がキーと値の組をブロックに渡すことを使っています。

`summary` と `format_missing` を `run` から切り出したのも、`Metrics/AbcSize` の上限に合わせるためです。

実行します。

```text
$ bundle exec rake 'run[chapter02]'
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

第 1 章で作った Rake の `run` タスクは、`"chapter02".capitalize` から `Chapter02` を引いて `run` を呼ぶので、章を足してもタスクを書き換える必要がありませんでした。Rust 版では `match` に腕を足し、章ごとのエラー型をそろえるための `.map_err(...)` まで書き足しました。

データ件数 150、欠損値 2・1・2・2・0、訓練 105 件・テスト 45 件は、**ほかの言語版と一致します**。一致しないのは、この出力には現れていない「どの行がどちらに入ったか」と、そこから決まる平均値です。

## 2.11 リファクタリング

### テストの重複をまとめる

テストの中で `Row` を作る式が何度も出てくるので、ヘルパーの `row` にまとめています。`Hash` をそのまま受け取るので、呼ぶ側は `row("がく片長さ" => "5.1")` と短く書けます。**テストの読みやすさのためにテスト用のコードを書く**のは、本番のコードと同じだけ価値があります。

### 整形と静的解析

この章から、RuboCop の設定を 2 つ足しました。

```yaml
# 機械学習の慣例（特徴量 x・正解ラベル t・y）の 1 文字の名前を許す。
Naming/MethodParameterName:
  AllowedNames: [x, t, y]

# テストは期待する出力をヒアドキュメントでそのまま書くので、メソッドの行数を求めない。
Metrics/MethodLength:
  Exclude:
    - "test/**/*"
```

`Naming/MethodParameterName` は、3 文字未満の引数名を咎めるルールです。`split_train_test(x, t, ...)` の `x` と `t` が引っかかります。機械学習では特徴量を `x`、正解ラベルを `t`（や `y`）と書くのが慣例で、`features` と `labels` にすると数式との対応が読みにくくなるので、この 3 つだけを許しました。**ルールを丸ごと切らずに、許す名前を列挙する**のが、静的解析との付き合い方です。

`Metrics/MethodLength` の除外は、第 3 章で実行結果をヒアドキュメントで丸ごと比べるテストのためです。

`Metrics/AbcSize` は既定の上限 17 のまま使い、2.8 節の `to_split`、2.10 節の `summary`・`format_missing` のように、超えたメソッドを分けて合わせました。

```bash
bundle exec rake check
```

### カバレッジ

学習データがある状態で `bundle exec rake check` を実行すると、第 3 章までのテストを合わせて 49 runs・84 assertions・0 skips で全部通り、SimpleCov の行カバレッジは 207 / 220 行（94.09%）でした。

学習データを外す（`ML_DATA_DIR=/nonexist bundle exec rake test`）と、実データのテストがスキップされます。

| 学習データ | runs | assertions | skips | 行カバレッジ |
|-----------|------|-----------|-------|------------|
| あり | 49 | 84 | 0 | 207 / 220（94.09%） |
| なし | 49 | 67 | 7 | 184 / 220（83.63%） |

スキップの 7 件は、第 1 章の 1 件・この章の 3 件・第 3 章の 3 件です。第 1 章と同じく、スキップの件数が結果の行にそのまま出るので、実データのテストが本当に走ったかを結果の行で確かめられます。

## 2.12 可視化について

Python 版では、この章でヒストグラムや散布図を描いてデータの分布を確かめました。Ruby 版では**可視化の節を作りません**。

理由は 2 つあります。1 つは、可視化の目的が「人がデータを見て理解する」ことなので、対話的に試せる環境（Jupyter Notebook）のほうが向いていること。もう 1 つは、Ruby 版で伝えたいこと（動的型付け・`nil`・例外・`Data.define`・テストが型の検査を肩代わりすること）と可視化が交わらないことです。

欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

## 2.13 まとめ

この章では、アヤメのデータを読み込み、欠損値を平均値で補完し、訓練データ 105 件とテストデータ 45 件に分けました。Ruby に固有の論点は次のとおりです。

1. **欠損値は `nil`、失敗は例外** — Rust 版が `Result<Option<f64>>` の 1 つの型で表した 3 つの状態を、`Float`・`nil`・例外に分けて表す。`filter_map` で欠損値を飛ばし、`||` で補完値に置き換えられる。そのかわり、`nil` が返りうることはコメントとテストでしか伝わらない
2. **`nil` を持ち込む場所を狭くする** — csv は空欄を `nil` にするので、読み込みの入口で `to_s` して空文字列にそろえる。`transpose` が空で `nil` を返す場所も `|| []` で塞ぐ
3. **`Data.define` の値オブジェクトをそのまま比べる** — `Features` も `TrainTestSplit` も `assert_equal` で丸ごと比べられる。`initialize` を上書きすれば作るときに検査でき、`with` で一部だけ差し替えた新しい値を作れる
4. **`Hash` は挿入の順を保つ** — Rust 版・Go 版が列の順を別に持った理由が無く、欠損値の数も `Hash` のまま列の順で返せる
5. **ジェネリクスも借用も要らないが、保証も無い** — `TrainTestSplit` は中身の型を問わず、配列は参照で渡る。「元の配列を変えない」「補完後に `nil` が残らない」は、テストで確かめる
6. **乱数は言語をまたいで一致しない** — `Array#shuffle(random: Random.new(0))` の並びは `[2, 8, 4, 9, 1, 6, 7, 3, 0, 5]` で、Rust 版とも違う。件数は一致し、平均値は一致しない。この事実をテストに書いて残した

**TODO リスト（この章の完了時点）**:

- [x] CSV を表として読み込む
- [x] 列ごとに欠損値を数える
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 実データで前処理の結果を表示する

次の章では、この前処理の結果を使って決定木を実装します。`Data.define` で葉と節を作り、`case`/`in` のパターンマッチで見分けます。そして**自作の決定木を Rumale の決定木と突き合わせ**、深さ 5 までは予測が一致し、深さを制限しないと 1 件分かれることを確かめます。
