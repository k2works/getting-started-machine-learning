# Scala で学ぶ関数型デザイン

## はじめに

本記事シリーズは、関数型プログラミングにおける設計原則とデザインパターンを Scala を使って実践的に学ぶためのガイドです。Scala のオブジェクト指向と関数型のハイブリッドな特性を活かし、従来のデザインパターンを関数型パラダイムでどのように表現し、活用できるかを探求します。

## 記事構成

### 第1部: 関数型プログラミングの基礎原則

1. [不変性とデータ変換](./01-immutability-and-data-transformation.md)


    - case class と不変データ構造
    - データ変換パイプライン
    - 副作用の分離

2. [関数合成と高階関数](./02-function-composition.md)


    - andThen と compose による関数合成
    - カリー化と部分適用
    - 高階関数の活用

3. [多態性の実現方法](./03-polymorphism.md)


    - sealed trait と代数的データ型
    - トレイトとミックスイン
    - 型クラスによる既存型の拡張

### 第2部: 仕様とテスト

4. [データ検証](./04-data-validation.md)


    - 型によるバリデーション
    - Either によるエラーハンドリング
    - カスタムバリデータの作成

5. [プロパティベーステスト](./05-property-based-testing.md)


    - ScalaCheck の基本
    - ジェネレータの作成
    - プロパティの定義と検証

6. [TDD と関数型プログラミング](./06-tdd-functional.md)


    - ScalaTest によるテスト
    - Red-Green-Refactor サイクル
    - テストファーストの関数設計

### 第3部: 構造パターン

7. [Composite パターン](./07-composite-pattern.md)


    - 再帰的なデータ構造
    - ADT によるツリー構造
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


    - 不変コマンドオブジェクト
    - コマンドの履歴と Undo
    - マクロコマンド

12. [Visitor パターン](./12-visitor-pattern.md)


    - パターンマッチによる訪問
    - fold ベースのビジター
    - 拡張可能なビジター

### 第5部: 生成パターン

13. [Abstract Factory パターン](./13-abstract-factory-pattern.md)


    - 関数ファクトリー
    - 型クラスベースの生成
    - 依存性注入

14. [Abstract Server パターン](./14-abstract-server-pattern.md)


    - トレイトによる抽象化
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


    - 状態マシン
    - イベントバス
    - AtomicReference による同期

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
    - IO モナド
    - 関数デコレーター
    - テスタブルな設計

22. [OO から FP への移行](./22-oo-to-fp-migration.md)


    - 移行戦略（Strangler Fig、アダプター）
    - イベントソーシング
    - 段階的な関数抽出
    - ADT による多態性

---

## 本シリーズの特徴

### Scala の特性を活かした実装

Scala 3 の新機能（enum、given/using、拡張メソッドなど）を活用し、モダンな関数型プログラミングを学びます。

### 実践重視

すべてのパターンは ScalaTest による実際に動作するテストコードを含みます。理論だけでなく、実装を通じて理解を深めることを重視しています。

### 段階的な学習

基礎から応用へと段階的に内容が進行します。各章は独立して読むこともできますが、順番に読むことでより深い理解が得られます。

---

## 対象読者

- 関数型プログラミングに興味がある Scala 開発者
- Scala を学習中の開発者
- オブジェクト指向デザインパターンの知識があり、関数型での表現を学びたい開発者
- Clojure 版と比較して学習したい開発者

---

## 前提知識

- プログラミングの基礎知識
- Scala の基本的な文法（case class、trait、パターンマッチングなど）
- デザインパターンの基本概念（推奨）

---

## 開発環境

本シリーズのコードは以下の環境で動作確認しています：

- Scala 3.3.1
- sbt 1.9.7
- ScalaTest 3.2.17
- Nix による環境管理

### 環境構築

```bash
# Nix を使用した開発環境の起動
nix develop .#scala

# テストの実行
cd apps/scala/part1
sbt test
```

---

## 参考書籍

本シリーズは以下の書籍の内容を参考にしています：

- 「Functional Design: Principles, Patterns, and Practices」Robert C. Martin
- 「Functional Programming in Scala」Paul Chiusano, Rúnar Bjarnason
- 「Programming in Scala」Martin Odersky, Lex Spoon, Bill Venners
- 「Scala with Cats」Noel Welsh, Dave Gurnell
