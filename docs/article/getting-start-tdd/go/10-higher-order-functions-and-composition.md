---
type: Article
title: "第 10 章: 高階関数と関数合成"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 ファーストクラス関数

Go では関数は**ファーストクラスオブジェクト**です。関数を変数に代入したり、引数として渡したり、戻り値として返したりできます。

### 関数型の定義

Go では `func` キーワードで関数型を定義できます。

```go
// 関数型の定義
type Predicate func(FizzBuzzValue) bool
type Mapper func(FizzBuzzValue) string
```

### テスト: 関数を変数に代入する

```go
func TestPredicate_Fizzを判定する(t *testing.T) {
    isFizz := func(v FizzBuzzValue) bool {
        return v.Value() == "Fizz"
    }

    v := NewFizzBuzzValue(3, "Fizz")
    if !isFizz(v) {
        t.Fatal("isFizz should return true for Fizz")
    }
}
```

### テスト: 関数を引数として渡す

```go
func TestFizzBuzzList_Filter_Fizzだけを抽出する(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    isFizz := func(v FizzBuzzValue) bool {
        return v.Value() == "Fizz"
    }
    filtered := list.Filter(isFizz)

    for _, v := range filtered.Value() {
        if v.Value() != "Fizz" {
            t.Fatalf("expected Fizz, got %q", v.Value())
        }
    }
}
```

<details>
<summary>実装コード: Filter メソッド</summary>

```go
// Predicate は FizzBuzzValue を受け取り bool を返す関数型です。
type Predicate func(FizzBuzzValue) bool

// Filter は条件に合致する要素だけを含む新しいリストを返します。
func (l *FizzBuzzList) Filter(pred Predicate) *FizzBuzzList {
    var result []FizzBuzzValue
    for _, v := range l.value {
        if pred(v) {
            result = append(result, v)
        }
    }
    return &FizzBuzzList{value: result}
}
```

</details>

## 10.2 クロージャ

クロージャは外側のスコープの変数をキャプチャした関数です。Go のクロージャは変数への**参照**をキャプチャします。

### テスト: クロージャで述語関数を生成する

```go
func TestMakeValuePredicate_指定した値と一致する述語を返す(t *testing.T) {
    isFizz := MakeValuePredicate("Fizz")
    isBuzz := MakeValuePredicate("Buzz")

    v := NewFizzBuzzValue(3, "Fizz")
    if !isFizz(v) {
        t.Fatal("isFizz should return true")
    }
    if isBuzz(v) {
        t.Fatal("isBuzz should return false for Fizz")
    }
}
```

<details>
<summary>実装コード: MakeValuePredicate</summary>

```go
// MakeValuePredicate は指定した値と一致するかを判定する述語関数を返します。
func MakeValuePredicate(target string) Predicate {
    return func(v FizzBuzzValue) bool {
        return v.Value() == target
    }
}
```

</details>

### テスト: クロージャで Map 関数を適用する

```go
func TestFizzBuzzList_Map_値を変換する(t *testing.T) {
    values := []FizzBuzzValue{
        NewFizzBuzzValue(1, "1"),
        NewFizzBuzzValue(3, "Fizz"),
    }
    list := NewFizzBuzzList(values)

    toUpper := func(v FizzBuzzValue) string {
        return strings.ToUpper(v.Value())
    }
    got := list.Map(toUpper)

    if got[0] != "1" || got[1] != "FIZZ" {
        t.Fatalf("Map result = %v", got)
    }
}
```

<details>
<summary>実装コード: Map メソッド</summary>

```go
// Mapper は FizzBuzzValue を受け取り string を返す関数型です。
type Mapper func(FizzBuzzValue) string

// Map は各要素に関数を適用した結果のスライスを返します。
func (l *FizzBuzzList) Map(fn Mapper) []string {
    result := make([]string, len(l.value))
    for i, v := range l.value {
        result[i] = fn(v)
    }
    return result
}
```

</details>

## 10.3 関数合成

Go には演算子レベルの関数合成構文はありませんが、**高階関数を使って手動で合成**できます。

### テスト: 2 つの関数を合成する

```go
func TestCompose_2つの関数を合成する(t *testing.T) {
    double := func(n int) int { return n * 2 }
    addOne := func(n int) int { return n + 1 }

    doubleThenAddOne := Compose(double, addOne)

    got := doubleThenAddOne(5)
    if got != 11 {
        t.Fatalf("Compose(double, addOne)(5) = %d, want 11", got)
    }
}
```

<details>
<summary>実装コード: Compose 関数</summary>

```go
// Compose は f を適用した後に g を適用する合成関数を返します。
func Compose(f, g func(int) int) func(int) int {
    return func(n int) int {
        return g(f(n))
    }
}
```

</details>

### テスト: Filter と Map を組み合わせる

```go
func TestFizzBuzzList_FilterとMapを組み合わせる(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    isFizz := MakeValuePredicate("Fizz")
    getValue := func(v FizzBuzzValue) string { return v.Value() }

    result := list.Filter(isFizz).Map(getValue)

    for _, s := range result {
        if s != "Fizz" {
            t.Fatalf("expected Fizz, got %q", s)
        }
    }
}
```

## 10.4 各言語の高階関数比較

| 機能 | Go | Java | Ruby | TypeScript |
|------|-----|------|------|------------|
| 関数型 | `func(T) R` | `Function<T,R>` | Proc / Lambda | `(t: T) => R` |
| クロージャ | `func` リテラル | Lambda 式 | ブロック / Lambda | アロー関数 |
| 関数合成 | 手動実装 | `andThen` / `compose` | `>>` / `<<` | 手動実装 |
| フィルタ | 手動 `for` ループ | `Stream.filter` | `select` | `Array.filter` |
| マップ | 手動 `for` ループ | `Stream.map` | `map` | `Array.map` |

Go はジェネリクス以前はスライス操作を `for` ループで明示的に書く必要がありました。Go 1.18+ のジェネリクスにより、型安全な汎用関数を書けるようになりました（第 12 章で詳述）。

## 10.5 まとめ

本章では以下を学びました:

- **ファーストクラス関数**: 関数を変数・引数・戻り値として扱える
- **関数型**: `type Predicate func(FizzBuzzValue) bool` のように型を定義
- **クロージャ**: 外側のスコープの変数をキャプチャする関数
- **関数合成**: 高階関数で手動実装
- **Filter / Map**: FizzBuzzList に関数型メソッドを追加

Go のシンプルな関数型サポートは、Java の `Function<T,R>` や Ruby の Lambda と比較すると構文は少ないですが、同じパターンを明示的に実装できます。
