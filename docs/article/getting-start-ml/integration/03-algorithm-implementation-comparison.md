---
type: Article
title: "第 3 章: アルゴリズムの実装の比較"
description: "同じアルゴリズムを 4 言語でどう書いたかを比べる。決定木の木の型（dataclass・sealed interface・判別可能なユニオン・判別共用体）と場合分けの漏れの見つけ方、線形回帰の行列、モデルと評価関数の共通の型、交差検証の遅延評価、K-means の繰り返し、失敗の表し方と API の入力の検証を整理する。"
tags: [article,getting-start-ml,integration]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T10:12:01Z }
---

# 第 3 章: アルゴリズムの実装の比較

## 3.1 同じアルゴリズム、違う書き方

4 言語とも、同じ TODO リストから同じアルゴリズム（決定木・正規方程式・ソフトマックス・K 分割交差検証・K-means など）を TDD で自作しました。アルゴリズムの手順は同じなので、違いは「その言語で、同じ手順をどんな型と制御構造で書いたか」に表れます。本章では、違いがはっきり表れた 6 つの場面を比べます。

## 3.2 決定木の木の型（第 3 章）

決定木は「葉（予測するラベル）」か「節（分け方と左右の部分木）」のどちらかです。この「どちらか」の表し方は、4 言語で次のようになりました。

| 言語 | 木の型 | 場合分け | 場合分けの漏れ |
|------|-------|---------|--------------|
| Python | `Leaf` と `Node` の 2 つの `@dataclass(frozen=True)`。木は `Leaf \| Node` の型ヒント | `isinstance(tree, Leaf)` で葉を先に返し、残りを節として扱う | mypy が型を絞り込むが、場合の数が増えたときに知らせる仕組みは無い |
| Kotlin | `sealed interface Tree` と、それを実装する `data class Leaf`・`Node` | `when (tree)` | `when` を式として使うと、コンパイラが網羅を検査する |
| TypeScript | 判別可能なユニオン `{ kind: "leaf"; ... } \| { kind: "node"; ... }` | `switch (tree.kind)` | `default` で `never` 型に代入する書き方を自分で仕込むと、型チェックが検査する |
| F# | 判別共用体 `type Tree<'L> = Leaf of 'L \| Node of Split * Tree<'L> * Tree<'L>` | `match tree with` | 何も書かなくてもコンパイラが検査する（FS0025。F# 版は警告をエラーにしている） |

```python
# Python
@dataclass(frozen=True)
class Leaf:
    label: str


@dataclass(frozen=True)
class Node:
    split: Split
    left: "Leaf | Node"
    right: "Leaf | Node"
```

```typescript
// TypeScript
export type Tree<K extends string> =
  | { kind: "leaf"; label: string }
  | { kind: "node"; split: Split<K>; left: Tree<K>; right: Tree<K> };
```

```fsharp
// F#
type Tree<'L> =
    | Leaf of 'L
    | Node of Split * Tree<'L> * Tree<'L>
```

- 4 言語とも、木を「不変な値」で表しました。Python は `frozen=True`、Kotlin は `data class`（`val`）、TypeScript はオブジェクトリテラル、F# は判別共用体です
- 網羅性の検査は、言語に組み込まれている度合いが違います。F# は何も書かなくても検査し、Kotlin は `sealed` と `when` の組み合わせで、TypeScript は `never` を使う書き方を自分で仕込んで初めて検査されます
- 学習の API も分かれました。Python・Kotlin・TypeScript は、`DecisionTree` クラスの `fit` が木を中に持ち、`fit` の前に `predict` を呼ぶと例外にしました（TypeScript 版にはそのテストがあります）。F# は、`fit` が木を **値として返し**、`predict` が木を引数に取るので、「学習する前に予測する」誤りをそもそも書けません

## 3.3 線形回帰の行列（第 7 章）

正規方程式 (XᵀX) w = Xᵀt を解くための行列の扱いです。

| 言語 | 行列 | 連立方程式の解き方 |
|------|------|-----------------|
| Python | NumPy の配列。`design.T @ design` のように演算子で書く | `np.linalg.solve` |
| Kotlin | 自作の `Matrix` クラス。`operator fun times` で `*` を行列の積にする | 自作 |
| TypeScript | 数値の配列の配列と、積・転置の関数 | 自作の部分ピボット選択付きのガウスの消去法。ml-matrix の `solve` とも突き合わせた |
| F# | `float[][]`（配列の配列）と、積・転置の関数 | 自作 |

Python は NumPy があるので、式をそのまま書けます。Kotlin は演算子オーバーロードで、自作の行列でも数式に近い見た目にしました。TypeScript と F# は、演算子を定義せずに関数で書いています。F# 版の第 12 章では、第 7 章の行列のモジュールを書き換えずに、足し算・定数倍・単位行列を別のモジュールで足しました。

## 3.4 モデルと評価関数の共通の型（第 10・11 章）

第 10 章では、自作の分類器とライブラリの分類器を同じ手順で評価するために、共通の型を決めました。第 11 章の交差検証でも、モデルと評価関数を差し替えられるようにしています。

| 言語 | モデルの型 | 評価関数の型 |
|------|----------|------------|
| Python | `Protocol`（`fit`・`predict` のメソッドを持つもの） | `Callable[[ArrayLike, ArrayLike], float]` |
| Kotlin | `interface Model<T>`（`fit`・`predict`） | 関数型 |
| TypeScript | 構造的部分型の `interface`（`fit`・`predict`） | 関数の型 |
| F# | 関数の型 `Model<'T> = 特徴量 -> 正解 -> (特徴量 -> 予測)` | 関数の型 `Metric<'T> = 'T list -> 'T list -> float` |

- Python の `Protocol` と TypeScript の `interface` は、どちらも **構造的** です。`fit`・`predict` を持っていれば、宣言しなくてもその型として扱えます。ライブラリの分類器を、アダプターを薄く書くだけで同じ型にできました
- Kotlin の `interface` は、クラスが実装を宣言します
- F# は、モデルを「学習して、予測する関数を返す関数」という **1 つの関数の型** で表しました。学習した状態は、返した関数が覚えています（クロージャ）。学習の状態を持つオブジェクトが無いので、交差検証で「分割ごとに新しいモデルを作る」ための工場（Python・Kotlin・TypeScript の `make_model`・`makeModel`）も要りません

評価関数を引数で渡す設計は、4 言語で共通でした。混同行列から求める指標（`ConfusionMatrix -> float`）を、正解と予測から求める評価関数に変える高階関数も、4 言語それぞれで書いています。

## 3.5 交差検証の遅延評価（第 11 章）

交差検証は、分割ごとに学習と評価を繰り返します。スコアを「すぐに全部計算する」か「取り出したときに計算する」かが分かれました。

| 言語 | 戻り値 | 評価の時期 |
|------|-------|----------|
| Python | `list[float]` | すぐにすべての分割を計算する |
| Kotlin | `Sequence<Double>`（`folds.asSequence().map { ... }`） | 取り出したときに計算する |
| TypeScript | ジェネレーター関数（`function*` と `yield`） | 取り出したときに計算する |
| F# | シーケンス式（`seq { for ... do yield ... }`） | 取り出したときに計算する |

Kotlin・TypeScript・F# は、「最初の分割のスコアだけを取り出せば、学習は 1 回で済む」ことをテストで確かめました。F# 版では、同じシーケンスから 2 回取り出すと学習もやり直されることと、`Seq.cache` で結果を覚えさせられることもテストに残しています。遅延評価は、重い学習を必要な分だけ行える一方で、「何度も取り出すと何度も計算する」ことに気を付ける必要があります。

## 3.6 K-means の繰り返し（第 14 章）

K-means は、「割り当て」と「中心の更新」を、中心が変わらなくなるまで繰り返します。

| 言語 | 繰り返しの書き方 |
|------|---------------|
| Python | `for _ in range(max_iterations):` のループと、変わらなければ `break` |
| Kotlin | `generateSequence(initialCenters) { ... }` で中心の列を無限に作り、`zipWithNext` で前後を比べて、最初に止まる位置を `first` で取る |
| TypeScript | `while (iterations < maxIterations)` のループ |
| F# | 末尾再帰の関数。`[<TailCall>]` でコンパイラに末尾呼び出しを確かめさせる |

Kotlin の書き方は、繰り返しを「中心の列」というデータとして扱っています。F# の末尾再帰は、コンパイラがループに変換するので、何回繰り返してもスタックを使い切りません。F# 版では、第 5 章で有効にした FSharpLint の FL0085 が `let rec` に `[<TailCall>]` を求めるので、末尾再帰でない再帰（決定木の `fit` など）には理由を書いて警告を抑えています。

4 言語とも、K-means は **初期中心を引数で受け取る** 設計にしました。乱数で初期中心を選ぶ関数と、初期中心からクラスタリングする関数を分けたので、局所解に陥る例を乱数に頼らずにテストで再現できました。

## 3.7 失敗の表し方と API の入力の検証（第 15 章）

第 15 章の API では、「モデルが無い」「入力が不正」という失敗を扱いました。

| 言語 | モデルが無いとき | 入力の検証 | 不正な入力の状態コード | モデルが無いときの状態コード |
|------|---------------|----------|-------------------|----------------------|
| Python | 例外 `ModelNotFoundError` を投げる | Pydantic の `BaseModel` と `Field(ge=0)` | 422（FastAPI の既定） | FastAPI の `exception_handler` で 503 |
| Kotlin | 標準ライブラリの `Result<T>` の失敗（中身は例外 `ModelNotFoundException`） | `sealed interface Validated<T>` を自作 | 422 | Ktor の `StatusPages` で 503 |
| TypeScript | 自作の判別可能なユニオン `Result<T>` の失敗（中身は `Error` を継承したクラス） | zod のスキーマと `safeParse` | 422 | Hono の `app.onError` で 503 |
| F# | 組み込みの `Result<'T, PredictionError>` の `Error`（中身は判別共用体） | `and!` の計算式を自作 | 422 | 失敗の型ごとに状態コードを決める（例外を使わない） |

- Python は失敗を例外で表し、フレームワークの例外ハンドラーで状態コードに変えました。Kotlin と TypeScript は、アプリケーション層（予測サービス）が `Result` を返し、プレゼンテーション層で例外に戻してから状態コードに変えています
- F# は、失敗を最後まで `Result` の値のまま扱いました。検証の失敗（`string list`）は 422、予測の失敗（`PredictionError`）は 503 と、失敗の **型** で状態コードが決まります。エラーの種類を増やしたときには、網羅性の検査が説明の書き忘れを知らせました
- 入力の検証は、Python（Pydantic）と TypeScript（zod）がライブラリのスキーマで、Kotlin と F# が自作で書きました。F# の `and!` の計算式は、不正な項目がいくつあっても理由をまとめて集めます。どの言語も、検証を通った値だけをドメインの型に変換しています

モデルの置き場は、4 言語とも差し替えられる形にして、テストではスタブを使いました。Python は `Protocol`、Kotlin は `interface`、TypeScript はオブジェクトリテラル、F# は関数のレコードです。API の統合テストは、4 言語ともサーバーを起動せずに書けました（第 1 章の表を参照）。

## 3.8 まとめ

1. **場合分けの型** — 4 言語とも木を不変な値で表した。網羅性の検査は F# が最も手厚く、TypeScript は `never` を自分で仕込む必要があった
2. **値として返すか、オブジェクトに持たせるか** — F# は学習の結果を値（木・関数）として返し、「学習前に予測する」誤りを型で防いだ。ほかの 3 言語はクラスに状態を持たせ、実行時に確かめた
3. **構造的な型と、関数の型** — Python と TypeScript は構造的な型でライブラリを同じ型に合わせ、F# はモデルそのものを関数の型にした
4. **遅延評価** — Kotlin・TypeScript・F# は交差検証を遅延評価にし、必要な分だけ学習した
5. **失敗の表し方** — 例外（Python）、例外を包んだ `Result`（Kotlin・TypeScript）、判別共用体の `Result`（F#）に分かれ、F# は失敗の型で状態コードが決まった

次の章では、ライブラリとの突き合わせで分かったことを比べます。
