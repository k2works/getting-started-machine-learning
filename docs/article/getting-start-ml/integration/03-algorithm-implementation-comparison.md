---
type: Article
title: "第 3 章: アルゴリズムの実装の比較"
description: "同じアルゴリズムを 14 言語でどう書いたかを比べる。決定木の木の型（代数的データ型・共用型・マップ・クラス・判別可能なユニオン・非公開メソッドの interface）と場合分けの漏れの見つけ方、線形回帰の行列、モデルと評価関数の共通の型、交差検証の遅延評価、K-means の繰り返し、失敗の表し方と API の入力の検証を整理する。"
tags: [article,getting-start-ml,integration]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 3 章: アルゴリズムの実装の比較

## 3.1 同じアルゴリズム、違う書き方

14 言語とも、同じ TODO リストから同じアルゴリズム（決定木・正規方程式・ソフトマックス・K 分割交差検証・K-means など）を TDD で自作しました。アルゴリズムの手順は同じなので、違いは「その言語で、同じ手順をどんな型と制御構造で書いたか」に表れます。本章では、違いがはっきり表れた場面を比べます。

言語は 3 つの波に分けて書きました。表が横に広がりすぎるところは、波ごとに分けています。

| 波 | 言語 |
|----|------|
| 第 1 波 | Python・Kotlin・TypeScript・F# |
| 第 2 波 | Java・C#・Scala・Go・Rust |
| 第 3 波 | Ruby・Clojure・Elixir・PHP・Haskell |

## 3.2 決定木の木の型（第 3 章）

決定木は「葉（予測するラベル）」か「節（分け方と左右の部分木）」のどちらかです。この「どちらか」の表し方は、14 言語で次のようになりました。

**第 1 波**

| 言語 | 木の型 | 場合分け | 場合分けの漏れ |
|------|-------|---------|--------------|
| Python | `Leaf` と `Node` の 2 つの `@dataclass(frozen=True)`。木は `Leaf \| Node` の型ヒント | `isinstance(tree, Leaf)` で葉を先に返し、残りを節として扱う | mypy が型を絞り込むが、場合の数が増えたときに知らせる仕組みは無い |
| Kotlin | `sealed interface Tree` と、それを実装する `data class Leaf`・`Node` | `when (tree)` | `when` を式として使うと、コンパイラが網羅を検査する |
| TypeScript | 判別可能なユニオン `{ kind: "leaf"; ... } \| { kind: "node"; ... }` | `switch (tree.kind)` | `default` で `never` 型に代入する書き方を自分で仕込むと、型チェックが検査する |
| F# | 判別共用体 `type Tree<'L> = Leaf of 'L \| Node of Split * Tree<'L> * Tree<'L>` | `match tree with` | 何も書かなくてもコンパイラが検査する（FS0025。F# 版は警告をエラーにしている） |

**第 2 波**

| 言語 | 木の型 | 場合分け | 場合分けの漏れ |
|------|-------|---------|--------------|
| Java | `sealed interface Tree` と `record Leaf`・`record Node` | `switch` のパターンマッチ | `switch` を式として使うと、コンパイラが網羅を検査する |
| C# | 抽象 record と、それを継承する `Leaf`・`Node` の record | `switch` 式のパターンマッチ | **網羅性を証明できないので `_ =>` の分岐が要る**（CS8509） |
| Scala | `enum Tree` の `case Leaf`・`case Node` | `match` | コンパイラが検査する（`-Xfatal-warnings` で警告をエラーにしている） |
| Go | 非公開のメソッドを持つ `type Tree interface { isTree() }` と、`Leaf`・`Node` の構造体 | 型スイッチ | **検査されない。** 既定の分岐でエラーを返して備える |
| Rust | `enum Tree { Leaf { label }, Node { split, left: Box<Tree>, right: Box<Tree> } }` | `match` | 何も書かなくてもコンパイラが検査する |

**第 3 波**

| 言語 | 木の型 | 場合分け | 場合分けの漏れ |
|------|-------|---------|--------------|
| Ruby | `Data.define` で作った `Leaf` と `Node` の 2 つの型。**互いに関係の無い 2 つの型**で、「木はどちらかである」はどこにも宣言されない | `case`/`in` のパターンマッチ（`Data` は `deconstruct_keys` を持つのでそのままパターンに書ける） | **検査されない** |
| Clojure | 素のマップ。`{:label "setosa"}` が葉、`{:split ... :left ... :right ...}` が節 | `leaf?`（`contains? tree :label`）で判定してから値を取り出す | **検査されない** |
| Elixir | 素のマップ。`%{label: ...}` が葉、`%{split: ..., left: ..., right: ...}` が節 | **関数節のパターンマッチ**（`%{label: label}` に一致したらその場で `label` が束縛される） | **検査されない** |
| PHP | **共用型 `Leaf\|Node`**。`final readonly class` を 2 つ作り、`Node` の `$left`・`$right` を `Leaf\|Node` と宣言する | `instanceof` | **実行時に「葉か節か」は検査され、PHPStan は絞り込みをするが、網羅性は強制しない** |
| Haskell | 代数的データ型 `data Tree = Leaf Text \| Branch Split Tree Tree` | パターンマッチの等式 | **コンパイルエラー**（`-Wall` を `-Werror` にしているので `-Wincomplete-patterns` が止める） |

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

```fsharp
// F#
type Tree<'L> =
    | Leaf of 'L
    | Node of Split * Tree<'L> * Tree<'L>
```

```rust
// Rust。再帰する型なので、部分木は Box で包む
pub enum Tree {
    Leaf { label: String },
    Node {
        split: Split,
        left: Box<Tree>,
        right: Box<Tree>,
    },
}
```

```go
// Go。判別共用体が無いので、非公開のメソッドを持つインターフェースで代用する
type Tree interface {
	isTree()
}

type Leaf struct{ Label string }

func (Leaf) isTree() {}
```

```haskell
-- Haskell。場合分けを 1 つ忘れるとコンパイルが止まる
data Tree
  = Leaf Text
  | Branch Split Tree Tree
  deriving (Eq, Show)
```

```php
// PHP。判別共用体は無いが、共用型はある
final readonly class Node
{
    public function __construct(
        public Split $split,
        public Leaf|Node $left,
        public Leaf|Node $right,
    ) {
    }
}
```

```elixir
# Elixir。木はマップ。葉と節を 2 つの関数節のパターンで見分ける
defp predict_one(%{label: label}, _row), do: label

defp predict_one(%{split: split, left: left, right: right}, row) do
  predict_one(if(goes_left?(split, row), do: left, else: right), row)
end
```

- 14 言語とも、木を「不変な値」で表しました。Python は `frozen=True`、Kotlin は `data class`（`val`）、TypeScript はオブジェクトリテラル、F#・Scala・Rust・Haskell は代数的データ型、Java・C# は record、Ruby は `Data.define`、PHP は `readonly class`、Clojure と Elixir は不変のマップです
- **網羅性の検査は 4 段階に分かれました。**
    1. **何も書かなくても検査する** — F#・Scala・Rust・**Haskell**（代数的データ型が言語の中核）
    2. **書き方を選べば検査する** — Kotlin（`when` を式にする）・Java（`switch` を式にする）
    3. **自分で仕込むか、別の道具を走らせれば絞り込める** — TypeScript（`never` に代入する書き方）・**PHP**（共用型を書けば実行時の型検査と PHPStan の絞り込みが効く。ただし網羅性そのものは強制されない）
    4. **検査されない** — Python（mypy が絞り込みはするが網羅は知らせない）・C#（`_ =>` の分岐を強制される）・Go（型スイッチの既定の分岐でエラーを返すしかない）・**Ruby・Clojure・Elixir**
- **第 3 波で、この軸の幅がいちばん広がりました。** Haskell は `data` を 1 つ書くだけで漏れがコンパイルエラーになり、PHP は「型としては書けるが網羅性までは守らせない」という中間に立ち、Ruby・Clojure・Elixir は型で書くこと自体をしません。**同じ動的型付けの言語の中でも、PHP と Ruby で判断が分かれた**のがこの章です（[第 4 章](04-library-ecosystem-comparison.md) の型の道具の話につながります）
- **Clojure と Elixir は、木を型にしないことの見返りを取りました。** 木がただのマップなので、`=`／`==` でそのまま比べられ、`println`／`IO.inspect` でそのまま読め、部分木を `(:left tree)`／`tree.left.label` と辿れます。木を表示する関数のテストが、木を組み立て直さずに書けるのはそのおかげです。Haskell も `deriving (Eq, Show)` で同じ見返りを、型を保ったまま得ています
- **Clojure と Elixir の違いは「判定と取り出しが同じ場所で起きるか」です。** Clojure は `leaf?` で判定してから `:label` を取り出す 2 段階、Elixir は関数節のパターンが一致した時点で `label` が束縛される 1 段階になりました
- **Rust だけが `Box` を要求します。** 再帰する型はサイズがコンパイル時に決まらないので、部分木をヒープに置く必要があります（忘れると `E0072`）。ほかの 13 言語は参照やポインタが既定なので、この一手間がありません。Ruby 版・Haskell 版はこの点を明示的に「Rust で要った `Box` が要らない」と書いています
- 学習の API も分かれました。Python・Kotlin・TypeScript・Java・C#・Go・Rust・Ruby・PHP は、`DecisionTree` の `fit` が木を中に持ち、`fit` の前に `predict` を呼ぶとエラーにしました。F#・Scala・Clojure・Elixir・Haskell は、`fit` が木を **値として返し**、`predict` が木を引数に取るので、「学習する前に予測する」誤りをそもそも書けません

## 3.3 線形回帰の行列（第 7 章）

正規方程式 (XᵀX) w = Xᵀt を解くための行列の扱いです。

**第 1 波・第 2 波**

| 言語 | 行列 | 連立方程式の解き方 |
|------|------|-----------------|
| Python | NumPy の配列。`design.T @ design` のように演算子で書く | `np.linalg.solve` |
| Kotlin | 自作の `Matrix` クラス。`operator fun times` で `*` を行列の積にする | 自作 |
| TypeScript | 数値の配列の配列と、積・転置の関数 | 自作の部分ピボット選択付きのガウスの消去法。ml-matrix の `solve` とも突き合わせた |
| F# | `float[][]`（配列の配列）と、積・転置の関数 | 自作 |
| Java | 自作の小さな行列の型 | 自作。Tribuo の `DenseMatrix` とも突き合わせた |
| C# | 自作の小さな行列の型 | 自作 |
| Scala | 自作の小さな不変の型 | 自作 |
| Go | **gonum の `mat`**（自作しない） | `mat` の分解。`stat.LinearRegression`（単回帰）とも突き合わせた |
| Rust | **ndarray 0.16**（自作しない） | 自作の掃き出し法。linfa-linear の `LinearRegression` とも突き合わせた |

**第 3 波**

| 言語 | 行列 | 連立方程式の解き方 |
|------|------|-----------------|
| Ruby | **numo-narray-alt の `Numo::DFloat`**（積と転置はある） | **自作の掃き出し法**（Numo に連立方程式を解く関数が無い。`numo-linalg` が要る） |
| Clojure | **ベクタのベクタをそのまま行列として扱う**（包まない） | 自作 |
| Elixir | **Nx のテンソル**（`type: :f64` を明示する） | `Nx.LinAlg.solve` |
| PHP | **MathPHP の `NumericMatrix`** | MathPHP の `solve()`。**`NumericMatrix::LU` を明示して解き方を固定した** |
| Haskell | **hmatrix**（BLAS/LAPACK を要求する） | 自作のガウス・ジョルダン法と、hmatrix の `<\>`・`linearSolveLS` の両方 |

**行列を自作したかどうかで 2 つに分かれました。** Python（NumPy）・Go（gonum）・Rust（ndarray）・Ruby（Numo）・Elixir（Nx）・PHP（MathPHP）・Haskell（hmatrix）は数値計算のライブラリを使い、Kotlin・TypeScript・F#・Java・C#・Scala は小さな行列の型を自作しています。

**Clojure は、そのどちらでもない 3 つめの道を取りました。** 行列を包む型を作らず、**ベクタのベクタをそのまま行列として扱います**。Clojure のベクタは変更できないので、Java 版・C# 版が配列のために書いた「写して守る」「深く比べる」コードがどちらも要らず、`=` がそのまま値の比較になります。そのかわり「行によって列数が違わないか」は自分で確かめる関数を持ちました。

**「ライブラリがある」と「必要な関数がある」は別です。** Rust の ndarray には連立方程式を解く関数がなく（`ndarray-linalg` は LAPACK を要求します）、Ruby の Numo も同じでした。どちらも「行列は借りて、解く部分だけ自作する」という組み合わせになっています。

**Haskell だけが、同じ実データを 4 通りで解いて比べました。** 自作のガウス・ジョルダン法（正規方程式）・`<\>`（計画行列をそのまま）・`linearSolveLS`（計画行列）の 3 つは 12 桁まで一致し、**`<\>` に正規方程式を渡したものだけが 9 桁に落ちます**（`XᵀX` を作ると条件数が 2 乗になるため）。同じ関数に別の入れ物で渡しただけで精度が動くことを、1 つのライブラリの中で見せられた章でした（[第 4 章](04-library-ecosystem-comparison.md) で、Elixir 版・PHP 版の結果と並べます）。

Kotlin は演算子オーバーロードで、自作の行列でも数式に近い見た目にしました。F# 版の第 12 章では、第 7 章の行列のモジュールを書き換えずに、足し算・定数倍・単位行列を別のモジュールで足しました。

## 3.4 モデルと評価関数の共通の型（第 10・11 章）

第 10 章では、自作の分類器とライブラリの分類器を同じ手順で評価するために、共通の型を決めました。第 11 章の交差検証でも、モデルと評価関数を差し替えられるようにしています。

**第 1 波・第 2 波**

| 言語 | モデルの型 | 評価関数の型 |
|------|----------|------------|
| Python | `Protocol`（`fit`・`predict` のメソッドを持つもの） | `Callable[[ArrayLike, ArrayLike], float]` |
| Kotlin | `interface Model<T>`（`fit`・`predict`） | 関数型 |
| TypeScript | 構造的部分型の `interface`（`fit`・`predict`） | 関数の型 |
| F# | 関数の型 `Model<'T> = 特徴量 -> 正解 -> (特徴量 -> 予測)` | 関数の型 `Metric<'T> = 'T list -> 'T list -> float` |
| Java | `interface Model<T>`（クラスが実装を宣言する） | 関数型インターフェース |
| C# | `interface IModel<T>` | デリゲート（`Func<...>`） |
| Scala | `trait Model[T]` | 関数の型 |
| Go | `interface`（**メソッドの形が合えば実装になる**） | 関数の型 |
| Rust | `trait`（実装を `impl Trait for Type` と明示する） | クロージャの型（`impl Fn(...)`） |

**第 3 波**

| 言語 | モデルの型 | 評価関数の型 |
|------|----------|------------|
| Ruby | **何も宣言しない**（ダックタイピング。`fit` と `predict` に応答すればよい） | **`call` に応えるもの**（lambda と `Method` オブジェクト。第 7 章の関数は `Chapter07.method(...)` でそのまま渡せる） |
| Clojure | **「学習して、予測する関数を返す関数」**。第 10 章では `defprotocol` もアダプターも使わなかった | 関数 |
| Elixir | **behaviour（`@callback`）**。behaviour は**モジュールへの約束**なので、状態を持つには `{モジュール, 状態}` の組が要る | 無名関数 |
| PHP | **`interface`** | `Closure`（**関数を返す関数の PHPDoc は戻り値側を括弧で包まないと構文誤りになる**） |
| Haskell | 第 10 章は**型クラス＋存在型**（`ExistentialQuantification`）、第 11 章は**ただの関数**（`type Trainer x t p = ...`）、第 15 章は**レコード** | 関数 |

- **構造的か、名前的か、宣言しないか**の 3 通りに分かれました。Python の `Protocol`・TypeScript の `interface`・Go の `interface` は **構造的** で、`fit`・`predict` を持っていれば宣言しなくてもその型として扱えます。Kotlin・Java・Scala・Rust・PHP の `interface`／`trait` は **名前的** で、`implements`／`impl ... for ...` と書く必要があります。**Ruby は 3 つめで、そもそも何も宣言しません。** 第 3 章の決定木はもともと `fit` と `predict` を持っているので、第 10 章にも第 11 章にも何も足さずに渡せました
- **関数そのものをモデルの型にした言語が 3 つあります。** F#・Clojure・Haskell（第 11 章）は、モデルを「学習して、予測する関数を返す関数」という 1 つの関数の型で表しました。学習した状態は、返した関数が覚えています（クロージャ）。学習の状態を持つオブジェクトが無いので、交差検証で「分割ごとに新しいモデルを作る」ための工場も要りません
- **Haskell は、同じシリーズの中で 3 通りを使い分けた唯一の版です。** 第 10 章で `TreeModel` と `ForestModel` を 1 つのリストに並べるには存在型（`ExistentialQuantification`）が要りました。**型クラスは「1 つの型に 1 つのインスタンス」**なので、PHP の `interface` や Elixir の関数には無かった手間です。ところが第 11 章の「予測する側」は関数そのものなので `type Predictor = ...` の 1 行で済み、アダプターが 1 つも要りませんでした。第 15 章の置き場では、型クラス版も実際に書いてコンパイルしたうえでレコードを選んでいます（理由は 3.7 節）
- **Elixir の behaviour だけが、状態の持ち方を変えました。** behaviour はモジュールへの約束なので、値としての置き場を表せません。そこで置き場が `{モジュール, 状態}` の組になりました（Plug 自身の `plug: {Api, store}` と同じ形です）。PHP 版はこの回り道が要らず、偽物のモデルを無名クラスでその場に書けています
- Rust では、`trait` の値を返すときに `Box<dyn Trait>` と書きます。動的なディスパッチとヒープへの確保を型に明示させる言語は 14 言語で Rust だけでした

評価関数を引数で渡す設計は、14 言語で共通でした。混同行列から求める指標（`ConfusionMatrix -> float`）を、正解と予測から求める評価関数に変える高階関数も、14 言語それぞれで書いています。

## 3.5 交差検証の遅延評価（第 11 章）

交差検証は、分割ごとに学習と評価を繰り返します。スコアを「すぐに全部計算する」か「取り出したときに計算する」かが分かれました。

**第 1 波・第 2 波**

| 言語 | 戻り値 | 評価の時期 |
|------|-------|----------|
| Python | `list[float]` | すぐにすべての分割を計算する |
| Kotlin | `Sequence<Double>` | 取り出したときに計算する |
| TypeScript | ジェネレーター関数（`function*` と `yield`） | 取り出したときに計算する |
| F# | シーケンス式（`seq { ... }`） | 取り出したときに計算する |
| Java | `Stream<Double>` | 取り出したときに計算する |
| C# | `IEnumerable<double>`（`yield return`） | 取り出したときに計算する |
| Scala | `LazyList[Double]` | 取り出したときに計算する |
| Go | `[]float64` | すぐにすべての分割を計算する |
| Rust | `Vec<f64>` | すぐにすべての分割を計算する |

**第 3 波**

| 言語 | 戻り値 | 評価の時期 |
|------|-------|----------|
| Ruby | 配列 | すぐにすべての分割を計算する |
| Clojure | 遅延シーケンス | **取り出したときに計算する。ただし `map` はベクタを 32 件ずつまとめて実現する** |
| Elixir | `Stream` | 取り出したときに計算する（**1 件ずつ**） |
| PHP | 配列（`array_map`） | すぐにすべての分割を計算する（`Generator` にはできるが、この章では要らなかった） |
| Haskell | `Either String [Double]` | **すぐにすべての分割を計算する**（遅延評価の言語なのに、`traverse` が全部を要求する） |

**遅延評価の道具があるかどうか**で、まず分かれました。Kotlin・TypeScript・F#・Java・C#・Scala・Clojure・Elixir は言語やライブラリに遅延の列があるので、「最初の分割のスコアだけを取り出せば、学習は 1 回で済む」ことをテストで確かめました。Python・Go・Rust・Ruby・PHP は、すべての分割を計算してから返します。

**第 3 波で、「遅延だから 1 件」が 2 回裏切られました。**

- **Clojure の `map` はチャンクします。** ベクタに対しては 32 件を一度に実現するので、分割が 4 個しかない交差検証では、1 つ目を取り出した時点で 4 回とも学習されていました。テストに `(= 1 @trained)` と書いて `4` が返ったところで気付き、リスト（`(apply list folds)`）に変えると 1 回になります。**遅延の細かさは言語ごとに実測して仕様として書く**という教訓が、ここで初めて出ました
- **Haskell の `traverse` は全分割を要求します。** Elixir 版の `Stream.map/2` が「取り出した分だけ学習する」ものだったのに対し、`Either` を 1 つにまとめるには全部の結果が要ります。「どれか 1 つでも失敗したら全体が失敗」と言うには全部を見るしかないからです。**遅延評価と、型で失敗を表すことは、ここでぶつかります**。遅延させたいなら戻り値を `[Either String Double]` にして判断を呼ぶ側に渡すことになりますが、この章ではそこまで要りませんでした

Elixir の `Stream.map/2` は 1 件ずつ実現することを、プロセスのメッセージで学習の回数を数えて確かめています。F# 版では、同じシーケンスから 2 回取り出すと学習もやり直されることと、`Seq.cache` で結果を覚えさせられることもテストに残しました。

## 3.6 K-means の繰り返し（第 14 章）

K-means は、「割り当て」と「中心の更新」を、中心が変わらなくなるまで繰り返します。

| 言語 | 繰り返しの書き方 |
|------|---------------|
| Python | `for _ in range(max_iterations):` のループと、変わらなければ `break` |
| Kotlin | `generateSequence(initialCenters) { ... }` で中心の列を無限に作り、`zipWithNext` で前後を比べて、最初に止まる位置を `first` で取る |
| TypeScript | `while (iterations < maxIterations)` のループ |
| F# | 末尾再帰の関数。`[<TailCall>]` でコンパイラに末尾呼び出しを確かめさせる |
| Java・C#・Go | `for` のループと `break` |
| Scala | `LazyList.iterate` で中心の列を作り、前後が等しくなる位置で止める |
| Rust | `for _ in 0..max_iterations` のループと `break` |
| Ruby | `max_iterations.times do ... break if updated == centers` |
| Clojure | **`loop`/`recur`**（可変の変数を使わない末尾再帰） |
| Elixir | 末尾再帰の `converge/3`。**残りの回数が 0 になる節を先に書く** |
| PHP | `for` のループと `break` |
| Haskell | `where` 節の中の末尾再帰 `converge`。**終了条件が `next == current` の 1 つの式** |

Kotlin と Scala の書き方は、繰り返しを「中心の列」というデータとして扱っています。F#・Clojure・Elixir・Haskell の末尾再帰は、可変の変数を使わずに「中心が動かなくなるまで」を表します。命令型のループで書いた 7 言語（Python・TypeScript・Java・C#・Go・Rust・Ruby・PHP）は、読む人にとっていちばん素直です。

中心が変わらなくなったかの判定は、14 言語とも**浮動小数点の厳密な比較**にしました。同じ計算から同じ値が出るので変化が無ければビット単位で同じになり、許容誤差を入れるとかえって微小な振動で止まらなくなるためです。

**「値としての比較がそのまま書けるか」が、ここで分かれました。** Java 版は `Arrays.deepEquals` を呼び、写し（`clone()`）を取る必要がありました。Clojure（`=`）・Elixir（`==`）・PHP（`===` が入れ子の配列を再帰的に比べる）・Haskell（`Eq` がそのまま使える）・Ruby（Numo の `==` がすべての要素の一致を返す）では、その悩みが起きません。**不変の値を既定にした言語ほど、収束の判定が短くなる**という形で現れています。

F# 版では、第 5 章で有効にした FSharpLint の FL0085 が `let rec` に `[<TailCall>]` を求めるので、末尾再帰でない再帰（決定木の `fit` など）には理由を書いて警告を抑えています。Haskell 版では、`meanPoint` の空リストの場合を書き忘れると `-Wincomplete-patterns` がコンパイルを止めるので、「空だったらどうするか」を決めないままでは先へ進めませんでした。

14 言語とも、K-means は **初期中心を引数で受け取る** 設計にしました。乱数で初期中心を選ぶ関数と、初期中心からクラスタリングする関数を分けたので、局所解に陥る例を乱数に頼らずにテストで再現できました。Haskell 版では、この局所解のテストが最初に落ちています——2 つのかたまりを k=2 で分けるだけなら K-means は自分で直るので、3 つのかたまりと 3 つの中心が要りました。

## 3.7 失敗の表し方と API の入力の検証（第 15 章）

第 15 章の API では、「モデルが無い」「入力が不正」という失敗を扱いました。どの版でも、不正な入力は 422、モデルが無い場合は 503 に割り当てています。

**第 1 波・第 2 波**

| 言語 | モデルが無いとき | 入力の検証 |
|------|---------------|----------|
| Python | 例外 `ModelNotFoundError` を投げる | Pydantic の `BaseModel` と `Field(ge=0)` |
| Kotlin | 標準ライブラリの `Result<T>` の失敗（中身は例外） | `sealed interface Validated<T>` を自作 |
| TypeScript | 自作の判別可能なユニオン `Result<T>` の失敗 | zod のスキーマと `safeParse` |
| F# | 組み込みの `Result<'T, PredictionError>` の `Error` | `and!` の計算式を自作 |
| Java | **検査例外** `ModelNotFoundException`（`throws` に現れる） | sealed interface の `Validated<T>` を自作 |
| C# | 例外 `ModelNotFoundException` | record と自作の検証 |
| Scala | `Either[PredictionError, T]` | 自作の検証 |
| Go | **番兵のエラー** `ErrModelNotFound` を `%w` で包み、`errors.Is` で判別 | 構造体と自作の検証（ポインタで欠落を表す） |
| Rust | `enum Error` の `ModelNotFound` | `enum Validated<T>` を自作（`Option` で欠落を表す） |

**第 3 波**

| 言語 | モデルが無いとき | 入力の検証 |
|------|---------------|----------|
| Ruby | 例外。`rescue` の節で 503 と 500 に分ける | 自作（`Numeric`・`Integer` で型を見分け、理由を並べる） |
| Clojure | 例外 | 自作（規則は「理由の文字列か、問題なしの `nil`」を返す） |
| Elixir | 例外（`defexception` の構造体にすると `rescue` の節で種類ごとに振り分けられる） | 自作（同上） |
| PHP | 例外（`ValidationException` を別の型にして 422 と分ける） | 自作（規則は `null` か理由を返す静的メソッド） |
| Haskell | **`Either` と直和型の `LoadError`**（`ModelNotFound` と `ModelUnreadable` を分ける） | 自作（`Nothing` か理由を返す関数。**aeson の `FromJSON` はあえて使わなかった**） |

- **失敗を「無視できない」側と、そうでない側に分かれます。** Haskell・Rust・F#（と Scala・Kotlin・TypeScript の `Result`）は、失敗が戻り値の型に現れるので取り出さずには使えません。Go の `error` は `_` で捨てられます。例外を使う 9 言語（Python・Java・C#・Ruby・Clojure・Elixir・PHP・および Kotlin／TypeScript の中身）は、捕まえ忘れても呼び出し側のコンパイルは通ります
- **Haskell 版は、失敗を直和型で表すことの見返りをいちばんはっきり示しました。** `LoadError` に節を 1 つ増やすと、ステータスコードへの変換の関数が**その場でコンパイルエラーになります**。PHP 版の `catch` の節、Elixir 版の `rescue` の節は、書き漏らしても通ります。「まだ学習していない（503、時間が経てば直る）」と「置き場が壊れている（500、人が直すしかない）」を分ける設計が、例外の版では例外クラスを増やす仕事に、Haskell では節を増やすだけの仕事になりました
- **F# は、失敗を最後まで `Result` の値のまま扱いました。** 検証の失敗（`string list`）は 422、予測の失敗（`PredictionError`）は 503 と、失敗の **型** で状態コードが決まります
- 入力の検証は、Python（Pydantic）と TypeScript（zod）がライブラリのスキーマで、ほかの 12 言語は自作で書きました。**Haskell 版は、aeson の `FromJSON` を使える立場にありながら使いませんでした。** この API は「型が違えば 422、値が範囲の外でも 422 で理由を並べる」という二段の振る舞いを求めるのに、`FromJSON` の失敗は文字列 1 本になるので、理由を並べるところが書けないためです。**型の道具があっても、仕様が求める粒度に合わなければ使わない**という判断です
- **「JSON に値が無かった」の表し方**も分かれました。Java はボックス化した型（`Integer`）、C# は `int?`、Go は**ポインタ**（`*float64`）、Rust は `Option<f64>`、F#・Haskell は `option`／`Maybe` です。値の既定値（0）と「値が無い」を区別できないと、必須の検査そのものが書けません。**PHP 版はここで言語の癖に当たりました**——`?:` と `if ($value)` は `0.0` を偽とするので、0.0〜1.0 に正規化されたデータでは欠損の判定に使えず、`!== null` で書く必要があります
- **JSON のオブジェクトと配列の区別**も版によって仕事の量が違いました。**PHP は両方を配列として受けるので**、`json_decode(..., true)` の結果を `array_is_list` で弾く必要があり、しかも `{"0": 1}` はリスト扱いになります。Haskell の aeson は `Value` が両者を取り違えようがないので、この判定が要りません

**置き場（モデルストア）の約束の表し方**も 14 通りに分かれました。

| 言語 | 置き場の約束 |
|------|------------|
| Python | `Protocol` |
| Kotlin・Java・C#・PHP | `interface` |
| TypeScript | オブジェクトリテラル |
| F# | 関数のレコード |
| Scala・Go・Rust | `trait`／`interface`（Rust は `Box<dyn ModelStore + Send + Sync>`） |
| Ruby | **型では表さない。本物と偽物の両方に走らせる約束のテストのモジュール** |
| Clojure | `defprotocol` ＋ 約束のテスト（protocol は名前と引数の数しか守らない） |
| Elixir | behaviour ＋ 約束のテスト。置き場は `{モジュール, 状態}` の組になる |
| Haskell | **関数を詰めたレコード**（型クラス版も書いて比べたうえで選んだ） |

- **第 3 波の 5 言語は、そろって「約束のテスト」を足しました。** 型で書けるのは「メソッドの名前と引数の数」までで、「無ければ例外を投げる」という取り決めは型に書けないからです。本物と偽物の両方に同じテストを走らせることで守っています。Ruby 版はこの手だけで通し、Clojure・Elixir・PHP は型の道具と併用しました
- **Haskell だけが、2 つの選択肢から選びました。** 型クラス版も使い捨ての module に書いて実際にコンパイルし、レコードを選んでいます。理由は 2 つ——**型クラスは 1 つの型に 1 つのインスタンス**なので、偽物を「モデルを持っている／持っていない」で作り分けるのに型が 2 つ要ること、そして**型クラスの制約は全層に伝播する**のに対し、レコードは `ModelStore ->` の 1 語で止まることです
- **Rust だけが、並行性を型で要求しました。** axum のハンドラーは複数のスレッドで同時に走るので、共有する状態は `Arc` で包み、置き場の trait に `+ Send + Sync` を課します。満たさない型を入れるとコンパイルが通りません。ほかの 13 言語では、スレッドセーフは設計の約束として守るものでした
- **Haskell では、`IO` を境界に閉じ込める構造がそのまま層の分離になりました。** 「データの読み込みだけが `IO` で、前処理も学習も評価も純粋関数」という構造が型で保証されるので、`Service.run` の 5 行に層の境界が書けています。ほかの版が設計の規律として守っていたものが、この版だけ型で守られました。**遅延評価がクロージャの代わりにもなり**、検証で「理由があるときに値を作らせない」ために他版がクロージャを渡していたところが、第 2 引数にそのまま書けています

API の統合テストは、14 言語ともサーバーを起動せずに書けました（第 1 章の表を参照）。**PHP 版だけは事情が逆向きです**——組み込みサーバーはプログラムから起動できない（`php -S` 自体がサーバーで、要求ごとにスクリプトを最初から走らせる）ので、学習・保存と待ち受けがコマンドとして分かれ、結果として組み立てのクラスまで 100% テストできました。

## 3.8 まとめ

1. **場合分けの型** — 14 言語とも木を不変な値で表した。網羅性の検査は 4 段階に分かれ、代数的データ型を持つ F#・Scala・Rust・Haskell は何も書かなくても検査する。第 3 波で幅が最も広がり、**Haskell（コンパイルエラー）・PHP（共用型で書けるが網羅性は強制しない）・Ruby／Clojure／Elixir（型で書かない）**の 3 つに割れた
2. **値として返すか、オブジェクトに持たせるか** — F#・Scala・Clojure・Elixir・Haskell は学習の結果を値（木・関数）として返し、「学習前に予測する」誤りを型で防いだ。ほかの 9 言語はオブジェクトに状態を持たせ、実行時に確かめた
3. **構造的な型、名前的な型、宣言しない** — Python・TypeScript・Go は構造的な型でライブラリを同じ型に合わせ、Kotlin・Java・Scala・Rust・PHP は実装を明示的に宣言し、**Ruby は何も宣言しなかった**。F#・Clojure・Haskell（第 11 章）はモデルそのものを関数の型にした。Haskell は 1 つの版の中で型クラス・関数・レコードを使い分けた唯一の例
4. **遅延評価** — 遅延の列がある 8 言語は交差検証を遅延評価にした。ただし **Clojure の `map` は 32 件ずつチャンクし**、**Haskell の `traverse` は `Either` のために全分割を要求する**。「遅延だから 1 件」は思い込みで、言語ごとに実測してテストに書いた
5. **失敗の表し方** — 例外（Python・C#・Ruby・Clojure・Elixir・PHP）、検査例外（Java）、例外を包んだ `Result`（Kotlin・TypeScript）、判別共用体の `Result`／`Either`（F#・Scala・Rust・**Haskell**）、番兵のエラーを包む多値返却（Go）に分かれた。失敗の**種類**をコンパイラが数えてくれるのは代数的データ型の 4 言語だけで、Haskell では失敗の種類を増やすと変換側がその場で止まる
6. **行列とライブラリ** — 行列を自作したのは 6 言語、ライブラリを使ったのは 7 言語、**包まずにベクタのベクタのまま扱ったのが Clojure**。「ライブラリはあるが連立方程式を解く関数は無い」（Rust の ndarray、Ruby の Numo）という状況も 2 回起きた
7. **不変の値が既定だと、収束の判定が短くなる** — K-means の停止条件は、Clojure・Elixir・PHP・Haskell・Ruby では値の比較 1 つで書けた。Java 版が `deepEquals` と `clone()` を必要とした場所である

次の章では、ライブラリとの突き合わせで分かったことを比べます。
