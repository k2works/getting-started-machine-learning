---
type: Article
title: "Rust で学ぶ関数型プログラミング Part IV: 非同期処理とストリーム"
description: "Part III では Option と Result を使ったエラーハンドリングと、代数的データ型（ADT）によるドメインモデリングを学びました。"
tags: [article, grokkingfp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# Rust で学ぶ関数型プログラミング Part IV: 非同期処理とストリーム

## はじめに

Part III では Option と Result を使ったエラーハンドリングと、代数的データ型（ADT）によるドメインモデリングを学びました。Part IV では、関数型プログラミングの重要なトピックである**副作用の管理**と**ストリーム処理**を扱います。

Scala では IO モナドと fs2 Stream を使いますが、Rust では `async/await` と `tokio-stream` / `futures` クレートを使って同様の概念を実現します。

## 第8章: 非同期処理と副作用の管理

### 8.1 副作用の問題

関数型プログラミングでは、副作用（side effects）を明示的に管理することが重要です。副作用とは、関数の外部の状態を変更したり、外部の状態に依存したりする操作のことです。

```rust
/// 不純な関数（副作用あり）- サイコロを振る
pub fn cast_the_die_impure() -> i32 {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .subsec_nanos();
    (nanos % 6) as i32 + 1
}
```

この関数は**参照透過性**（referential transparency）を持ちません。同じ引数で呼び出しても、毎回異なる結果を返す可能性があります。

### 8.2 async/await による副作用の遅延実行

Rust の `async/await` は、副作用を遅延実行するための仕組みを提供します。Scala の IO モナドと同様に、副作用を「記述」と「実行」に分離できます。

```rust
/// 非同期でサイコロを振る（副作用を遅延）
pub async fn cast_the_die() -> i32 {
    cast_the_die_impure()
}

/// 2回サイコロを振って合計を返す
pub async fn cast_the_die_twice() -> i32 {
    let first = cast_the_die().await;
    let second = cast_the_die().await;
    first + second
}
```

`async fn` は `Future` を返します。`Future` は「将来の値の約束」であり、`.await` されるまで実行されません。これにより：

1. **遅延評価**: 副作用は `.await` まで実行されない
2. **合成可能**: 複数の非同期処理を組み合わせられる
3. **明示的な副作用**: 型システムで副作用の存在が明示される

### 8.3 純粋関数と非同期関数の分離

実際のアプリケーションでは、ビジネスロジック（純粋関数）と副作用を含む処理を分離することが重要です。

```rust
/// ミーティング時間を表す構造体
#[derive(Debug, Clone, PartialEq)]
pub struct MeetingTime {
    pub start_hour: i32,
    pub end_hour: i32,
}

/// 2つのミーティングが重なっているか判定（純粋関数）
pub fn meetings_overlap(m1: &MeetingTime, m2: &MeetingTime) -> bool {
    m1.start_hour < m2.end_hour && m2.start_hour < m1.end_hour
}

/// 可能なミーティング時間を計算（純粋関数）
pub fn possible_meetings(
    existing_meetings: &[MeetingTime],
    start_hour: i32,
    end_hour: i32,
    length_hours: i32,
) -> Vec<MeetingTime> {
    (start_hour..=end_hour - length_hours)
        .map(|start| MeetingTime::new(start, start + length_hours))
        .filter(|slot| {
            existing_meetings
                .iter()
                .all(|meeting| !meetings_overlap(meeting, slot))
        })
        .collect()
}
```

純粋関数は：


- テストが容易
- 並列実行が安全
- 結果を予測可能

### 8.4 カレンダー API のシミュレーション

外部 API との連携は典型的な副作用です。`async fn` で明示的に表現します。

```rust
/// カレンダーエントリを取得（非同期）
pub async fn calendar_entries(name: &str) -> Vec<MeetingTime> {
    // 実際にはAPIコールをシミュレート
    tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;

    // ダミーデータを返す
    match name {
        "Alice" => vec![MeetingTime::new(9, 10), MeetingTime::new(14, 15)],
        "Bob" => vec![MeetingTime::new(10, 12)],
        _ => vec![],
    }
}

/// 複数人の予定を取得（非同期）
pub async fn scheduled_meetings(person1: &str, person2: &str) -> Vec<MeetingTime> {
    let entries1 = calendar_entries(person1).await;
    let entries2 = calendar_entries(person2).await;

    let mut result = entries1;
    result.extend(entries2);
    result
}
```

### 8.5 Result を使ったエラーハンドリング

非同期処理では、ネットワークエラーなどの失敗が発生します。`Result` と組み合わせて堅牢な処理を実現します。

```rust
/// 失敗する可能性のあるカレンダー取得
pub async fn calendar_entries_may_fail(name: &str) -> Result<Vec<MeetingTime>, String> {
    if name == "Error" {
        Err("API call failed".to_string())
    } else {
        Ok(calendar_entries(name).await)
    }
}

/// フォールバック付きのカレンダー取得
pub async fn calendar_entries_with_fallback(name: &str) -> Vec<MeetingTime> {
    calendar_entries_may_fail(name)
        .await
        .unwrap_or_else(|_| vec![])
}
```

### 8.6 リトライ機能

一時的なエラーに対してリトライを行う汎用関数を実装します。

```rust
/// 指定回数リトライする
pub async fn retry<T, E, F, Fut>(action: F, max_retries: usize) -> Result<T, E>
where
    F: Fn() -> Fut,
    Fut: Future<Output = Result<T, E>>,
{
    let mut last_error = None;

    for _ in 0..max_retries {
        match action().await {
            Ok(result) => return Ok(result),
            Err(e) => last_error = Some(e),
        }
    }

    Err(last_error.unwrap())
}

/// デフォルト値付きリトライ
pub async fn retry_with_default<T, E, F, Fut>(action: F, max_retries: usize, default: T) -> T
where
    F: Fn() -> Fut,
    Fut: Future<Output = Result<T, E>>,
{
    retry(action, max_retries).await.unwrap_or(default)
}
```

### 8.7 複数の非同期処理の合成

複数の非同期処理を並行実行することで、パフォーマンスを向上させます。

```rust
/// 複数人の予定を並行取得
pub async fn scheduled_meetings_for_all(attendees: &[&str]) -> Vec<MeetingTime> {
    let futures: Vec<_> = attendees
        .iter()
        .map(|name| calendar_entries(name))
        .collect();

    let results = futures::future::join_all(futures).await;
    results.into_iter().flatten().collect()
}

/// 複数の非同期処理を順番に実行して結合
pub async fn combine_async<A, B, C, F>(
    future1: impl Future<Output = A>,
    future2: impl Future<Output = B>,
    combine: F,
) -> C
where
    F: FnOnce(A, B) -> C,
{
    let a = future1.await;
    let b = future2.await;
    combine(a, b)
}
```

### 8.8 動的な Future の処理

条件によって異なる型の Future を返す場合、`Box<dyn Future>` を使用します。

```rust
pub type BoxFuture<'a, T> = Pin<Box<dyn Future<Output = T> + Send + 'a>>;

/// 条件に応じて異なる非同期処理を返す
pub fn conditional_async(condition: bool) -> BoxFuture<'static, &'static str> {
    if condition {
        Box::pin(async { "Success" })
    } else {
        Box::pin(async { "Failure" })
    }
}
```

## 第9章: ストリーム処理

### 9.1 ストリームの基本

ストリームは「遅延評価される値のシーケンス」です。Rust では `futures::Stream` トレイトで表現します。

```rust
use futures::stream::{self, Stream, StreamExt};

/// イテレータからストリームを作成
pub fn numbers_stream(start: i32, end: i32) -> impl Stream<Item = i32> {
    stream::iter(start..=end)
}

/// ストリームに map を適用
pub fn map_stream<S, F, T, U>(stream: S, f: F) -> impl Stream<Item = U>
where
    S: Stream<Item = T>,
    F: FnMut(T) -> U,
{
    stream.map(f)
}

/// ストリームに filter を適用
pub fn filter_stream<S, F, T>(stream: S, mut f: F) -> impl Stream<Item = T>
where
    S: Stream<Item = T>,
    F: FnMut(&T) -> bool,
{
    stream.filter(move |x| {
        let result = f(x);
        async move { result }
    })
}
```

### 9.2 ストリームの合成

ストリームは様々な方法で合成できます。

```rust
/// 2つのストリームを結合
pub fn concat_streams<S1, S2, T>(stream1: S1, stream2: S2) -> impl Stream<Item = T>
where
    S1: Stream<Item = T>,
    S2: Stream<Item = T>,
{
    stream1.chain(stream2)
}

/// ストリームを take で切り取り
pub fn take_stream<S, T>(stream: S, n: usize) -> impl Stream<Item = T>
where
    S: Stream<Item = T>,
{
    stream.take(n)
}

/// ストリームを skip で読み飛ばし
pub fn skip_stream<S, T>(stream: S, n: usize) -> impl Stream<Item = T>
where
    S: Stream<Item = T>,
{
    stream.skip(n)
}
```

### 9.3 無限ストリーム

遅延評価の力により、無限のシーケンスを表現できます。

```rust
/// 無限ストリームを生成
pub fn infinite_stream<T, F>(initial: T, f: F) -> impl Stream<Item = T>
where
    T: Clone,
    F: FnMut(&T) -> T,
{
    stream::unfold((initial, f), |(state, mut f)| async move {
        let next = f(&state);
        Some((state, (next, f)))
    })
}

/// フィボナッチ数列のストリーム
pub fn fibonacci_stream() -> impl Stream<Item = u64> {
    stream::unfold((0u64, 1u64), |(a, b)| async move {
        Some((a, (b, a + b)))
    })
}

/// 繰り返しストリーム
pub fn repeat_stream<T: Clone>(value: T) -> impl Stream<Item = T> {
    stream::repeat(value)
}
```

使用例：
```rust
let stream = fibonacci_stream();
let first_ten: Vec<u64> = stream.take(10).collect().await;
assert_eq!(first_ten, vec![0, 1, 1, 2, 3, 5, 8, 13, 21, 34]);
```

### 9.4 ストリームの畳み込み

ストリームの全要素を単一の値に畳み込みます。

```rust
/// ストリームを fold で畳み込み
pub async fn fold_stream<S, T, U, F>(stream: S, initial: U, mut f: F) -> U
where
    S: Stream<Item = T>,
    F: FnMut(U, T) -> U,
{
    stream
        .fold(initial, |acc, x| {
            let result = f(acc, x);
            async move { result }
        })
        .await
}

/// ストリームの合計
pub async fn sum_stream<S>(stream: S) -> i32
where
    S: Stream<Item = i32>,
{
    stream.fold(0, |acc, x| async move { acc + x }).await
}
```

### 9.5 チャンクと移動平均

ストリームをチャンク（グループ）に分割したり、移動平均を計算したりできます。

```rust
/// チャンク版
pub fn chunks_stream<S, T>(stream: S, size: usize) -> impl Stream<Item = Vec<T>>
where
    S: Stream<Item = T>,
{
    stream.chunks(size)
}

/// 移動平均を計算するストリーム
pub fn moving_average<S>(stream: S, window_size: usize) -> impl Stream<Item = f64>
where
    S: Stream<Item = f64> + Send + 'static,
{
    stream::unfold(
        (stream.boxed(), Vec::new(), window_size),
        |(mut stream, mut window, size)| async move {
            match stream.next().await {
                Some(value) => {
                    window.push(value);
                    if window.len() > size {
                        window.remove(0);
                    }
                    let avg = window.iter().sum::<f64>() / window.len() as f64;
                    Some((avg, (stream, window, size)))
                }
                None => None,
            }
        },
    )
}
```

### 9.6 zip でストリームを結合

2つのストリームを要素ごとにペアにします。

```rust
/// 2つのストリームを zip
pub fn zip_streams<S1, S2, T1, T2>(stream1: S1, stream2: S2) -> impl Stream<Item = (T1, T2)>
where
    S1: Stream<Item = T1>,
    S2: Stream<Item = T2>,
{
    stream1.zip(stream2)
}

/// インデックス付きストリーム
pub fn enumerate_stream<S, T>(stream: S) -> impl Stream<Item = (usize, T)>
where
    S: Stream<Item = T>,
{
    stream.enumerate()
}
```

### 9.7 flatMap でストリームを平坦化

ネストされたストリームを平坦化します。

```rust
/// ストリームの各要素を展開して平坦化
pub fn flat_map_stream<S, F, U, T>(stream: S, f: F) -> impl Stream<Item = T>
where
    S: Stream<Item = U>,
    F: FnMut(U) -> Pin<Box<dyn Stream<Item = T> + Send>>,
{
    stream.flat_map(f)
}

/// ネストされたストリームを平坦化
pub fn flatten_stream<S, Inner, T>(stream: S) -> impl Stream<Item = T>
where
    S: Stream<Item = Inner>,
    Inner: Stream<Item = T>,
{
    stream.flatten()
}
```

### 9.8 非同期ストリーム処理

各要素に非同期処理を適用します。

```rust
/// 各要素に非同期処理を適用
pub fn async_map_stream<S, F, Fut, T, U>(stream: S, f: F) -> impl Stream<Item = U>
where
    S: Stream<Item = T>,
    F: FnMut(T) -> Fut,
    Fut: std::future::Future<Output = U>,
{
    stream.then(f)
}

/// バッファ付き非同期処理（並行実行）
pub fn buffered_async_stream<S, F, Fut, T, U>(
    stream: S,
    f: F,
    buffer_size: usize,
) -> impl Stream<Item = U>
where
    S: Stream<Item = T>,
    F: FnMut(T) -> Fut,
    Fut: std::future::Future<Output = U>,
{
    stream.map(f).buffered(buffer_size)
}
```

`buffered` を使うと、指定した数の Future を並行実行できます。

### 9.9 エラーハンドリング

`Result` を含むストリームの処理パターンです。

```rust
/// Result を含むストリームから成功値のみを抽出
pub fn filter_ok_stream<S, T, E>(stream: S) -> impl Stream<Item = T>
where
    S: Stream<Item = Result<T, E>>,
{
    stream.filter_map(|r| async move { r.ok() })
}

/// ストリーム処理中のエラーを収集
pub async fn collect_results_stream<S, T, E>(stream: S) -> Result<Vec<T>, E>
where
    S: Stream<Item = Result<T, E>>,
{
    stream.collect::<Vec<_>>().await.into_iter().collect()
}
```

### 9.10 イベント処理シミュレーション

実際のアプリケーションでは、イベントストリームを処理することが多いです。

```rust
/// イベントの種類
#[derive(Debug, Clone, PartialEq)]
pub enum Event {
    Click { x: i32, y: i32 },
    KeyPress { key: char },
    Scroll { delta: i32 },
}

/// イベントストリームをフィルタリング
pub fn filter_clicks<S>(stream: S) -> impl Stream<Item = Event>
where
    S: Stream<Item = Event>,
{
    stream.filter(|e| {
        let is_click = matches!(e, Event::Click { .. });
        async move { is_click }
    })
}

/// イベントを集計
pub async fn count_events<S>(stream: S) -> (usize, usize, usize)
where
    S: Stream<Item = Event>,
{
    stream
        .fold((0, 0, 0), |(clicks, keys, scrolls), event| async move {
            match event {
                Event::Click { .. } => (clicks + 1, keys, scrolls),
                Event::KeyPress { .. } => (clicks, keys + 1, scrolls),
                Event::Scroll { .. } => (clicks, keys, scrolls + 1),
            }
        })
        .await
}
```

## Scala との対比

| 概念 | Scala | Rust |
|------|-------|------|
| 副作用の遅延 | `IO[A]` | `impl Future<Output = A>` |
| 副作用の実行 | `unsafeRunSync()` | `.await` |
| ストリーム | `fs2.Stream[F, A]` | `impl Stream<Item = A>` |
| エラー | `IO[Either[E, A]]` | `Result<A, E>` |
| 並行実行 | `parTraverse` | `join_all` / `buffered` |
| 無限シーケンス | `Stream.iterate` | `stream::unfold` |

## まとめ

Part IV では以下を学びました：

1. **副作用の管理**: `async/await` で副作用を明示的に遅延実行
2. **純粋関数との分離**: ビジネスロジックを純粋関数として実装
3. **エラーハンドリング**: `Result` との組み合わせで堅牢な処理
4. **リトライパターン**: 汎用的なリトライ関数の実装
5. **並行処理**: `join_all` で複数の非同期処理を並行実行
6. **ストリーム処理**: 遅延評価されるシーケンスの操作
7. **無限ストリーム**: `unfold` による無限シーケンスの表現
8. **イベント処理**: 実際のアプリケーションでのストリーム活用

Rust の `async/await` と `futures::Stream` は、Scala の IO モナドと fs2 Stream と同等の表現力を持ちます。型システムによる安全性と、ゼロコスト抽象化によるパフォーマンスを両立しています。

## 次のステップ

Part V では、以下のトピックを扱う予定です：

- 型クラスパターン
- モナド変換子
- 依存性注入
- エフェクトシステム

これらの高度なパターンを学ぶことで、より大規模で保守性の高いアプリケーションを構築できるようになります。
