---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 不変データ設計の確認

Go には `final` や `freeze` のようなキーワードがありませんが、**非公開フィールド + コンストラクタ + 防御的コピー**で不変性を実現できます。

### FizzBuzzValue の不変設計

第 8 章で作成した `FizzBuzzValue` は既に不変です:

```go
type FizzBuzzValue struct {
    number int    // 非公開: 外部から変更不可
    value  string // 非公開: 外部から変更不可
}
```

- setter メソッドがない
- フィールドが非公開（小文字）
- 値レシーバを使用（ポインタでないため元の構造体は変更されない）

### テスト: 不変性を確認する

```go
func TestFizzBuzzValue_不変性を確認する(t *testing.T) {
    v1 := NewFizzBuzzValue(3, "Fizz")
    // v1.number = 5  // コンパイルエラー: 非公開フィールドにアクセスできない
    // v1 の値は変更できない
    v2 := v1 // コピーが作成される（値型）
    if !v1.Equal(v2) {
        t.Fatal("v1 and v2 should be equal")
    }
}
```

### FizzBuzzList の不変設計

```go
type FizzBuzzList struct {
    value []FizzBuzzValue // 非公開
}
```

- `NewFizzBuzzList` でスライスをコピーして保持
- `Value()` で防御的コピーを返す
- `Filter` や `Map` は**新しいスライス/リスト**を返す（元のリストは変更しない）

### テスト: Filter が元のリストを変更しないことを確認する

```go
func TestFizzBuzzList_Filterは元のリストを変更しない(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    original := cmd.Execute().(*FizzBuzzList)
    originalCount := original.Count()

    isFizz := MakeValuePredicate("Fizz")
    _ = original.Filter(isFizz)

    if original.Count() != originalCount {
        t.Fatal("original list should not be modified")
    }
}
```

## 11.2 パイプラインメソッド

FizzBuzzList にパイプライン処理のためのメソッドを追加します。

### テスト: GroupByValue — 値でグルーピングする

```go
func TestFizzBuzzList_GroupByValue_値でグルーピングする(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    grouped := list.GroupByValue()

    if _, ok := grouped["Fizz"]; !ok {
        t.Fatal("grouped should contain 'Fizz' key")
    }
    if _, ok := grouped["Buzz"]; !ok {
        t.Fatal("grouped should contain 'Buzz' key")
    }
    if _, ok := grouped["FizzBuzz"]; !ok {
        t.Fatal("grouped should contain 'FizzBuzz' key")
    }
}
```

<details>
<summary>実装コード: GroupByValue</summary>

```go
// GroupByValue は値でグルーピングした map を返します。
func (l *FizzBuzzList) GroupByValue() map[string][]FizzBuzzValue {
    result := make(map[string][]FizzBuzzValue)
    for _, v := range l.value {
        result[v.Value()] = append(result[v.Value()], v)
    }
    return result
}
```

</details>

### テスト: CountByValue — 値ごとの出現回数を数える

```go
func TestFizzBuzzList_CountByValue_値ごとの出現回数を数える(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    counts := list.CountByValue()

    if counts["FizzBuzz"] != 1 {
        t.Fatalf("FizzBuzz count = %d, want 1", counts["FizzBuzz"])
    }
}
```

<details>
<summary>実装コード: CountByValue</summary>

```go
// CountByValue は値ごとの出現回数を返します。
func (l *FizzBuzzList) CountByValue() map[string]int {
    result := make(map[string]int)
    for _, v := range l.value {
        result[v.Value()]++
    }
    return result
}
```

</details>

### テスト: Take — 先頭 N 件を取得する

```go
func TestFizzBuzzList_Take_先頭N件を取得する(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    taken := list.Take(5)

    if taken.Count() != 5 {
        t.Fatalf("Take(5).Count() = %d, want 5", taken.Count())
    }
}
```

<details>
<summary>実装コード: Take</summary>

```go
// Take は先頭から n 件の要素を含む新しいリストを返します。
func (l *FizzBuzzList) Take(n int) *FizzBuzzList {
    if n > len(l.value) {
        n = len(l.value)
    }
    result := make([]FizzBuzzValue, n)
    copy(result, l.value[:n])
    return &FizzBuzzList{value: result}
}
```

</details>

### テスト: Join — 要素を文字列で結合する

```go
func TestFizzBuzzList_Join_要素を文字列で結合する(t *testing.T) {
    values := []FizzBuzzValue{
        NewFizzBuzzValue(1, "1"),
        NewFizzBuzzValue(2, "2"),
        NewFizzBuzzValue(3, "Fizz"),
    }
    list := NewFizzBuzzList(values)

    got := list.Join(", ")

    if got != "1, 2, Fizz" {
        t.Fatalf("Join(', ') = %q, want %q", got, "1, 2, Fizz")
    }
}
```

<details>
<summary>実装コード: Join</summary>

```go
// Join は各要素の文字列表現を区切り文字で結合した文字列を返します。
func (l *FizzBuzzList) Join(sep string) string {
    strs := make([]string, len(l.value))
    for i, v := range l.value {
        strs[i] = v.String()
    }
    return strings.Join(strs, sep)
}
```

</details>

## 11.3 メソッドチェーンによるパイプライン

Filter と Take は `*FizzBuzzList` を返すため、**メソッドチェーン**でパイプラインを構築できます。

### テスト: メソッドチェーンで Fizz を 3 件取得して結合する

```go
func TestFizzBuzzList_メソッドチェーンでパイプラインを構築する(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 100)
    list := cmd.Execute().(*FizzBuzzList)

    result := list.
        Filter(MakeValuePredicate("Fizz")).
        Take(3).
        Join(", ")

    if result != "Fizz, Fizz, Fizz" {
        t.Fatalf("pipeline result = %q, want %q", result, "Fizz, Fizz, Fizz")
    }
}
```

### Reduce パターン

Go には組み込みの `reduce` がありませんが、関数として実装できます。

### テスト: Reduce で合計を計算する

```go
func TestFizzBuzzList_Reduce_数値の合計を計算する(t *testing.T) {
    values := []FizzBuzzValue{
        NewFizzBuzzValue(1, "1"),
        NewFizzBuzzValue(2, "2"),
        NewFizzBuzzValue(3, "Fizz"),
    }
    list := NewFizzBuzzList(values)

    sum := list.Reduce(0, func(acc int, v FizzBuzzValue) int {
        return acc + v.Number()
    })

    if sum != 6 {
        t.Fatalf("Reduce sum = %d, want 6", sum)
    }
}
```

<details>
<summary>実装コード: Reduce</summary>

```go
// Reducer は累積値と要素を受け取り新しい累積値を返す関数型です。
type Reducer func(acc int, v FizzBuzzValue) int

// Reduce は初期値から始めて各要素に関数を適用し、単一の値に畳み込みます。
func (l *FizzBuzzList) Reduce(initial int, fn Reducer) int {
    acc := initial
    for _, v := range l.value {
        acc = fn(acc, v)
    }
    return acc
}
```

</details>

## 11.4 各言語のパイプライン比較

| 機能 | Go | Java | Ruby | TypeScript |
|------|-----|------|------|------------|
| パイプライン構文 | メソッドチェーン | Stream API | `then` / メソッドチェーン | メソッドチェーン |
| グルーピング | 手動 `map` 構築 | `Collectors.groupingBy` | `group_by` | `reduce` |
| 結合 | `strings.Join` | `Collectors.joining` | `join` | `Array.join` |
| 畳み込み | 手動 `Reduce` | `Stream.reduce` | `reduce` / `inject` | `Array.reduce` |
| 遅延評価 | なし（即時評価） | `Stream`（遅延） | `Lazy` | なし |

Go はメソッドチェーンでパイプラインを構築しますが、Java の Stream API のような遅延評価はありません。すべての操作は即時評価されます。

## 11.5 まとめ

本章では以下を学びました:

- **不変データ設計**: 非公開フィールド + 防御的コピーで不変性を実現
- **パイプラインメソッド**: GroupByValue、CountByValue、Take、Join
- **メソッドチェーン**: Filter → Take → Join のパイプライン構築
- **Reduce パターン**: 畳み込みの手動実装

Go のパイプラインは Java の Stream API ほど洗練されていませんが、メソッドチェーンを使って同等の処理を記述できます。不変設計は言語機能に頼らず、設計パターンで実現します。
