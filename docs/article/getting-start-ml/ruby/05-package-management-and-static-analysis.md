---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "Bundler（Gemfile・悲観的バージョン制約 ~>・Gemfile.lock・bundle outdated）で依存を固定し、Ruby の版を Gemfile と Nix で押さえ、RuboCop（NewCops・わざと入れた違反・除外の理由）と SimpleCov で品質を機械的に検査する。numo-narray から numo-narray-alt への移り変わりも扱う。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:21:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決めました。この章では、コミットした情報から **同じ環境を作り直す** 仕組み（パッケージ管理）と、コードの品質を **機械的に確かめる** 仕組み（静的解析・カバレッジ）を整えます。

Ruby の道具立ては、Python 版とよく似た「役割ごとに別の道具を足す」構成です。

| 役割 | Ruby | Python 版 | Rust 版 |
|------|------|-----------|---------|
| 依存の解決 | Bundler（rubygems.org） | uv（PyPI） | cargo（crates.io） |
| 依存の記録 | `Gemfile`・`Gemfile.lock` | `pyproject.toml`・`uv.lock` | `Cargo.toml`・`Cargo.lock` |
| 整形 | RuboCop（`Layout/*`） | Ruff（`ruff format`） | rustfmt |
| 型検査 | なし（テストで捕まえる） | mypy | rustc |
| 静的解析 | RuboCop（`Style/*`・`Lint/*`・`Metrics/*`） | Ruff（`ruff check`） | clippy |
| テスト | Minitest | pytest | `cargo test` |
| カバレッジ | SimpleCov | pytest-cov | cargo-llvm-cov |
| 設定ファイル | `Gemfile`・`.rubocop.yml`・`test/test_helper.rb` | `pyproject.toml` | `Cargo.toml` |

大きな違いは 2 つです。**型検査の道具を使わない**こと（Ruby 版は RBS・Steep を使わないと決めています）と、**整形と静的解析が RuboCop 1 つに入っている**ことです。Python 版の Ruff も両方を持っていますが、`ruff format` と `ruff check` の 2 つのコマンドに分かれていました。RuboCop は 1 回の実行で、字下げも書き方もまとめて検査します。

## 5.2 Bundler によるパッケージ管理

### Gemfile

Ruby のプロジェクトの依存は `Gemfile` に書きます。第 3 章までの `apps/ruby/Gemfile` はこれだけです。

```ruby
# frozen_string_literal: true

source "https://rubygems.org"

ruby ">= 3.3"

gem "csv", "~> 3.3"
gem "rumale", "~> 2.2"

group :development, :test do
  gem "minitest", "~> 6.0"
  gem "rake", "~> 13.0"
  gem "rubocop", "~> 1.91", require: false
  gem "simplecov", "~> 1.3", require: false
end
```

| 項目 | 意味 |
|------|------|
| `source` | gem を取ってくる場所 |
| `ruby ">= 3.3"` | 動かせる Ruby の版（5.3 節） |
| `gem "rumale", "~> 2.2"` | 依存する gem と、受け入れる版の範囲 |
| `group :development, :test` | 開発とテストでだけ使う gem |
| `require: false` | `Bundler.require` で自動的に読み込まない。RuboCop と SimpleCov は、使う場所で自分で `require` する |

`Gemfile` は Ruby のコードそのものです。`pyproject.toml`（TOML）や `Cargo.toml` のような設定ファイルの形式ではなく、`gem` や `group` というメソッドを呼ぶ DSL になっています。先頭の `# frozen_string_literal: true` は、`Gemfile` も RuboCop の検査の対象になっているからです。

`csv` を明示しているのは、Ruby 3.4 で csv が default gem から bundled gem に移ったからです。`Gemfile` に書いておけば、Ruby の版を上げても同じ版の csv を使い続けられます。

### 悲観的バージョン制約 ~>

`"~> 2.2"` は「2.2 を使う」という意味ではありません。**最後の桁だけを上げてよい** という意味の制約です。英語では pessimistic version constraint（悲観的バージョン制約）と呼ばれます。

| 書き方 | 意味する範囲 | 受け入れる例 |
|--------|------------|------------|
| `"~> 2.2"` | `>= 2.2, < 3.0` | 2.2.0、2.9.1 |
| `"~> 2.2.0"` | `>= 2.2.0, < 2.3` | 2.2.0、2.2.5 |
| `"~> 1.91"` | `>= 1.91, < 2.0` | 1.91.0、1.99.0 |
| `"= 2.2.0"` | 2.2.0 だけ | 2.2.0 |
| `">= 3.3"` | 3.3 以上すべて | 3.3.10、4.0.0 |

Rust 版のキャレット要件（`"0.8"` は `>=0.8.0, <0.9.0`）と違い、`~>` は **書いた桁数で範囲が決まります**。`"~> 2.2"` と `"~> 2.2.0"` は受け入れる範囲が違うので、どこまで上がってよいかを桁数で表します。Rumale 自身も、分割した gem どうしを `rumale-core (~> 2.2.0)` のように 3 桁で縛り、同じ 2.2 系の中だけで組み合わさるようにしています。

### Gemfile.lock

解決の結果は `Gemfile.lock` に書かれます。第 3 章までで、`Gemfile` に書いた 6 個から、推移的依存を含めて 42 個の gem が並びます。

```text
    rumale (2.2.0)
      numo-narray-alt (>= 0.9.10, < 0.12.0)
      rumale-clustering (~> 2.2.0)
      rumale-core (~> 2.2.0)
      rumale-decomposition (~> 2.2.0)
```

lock の末尾には、`Gemfile` に書いた依存・Ruby の版・Bundler の版が記録されます。

```text
DEPENDENCIES
  csv (~> 3.3)
  minitest (~> 6.0)
  rake (~> 13.0)
  rubocop (~> 1.91)
  rumale (~> 2.2)
  simplecov (~> 1.3)

RUBY VERSION
   ruby 3.3.10p183

BUNDLED WITH
   2.7.2
```

Rust 版の `Cargo.lock` には各クレートのチェックサムがありましたが、本シリーズの `Gemfile.lock` には **チェックサムの節（`CHECKSUMS`）がありません**。Bundler 2.7.2 には `bundle lock --add-checksums` という選択肢があり、使えば lock にチェックサムを記録できます。本シリーズではまだ使っていません。

lock ファイルがあると、`bundle install` は **記録された版をそのまま使います**。

```bash
bundle install
```

```text
Bundle complete! 6 Gemfile dependencies, 43 gems now installed.
Bundled gems are installed into `./vendor/bundle`
```

lock の 42 個に Bundler 自身を加えた 43 個が、第 4 章で見た `vendor/bundle` に入ります。

新しい版があるかは `bundle outdated` で確かめます。`--only-explicit` を付けると、`Gemfile` に書いた gem だけを調べます。

```bash
bundle outdated --only-explicit
```

```text
Fetching gem metadata from https://rubygems.org/...........
Resolving dependencies...

Bundle up to date!
```

| コマンド | 何をするか |
|---------|-----------|
| `bundle add <gem>` | `Gemfile` に gem を足し、lock を更新する |
| `bundle install` | lock のとおりに gem を入れる。lock が無ければその場で作る |
| `bundle outdated` | 新しい版がある gem を表示する |
| `bundle update <gem>` | `Gemfile` の範囲内で、指定した gem の lock を上げる |
| `bundle lock --add-platform <platform>` | lock にプラットフォームを足す（第 4 章） |
| `bundle exec <command>` | lock の版の gem で command を実行する |

`bundle update` を gem の名前なしで実行すると、すべての gem を一度に上げます。上げるときは 1 つずつ名前を指定し、テストを通してからコミットするほうが、何が結果を変えたのかを追いやすくなります。

### 本番依存と開発依存

`Gemfile` の `group` が、Python 版の `[dependency-groups]`、Rust 版の `[dev-dependencies]` に当たります。

```ruby
group :development, :test do
  gem "minitest", "~> 6.0"
  # ...
end
```

本シリーズでは、`csv` と `rumale` だけが章のプログラムの実行に必要で、Minitest・Rake・RuboCop・SimpleCov は開発とテストでだけ使います。本番の環境で開発用の gem を入れないときは、`BUNDLE_WITHOUT="development:test" bundle install` のように除くグループを指定します。

## 5.3 Ruby の版を固定する

Ruby の版は、3 つの場所に書かれています。

| 場所 | 書いてあること | 誰が読むか |
|------|--------------|-----------|
| `Gemfile` の `ruby ">= 3.3"` | 動かせる版の下限 | Bundler（合わなければ `bundle install` が止まる） |
| `Gemfile.lock` の `RUBY VERSION` | lock を作ったときの版（3.3.10p183） | 記録 |
| `.ruby-version` の `3.3` | 使う版 | rbenv・chruby などのバージョン管理ツール |

実際に Ruby を用意しているのは Nix の環境定義です。

```nix
  buildInputs = baseShell.buildInputs ++ (with packages; [
    ruby
    rubyPackages_3_3.solargraph
    bundler
  ]);
```

```bash
nix develop .#ruby
```

```text
Ruby development environment activated
  - Ruby: ruby 3.3.10 (2025-10-23 revision 343ea05002) [x86_64-darwin24]
  - Bundler: Bundler version 2.7.2
  - Solargraph: 0.57.0
```

第 4 章で見たとおり、`Random` と `Array#shuffle` の並びは Ruby 本体が決めます。Rust 版の `rand` クレートの版を `Cargo.lock` で固定したのと同じ役目を、Ruby 版では **Nix の環境定義** が担っています。

macOS に付属する Ruby は 2.6 です。`Data.define`（Ruby 3.2 以降）などを使う本シリーズのコードは動きません。付属の Ruby で版を確かめると、条件を満たさないことが終了コードで分かります。

```bash
/usr/bin/ruby -e 'exit(Gem::Version.new(RUBY_VERSION) >= Gem::Version.new("3.3"))'; echo $?
```

```text
1
```

この 1 行は、第 6 章の Gulp のタスクが「手元の Ruby を使うか、Nix の Ruby を使うか」を決めるときにも使います。

## 5.4 numo-narray から numo-narray-alt へ

Ruby 版で依存を選ぶときに 1 か所、気をつけたところがあります。Rumale が使う数値計算の gem です。

Ruby の数値計算では、長く **numo-narray**（Python の NumPy に当たる多次元配列）が使われてきました。ところが本家の numo-narray は 2022 年のリリースを最後に更新が止まっています。Rumale の作者は、その fork である **numo-narray-alt** を保守していて、Rumale 2.x はこちらに依存します。

```text
    numo-narray-alt (0.11.2)
    numo-optimize (0.4.0)
      numo-narray-alt (>= 0.9.9, < 0.12.0)
```

名前が `-alt` でも、Ruby のコードからは同じ `Numo::NArray`・`Numo::DFloat` として使います。第 3 章で `Numo::DFloat.cast(...)` と書いたのは、numo-narray-alt が定義したクラスです。

| 観点 | numo-narray（本家） | numo-narray-alt |
|------|------------------|-----------------|
| 保守 | 2022 年で止まっている | Rumale の作者が保守している |
| Rumale 2.x | 依存しない | 依存する（`>= 0.9.10, < 0.12.0`） |
| Ruby のコードからの名前 | `Numo::NArray` | `Numo::NArray`（同じ） |
| ネイティブ拡張 | あり | あり（`bundle install` でビルドする） |

numo-narray-alt の README は、モジュール名・クラス名・メソッド名を変えない「drop-in replacement」だと説明しています。`require "numo/narray"` で読み込め、numo-narray-alt であることを明示したいときは `require "numo/narray/alt"` と書きます。

「インターネットで見つかる numo-narray の記事」と「いま Rumale と一緒に入る gem」が違う、ということを覚えておいてください。`Gemfile` に numo-narray を自分で足すと、同じ `require` の名前と同じクラス名を持つ gem が 2 つ並び、どちらが読み込まれるのかが分かりにくくなります。本シリーズでは numo 系の gem を `Gemfile` に直接書かず、Rumale が選んだものに任せています。gem の選定の経緯は ADR 010 を参照してください。

numo-narray-alt は C で書かれたネイティブ拡張（gem の中の `ext/`）を持つので、`bundle install` のときに手元でコンパイルします。

## 5.5 静的解析 — RuboCop

### RuboCop の設定

RuboCop の設定は `apps/ruby/.rubocop.yml` です。第 1 章で作り、この章で 1 つ足しました。

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

# 機械学習の慣例（特徴量 x・正解ラベル t・y）の 1 文字の名前を許す。
Naming/MethodParameterName:
  AllowedNames: [x, t, y]

# テストは期待する出力をヒアドキュメントでそのまま書くので、メソッドの行数を求めない。
Metrics/MethodLength:
  Exclude:
    - "test/**/*"

# テストは 1 つの振る舞いを 1 つのメソッドに書くので、章のテストのクラスは長くなる。
Metrics/ClassLength:
  Exclude:
    - "test/**/*"
```

RuboCop のルールは cop（警官）と呼ばれ、部門（department）に分かれています。

| 部門 | 内容 | 本シリーズでの例 |
|------|------|--------------|
| `Layout` | 字下げ・空白・改行などの見た目 | `Layout/EndOfLine`（第 4 章） |
| `Style` | 慣用的な書き方 | `Style/StringLiterals`（二重引用符にそろえる） |
| `Lint` | 誤りの可能性が高い書き方 | `Lint/UselessAssignment`（使っていない変数） |
| `Metrics` | メソッドやクラスの長さ・複雑さ | `Metrics/MethodLength`・`Metrics/ClassLength` |
| `Naming` | 名前の付け方 | `Naming/MethodName`・`Naming/AsciiIdentifiers` |

Rust 版の clippy がグループ単位で「使う・使わない」を決めたのに対して、RuboCop は **既定でほぼすべての cop が有効** で、合わないものを 1 つずつ外していきます。外した cop にはすべて、なぜ外すのかをコメントで書いています。

### NewCops を明示しないとどうなるか

`NewCops: enable` は、RuboCop の版を上げたときに増えた新しい cop（pending の cop）を有効にする指定です。`.rubocop.yml` から `NewCops: enable` の行だけを消した設定を `tmp/nonew.yml` に作り、それで実行してみました。

```bash
bundle exec rubocop -c tmp/nonew.yml lib/getting_started_ml/dataset.rb
```

```text
The following cops were added to RuboCop, but are not configured. Please set Enabled to either `true` or `false` in your `.rubocop.yml` file.

Please also note that you can opt-in to new cops by default by adding this to your config:
  AllCops:
    NewCops: enable
Gemspec/AddRuntimeDependency: # new in 1.65
  Enabled: true
Gemspec/AttributeAssignment: # new in 1.77
  Enabled: true
```

`# new in` の行を数えると 163 個ありました。1 ファイルを検査するだけで、163 個の cop について「有効にするか決めてください」と言われます。これでは本当の指摘が埋もれるので、本シリーズは `NewCops: enable` で新しい cop をまとめて有効にしています。代わりに、RuboCop の版を上げると新しい cop が増え、それまで通っていたコードが指摘されることがあります。`Gemfile.lock` で RuboCop の版を固定している（`~> 1.91`）ので、それが起きるのは自分で `bundle update rubocop` したときだけ、のはずです。ただし Nix の環境には、この前提を崩す落とし穴がありました（第 6 章の 6.6 節）。

### わざと違反を入れて、検査が効いていることを確かめる

検査は「動いているつもり」が一番危ないので、違反を入れて失敗することを確かめます。Rust 版の 5.6 節と同じ種類の違反を、Ruby で書きました。

```ruby
# frozen_string_literal: true

module GettingStartedMl
  # 静的解析の確認用。記事のために違反を並べたモジュール。
  module Zzdemo
    module_function

    # 種類が setosa かどうかを返す。
    def setosa?(name)
      if name == "setosa" then true else false end
    end

    # 値を順に表示する。
    def show(values)
      for i in 0...values.size
        puts values[i]
      end
    end

    # 欠損値があるかどうかを返す。
    def missing?(value)
      value == nil
    end

    # 合計を求める。
    def total(values)
      sum = 0.0
      count = values.size
      values.each { |value| sum += value }
      sum
    end
  end
end
```

```bash
bundle exec rake check
```

```text
Running RuboCop...
RuboCop failed!
Inspecting 21 files
...........CW.C......

Offenses:

lib/getting_started_ml/zzdemo.rb:10:7: C: [Correctable] Style/IfWithBooleanLiteralBranches: Remove redundant if with boolean literal branches.
      if name == "setosa" then true else false end
      ^^
lib/getting_started_ml/zzdemo.rb:10:7: C: [Correctable] Style/OneLineConditional: Favor the ternary operator (?:) or multi-line constructs over single-line if/then/else/end constructs.
      if name == "setosa" then true else false end
      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
lib/getting_started_ml/zzdemo.rb:10:7: C: [Correctable] Style/RedundantConditional: This conditional expression can just be replaced by name == "setosa".
      if name == "setosa" then true else false end
      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
lib/getting_started_ml/zzdemo.rb:15:7: C: [Correctable] Style/For: Prefer each over for.
      for i in 0...values.size ...
      ^^^^^^^^^^^^^^^^^^^^^^^^
lib/getting_started_ml/zzdemo.rb:22:13: C: [Correctable] Style/NilComparison: Prefer the use of the nil? predicate.
      value == nil
            ^^
lib/getting_started_ml/zzdemo.rb:28:7: W: [Correctable] Lint/UselessAssignment: Useless assignment to variable - count.
      count = values.size
      ^^^^^
test/chapter02_test.rb:5:1: C: Metrics/ClassLength: Class has too many lines. [101/100]
class Chapter02Test < Minitest::Test ...
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

（同時に置いた CRLF のファイルへの `Layout/EndOfLine` の指摘は、第 4 章で見たので省いています）

違反を入れた 4 つのメソッドから、6 件の指摘が出ました。左端の 1 文字は重さで、`C` は Convention（慣習）、`W` は Warning（警告）です。

| 指摘 | 部門 | 伝えていること | Rust 版の対応する指摘 |
|------|------|--------------|------------------|
| `IfWithBooleanLiteralBranches`・`RedundantConditional` | Style | `if ... then true else false end` は条件式そのものでよい | `needless_bool` |
| `OneLineConditional` | Style | 1 行の `if/then/else/end` より三項演算子か複数行 | なし |
| `Style/For` | Style | `for` より `each` | `needless_range_loop` |
| `Style/NilComparison` | Style | `== nil` より `nil?` | `partialeq_to_none` |
| `Lint/UselessAssignment` | Lint | 使っていない変数 | rustc の `unused_variables` |

1 つの書き方に 3 つの cop が反応しているのが RuboCop らしいところです。`Style/For` は、Ruby では `for` がブロックを作らず、ループの変数がループの外に漏れることを嫌う cop です。Rust 版の `&Vec<f64>` への指摘（`ptr_arg`）に当たるものはありません。Ruby には借用や型の区別が無いからです。

`[Correctable]` の付いた指摘は、`bundle exec rubocop -a`（安全な修正だけ）で自動的に直せます。

### 最後の 1 件は、この章で足したテストから出た

指摘の最後の 1 件は、違反を入れたファイルからではありません。第 4 章で並べ替えの並びを固定するテストを足したら、`Chapter02Test` が 101 行になり、`Metrics/ClassLength` の上限 100 行を超えたのです。

直し方は 2 つ考えられます。テストのクラスを分けるか、`test/` では `Metrics/ClassLength` を外すかです。本シリーズでは後者にしました。章のテストは「1 つの振る舞いを 1 つのメソッド」に書くので、章が進むと自然に長くなります。長さを抑えるためにテストをまとめると、どの振る舞いが壊れたのかが分かりにくくなります。`Metrics/MethodLength` を `test/` で外したのと同じ理由です。

RuboCop の既定の設定（`config/default.yml`）には、`Metrics/ClassLength` について次のコメントがあります。

```yaml
Metrics/ClassLength:
  Description: 'Avoid classes longer than 100 lines of code.'
  Enabled: true
  # Expected to be disabled by default in the next major release.
  # Rejected by a third of projects; those that keep it set the limit three times higher.
```

「次のメジャーリリースでは既定で無効になる見込み。3 分の 1 のプロジェクトが拒否し、残すプロジェクトは上限を 3 倍にしている」。RuboCop 自身が、この cop は意見が分かれると認めています。外すときは、`lib/` には効かせたまま `test/` だけを外し、理由を `.rubocop.yml` のコメントに残しました。

### 抑える範囲

指摘を抑える書き方は 3 つあり、範囲が違います。

| 書き方 | 範囲 |
|--------|------|
| 行末の `# rubocop:disable Style/For` | その行（`# rubocop:disable` と `# rubocop:enable` で挟めばその範囲） |
| `.rubocop.yml` の `Exclude` | 指定したファイル |
| `.rubocop.yml` の `Enabled: false` | プロジェクト全体 |

いちばん狭い範囲で抑えるのが原則です。本シリーズは今のところコードの中に `# rubocop:disable` を 1 つも書いていません。抑えているのは、テストのメソッド名を日本語にするための `Naming` の 2 つと、テストの長さの `Metrics` の 2 つで、どれも `test/` に限っています（`Naming/AsciiIdentifiers` だけはプロジェクト全体で外しています）。Rust 版と同じく、**何を抑えるかより、なぜ抑えるか** を書き残すほうが大切です。

## 5.6 コードカバレッジ — SimpleCov

Ruby の標準ライブラリには、行ごとの実行回数を数える `Coverage` があります。SimpleCov はそれを使ってテストの実行を計測し、報告を作る gem です。第 1 章で `test/test_helper.rb` に組み込みました。

```ruby
require "simplecov"
SimpleCov.start do
  skip "/test/"
  skip "/vendor/"
end
```

`bundle exec rake test` を実行すると、最後に行カバレッジが表示され、`coverage/index.html` に HTML の報告が出ます。第 3 章までで、学習データがある状態では次のとおりでした。

```text
50 runs, 85 assertions, 0 failures, 0 errors, 0 skips
Coverage report generated for Minitest to coverage/index.html
Line coverage: 207 / 220 (94.09%)
```

Rust 版の cargo-llvm-cov がリージョン・関数・行の 3 つを出したのに対して、SimpleCov の既定は **行だけ** です（分岐のカバレッジは `enable_coverage :branch` で有効にできますが、本シリーズでは使っていません）。

### 数字の読み方に注意する

上の数字は、**学習データがある状態** で測ったものです。第 4 章で見たとおり、実データのテストはデータが無ければスキップします。スキップしたテストが通るはずだった行は計測されないので、カバレッジが下がります。

```bash
ML_DATA_DIR=/nonexistent bundle exec rake test
```

```text
50 runs, 68 assertions, 0 failures, 0 errors, 7 skips

You have skipped tests. Run with --verbose for details.
Coverage report generated for Minitest to coverage/index.html
Line coverage: 184 / 220 (83.63%)
```

| 測り方 | テスト | 行カバレッジ |
|--------|-------|------------|
| データあり | 50 runs・0 skips | 207 / 220（94.09%） |
| データなし（CI と同じ条件） | 50 runs・7 skips | 184 / 220（83.63%） |

同じコードのまま 10 ポイント動きます。ファイル別に見ると、動いたのは実データを読むところです（`coverage/.resultset.json` から集計しました）。

| ファイル | データなし | データあり |
|---------|----------|-----------|
| `lib/getting_started_ml/chapter03.rb` | 12 / 22（54.55%） | 22 / 22（100.00%） |
| `lib/getting_started_ml/chapter03/rumale_tree.rb` | 10 / 15（66.67%） | 15 / 15（100.00%） |
| `lib/getting_started_ml/chapter02/table.rb` | 21 / 25（84.00%） | 24 / 25（96.00%） |
| `lib/getting_started_ml/chapter02/preprocessing.rb` | 37 / 42（88.10%） | 42 / 42（100.00%） |
| `lib/getting_started_ml/chapter01.rb` | 30 / 35（85.71%） | 30 / 35（85.71%） |
| `lib/getting_started_ml/chapter02.rb` | 9 / 16（56.25%） | 9 / 16（56.25%） |

`chapter03.rb` が 54.55% から 100% に上がるのは、`test/iris_tree_test.rb` に「実行すると深さごとの正解率と決定木を表示する」というテストがあり、データがあるときだけ第 3 章の `run` を最後まで通すからです。逆に `chapter02.rb` はどちらも 56.25% で、第 2 章の `run` を通すテストがまだ無いことが分かります。

Ruby 版では、実データのテストが走ったかどうかは `skips` の件数で分かります（第 4 章）。カバレッジの差は、**どの行が実データのテストでしか通っていないか** を教えてくれます。データなしで 0% にならない行は、単体テストが架空の値で通しているということです。

カバレッジに下限を設けて CI で強制する運用にするなら、どちらの条件で測った数字なのかを決めておかないと意味がありません。SimpleCov には `minimum_coverage` で下限を設ける機能がありますが、本シリーズは CI に学習データを置けない（再配布できない）ので、下限は設けず、数字は傾向を見るために使います。

`coverage/` は第 4 章の `.gitignore` で除外済みです。

## 5.7 まとめ

この章では、Ruby の依存と品質の道具立てを見ました。

1. **Bundler** — `Gemfile`（Ruby の DSL）に直接の依存を、`Gemfile.lock` に解決済みの版とプラットフォームを記録する。`bundle install`・`bundle outdated`・`bundle update <gem>` で依存を操作する。lock にチェックサムは無い
2. **悲観的バージョン制約** — `"~> 2.2"` は `>= 2.2, < 3.0`、`"~> 2.2.0"` は `>= 2.2.0, < 2.3`。書いた桁数で範囲が決まる
3. **本番依存と開発依存** — `group :development, :test` で分け、`BUNDLE_WITHOUT` で除ける
4. **Ruby の版** — `Gemfile` の下限・lock の記録・`.ruby-version` の 3 か所に書かれ、実際には Nix が 3.3.10 を用意する。乱数の並びもこれで固定される
5. **numo-narray-alt** — 本家の numo-narray は 2022 年で止まり、Rumale 2.x は fork の numo-narray-alt に依存する。numo 系は `Gemfile` に直接書かず Rumale に任せる
6. **RuboCop** — 整形と静的解析を 1 回で検査する。`NewCops: enable` を書かないと 163 個の cop について警告が出る。外す cop は `test/` に限り、理由をコメントに残す
7. **SimpleCov** — 行カバレッジを測る。学習データの有無で 94.09% と 83.63% に分かれるので、どの条件で測った数字かを明示する

次の章では、これらの検査を Rake と Gulp の 1 つのタスクにまとめ、GitHub Actions で自動的に走らせます。そこで、Nix の環境の中で `bundle exec` が `Gemfile.lock` と違う版の gem を読み込んでいた、という落とし穴も見ます。
