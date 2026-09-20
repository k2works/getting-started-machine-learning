# 機械学習から始める Rust 入門

Rust は、所有権による記憶域の管理と、例外を持たない型ベースのエラー処理を特徴とするコンパイル言語です。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、機械学習ライブラリ linfa と突き合わせながら、Rust の書き方とエコシステムを学びます。

Rust は「**ライブラリが揃った静的型付け言語**」として扱います。[Java 版](../java/index.md)（Tribuo）・[C# 版](../csharp/index.md)（ML.NET）と同じ立場で、決定木から交差検証まで自作 → ライブラリで突き合わせるという流れをほぼ全章で書けます。一方、エラーの扱いでは [Go 版](../go/index.md)（`error` の戻り値）・[F# 版](../fsharp/index.md)（`Result`）と、所有権では全言語版と対比します。

## 特徴

- **所有権と借用**: データを渡すときに「所有権を移す」「借りる」「複製する」のどれかを選ぶ。読むだけなら `&[T]` で借りる。どこで `clone()` するかが設計になる
- **例外が無い**: 失敗しうる処理は `Result<T, E>` を返し、`?` 演算子で上へ返す。エラーの種類は `enum` で表し、`match` の網羅性をコンパイラが検査する
- **テストが実装と同じファイルにある**: `#[cfg(test)] mod tests` はテストのときだけコンパイルされ、非公開の関数にも手が届く。関数名に日本語をそのまま使える
- **クレートの版が型を分ける**: ndarray 0.16 と 0.17、rand 0.8 と 0.9 を混ぜると「同じ名前の別の型」になる。linfa が使う版にそろえる

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Rust で取り組んでください
- 分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は Rust 版の実装で実測した値を載せています
- linfa に無いもの（ランダムフォレスト・欠損値の補完・ダミー変数化）は自作が最終実装です。該当する章ではライブラリへの置き換えの節を設けず、理由を明記します
- BOM 付きの CSV は、csv クレートが BOM を自動で取り除きます。ほかの言語版で毎回書いてきた BOM の除去が、Rust 版だけ要りません

## 開発環境

| ツール | 用途 |
|--------|------|
| [Rust](https://www.rust-lang.org/) 1.91（edition 2024）・Cargo | 言語・ビルド・依存管理 |
| 標準の `#[test]`（`cargo test`） | テスティングフレームワーク |
| [rustfmt](https://github.com/rust-lang/rustfmt) | 整形（`cargo fmt --check` で検査） |
| [clippy](https://doc.rust-lang.org/clippy/) | 静的解析（`cargo clippy -- -D warnings`） |
| [cargo-llvm-cov](https://github.com/taiki-e/cargo-llvm-cov) | カバレッジ |

Nix の環境（`nix develop .#rust`）は rustc 1.91.1・cargo 1.91.0・rustfmt 1.8.0・clippy 0.1.91・cargo-llvm-cov 0.6.20 です。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [csv](https://docs.rs/csv/) | CSV の読み込み（BOM を自動で取り除く） | 第 1 章 |
| [rand](https://docs.rs/rand/) | 乱数（訓練データとテストデータの分割） | 第 2 章 |
| [linfa](https://rust-ml.github.io/linfa/) | 決定木・回帰・ロジスティック回帰・K-means・主成分分析・評価指標・交差検証 | 第 3 章 |
| [ndarray](https://docs.rs/ndarray/) | 行列 | 第 7 章 |
| [serde](https://serde.rs/)・serde_json | 学習済みモデルの保存 | 第 8 章 |
| [encoding_rs](https://docs.rs/encoding_rs/) | Shift_JIS の CSV | 第 9 章 |
| [axum](https://docs.rs/axum/)・tokio | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 009](../../../adr/009-rust-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/rust/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/rust
cargo test
cargo run --bin chapters -- chapter01
```

リポジトリのルートで `npx gulp apps:check:rust` を実行すると、CI と同じ順（整形・clippy・テスト・カバレッジ）でまとめて検査できます。

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| 第 2 章 | データの前処理と三角測量 |
| 第 3 章 | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| 第 4 章 | バージョン管理とデータ管理 |
| 第 5 章 | パッケージ管理と静的解析 |
| 第 6 章 | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| 第 7 章 | 線形回帰による数値予測 |
| 第 8 章 | 実践的な分類と前処理パイプライン |
| 第 9 章 | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| 第 10 章 | ロジスティック回帰とアンサンブル学習 |
| 第 11 章 | 評価指標と交差検証 |
| 第 12 章 | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| 第 13 章 | 主成分分析による次元削減 |
| 第 14 章 | K-means によるクラスタリング |
| 第 15 章 | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Rust で取り組んでください。
