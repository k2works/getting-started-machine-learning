---
type: Article
title: "第8章: Decorator パターン — 6言語統合ガイド"
description: "Decorator パターンは、既存の機能に新しい振る舞いを動的に追加する GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第8章: Decorator パターン — 6言語統合ガイド

## 1. はじめに

Decorator パターンは、既存の機能に新しい振る舞いを動的に追加する GoF パターンです。OOP ではラッパークラスを重ねますが、関数型プログラミングでは**高階関数とクロージャ**がデコレータの役割を果たします。関数を受け取り、拡張された関数を返す — これが関数型デコレータの本質です。

> **Elixir の読者へ**: Elixir 版は本章で「エラーハンドリング戦略」を扱っています。Elixir 固有のアプローチについては[コラム](#elixir-コラムエラーハンドリング戦略)を参照してください。

## 2. 共通の本質

### Decorator の構造

```
decorator(f) → g
  where g(x) = 前処理 + f(x) + 後処理
```

- **横断的関心事の分離**: ロギング、キャッシュ、リトライなどをビジネスロジックから分離
- **合成可能性**: 複数のデコレータを連鎖的に適用
- **透過性**: デコレートされた関数は元の関数と同じインターフェース

### 典型的なデコレータ

- **ロギング**: 入出力を記録
- **キャッシュ**: 結果をメモ化
- **リトライ**: 失敗時に再試行
- **タイミング**: 実行時間を計測
- **バリデーション**: 入力を事前検証

## 3. 言語別実装比較

### 3.1 デコレータの実現方法

| 言語 | デコレータの表現 | 状態管理 |
|------|---------------|---------|
| Clojure | 高階関数 + クロージャ | `atom` で可変状態 |
| Scala | 高階関数 / trait ラッパー | `var` / `mutable.Map` |
| F# | 高階関数 + パイプライン | `mutable` / `Dictionary` |
| Haskell | 高階関数 + IO モナド | `IORef` / 純粋な Map 返却 |
| Rust | クロージャ + `Rc<RefCell<T>>` | 共有可変参照 |

### 3.2 ロギングデコレータ

<details>
<summary>全言語のロギングデコレータ比較</summary>

```clojure
;; Clojure
(defn with-logging [f name]
  (fn [& args]
    (println (str "Calling " name " with " args))
    (let [result (apply f args)]
      (println (str name " returned " result))
      result)))

;; 使用
(def logged-add (with-logging + "add"))
(logged-add 1 2 3) ;; => 6（ログ出力付き）
```

```scala
// Scala
def withLogging[A, B](f: A => B, name: String): A => B =
  input =>
    println(s"Calling $name with $input")
    val result = f(input)
    println(s"$name returned $result")
    result
```

```fsharp
// F#
let withLogging (name: string) (f: 'a -> 'b) : 'a -> 'b =
    fun input ->
        printfn "Calling %s with %A" name input
        let result = f input
        printfn "%s returned %A" name result
        result
```

```haskell
-- Haskell
withLogging :: Show a => Show b => String -> (a -> b) -> a -> IO b
withLogging name f input = do
    putStrLn $ "Calling " ++ name ++ " with " ++ show input
    let result = f input
    putStrLn $ name ++ " returned " ++ show result
    return result
```

Haskell ではロギングが副作用であるため、戻り値の型が `IO b` に変わる点に注目してください。

```rust
// Rust
pub fn with_logging<F, A, B>(f: F, name: &str) -> impl Fn(A) -> B + '_
where
    F: Fn(A) -> B,
    A: std::fmt::Debug,
    B: std::fmt::Debug,
{
    move |input| {
        println!("Calling {} with {:?}", name, input);
        let result = f(input);
        println!("{} returned {:?}", name, result);
        result
    }
}
```

</details>

### 3.3 キャッシュデコレータ

キャッシュデコレータは状態（キャッシュストア）を持つため、言語ごとの状態管理の違いが顕著に現れます。

<details>
<summary>キャッシュデコレータの状態管理比較</summary>

```clojure
;; Clojure: atom でスレッドセーフな可変状態
(defn with-cache [f]
  (let [cache (atom {})]
    (fn [& args]
      (if-let [cached (get @cache args)]
        cached
        (let [result (apply f args)]
          (swap! cache assoc args result)
          result)))))
```

```scala
// Scala: mutable.Map
def withCache[A, B](f: A => B): A => B =
  val cache = scala.collection.mutable.Map.empty[A, B]
  input => cache.getOrElseUpdate(input, f(input))
```

```haskell
-- Haskell: 純粋版（Map を状態として返す）
withCache :: Ord a => (a -> b) -> a -> Map a b -> (b, Map a b)
withCache f input cache = case Map.lookup input cache of
    Just result -> (result, cache)
    Nothing     -> let result = f input
                   in (result, Map.insert input result cache)
```

Haskell の純粋版はキャッシュの状態を**戻り値として返す**ため、副作用がありません。

```rust
// Rust: Rc<RefCell<HashMap>>
pub fn with_cache<F, A, B>(f: F) -> impl Fn(A) -> B
where
    F: Fn(&A) -> B,
    A: Eq + Hash + Clone,
    B: Clone,
{
    let cache = Rc::new(RefCell::new(HashMap::new()));
    move |input| {
        if let Some(result) = cache.borrow().get(&input) {
            return result.clone();
        }
        let result = f(&input);
        cache.borrow_mut().insert(input, result.clone());
        result
    }
}
```

</details>

### 3.4 デコレータの合成

複数のデコレータを連鎖的に適用するパターンです。

<details>
<summary>デコレータ合成の比較</summary>

```clojure
;; Clojure: comp で合成
(defn compose-decorators [f & decorators]
  (reduce (fn [acc dec] (dec acc)) f decorators))

(def enhanced-fn
  (compose-decorators my-fn
    (partial with-logging "my-fn")
    with-cache
    (partial with-retry 3)))
```

```scala
// Scala: andThen チェーン
val enhancedFn =
  withLogging("calc", _: Int => Int) andThen
  withCache andThen
  withRetry(3)
```

```fsharp
// F# : パイプライン
let enhancedFn =
    myFn
    |> withLogging "myFn"
    |> withCache
    |> withRetry 3
```

</details>

### 3.5 JournaledShape（操作履歴デコレータ）

すべての言語で共通して実装される「操作履歴を記録するデコレータ」です。

| 言語 | 実装 | 履歴の保持方法 |
|------|------|-------------|
| Clojure | マップ + `:journal` キー | 不変リスト |
| Scala | `JournaledShape` case class | `Vector[String]` |
| F# | `JournaledShape` レコード | `string list` |
| Haskell | `JournaledShape` データ型 | `[String]` |
| Rust | `JournaledShape<S>` ジェネリクス | `Vec<String>` |

## 4. 比較分析

### 4.1 副作用の扱い

デコレータは本質的に副作用（ロギング、キャッシュ）を扱うため、言語の副作用管理戦略が直接影響します。

| 言語 | 副作用の扱い | デコレータへの影響 |
|------|-----------|----------------|
| Clojure | 規約ベース | `atom` で自由に状態管理 |
| Scala | 自由（ただし推奨は純粋） | `var` / `mutable` を局所的に使用 |
| F# | 基本は純粋、`mutable` で例外 | 明示的な可変性宣言 |
| Haskell | IO モナドで厳密に分離 | 副作用デコレータは `IO` 型に |
| Rust | 所有権で管理 | `Rc<RefCell<T>>` で共有可変状態 |

### 4.2 型の変化

Haskell ではデコレータの追加により**型が変化**します（`a -> b` が `a -> IO b` に）。他の言語ではデコレートしても型が変わりません。これは Haskell の純粋性保証の強さの表れです。

## 5. Elixir コラム：エラーハンドリング戦略

Elixir の第 8 章は Decorator パターンではなく、**エラーハンドリング戦略**を扱っています。

### Railway Oriented Programming

```elixir
# {:ok, value} / {:error, reason} による逐次処理
def process(params) do
  with {:ok, validated} <- validate(params),
       {:ok, saved} <- save(validated),
       {:ok, notified} <- notify(saved) do
    {:ok, notified}
  end
end
```

### リトライとフォールバック

```elixir
def retry(f, max_attempts, attempt \\ 1) do
  case f.() do
    {:ok, result} -> {:ok, result}
    {:error, _} when attempt < max_attempts ->
      Process.sleep(100 * attempt)
      retry(f, max_attempts, attempt + 1)
    {:error, reason} -> {:error, reason}
  end
end

def fallback_chain(strategies) do
  Enum.reduce_while(strategies, {:error, :no_strategy}, fn strategy, _acc ->
    case strategy.() do
      {:ok, result} -> {:halt, {:ok, result}}
      {:error, _} -> {:cont, {:error, :all_failed}}
    end
  end)
end
```

Decorator パターンの「機能の動的追加」は、Elixir ではパイプラインと `with` 式による関数チェーンとして表現されます。

## 6. まとめ

Decorator パターンは、関数型プログラミングで**高階関数**として最も自然に表現されます：

1. **関数がデコレータ**: 関数を受け取り、拡張された関数を返す
2. **横断的関心事の分離**: ロギング・キャッシュ・リトライを独立した高階関数に
3. **合成可能性**: `comp` / `andThen` / `|>` で複数デコレータを連鎖

## 言語別個別記事

- [Clojure](../clojure/08-decorator-pattern.md) | [Scala](../scala/08-decorator-pattern.md) | [Elixir](../elixir/08-error-handling-strategies.md) | [F#](../fsharp/08-decorator-pattern.md) | [Haskell](../haskell/08-decorator-pattern.md) | [Rust](../rust/08-decorator-pattern.md)
