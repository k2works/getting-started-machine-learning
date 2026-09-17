---
type: Article
title: "第11章: Command パターン — 6言語統合ガイド"
description: "Command パターンは、「リクエストをオブジェクトとしてカプセル化する」GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第11章: Command パターン — 6言語統合ガイド

## 1. はじめに

Command パターンは、「リクエストをオブジェクトとしてカプセル化する」GoF パターンです。OOP ではコマンドインターフェースと実装クラスを用いますが、関数型プログラミングでは**関数そのものがコマンド**になります。不変データ構造と組み合わせることで、Undo/Redo やバッチ実行が自然に実現できます。

## 2. 共通の本質

### コマンドの構造

```
Command = { execute: State → State, undo: State → State }
```

すべての言語で共通する設計：

- **コマンドは状態変換関数**: 現在の状態を受け取り、新しい状態を返す
- **Undo は逆変換**: execute の逆操作を同じインターフェースで表現
- **MacroCommand**: 複数のコマンドを集約し、順次実行する合成パターン

### 典型的なユースケース

- **テキストエディタ**: Insert / Delete / Replace コマンド + Undo/Redo
- **計算機**: 四則演算コマンドの履歴管理
- **バッチ処理**: コマンドキューの順次実行

## 3. 言語別実装比較

### 3.1 コマンドの型表現

| 言語 | コマンド表現 | ディスパッチ方法 |
|------|-----------|----------------|
| Clojure | マップ + マルチメソッド | `:type` キーでディスパッチ |
| Scala | `trait Command[S]` + case class | パターンマッチ / メソッド呼び出し |
| Elixir | プロトコル + 構造体 | `defimpl` でディスパッチ |
| F# | 判別共用体 `TextCommand` | `match` 式 |
| Haskell | ADT `TextCommand` | `case` 式 |
| Rust | `trait Command` + struct | trait メソッド呼び出し |

<details>
<summary>Clojure: マップ + マルチメソッド</summary>

```clojure
(defmulti execute :type)

(defmethod execute :insert-command [{:keys [position text]}]
  (fn [doc]
    (str (subs doc 0 position) text (subs doc position))))

(defmethod execute :delete-command [{:keys [start end]}]
  (fn [doc]
    (str (subs doc 0 start) (subs doc end))))

;; コマンドはマップとして生成
(def cmd {:type :insert-command :position 0 :text "Hello"})
(def new-doc ((execute cmd) "World"))
```

Clojure ではコマンドが**関数を返す**パターンを採用しています。execute はコマンドマップを受け取り、状態変換関数を返します。

</details>

<details>
<summary>Scala: trait + case class</summary>

```scala
trait Command[S]:
  def execute(state: S): S
  def undo(state: S): S

case class InsertCommand(position: Int, text: String) extends Command[String]:
  def execute(doc: String): String =
    doc.substring(0, position) + text + doc.substring(position)
  def undo(doc: String): String =
    doc.substring(0, position) + doc.substring(position + text.length)

case class MacroCommand[S](commands: List[Command[S]]) extends Command[S]:
  def execute(state: S): S = commands.foldLeft(state)(_.execute(_))
  def undo(state: S): S = commands.reverse.foldLeft(state)(_.undo(_))
```

型パラメータ `[S]` により、任意の状態型に対してコマンドを定義できます。

</details>

<details>
<summary>F#: 判別共用体</summary>

```fsharp
[<RequireQualifiedAccess>]
type TextCommand =
    | Insert of position: int * text: string
    | Delete of startPos: int * endPos: int * deletedText: string
    | Replace of position: int * oldText: string * newText: string

module TextCommand =
    let execute (command: TextCommand) (document: string) : string =
        match command with
        | TextCommand.Insert(pos, text) ->
            document.[..pos-1] + text + document.[pos..]
        | TextCommand.Delete(startPos, endPos, _) ->
            document.[..startPos-1] + document.[endPos..]
        | TextCommand.Replace(pos, oldText, newText) ->
            document.Replace(oldText, newText)

    let undo (command: TextCommand) (document: string) : string =
        match command with
        | TextCommand.Insert(pos, text) ->
            document.[..pos-1] + document.[pos + text.Length..]
        | TextCommand.Delete(startPos, _, deletedText) ->
            document.[..startPos-1] + deletedText + document.[startPos..]
        | TextCommand.Replace(pos, oldText, newText) ->
            document.Replace(newText, oldText)
```

</details>

<details>
<summary>Haskell: ADT + パターンマッチ</summary>

```haskell
data TextCommand
    = InsertCommand Int String
    | DeleteCommand Int Int
    | ReplaceCommand Int String String

executeTextCommand :: TextCommand -> String -> String
executeTextCommand cmd text = case cmd of
    InsertCommand pos str ->
        let (before, after) = splitAt pos text
        in before ++ str ++ after
    DeleteCommand start end ->
        let (before, rest) = splitAt start text
        in before ++ drop (end - start) rest
    ReplaceCommand _ old new ->
        replace old new text
```

</details>

<details>
<summary>Rust: trait + struct</summary>

```rust
pub trait Command: CloneCommand {
    fn execute(&self, doc: &Document) -> Document;
    fn undo(&self, doc: &Document) -> Document;
}

pub struct InsertCommand {
    pub position: usize,
    pub text: String,
}

impl Command for InsertCommand {
    fn execute(&self, doc: &Document) -> Document {
        let content = format!("{}{}{}",
            &doc.content[..self.position],
            self.text,
            &doc.content[self.position..]);
        Document { content }
    }

    fn undo(&self, doc: &Document) -> Document {
        let content = format!("{}{}",
            &doc.content[..self.position],
            &doc.content[self.position + self.text.len()..]);
        Document { content }
    }
}
```

</details>

<details>
<summary>Elixir: プロトコル + defimpl</summary>

```elixir
defprotocol Command do
  def execute(command, state)
  def undo(command, state)
end

defmodule InsertCommand do
  defstruct [:pos, :text]
end

defimpl Command, for: InsertCommand do
  def execute(%InsertCommand{pos: p, text: t}, doc) do
    before = String.slice(doc, 0, p)
    after = String.slice(doc, p..-1//1)
    before <> t <> after
  end

  def undo(%InsertCommand{pos: p, text: t}, doc) do
    before = String.slice(doc, 0, p)
    after = String.slice(doc, (p + String.length(t))..-1//1)
    before <> after
  end
end
```

Elixir 版はプロトコルベースでシンプルかつ簡潔な実装です。

</details>

### 3.2 Undo/Redo の実装

すべての言語で**履歴スタック**を用いた Undo/Redo を実装します。不変データ構造との組み合わせにより、状態の巻き戻しが安全に行えます。

| 言語 | 履歴管理 | 状態の巻き戻し |
|------|---------|--------------|
| Clojure | リスト（undo-stack / redo-stack） | 不変マップの差し替え |
| Scala | `List[Command[S]]` | case class の再構築 |
| Elixir | リスト | 構造体の再構築 |
| F# | `Command list` | レコードの再構築 |
| Haskell | `[TextCommand]` | 純粋関数で新状態生成 |
| Rust | `Vec<Box<dyn Command>>` | clone + 新インスタンス |

### 3.3 MacroCommand（コマンドの合成）

複数のコマンドを 1 つのコマンドとして扱うパターンです。

```
MacroCommand.execute = commands.foldLeft(state)(cmd.execute)
MacroCommand.undo    = commands.reverse.foldLeft(state)(cmd.undo)
```

| 言語 | 合成方法 |
|------|---------|
| Clojure | `reduce` で順次適用 |
| Scala | `foldLeft` |
| Elixir | `Enum.reduce` |
| F# | `List.fold` |
| Haskell | `foldl'` |
| Rust | `iter().fold()` |

## 4. 比較分析

### 4.1 関数型 Command vs OOP Command

| 観点 | OOP | 関数型 |
|------|-----|--------|
| コマンドの実体 | インターフェース実装クラス | 関数 / ADT / マップ |
| 状態変更 | ミュータブルな receiver を変更 | 新しい状態を返す |
| Undo | 逆操作を手動実装 | 逆操作 + 不変状態で安全 |
| 合成 | Composite パターン | fold / reduce |

### 4.2 不変性による Undo の安全性

関数型の最大の利点は、**状態が不変**であるため Undo が安全なことです。OOP では Undo 時にオブジェクトの状態を元に戻す必要がありますが、関数型では過去の状態をそのまま保持しています。

### 4.3 言語間のボリューム差

| 言語 | 行数 | 特徴 |
|------|------|------|
| Clojure | 567 | マルチメソッドの詳細な解説、複数ユースケース |
| Scala | 408 | 型パラメータの活用、複数の実装例 |
| F# | 429 | パイプラインとの統合、複合コマンド |
| Rust | 357 | 所有権管理の詳細解説 |
| Haskell | 304 | 純粋関数の簡潔な実装 |
| Elixir | 148 | プロトコルベースの最小実装 |

Elixir 版が最も簡潔なのは、プロトコル + `defimpl` により定型コードが最小化されるためです。

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 動的なコマンド追加 | Clojure | マルチメソッドでオープンに拡張 |
| 型安全なコマンド合成 | Scala | 型パラメータ付き `Command[S]` |
| 最小限の実装 | Elixir | プロトコルで簡潔 |
| コンパイル時の網羅性保証 | F#, Haskell | 判別共用体 / ADT のパターンマッチ |
| メモリ効率的な履歴管理 | Rust | 所有権による明示的なリソース管理 |

## 6. まとめ

Command パターンの関数型実装は、OOP 版より**自然で安全**です：

1. **関数がコマンド**: 状態変換関数そのものがコマンドの本質
2. **不変性で安全な Undo**: 過去の状態を破壊しないため、履歴管理が容易
3. **fold による合成**: MacroCommand は reduce/fold で自然に表現

## 言語別個別記事

- [Clojure](../clojure/11-command-pattern.md) | [Scala](../scala/11-command-pattern.md) | [Elixir](../elixir/11-command-pattern.md) | [F#](../fsharp/11-command-pattern.md) | [Haskell](../haskell/11-command-pattern.md) | [Rust](../rust/11-command-pattern.md)
