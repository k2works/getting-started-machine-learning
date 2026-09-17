---
type: Article
title: "第18章: 並行処理システム — 6言語統合ガイド"
description: "並行処理は、関数型プログラミングの不変性が最も威力を発揮する領域です。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第18章: 並行処理システム — 6言語統合ガイド

## 1. はじめに

並行処理は、関数型プログラミングの不変性が最も威力を発揮する領域です。共有ミュータブル状態の排除により、データ競合やデッドロックのリスクを根本的に低減できます。しかし、各言語の並行処理モデルは**根本的に異なります**。本章では、電話システムの状態機械を題材に、6 言語の並行処理アプローチを比較します。

## 2. 共通の本質

### 状態機械モデル

すべての言語で共通する電話システムの状態遷移：

```
Idle → (dial) → Connecting → (connect) → Connected → (hangup) → Idle
                                            ↓
                                          (hold) → OnHold → (resume) → Connected
```

### 並行処理の課題

1. **共有状態の安全な更新**: 複数スレッドからの同時アクセス
2. **イベントの順序保証**: 状態遷移の一貫性
3. **デッドロックの回避**: リソースの競合防止

## 3. 言語別実装比較

### 3.1 並行処理モデルの比較

| 言語 | モデル | プリミティブ | 特徴 |
|------|--------|-----------|------|
| Clojure | Agent（非同期） | `agent` / `send` / `await` | 非同期メッセージ、順序保証 |
| Scala | CAS（ロックフリー） | `AtomicReference` / `compareAndSet` | 楽観的並行制御 |
| Elixir | Actor（OTP） | `GenServer` / `Agent` | メッセージパッシング、障害復旧 |
| F# | Actor（MailboxProcessor） | `MailboxProcessor` / `async` | 非同期ワークフロー |
| Haskell | STM（トランザクション） | `TVar` / `atomically` | 自動競合解決 |
| Rust | Mutex + Async | `Arc<Mutex<T>>` / `tokio` | 所有権による安全性 |

### 3.2 状態機械の実装

<details>
<summary>Clojure: Agent による非同期状態管理</summary>

```clojure
(def user-agent
  (agent {:state :idle :peer nil}))

(defmulti process-event
  (fn [state event] [(:state state) event]))

(defmethod process-event [:idle :dial] [state _]
  (assoc state :state :connecting))

(defmethod process-event [:connecting :connect] [state _]
  (assoc state :state :connected))

(defmethod process-event [:connected :hangup] [state _]
  (assoc state :state :idle :peer nil))

;; 非同期に状態更新
(send user-agent process-event :dial)
(await user-agent)
```

Agent は同一エージェント内の更新を**順序保証**します。複数エージェント間の調整には `ref` + STM を使います。

</details>

<details>
<summary>Scala: AtomicReference + CAS</summary>

```scala
case class UserState(
  userId: String,
  state: PhoneState = PhoneState.Idle,
  peer: Option[String] = None
)

class UserAgent(userId: String):
  private val stateRef = new AtomicReference(UserState(userId))

  def processEvent(event: PhoneEvent): Boolean =
    var done = false
    while !done do
      val current = stateRef.get()
      findTransition(current.state, event) match
        case Some(Transition(_, nextState, action)) =>
          val newState = current.copy(state = nextState)
          if stateRef.compareAndSet(current, newState) then
            action.foreach(_.apply())
            done = true
        case None =>
          done = true
          return false
    true
```

CAS（Compare-And-Swap）はロックを使わず、衝突時にリトライする楽観的アプローチです。

</details>

<details>
<summary>Elixir: GenServer（OTP）</summary>

```elixir
defmodule StateMachine do
  use GenServer

  def init(config) do
    {:ok, %{state: :idle, transitions: config.transitions, log: []}}
  end

  def handle_call({:trigger, event}, _from, machine) do
    case get_transition(machine, event) do
      {next_state, action} ->
        if action, do: action.()
        updated = %{machine |
          state: next_state,
          log: [{machine.state, event, next_state} | machine.log]}
        {:reply, {:ok, next_state}, updated}
      nil ->
        {:reply, {:error, :invalid_transition}, machine}
    end
  end
end
```

GenServer は OTP のスーパービジョンツリーと統合され、プロセスがクラッシュしても**自動復旧**します。

</details>

<details>
<summary>F#: MailboxProcessor</summary>

```fsharp
type AgentMessage<'TEvent> =
    | SendEvent of 'TEvent
    | GetState of AsyncReplyChannel<AgentState>

let createAgent initialState transitions =
    MailboxProcessor.Start(fun inbox ->
        let rec loop state = async {
            let! msg = inbox.Receive()
            match msg with
            | SendEvent event ->
                match findTransition state.State event transitions with
                | Some(nextState, action) ->
                    action |> Option.iter (fun a -> a())
                    return! loop { state with State = nextState }
                | None ->
                    return! loop state
            | GetState channel ->
                channel.Reply(state)
                return! loop state
        }
        loop { State = Idle; Peer = None })
```

MailboxProcessor は F# の軽量アクターで、`async` ワークフロー内でメッセージを処理します。

</details>

<details>
<summary>Haskell: STM（Software Transactional Memory）</summary>

```haskell
data UserAgentState = UserAgentState
    { uasState :: CallState
    , uasPeer  :: Maybe String
    } deriving (Show)

newtype UserAgent = UserAgent { uaState :: TVar UserAgentState }

makeUserAgent :: STM UserAgent
makeUserAgent = do
    state <- newTVar (UserAgentState Idle Nothing)
    return (UserAgent state)

processEvent :: UserAgent -> Event -> STM (Maybe Action)
processEvent ua event = do
    current <- readTVar (uaState ua)
    case findTransition (uasState current) event of
        Just (nextState, action) -> do
            writeTVar (uaState ua) current { uasState = nextState }
            return (Just action)
        Nothing -> return Nothing

-- トランザクション内で原子的に実行
atomically $ processEvent agent DialEvent
```

STM は**自動的に競合を検出**し、衝突した場合はトランザクションを自動リトライします。デッドロックが原理的に発生しません。

</details>

<details>
<summary>Rust: Arc + Mutex + Tokio</summary>

```rust
pub struct StateMachine {
    state: Arc<Mutex<PhoneState>>,
    transitions: HashMap<(PhoneState, PhoneEvent), (PhoneState, Option<Action>)>,
}

impl StateMachine {
    pub fn process_event(&self, event: PhoneEvent) -> Result<PhoneState, String> {
        let mut state = self.state.lock().map_err(|e| e.to_string())?;
        let key = (*state, event);
        match self.transitions.get(&key) {
            Some((next_state, action)) => {
                if let Some(action) = action {
                    action();
                }
                *state = *next_state;
                Ok(*next_state)
            }
            None => Err("Invalid transition".to_string()),
        }
    }
}
```

Rust の `Arc<Mutex<T>>` は所有権システムと組み合わさり、**コンパイル時にデータ競合を完全に排除**します。

</details>

### 3.3 イベントバスの実装

| 言語 | 実装方式 | 購読者管理 |
|------|---------|----------|
| Clojure | atom + マップ | ハンドラ関数のリスト |
| Scala | TrieMap（並行マップ） | リスナーのリスト |
| Elixir | GenServer | PID のリスト |
| F# | MailboxProcessor | コールバック関数のリスト |
| Haskell | TVar + Map | ハンドラ関数のリスト |
| Rust | Arc<Mutex<HashMap>> | Box<dyn Fn> のリスト |

## 4. 比較分析

### 4.1 安全性の保証レベル

| 保証 | Clojure | Scala | Elixir | F# | Haskell | Rust |
|------|---------|-------|--------|-----|---------|------|
| データ競合防止 | ランタイム | ロックフリー | プロセス分離 | 型 + async | STM | コンパイル時 |
| デッドロック防止 | Agent 設計 | 注意が必要 | プロセス分離 | 注意が必要 | STM で保証 | 注意が必要 |
| 障害復旧 | なし | なし | スーパービジョン | なし | なし | なし |

### 4.2 並行処理モデルのトレードオフ

| モデル | 利点 | 欠点 | 代表言語 |
|--------|------|------|---------|
| Agent | シンプル、順序保証 | エージェント間の調整が難しい | Clojure |
| CAS | ロックフリー、高性能 | 衝突が多いとリトライコスト | Scala |
| Actor (OTP) | 障害復旧、スケーラビリティ | メッセージの順序管理が複雑 | Elixir |
| MailboxProcessor | 軽量、async 統合 | .NET エコシステムに依存 | F# |
| STM | 合成可能、デッドロック回避 | パフォーマンスオーバーヘッド | Haskell |
| Mutex + Async | 最高の安全性（コンパイル時） | 所有権の複雑さ | Rust |

### 4.3 不変性と並行処理の関係

不変データ構造は並行処理の安全性に直接貢献します：

- **共有しても安全**: データが変更されないため、ロック不要で共有可能
- **スナップショット**: いつでも一貫した状態のコピーを取得可能
- **再現性**: 同じ入力に対して常に同じ結果

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 分散システム | Elixir | OTP のスーパービジョンと障害復旧 |
| デッドロック回避 | Haskell | STM による自動的な競合解決 |
| コンパイル時安全性 | Rust | 所有権によるデータ競合の完全排除 |
| シンプルな並行処理 | Clojure | Agent の直感的な API |
| 高パフォーマンス | Scala, Rust | CAS / ゼロコスト抽象化 |
| .NET エコシステム | F# | MailboxProcessor + async |

## 6. まとめ

並行処理は各言語のパラダイムが最も鮮明に現れる領域です：

1. **不変性が基盤**: すべての言語が不変データ構造で並行処理の安全性を確保
2. **モデルの多様性**: Agent / Actor / STM / Mutex と根本的に異なるアプローチ
3. **安全性のレベル**: ランタイム保証（Clojure）からコンパイル時保証（Rust）まで

## 言語別個別記事

- [Clojure](../clojure/18-concurrency-system.md) | [Scala](../scala/18-concurrency-system.md) | [Elixir](../elixir/18-concurrency-system.md) | [F#](../fsharp/18-concurrency-system.md) | [Haskell](../haskell/18-concurrency-system.md) | [Rust](../rust/18-concurrency-system.md)
