---
type: Article
title: "第18章 並行処理システム - 状態機械パターン"
description: "この章では、Software Transactional Memory (STM) を使用した並行処理システムを実装します。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第18章 並行処理システム - 状態機械パターン

## 概要

この章では、Software Transactional Memory (STM) を使用した並行処理システムを実装します。電話呼び出しの状態機械をモデル化し、複数のユーザーエージェントが安全に状態を管理できるシステムを構築します。

## 学習目標

- STM による安全な並行状態管理
- 状態機械パターンの関数型実装
- トランザクション的な状態遷移
- 純粋関数と副作用の分離

## 状態機械の基礎

### 状態の定義

```haskell
-- | 通話状態
data CallState
  = Idle                    -- 待機中
  | Calling                 -- 発信中
  | Dialing                 -- ダイヤル中
  | WaitingForConnection    -- 接続待ち
  | Talking                 -- 通話中
  deriving (Show, Eq, Ord)
```

### イベントの定義

```haskell
-- | 状態遷移を引き起こすイベント
data Event
  = Call        -- 発信
  | Ring        -- 着信
  | Dialtone    -- ダイヤルトーン
  | Ringback    -- 呼び出し音
  | Connected   -- 接続完了
  | Disconnect  -- 切断
  deriving (Show, Eq, Ord)
```

### アクションの定義

```haskell
-- | 遷移時に実行するアクション
data Action
  = CallerOffHook   -- 発信者が受話器を取る
  | CalleeOffHook   -- 着信者が受話器を取る
  | Dial            -- ダイヤル
  | Talk            -- 通話開始
  | NoAction        -- アクションなし
  deriving (Show, Eq)
```

## 状態機械の実装

### 遷移の定義

```haskell
-- | 状態遷移
data Transition = Transition
  { transNextState :: CallState  -- 次の状態
  , transAction :: Action        -- 実行するアクション
  } deriving (Show, Eq)

-- | 状態機械: (状態, イベント) → 遷移
type StateMachine = Map (CallState, Event) Transition
```

### 電話状態機械

```haskell
phoneStateMachine :: StateMachine
phoneStateMachine = Map.fromList
  [ ((Idle, Call), Transition Calling CallerOffHook)
  , ((Idle, Ring), Transition WaitingForConnection CalleeOffHook)
  , ((Idle, Disconnect), Transition Idle NoAction)
  , ((Calling, Dialtone), Transition Dialing Dial)
  , ((Dialing, Ringback), Transition WaitingForConnection NoAction)
  , ((WaitingForConnection, Connected), Transition Talking Talk)
  , ((Talking, Disconnect), Transition Idle NoAction)
  ]
```

### 純粋な遷移関数

```haskell
-- | 遷移の取得
getTransition :: StateMachine -> CallState -> Event -> Maybe Transition
getTransition sm state event = Map.lookup (state, event) sm

-- | 純粋な状態遷移
transition :: StateMachine -> CallState -> Event -> Maybe CallState
transition sm state event = transNextState <$> getTransition sm state event
```

## STM によるユーザーエージェント

### ユーザーエージェントの状態

```haskell
-- | ユーザーエージェントの内部状態
data UserAgentState = UserAgentState
  { uasUserId :: String       -- ユーザーID
  , uasState :: CallState     -- 現在の通話状態
  , uasPeer :: Maybe String   -- 通話相手
  } deriving (Show, Eq)

-- | STM ベースのユーザーエージェント
data UserAgent = UserAgent
  { uaState :: TVar UserAgentState  -- STM変数
  , uaMachine :: StateMachine       -- 状態機械
  }
```

### エージェントの作成

```haskell
-- | 新しいユーザーエージェントを作成
makeUserAgent :: String -> STM UserAgent
makeUserAgent userId = do
  state <- newTVar UserAgentState
    { uasUserId = userId
    , uasState = Idle
    , uasPeer = Nothing
    }
  return UserAgent
    { uaState = state
    , uaMachine = phoneStateMachine
    }
```

### 状態の読み取り

```haskell
-- | 現在の状態を取得
getUserState :: UserAgent -> STM CallState
getUserState ua = uasState <$> readTVar (uaState ua)

-- | ユーザーIDを取得
getUserId :: UserAgent -> STM String
getUserId ua = uasUserId <$> readTVar (uaState ua)

-- | 通話相手を取得
getPeer :: UserAgent -> STM (Maybe String)
getPeer ua = uasPeer <$> readTVar (uaState ua)
```

## 状態遷移の処理

### トランザクション的な遷移

```haskell
-- | 遷移を処理してアクションを返す
processTransition :: UserAgent -> Event -> Maybe String -> STM (Maybe Action)
processTransition ua event peer = do
  currentState <- readTVar (uaState ua)
  case getTransition (uaMachine ua) (uasState currentState) event of
    Nothing -> return Nothing
    Just trans -> do
      writeTVar (uaState ua) currentState
        { uasState = transNextState trans
        , uasPeer = peer <|> uasPeer currentState
        }
      return (Just (transAction trans))

-- | イベントを送信
sendEvent :: UserAgent -> Event -> STM (Maybe Action)
sendEvent ua event = processTransition ua event Nothing

-- | 相手情報付きでイベントを送信
sendEventWithPeer :: UserAgent -> Event -> String -> STM (Maybe Action)
sendEventWithPeer ua event peer = processTransition ua event (Just peer)
```

## 使用例

### 発信者のフロー

```haskell
-- 発信者のフローをシミュレート
callerFlow :: IO ()
callerFlow = atomically $ do
  alice <- makeUserAgent "alice"
  
  -- 発信
  _ <- sendEventWithPeer alice Call "bob"
  s1 <- getUserState alice  -- Calling
  
  -- ダイヤルトーン受信
  _ <- sendEvent alice Dialtone
  s2 <- getUserState alice  -- Dialing
  
  -- 呼び出し音受信
  _ <- sendEvent alice Ringback
  s3 <- getUserState alice  -- WaitingForConnection
  
  -- 接続完了
  _ <- sendEvent alice Connected
  s4 <- getUserState alice  -- Talking
  
  -- 切断
  _ <- sendEvent alice Disconnect
  s5 <- getUserState alice  -- Idle
  
  return ()
```

### 着信者のフロー

```haskell
-- 着信者のフローをシミュレート
calleeFlow :: IO ()
calleeFlow = atomically $ do
  bob <- makeUserAgent "bob"
  
  -- 着信
  _ <- sendEventWithPeer bob Ring "alice"
  s1 <- getUserState bob  -- WaitingForConnection
  
  -- 接続完了
  _ <- sendEvent bob Connected
  s2 <- getUserState bob  -- Talking
  
  -- 切断
  _ <- sendEvent bob Disconnect
  s3 <- getUserState bob  -- Idle
  
  return ()
```

### 複数エージェントの独立動作

```haskell
-- 複数のエージェントが独立して動作
multipleAgents :: IO ()
multipleAgents = do
  (aliceState, bobState) <- atomically $ do
    alice <- makeUserAgent "alice"
    bob <- makeUserAgent "bob"
    
    -- それぞれ独立して状態遷移
    _ <- sendEvent alice Call
    _ <- sendEvent bob Ring
    
    as <- getUserState alice
    bs <- getUserState bob
    return (as, bs)
  
  print aliceState  -- Calling
  print bobState    -- WaitingForConnection
```

## アクションの実行

```haskell
-- | アクションの説明を生成
executeAction :: Action -> String -> Maybe String -> String
executeAction action userId peer = case action of
  CallerOffHook -> userId ++ " picked up the phone to call " ++ peerStr
  CalleeOffHook -> userId ++ " answered the phone from " ++ peerStr
  Dial -> userId ++ " is dialing " ++ peerStr
  Talk -> userId ++ " is talking to " ++ peerStr
  NoAction -> ""
  where
    peerStr = maybe "unknown" id peer
```

## STM の利点

### 1. 原子性

```haskell
-- 複数の状態更新が原子的に実行される
atomicUpdate :: IO ()
atomicUpdate = atomically $ do
  ua <- makeUserAgent "test"
  -- この2つの操作は一緒に成功または失敗
  _ <- sendEvent ua Call
  _ <- sendEvent ua Dialtone
  return ()
```

### 2. 競合の自動解決

STM は競合を検出し、トランザクションを自動的に再試行します。

### 3. デッドロックの回避

ロックベースの並行処理と異なり、STM はデッドロックを回避します。

### 4. 合成可能性

```haskell
-- 複数のSTM操作を合成できる
composedTransaction :: STM ()
composedTransaction = do
  alice <- makeUserAgent "alice"
  bob <- makeUserAgent "bob"
  _ <- sendEvent alice Call
  _ <- sendEvent bob Ring
  return ()
```

## テスト

```haskell
spec :: Spec
spec = do
  describe "Pure State Machine" $ do
    it "transitions from Idle to Calling on Call event" $ do
      transition phoneStateMachine Idle Call `shouldBe` Just Calling

    it "returns Nothing for invalid transitions" $ do
      transition phoneStateMachine Idle Dialtone `shouldBe` Nothing

  describe "UserAgent (STM)" $ do
    it "creates agent in Idle state" $ do
      state <- atomically $ do
        ua <- makeUserAgent "alice"
        getUserState ua
      state `shouldBe` Idle

    it "transitions through full caller flow" $ do
      states <- atomically $ do
        ua <- makeUserAgent "alice"
        _ <- sendEvent ua Call
        s1 <- getUserState ua
        _ <- sendEvent ua Dialtone
        s2 <- getUserState ua
        _ <- sendEvent ua Ringback
        s3 <- getUserState ua
        _ <- sendEvent ua Connected
        s4 <- getUserState ua
        return [s1, s2, s3, s4]
      states `shouldBe` [Calling, Dialing, WaitingForConnection, Talking]
```

## 設計のポイント

### 純粋関数と副作用の分離

- 状態機械の定義は純粋なデータ
- 遷移関数は純粋（`transition`）
- STM操作のみが副作用を持つ

### 型安全性

- 代数的データ型で状態とイベントを表現
- 不正な状態遷移は型レベルで防止

### 合成可能性

- 小さなSTM操作を組み合わせて大きな操作を構築
- トランザクションの境界を柔軟に設定

## まとめ

- 状態機械は関数型プログラミングで自然に表現できる
- STM は安全な並行状態管理を提供する
- 純粋関数と副作用を分離することで、テストと推論が容易になる
- 代数的データ型により、状態空間を明確に定義できる
