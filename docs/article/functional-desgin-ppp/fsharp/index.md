# F# で学ぶ関数型デザイン

## はじめに

本記事シリーズは、関数型プログラミングにおける設計原則とデザインパターンを F# を使って実践的に学ぶためのガイドです。F# は .NET プラットフォーム上で動作する関数型ファースト言語であり、代数的データ型、パターンマッチング、型推論など、関数型プログラミングの本質的な機能をネイティブにサポートしています。

## 記事構成

### 第1部: 関数型プログラミングの基礎原則

1. [不変性とデータ変換](./01-immutability-and-data-transformation.md)


    - レコードと不変データ構造
    - パイプライン演算子（|>）
    - 副作用の分離

2. [関数合成と高階関数](./02-function-composition.md)


    - >> と << による関数合成
    - カリー化と部分適用
    - 高階関数の活用

3. [多態性の実現方法](./03-polymorphism.md)


    - 判別共用体（Discriminated Union）
    - アクティブパターン
    - 型拡張とインターフェース

### 第2部: 仕様とテスト

4. [データ検証](./04-data-validation.md)


    - Result 型によるエラーハンドリング
    - Computation Expression
    - カスタムバリデータの作成

5. [プロパティベーステスト](./05-property-based-testing.md)


    - FsCheck の基本
    - ジェネレータの作成
    - プロパティの定義と検証

6. [TDD と関数型プログラミング](./06-tdd-functional.md)


    - xUnit / Expecto によるテスト
    - Red-Green-Refactor サイクル
    - テストファーストの関数設計

### 第3部: 構造パターン

7. [Composite パターン](./07-composite-pattern.md)


    - 再帰的な判別共用体
    - ツリー構造の表現
    - fold による走査

8. [Decorator パターン](./08-decorator-pattern.md)


    - 関数合成によるデコレーション
    - 高階関数でのラッピング
    - 振る舞いの動的追加

9. [Adapter パターン](./09-adapter-pattern.md)


    - 関数アダプター
    - プロトコル変換
    - レガシーコードとの統合

### 第4部: 振る舞いパターン

10. [Strategy パターン](./10-strategy-pattern.md)


    - 第一級関数としての戦略
    - 動的な戦略切り替え
    - 戦略の合成

11. [Command パターン](./11-command-pattern.md)


    - 不変コマンドレコード
    - コマンドの履歴と Undo
    - マクロコマンド

12. [Visitor パターン](./12-visitor-pattern.md)


    - パターンマッチによる訪問
    - fold ベースのビジター
    - 拡張可能なビジター

### 第5部: 生成パターン

13. [Abstract Factory パターン](./13-abstract-factory-pattern.md)


    - 関数ファクトリー
    - モジュールによる抽象化
    - 依存性注入

14. [Abstract Server パターン](./14-abstract-server-pattern.md)


    - インターフェースによる抽象化
    - 実装の差し替え
    - テスタブルな設計

### 第6部: ケーススタディ

15. [ゴシップ好きなバスの運転手](./15-gossiping-bus-drivers.md)


    - 不変状態の管理
    - シミュレーションの実装
    - 再帰的な処理

16. [給与計算システム](./16-payroll-system.md)


    - ドメインモデリング
    - 多態的な計算
    - レポート生成

17. [レンタルビデオシステム](./17-video-rental-system.md)


    - 状態遷移の管理
    - イベントソーシング
    - ビジネスルールの実装

18. [並行処理システム](./18-concurrency-system.md)


    - MailboxProcessor（Agent）
    - 非同期ワークフロー
    - 並行状態管理

19. [Wa-Tor シミュレーション](./19-wator-simulation.md)


    - セルオートマトン
    - 不変グリッドの更新
    - シミュレーションループ

### 第7部: まとめと応用

20. [パターン間の相互作用](./20-pattern-interactions.md)


    - Composite + Decorator
    - Command + Observer
    - パターンの組み合わせ

21. [ベストプラクティス](./21-best-practices.md)


    - データ中心設計
    - Computation Expression
    - 関数デコレーター
    - テスタブルな設計

22. [OO から FP への移行](./22-oo-to-fp-migration.md)


    - 移行戦略
    - イベントソーシング
    - 段階的な関数抽出
    - 判別共用体による多態性

---

## 本シリーズの特徴

### F# の特性を活かした実装

F# は関数型プログラミングのために設計された言語であり、以下の機能をネイティブにサポートしています：

- **判別共用体（Discriminated Union）**: 代数的データ型を簡潔に定義
- **パイプライン演算子（|>）**: データ変換の流れを自然に記述
- **パターンマッチング**: 網羅性チェック付きの強力なパターンマッチ
- **型推論**: 明示的な型注釈を最小限に
- **Computation Expression**: モナド的な計算を DSL として定義

### 実践重視

すべてのパターンは xUnit による実際に動作するテストコードを含みます。理論だけでなく、実装を通じて理解を深めることを重視しています。

### 段階的な学習

基礎から応用へと段階的に内容が進行します。各章は独立して読むこともできますが、順番に読むことでより深い理解が得られます。

---

## 対象読者

- 関数型プログラミングに興味がある F# 開発者
- F# を学習中の開発者
- オブジェクト指向デザインパターンの知識があり、関数型での表現を学びたい開発者
- Clojure / Scala 版と比較して学習したい開発者
- .NET 環境で関数型アプローチを導入したい開発者

---

## 前提知識

- プログラミングの基礎知識
- F# の基本的な文法（let バインディング、関数定義、パターンマッチングなど）
- デザインパターンの基本概念（推奨）

---

## 開発環境

本シリーズのコードは以下の環境で動作確認しています：

- .NET 8.0
- F# 8.0
- xUnit 2.x
- FsCheck（プロパティベーステスト用）
- Nix による環境管理

### 環境構築

```bash
# Nix を使用した開発環境の起動
nix develop .#fsharp

# プロジェクトの作成（Part 1 の例）
cd apps/fsharp/part1
dotnet new sln -n FunctionalDesignPart1
dotnet new classlib -lang F# -n FunctionalDesign -o src
dotnet new xunit -lang F# -n FunctionalDesign.Tests -o tests
dotnet sln add src/FunctionalDesign.fsproj tests/FunctionalDesign.Tests.fsproj
dotnet add tests/FunctionalDesign.Tests.fsproj reference src/FunctionalDesign.fsproj

# テストの実行
dotnet test

# REPL の起動
dotnet fsi
```

---

## Clojure / Scala との比較

| 概念 | Clojure | Scala | F# |
|-----|---------|-------|-----|
| 不変データ | デフォルト | `val`, `case class` | デフォルト |
| ADT | プロトコル + レコード | `sealed trait` | 判別共用体 |
| パイプライン | `->`, `->>` | メソッドチェーン | `|>`, `>>` |
| パターンマッチ | `case`, `cond` | `match` | `match` |
| 型システム | 動的 | 静的（強い） | 静的（強い推論） |
| 並行処理 | Agent, core.async | Future, Actor | MailboxProcessor, Async |

---

## 参考書籍

本シリーズは以下の書籍の内容を参考にしています：

- 「Functional Design: Principles, Patterns, and Practices」Robert C. Martin
- 「Domain Modeling Made Functional」Scott Wlaschin
- 「F# for Fun and Profit」Scott Wlaschin（オンライン）
- 「Real-World Functional Programming」Tomas Petricek, Jon Skeet
