# Scala で学ぶ並行処理

## 概要

本シリーズでは、Scala 3 を使用して並行処理プログラミングの基礎から応用までを学びます。

## 目次

| Part | トピック | 内容 |
|------|----------|------|
| [Part I](part-1.md) | 逐次処理 | パスワードクラッキング、パフォーマンス |
| [Part II](part-2.md) | スレッド | Thread、Future、並列実行 |
| [Part III](part-3.md) | マルチタスキング | ゲームループ、タスクスケジューリング |
| [Part IV](part-4.md) | 並列パターン | Fork/Join、パイプライン |
| [Part V](part-5.md) | 同期と排他制御 | synchronized、デッドロック回避 |
| [Part VI](part-6.md) | ノンブロッキング I/O | NIO、Selector |
| [Part VII](part-7.md) | 非同期プログラミング | Future、Promise |
| [Part VIII](part-8.md) | 分散並列処理 | MapReduce、並列コレクション |

## 環境設定

### 必要なツール

- Scala 3.3.1
- sbt 1.9.7
- JDK 11 以上

### プロジェクト構成

```
apps/scala/
├── build.sbt
├── project/
│   └── build.properties
├── src/
│   ├── main/scala/concurrency/
│   │   ├── ch02/    # Part I
│   │   ├── ch04/    # Part II
│   │   ├── ch05/    # Part II
│   │   ├── ch06/    # Part III
│   │   ├── ch07/    # Part IV
│   │   ├── ch08/    # Part V
│   │   └── ch13/    # Part VIII
│   └── test/scala/concurrency/
```

### ビルドと実行

```bash
cd apps/scala
sbt compile    # コンパイル
sbt test       # テスト実行
```

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
- [Scala Documentation](https://docs.scala-lang.org/)
- [Scala Parallel Collections](https://docs.scala-lang.org/overviews/parallel-collections/overview.html)
