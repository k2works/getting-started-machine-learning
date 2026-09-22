# 機械学習から始める Ruby 入門

Ruby は、ブロックと `Enumerable` による簡潔な書き方を特徴とする動的型付けのスクリプト言語です。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、機械学習ライブラリ Rumale と突き合わせながら、Ruby の書き方とエコシステムを学びます。

Ruby は「**Python に近い動的型付け言語**」として扱います。[Python 版](../python/index.md) とは書き方が近く、Rumale の `fit`・`predict` は scikit-learn に似た API なので、自作 → ライブラリで突き合わせるという流れをほぼ全章で書けます。一方、型の検査では [TypeScript 版](../typescript/index.md)・[Kotlin 版](../kotlin/index.md) と対比します。Ruby 版では型注釈（RBS・Steep）を使わず、型の誤りをテストで捕まえます。

## 特徴

- **動的型付け**: 変数にも引数にも型を書かない。型の誤りやメソッド名の衝突は、実行して初めて分かる。だからテストが唯一の安全網になる
- **ブロックと `Enumerable`**: `map`・`count`・`zip` などにブロックを渡して、ループを書かずにデータを変換する
- **例外で失敗を表す**: 失敗しうる処理は `ArgumentError`・`KeyError` などの例外を投げる。Rust 版の `Result` とは逆の流儀
- **値オブジェクトが 1 行で書ける**: `Data.define`（Ruby 3.2 以降）で、不変で `==` で比べられる型を作れる

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Ruby で取り組んでください
- 乱数生成器が Python 版と異なるため、第 2 章以降で訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は Ruby 版の実装で実測した値を載せます
- Rumale に無いもの（PCA の寄与率）は自作が最終実装です。該当する章ではライブラリへの置き換えの節を設けず、理由を明記します
- BOM 付きの CSV は、`CSV.foreach`・`CSV.read` でファイルを開けば BOM が取り除かれます。ただし文字列から読む `CSV.parse` では残るので、読み方に注意が要ります（第 1 章）
- 型注釈（RBS・Steep）は使いません。理由は第 1 章と第 5 章で扱います

## 開発環境

| ツール | 用途 |
|--------|------|
| [Ruby](https://www.ruby-lang.org/) 3.3・Bundler | 言語・依存管理 |
| [Rake](https://ruby.github.io/rake/) | タスク（`rake check`・`rake run`） |
| [Minitest](https://github.com/minitest/minitest) | テスティングフレームワーク |
| [RuboCop](https://rubocop.org/) | 整形・静的解析 |
| [SimpleCov](https://github.com/simplecov-ruby/simplecov) | カバレッジ |

Nix の環境（`nix develop .#ruby`）は Ruby 3.3.10・Bundler 2.7.2 です。macOS に付属する Ruby 2.6 は対象外です。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [csv](https://github.com/ruby/csv) | CSV の読み込み（ファイルを開けば BOM を取り除く） | 第 1 章 |
| [Rumale](https://github.com/yoshoku/rumale) | 決定木・ランダムフォレスト・回帰・ロジスティック回帰・K-means・主成分分析・評価指標・交差検証 | 第 3 章（執筆予定） |
| numo-narray-alt | 行列（Rumale の依存として入る） | 第 7 章（執筆予定） |
| `Marshal` | 学習済みモデルの保存 | 第 8 章（執筆予定） |
| [Sinatra](https://sinatrarb.com/)・Puma | 予測 API | 第 15 章（執筆予定） |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 010](../../../adr/010-ruby-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/ruby/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#ruby
cd apps/ruby
bundle install
bundle exec rake check
bundle exec rake 'run[chapter01]'
```

`rake check` は RuboCop → テスト（カバレッジつき）の順に検査します。

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| 第 2 章（執筆予定） | データの前処理と三角測量 |
| 第 3 章（執筆予定） | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| 第 4 章（執筆予定） | バージョン管理とデータ管理 |
| 第 5 章（執筆予定） | パッケージ管理と静的解析 |
| 第 6 章（執筆予定） | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| 第 7 章（執筆予定） | 線形回帰による数値予測 |
| 第 8 章（執筆予定） | 実践的な分類と前処理パイプライン |
| 第 9 章（執筆予定） | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| 第 10 章（執筆予定） | ロジスティック回帰とアンサンブル学習 |
| 第 11 章（執筆予定） | 評価指標と交差検証 |
| 第 12 章（執筆予定） | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| 第 13 章（執筆予定） | 主成分分析による次元削減 |
| 第 14 章（執筆予定） | K-means によるクラスタリング |
| 第 15 章（執筆予定） | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Ruby で取り組んでください。
