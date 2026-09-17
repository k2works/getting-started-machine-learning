---
type: Article
title: "第15章: ゴシップ好きなバスの運転手"
description: "本章では、「ゴシップ好きなバスの運転手」という問題を通じて、関数型プログラミングの実践的なアプローチを学びます。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 第15章: ゴシップ好きなバスの運転手

## はじめに

本章では、「ゴシップ好きなバスの運転手」という問題を通じて、関数型プログラミングの実践的なアプローチを学びます。この問題は、バス運転手が停留所で出会ったときに噂を共有するというシミュレーションです。

この問題を通じて以下の概念を学びます：

- イミュータブルなデータモデル設計
- 循環データ（ルート）の表現
- 集合演算による状態の伝播
- 再帰的なシミュレーションループ

## 1. 問題の説明

複数のバス運転手がそれぞれのルートを巡回しています。各運転手は最初に1つ以上の噂を知っています。同じ停留所に複数の運転手がいると、彼らは知っている噂をすべて共有します。

**ゴール**: 全ての運転手が全ての噂を知るまでに何分かかるか？

```plantuml
@startuml
title ゴシップの伝播

actor "Driver 1\n(噂A)" as D1
actor "Driver 2\n(噂B)" as D2
node "Stop 1" as S1

D1 --> S1 : 到着
D2 --> S1 : 到着
S1 --> D1 : 噂を共有
S1 --> D2 : 噂を共有

note right of S1
  結果: 両者とも {噂A, 噂B} を取得
end note
@enduml
```

## 2. データモデル

### Driver ケースクラス

```scala
type Rumor = String
type Stop = Int

case class Driver(
  name: String,
  route: Vector[Stop],
  position: Int,
  rumors: Set[Rumor]
):
  /**
   * 現在の停留所を取得（循環）
   */
  def currentStop: Stop = route(position % route.length)

  /**
   * 次の停留所に移動
   */
  def move: Driver = copy(position = position + 1)

  /**
   * 噂を追加
   */
  def addRumors(newRumors: Set[Rumor]): Driver = 
    copy(rumors = rumors ++ newRumors)

object Driver:
  def apply(name: String, route: Seq[Stop], rumors: Set[Rumor]): Driver =
    Driver(name, route.toVector, 0, rumors)

  def withAutoRumor(name: String, route: Seq[Stop]): Driver =
    Driver(name, route.toVector, 0, Set(s"rumor-$name"))
```

ポイント：


- `position % route.length` で循環ルートを実現
- 噂は `Set` で管理し、重複を自動排除
- すべての状態変更は新しいインスタンスを返す（イミュータブル）

### World ケースクラス

```scala
case class World(drivers: Vector[Driver], time: Int = 0):

  /**
   * 全ドライバーを移動
   */
  def moveDrivers: World = copy(drivers = drivers.map(_.move))

  /**
   * 各停留所にいるドライバーをグループ化
   */
  def driversByStop: Map[Stop, Vector[Driver]] =
    drivers.groupBy(_.currentStop)

  /**
   * 同じ停留所にいるドライバー間で噂を共有
   */
  def spreadRumors: World =
    val newDrivers = driversByStop.values.flatMap { driversAtStop =>
      val allRumors = driversAtStop.flatMap(_.rumors).toSet
      driversAtStop.map(_.addRumors(allRumors))
    }.toVector
    copy(drivers = drivers.map(d => newDrivers.find(_.name == d.name).getOrElse(d)))

  /**
   * 1ステップ実行（移動→噂の伝播）
   */
  def step: World =
    this.moveDrivers.spreadRumors.copy(time = time + 1)

  /**
   * 全ドライバーが同じ噂を持っているか確認
   */
  def allRumorsShared: Boolean =
    if drivers.isEmpty then true
    else drivers.map(_.rumors).distinct.length == 1
```

## 3. シミュレーション

### シミュレーション結果の型

```scala
sealed trait SimulationResult
case class Completed(minutes: Int) extends SimulationResult
case object Never extends SimulationResult
```

### シミュレーション実行

```scala
def simulate(world: World, maxMinutes: Int = 480): SimulationResult =
  @annotation.tailrec
  def loop(current: World): SimulationResult =
    if current.time > maxMinutes then Never
    else if current.allRumorsShared then Completed(current.time)
    else loop(current.step)

  if world.drivers.length <= 1 then Completed(0)
  else if world.allRumorsShared then Completed(0)
  else loop(world.step)
```

### 使用例

```scala
// 3人のドライバーを作成
val world = World(
  Driver("D1", Seq(3, 1, 2, 3), Set("rumor-a")),
  Driver("D2", Seq(3, 2, 3, 1), Set("rumor-b")),
  Driver("D3", Seq(4, 2, 3, 4, 5), Set("rumor-c"))
)

// シミュレーション実行
simulate(world) // => Completed(n) - n分で全噂が共有される

// 絶対に出会わないルートの場合
val neverMeet = World(
  Driver("D1", Seq(1), Set("rumor-a")),
  Driver("D2", Seq(2), Set("rumor-b"))
)

simulate(neverMeet, maxMinutes = 10) // => Never
```

## 4. 履歴と統計情報

### 履歴付きシミュレーション

```scala
def simulateWithHistory(world: World, maxMinutes: Int = 480): (SimulationResult, List[World]) =
  @annotation.tailrec
  def loop(current: World, history: List[World]): (SimulationResult, List[World]) =
    if current.time > maxMinutes then (Never, history.reverse)
    else if current.allRumorsShared then (Completed(current.time), (current :: history).reverse)
    else loop(current.step, current :: history)

  if world.drivers.length <= 1 then (Completed(0), List(world))
  else loop(world.step, List(world))
```

### 統計情報

```scala
case class SimulationStats(
  totalMinutes: Int,
  totalMeetings: Int,
  meetingsByStop: Map[Stop, Int],
  rumorSpreadTimeline: List[(Int, Int)]  // (時間, 噂の共有数)
)

def simulateWithStats(world: World, maxMinutes: Int = 480): (SimulationResult, SimulationStats)
```

## 5. ビルダーパターン

```scala
class SimulationBuilder:
  private var drivers = Vector.empty[Driver]
  private var maxMinutes = 480

  def addDriver(name: String, route: Seq[Stop], rumors: Set[Rumor]): SimulationBuilder =
    drivers = drivers :+ Driver(name, route, rumors)
    this

  def addDriverWithAutoRumor(name: String, route: Seq[Stop]): SimulationBuilder =
    drivers = drivers :+ Driver.withAutoRumor(name, route)
    this

  def withMaxMinutes(minutes: Int): SimulationBuilder =
    maxMinutes = minutes
    this

  def run: SimulationResult = simulate(World(drivers), maxMinutes)
  def runWithHistory: (SimulationResult, List[World]) = simulateWithHistory(World(drivers), maxMinutes)
  def runWithStats: (SimulationResult, SimulationStats) = simulateWithStats(World(drivers), maxMinutes)

// 使用例
val result = builder
  .addDriverWithAutoRumor("D1", Seq(1, 2, 3))
  .addDriverWithAutoRumor("D2", Seq(3, 2, 1))
  .withMaxMinutes(100)
  .run
```

## 6. ルート定義 DSL

```scala
object RouteDSL:
  def range(start: Int, end: Int): Seq[Stop] = start to end
  def reverse(route: Seq[Stop]): Seq[Stop] = route.reverse
  def roundTrip(route: Seq[Stop]): Seq[Stop] =
    if route.length <= 1 then route
    else route ++ route.reverse.tail
  def concat(segments: Seq[Stop]*): Seq[Stop] = segments.flatten

// 使用例
import RouteDSL.*
range(1, 5)           // Seq(1, 2, 3, 4, 5)
roundTrip(Seq(1,2,3)) // Seq(1, 2, 3, 2, 1)
```

## 7. 関数型コンビネータ

```scala
type WorldTransform = World => World

def compose(transforms: WorldTransform*): WorldTransform =
  transforms.reduce(_ andThen _)

def when(condition: World => Boolean)(transform: WorldTransform): WorldTransform =
  world => if condition(world) then transform(world) else world

def repeat(n: Int)(transform: WorldTransform): WorldTransform =
  world => (1 to n).foldLeft(world)((w, _) => transform(w))

def until(condition: World => Boolean)(transform: WorldTransform): World => World =
  world =>
    @annotation.tailrec
    def loop(current: World): World =
      if condition(current) then current
      else loop(transform(current))
    loop(world)

// 使用例
val doubleMove = compose(_.moveDrivers, _.moveDrivers)
val moveIfAt1 = when(_.drivers.head.currentStop == 1)(_.moveDrivers)
val moveThrice = repeat(3)(_.moveDrivers)
val untilShared = until(_.allRumorsShared)(_.step)
```

## 8. 遅延付きシミュレーション

実際のバス運転では、各停留所で乗客の乗降に時間がかかります。

```scala
case class DelayedDriver(
  name: String,
  route: Vector[Stop],
  position: Int,
  rumors: Set[Rumor],
  delay: Int = 0,
  delayPattern: Vector[Int] = Vector.empty
):
  def currentStop: Stop = route(position % route.length)

  def move: DelayedDriver =
    if delay > 0 then
      copy(delay = delay - 1)
    else
      val nextPos = position + 1
      val nextDelay = if delayPattern.nonEmpty then
        delayPattern(nextPos % delayPattern.length)
      else 0
      copy(position = nextPos, delay = nextDelay)
```

## 9. ヘルパー関数

```scala
/**
 * ドライバーが特定の時間後にどの停留所にいるかを計算
 */
def stopAtTime(driver: Driver, time: Int): Stop =
  driver.route((driver.position + time) % driver.route.length)

/**
 * 2人のドライバーが出会う最初の時間を計算
 */
def firstMeetingTime(d1: Driver, d2: Driver, maxTime: Int = 480): Option[Int] =
  (0 to maxTime).find { t =>
    stopAtTime(d1, t) == stopAtTime(d2, t)
  }

/**
 * ルートの最小公倍数を計算
 */
def routeLcm(routes: Seq[Vector[Stop]]): Int =
  def gcd(a: Int, b: Int): Int = if b == 0 then a else gcd(b, a % b)
  def lcm(a: Int, b: Int): Int = a * b / gcd(a, b)
  routes.map(_.length).reduce(lcm)
```

## 10. 関数型アプローチの特徴

### 不変データ構造

すべての状態変更は新しいデータ構造を返します。

```scala
val original = Driver("Test", Seq(1, 2, 3), Set("a"))
val moved = original.move

original.currentStop // 1 (変更なし)
moved.currentStop    // 2
```

### 純粋関数

すべての関数は副作用がなく、同じ入力に対して常に同じ出力を返します。

### 集合演算

噂の伝播は集合の和集合として表現されます。

```scala
val rumors1 = Set("a", "b")
val rumors2 = Set("c")
val merged = rumors1 ++ rumors2  // Set("a", "b", "c")
```

## 11. Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| 循環データ | `cycle` (遅延シーケンス) | `position % route.length` |
| 集合演算 | `set/union` | `++` |
| グループ化 | `group-by` | `groupBy` |
| 末尾再帰 | `recur` | `@annotation.tailrec` |
| パイプライン | `->` マクロ | メソッドチェーン |

## 12. パターンの応用

このパターンは以下のような場面で応用できます：

- **ネットワーク伝播**: ウイルスや情報の拡散シミュレーション
- **グラフ探索**: 巡回セールスマン問題の変形
- **状態同期**: 分散システムでの状態共有
- **ゲームAI**: NPCの移動と相互作用

## まとめ

本章では、ゴシップ好きなバスの運転手問題を通じて以下を学びました：

1. **イミュータブルデータモデル**: `case class` によるドライバーとワールドの表現
2. **循環ルート**: 剰余演算による無限循環の実現
3. **集合演算**: 噂の伝播を集合の和集合で表現
4. **末尾再帰**: スタックオーバーフローを防ぐシミュレーションループ
5. **関数型コンビネータ**: 変換の合成と条件付き適用

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part6/src/main/scala/GossipingBusDrivers.scala`
- テストコード: `apps/scala/part6/src/test/scala/GossipingBusDriversSpec.scala`
