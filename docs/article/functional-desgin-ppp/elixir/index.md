# Elixir で学ぶ関数型デザイン

## はじめに

本記事シリーズは、関数型プログラミングにおける設計原則とデザインパターンを Elixir を使って実践的に学ぶためのガイドです。Elixir は Erlang VM 上で動作する関数型言語であり、不変データ、パターンマッチング、並行処理に優れた特性を持っています。これらの特性を活かし、従来のデザインパターンを関数型パラダイムでどのように表現し、活用できるかを探求します。

## 記事構成

### 第1部: 関数型プログラミングの基礎原則

1. [不変性とデータ変換](./01-immutability-and-data-transformation.md)


    - マップと構造体による不変データ
    - パイプ演算子によるデータ変換
    - Enum と Stream による遅延評価

2. [関数合成と高階関数](./02-function-composition.md)


    - 無名関数とキャプチャ演算子
    - 関数合成のパターン
    - 高階関数の活用

3. [多態性の実現方法](./03-polymorphism.md)


    - プロトコルによる多態性
    - ビヘイビアとコールバック
    - パターンマッチングによる型の切り替え

### 第2部: 仕様とテスト

4. [データ検証](./04-data-validation.md)


    - Ecto.Changeset によるバリデーション
    - カスタムバリデータの作成
    - with 式によるエラーハンドリング

5. [プロパティベーステスト](./05-property-based-testing.md)


    - StreamData の基本
    - ジェネレータの作成
    - プロパティの定義と検証

6. [TDD と関数型プログラミング](./06-tdd-and-fp.md)


    - TDD の基本サイクル
    - テスタブルな設計
    - 純粋関数によるビジネスロジック

### 第3部: 副作用と外部システム

7. [副作用と純粋関数](./07-effects-and-pure-functions.md)


    - 純粋関数 vs 副作用
    - Functional Core, Imperative Shell
    - 副作用の遅延実行（Effect as Data）

8. [エラーハンドリング戦略](./08-error-handling-strategies.md)


    - Result 型
    - Validated パターン
    - エラードメインモデリング

9. [I/O と外部システム](./09-io-and-external-systems.md)


    - コンソール I/O の抽象化
    - ファイルシステムの抽象化
    - リポジトリパターン

10. [並行処理パターン](./10-concurrency-patterns.md)


    - 基本的なプロセス
    - Agent（状態管理）
    - GenServer / Task

### 第4部: 振る舞いパターン

11. [Command パターン](./11-command-pattern.md)


    - 不変コマンドデータ
    - コマンドの履歴と Undo
    - GenServer によるコマンド管理

12. [Visitor パターン](./12-visitor-pattern.md)


    - パターンマッチによる訪問
    - プロトコルベースのビジター
    - Enum.reduce による走査

### 第5部: 生成パターン

13. [Abstract Factory パターン](./13-abstract-factory-pattern.md)


    - 関数ファクトリー
    - モジュールベースの生成
    - 設定による依存性注入

14. [Abstract Server パターン](./14-abstract-server-pattern.md)


    - ビヘイビアによる抽象化
    - 実装の差し替え
    - テスタブルな設計

### 第6部: ケーススタディ

15. [ゴシップ好きなバスの運転手](./15-gossiping-bus-drivers.md)


    - Agent による状態管理
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


    - GenServer による状態管理
    - Task による並行実行
    - OTP パターン

19. [Wa-Tor シミュレーション](./19-wator-simulation.md)


    - セルオートマトン
    - 不変グリッドの更新
    - プロセスベースのシミュレーション

### 第7部: まとめと応用

20. [パターン間の相互作用](./20-pattern-interactions.md)


    - Composite + Decorator
    - Command + Observer
    - パターンの組み合わせ

21. [ベストプラクティス](./21-best-practices.md)


    - データ中心設計
    - 純粋関数の分離
    - OTP パターンの活用
    - テスタブルな設計

22. [OO から FP への移行](./22-oo-to-fp-migration.md)


    - 移行戦略（Strangler Fig、アダプター）
    - イベントソーシング
    - 段階的な関数抽出
    - プロトコルによる多態性

---

## 本シリーズの特徴

### Elixir の特性を活かした実装

Elixir の強力な機能（パターンマッチング、パイプ演算子、プロトコル、OTP など）を活用し、実践的な関数型プログラミングを学びます。

### 並行処理の自然な統合

Elixir は Erlang VM 上で動作するため、並行処理が言語の中核に組み込まれています。アクターモデルと不変性により、スケーラブルで堅牢なシステムを構築する方法を学びます。

### 実践重視

すべてのパターンは ExUnit による実際に動作するテストコードを含みます。理論だけでなく、実装を通じて理解を深めることを重視しています。

### 段階的な学習

基礎から応用へと段階的に内容が進行します。各章は独立して読むこともできますが、順番に読むことでより深い理解が得られます。

---

## 対象読者

- 関数型プログラミングに興味がある Elixir 開発者
- Elixir を学習中の開発者
- オブジェクト指向デザインパターンの知識があり、関数型での表現を学びたい開発者
- Clojure 版や他言語版と比較して学習したい開発者

---

## 前提知識

- プログラミングの基礎知識
- Elixir の基本的な文法（マップ、構造体、パターンマッチングなど）
- デザインパターンの基本概念（推奨）

---

## 開発環境

本シリーズのコードは以下の環境で動作確認しています：

- Elixir 1.16+
- Erlang/OTP 26+
- Mix ビルドツール
- ExUnit テストフレームワーク
- Nix による環境管理

### 環境構築

```bash
# Nix を使用した開発環境の起動
nix develop .#elixir

# テストの実行
cd apps/elixir/part1
mix test
```

---

## 参考書籍

本シリーズは以下の書籍の内容を参考にしています：

- 「Functional Design: Principles, Patterns, and Practices」Robert C. Martin
- 「Programming Elixir」Dave Thomas
- 「Elixir in Action」Saša Jurić
- 「Designing Elixir Systems with OTP」James Edward Gray II, Bruce A. Tate
