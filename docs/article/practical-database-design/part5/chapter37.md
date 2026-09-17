---
type: Article
title: "第37章：イベント駆動アーキテクチャ"
description: "本章では、モダンなシステム統合の基盤となるイベント駆動アーキテクチャについて解説します。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第37章：イベント駆動アーキテクチャ

本章では、モダンなシステム統合の基盤となるイベント駆動アーキテクチャについて解説します。ドメインイベント、イベントソーシング、CQRS といった概念を理解し、基幹業務システムへの適用方法を学びます。

---

## 37.1 イベント駆動の基礎

### イベント駆動アーキテクチャとは

イベント駆動アーキテクチャ（Event-Driven Architecture: EDA）は、システム間の通信をイベントの発行と購読によって行うアーキテクチャスタイルです。

```plantuml
@startuml
title イベント駆動アーキテクチャの基本構造

rectangle "イベントプロデューサー" as producer {
    rectangle "販売管理" as sales
    rectangle "生産管理" as production
}

rectangle "イベントブローカー" as broker {
    collections "イベントバス" as bus
    database "イベントストア" as store
}

rectangle "イベントコンシューマー" as consumer {
    rectangle "財務会計" as accounting
    rectangle "分析サービス" as analytics
    rectangle "通知サービス" as notification
}

sales --> bus : イベント発行
production --> bus : イベント発行

bus --> store : 永続化
bus --> accounting : イベント配信
bus --> analytics : イベント配信
bus --> notification : イベント配信

note bottom of broker
  【イベント駆動の特徴】
  ・疎結合：プロデューサーとコンシューマーが独立
  ・非同期：リアルタイム性と可用性の両立
  ・スケーラビリティ：コンシューマーの追加が容易
  ・監査性：イベントの永続化による追跡可能性
end note

@enduml
```

### ドメインイベントとは

ドメインイベントは、ビジネスドメインで発生した重要な出来事を表現するオブジェクトです。「過去に起きたこと」を記録するため、常に過去形で命名します。

```plantuml
@startuml
title ドメインイベントの特徴

class "ドメインイベント" as event {
    +eventId: String
    +occurredAt: Instant
    +aggregateId: String
    +aggregateType: String
    +payload: Object
    --
    不変（Immutable）
    過去形で命名
    ビジネス上の意味を持つ
}

note right of event
  【命名規則】
  ・OrderPlaced（受注された）
  ・ShipmentCompleted（出荷完了した）
  ・PaymentReceived（入金された）
  ・ProductManufactured（製造された）

  【含めるべき情報】
  ・いつ発生したか（タイムスタンプ）
  ・何が発生したか（イベント種別）
  ・どの集約で発生したか（集約ID）
  ・詳細情報（ペイロード）
end note

@enduml
```

#### ドメインイベントの構造

```plantuml
@startuml
title ドメインイベントの階層構造

interface "DomainEvent" as base {
    +getEventId(): String
    +getOccurredAt(): Instant
    +getAggregateId(): String
}

class "OrderPlacedEvent" as order_placed {
    +orderId: String
    +customerId: String
    +orderDate: LocalDate
    +totalAmount: Money
    +lines: List<OrderLine>
}

class "ShipmentCompletedEvent" as shipment_completed {
    +shipmentId: String
    +orderId: String
    +shippedDate: LocalDate
    +carrier: String
    +trackingNumber: String
}

class "PaymentReceivedEvent" as payment_received {
    +paymentId: String
    +invoiceId: String
    +receivedDate: LocalDate
    +amount: Money
    +paymentMethod: String
}

base <|.. order_placed
base <|.. shipment_completed
base <|.. payment_received

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// ドメインイベント基底インターフェース
public interface DomainEvent {
    String getEventId();
    Instant getOccurredAt();
    String getAggregateId();
    String getAggregateType();
}

// 抽象基底クラス
public abstract class AbstractDomainEvent implements DomainEvent {
    private final String eventId;
    private final Instant occurredAt;
    private final String aggregateId;
    private final String aggregateType;

    protected AbstractDomainEvent(String aggregateId, String aggregateType) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }

    // getters...
}

// 受注イベント
public record OrderPlacedEvent(
    String eventId,
    Instant occurredAt,
    String orderId,
    String customerId,
    LocalDate orderDate,
    Money totalAmount,
    List<OrderLineEvent> lines
) implements DomainEvent {

    public OrderPlacedEvent(String orderId, String customerId,
            LocalDate orderDate, Money totalAmount, List<OrderLineEvent> lines) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            orderId,
            customerId,
            orderDate,
            totalAmount,
            lines
        );
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public String getAggregateType() {
        return "Order";
    }
}

// sealed interface による型安全なイベント定義
public sealed interface OrderEvent extends DomainEvent
    permits OrderPlacedEvent, OrderConfirmedEvent,
            OrderShippedEvent, OrderCancelledEvent {
}

public record OrderPlacedEvent(...) implements OrderEvent { }
public record OrderConfirmedEvent(...) implements OrderEvent { }
public record OrderShippedEvent(...) implements OrderEvent { }
public record OrderCancelledEvent(...) implements OrderEvent { }
```

</details>

### イベントソーシング

イベントソーシングは、エンティティの状態を「状態そのもの」ではなく「状態変化の履歴（イベント）」として保存するパターンです。

```plantuml
@startuml
title 従来のCRUDとイベントソーシングの比較

rectangle "従来のCRUD" as crud {
    database "現在の状態のみ\n----\n受注ID: ORD-001\nステータス: 出荷済\n金額: 100,000\n更新日時: 2024/01/20" as current_state
}

rectangle "イベントソーシング" as es {
    database "イベントの履歴\n----\n1: OrderPlaced (2024/01/15)\n2: OrderConfirmed (2024/01/16)\n3: PaymentReceived (2024/01/17)\n4: ShipmentStarted (2024/01/18)\n5: ShipmentCompleted (2024/01/20)" as event_history
}

note bottom of crud
  【CRUD方式】
  ・最新状態のみ保持
  ・過去の経緯は不明
  ・監査が困難
end note

note bottom of es
  【イベントソーシング】
  ・すべての変化を記録
  ・任意の時点の状態を再現可能
  ・完全な監査証跡
end note

@enduml
```

#### イベントソーシングの仕組み

```plantuml
@startuml
title イベントソーシングによる状態再構築

|コマンド処理|
start
:コマンド受信;
note right
  PlaceOrderCommand
  ConfirmOrderCommand
  etc.
end note

:現在の状態を取得;

|イベントストア|
:イベント履歴を読み込み;

|集約|
:イベントを順次適用;
note right
  event1 → state1
  event2 → state2
  event3 → state3（現在）
end note

:ビジネスルール検証;

if (検証OK?) then (yes)
    :新しいイベント生成;

    |イベントストア|
    :イベントを追記;
    note right
      Append-Only
      更新・削除なし
    end note

    |イベントバス|
    :イベントを発行;
else (no)
    :エラー返却;
endif

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// イベントソース集約の基底クラス
public abstract class EventSourcedAggregate<ID> {
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private int version = 0;

    // イベントから状態を再構築
    public void replayEvents(List<DomainEvent> events) {
        events.forEach(event -> {
            apply(event);
            version++;
        });
    }

    // 新しいイベントを適用
    protected void applyChange(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    // イベントハンドラー（サブクラスで実装）
    protected abstract void apply(DomainEvent event);

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    public int getVersion() {
        return version;
    }
}

// 受注集約
public class Order extends EventSourcedAggregate<OrderId> {
    private OrderId orderId;
    private CustomerId customerId;
    private OrderStatus status;
    private Money totalAmount;
    private List<OrderLine> lines;

    // コマンドハンドラー
    public void place(PlaceOrderCommand command) {
        // ビジネスルール検証
        if (status != null) {
            throw new IllegalStateException("Order already exists");
        }

        // イベント生成と適用
        applyChange(new OrderPlacedEvent(
            command.orderId(),
            command.customerId(),
            command.orderDate(),
            command.totalAmount(),
            command.lines()
        ));
    }

    public void confirm() {
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException("Order cannot be confirmed");
        }

        applyChange(new OrderConfirmedEvent(orderId.value(), Instant.now()));
    }

    // イベントハンドラー
    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case OrderPlacedEvent e -> {
                this.orderId = new OrderId(e.orderId());
                this.customerId = new CustomerId(e.customerId());
                this.status = OrderStatus.PLACED;
                this.totalAmount = e.totalAmount();
                this.lines = e.lines().stream()
                    .map(OrderLine::from)
                    .toList();
            }
            case OrderConfirmedEvent e -> {
                this.status = OrderStatus.CONFIRMED;
            }
            case OrderShippedEvent e -> {
                this.status = OrderStatus.SHIPPED;
            }
            default -> throw new IllegalArgumentException(
                "Unknown event: " + event.getClass()
            );
        }
    }
}

// リポジトリ
@Repository
public class EventSourcedOrderRepository {
    private final EventStore eventStore;

    public Order findById(OrderId orderId) {
        List<DomainEvent> events = eventStore.getEvents(
            orderId.value(),
            "Order"
        );

        if (events.isEmpty()) {
            return null;
        }

        Order order = new Order();
        order.replayEvents(events);
        return order;
    }

    public void save(Order order) {
        List<DomainEvent> events = order.getUncommittedEvents();

        eventStore.appendEvents(
            order.getOrderId().value(),
            "Order",
            order.getVersion(),
            events
        );

        order.markEventsAsCommitted();
    }
}
```

</details>

### CQRS（コマンドクエリ責務分離）

CQRS（Command Query Responsibility Segregation）は、データの書き込み（コマンド）と読み取り（クエリ）を分離するアーキテクチャパターンです。

```plantuml
@startuml
title CQRS アーキテクチャ

rectangle "クライアント" as client

rectangle "コマンドサイド" as command_side {
    rectangle "コマンドハンドラー" as cmd_handler
    rectangle "ドメインモデル" as domain
    database "イベントストア" as event_store
}

rectangle "クエリサイド" as query_side {
    rectangle "クエリハンドラー" as query_handler
    database "リードモデル" as read_model
}

rectangle "プロジェクション" as projection {
    rectangle "イベントハンドラー" as event_handler
}

client --> cmd_handler : コマンド
cmd_handler --> domain : 処理
domain --> event_store : イベント保存

event_store --> event_handler : イベント配信
event_handler --> read_model : 更新

client --> query_handler : クエリ
query_handler --> read_model : 参照

note bottom of command_side
  【コマンドサイド】
  ・ビジネスロジック
  ・整合性の保証
  ・イベントソーシング
end note

note bottom of query_side
  【クエリサイド】
  ・読み取り最適化
  ・非正規化データ
  ・高速なレスポンス
end note

@enduml
```

#### CQRS の利点と適用場面

```plantuml
@startuml
title CQRS の利点

rectangle "スケーラビリティ" as scale {
    note right
      ・読み取りと書き込みを
        独立してスケール
      ・読み取り負荷が高い場合に
        リードモデルをスケールアウト
    end note
}

rectangle "パフォーマンス最適化" as perf {
    note right
      ・リードモデルを
        クエリに最適化
      ・非正規化による
        JOINの削減
    end note
}

rectangle "複雑性の分離" as separation {
    note right
      ・書き込みはドメインモデル
      ・読み取りはシンプルなDTO
      ・各々を独立して進化
    end note
}

rectangle "イベントソーシングとの親和性" as es {
    note right
      ・イベントから
        リードモデルを構築
      ・複数のビューを
        同じイベントから生成
    end note
}

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// コマンド
public sealed interface OrderCommand
    permits PlaceOrderCommand, ConfirmOrderCommand, ShipOrderCommand {
}

public record PlaceOrderCommand(
    String orderId,
    String customerId,
    LocalDate orderDate,
    List<OrderLineCommand> lines
) implements OrderCommand {}

// クエリ
public sealed interface OrderQuery
    permits GetOrderByIdQuery, GetOrdersByCustomerQuery,
            GetOrdersSummaryQuery {
}

public record GetOrderByIdQuery(String orderId) implements OrderQuery {}
public record GetOrdersByCustomerQuery(
    String customerId,
    LocalDate from,
    LocalDate to
) implements OrderQuery {}

// コマンドハンドラー
@Service
public class OrderCommandHandler {
    private final EventSourcedOrderRepository repository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void handle(PlaceOrderCommand command) {
        Order order = new Order();
        order.place(command);

        repository.save(order);

        // イベント発行
        order.getUncommittedEvents().forEach(eventPublisher::publish);
    }

    @Transactional
    public void handle(ConfirmOrderCommand command) {
        Order order = repository.findById(new OrderId(command.orderId()));
        if (order == null) {
            throw new OrderNotFoundException(command.orderId());
        }

        order.confirm();
        repository.save(order);

        order.getUncommittedEvents().forEach(eventPublisher::publish);
    }
}

// クエリハンドラー
@Service
public class OrderQueryHandler {
    private final OrderReadModelRepository readModelRepository;

    public OrderDetailView handle(GetOrderByIdQuery query) {
        return readModelRepository.findById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));
    }

    public List<OrderSummaryView> handle(GetOrdersByCustomerQuery query) {
        return readModelRepository.findByCustomerAndPeriod(
            query.customerId(),
            query.from(),
            query.to()
        );
    }
}

// リードモデル（非正規化されたビュー）
@Entity
@Table(name = "order_read_model")
public class OrderReadModel {
    @Id
    private String orderId;
    private String customerId;
    private String customerName;  // 非正規化
    private LocalDate orderDate;
    private String status;
    private BigDecimal totalAmount;
    private int lineCount;
    private LocalDateTime lastUpdated;
}

// プロジェクション（イベントからリードモデルを構築）
@Component
public class OrderProjection {
    private final OrderReadModelRepository repository;
    private final CustomerRepository customerRepository;

    @EventHandler
    public void on(OrderPlacedEvent event) {
        Customer customer = customerRepository.findById(event.customerId());

        OrderReadModel readModel = new OrderReadModel();
        readModel.setOrderId(event.orderId());
        readModel.setCustomerId(event.customerId());
        readModel.setCustomerName(customer.getName());  // 非正規化
        readModel.setOrderDate(event.orderDate());
        readModel.setStatus("PLACED");
        readModel.setTotalAmount(event.totalAmount().amount());
        readModel.setLineCount(event.lines().size());
        readModel.setLastUpdated(LocalDateTime.now());

        repository.save(readModel);
    }

    @EventHandler
    public void on(OrderConfirmedEvent event) {
        OrderReadModel readModel = repository.findById(event.orderId())
            .orElseThrow();
        readModel.setStatus("CONFIRMED");
        readModel.setLastUpdated(LocalDateTime.now());
        repository.save(readModel);
    }
}
```

</details>

---

## 37.2 基幹業務システムのイベント設計

### 販売管理イベント

販売管理システムで発生する主要なドメインイベントを定義します。

```plantuml
@startuml
title 販売管理ドメインイベント

package "受注イベント" as order_events {
    class OrderPlacedEvent {
        受注登録
    }
    class OrderConfirmedEvent {
        受注確定
    }
    class OrderCancelledEvent {
        受注取消
    }
    class OrderModifiedEvent {
        受注変更
    }
}

package "出荷イベント" as shipment_events {
    class ShipmentInstructedEvent {
        出荷指示
    }
    class ShipmentStartedEvent {
        出荷開始
    }
    class ShipmentCompletedEvent {
        出荷完了
    }
    class ShipmentCancelledEvent {
        出荷取消
    }
}

package "売上イベント" as sales_events {
    class SalesRecordedEvent {
        売上計上
    }
    class SalesReturnedEvent {
        売上返品
    }
}

package "債権イベント" as receivable_events {
    class InvoiceIssuedEvent {
        請求書発行
    }
    class PaymentReceivedEvent {
        入金確認
    }
    class PaymentAllocatedEvent {
        入金消込
    }
}

@enduml
```

#### 受注イベントの詳細設計

```plantuml
@startuml
title 受注ライフサイクルとイベント

[*] --> 登録済 : OrderPlacedEvent

登録済 --> 確定済 : OrderConfirmedEvent
登録済 --> 取消済 : OrderCancelledEvent

確定済 --> 出荷指示済 : ShipmentInstructedEvent
確定済 --> 変更済 : OrderModifiedEvent
確定済 --> 取消済 : OrderCancelledEvent

変更済 --> 確定済 : OrderConfirmedEvent
変更済 --> 取消済 : OrderCancelledEvent

出荷指示済 --> 出荷中 : ShipmentStartedEvent
出荷中 --> 出荷完了 : ShipmentCompletedEvent

出荷完了 --> 売上計上済 : SalesRecordedEvent

売上計上済 --> [*]
取消済 --> [*]

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 販売管理イベント定義
public sealed interface SalesEvent extends DomainEvent
    permits OrderEvent, ShipmentEvent, SalesTransactionEvent, ReceivableEvent {
}

// 受注イベント
public sealed interface OrderEvent extends SalesEvent
    permits OrderPlacedEvent, OrderConfirmedEvent,
            OrderModifiedEvent, OrderCancelledEvent {
}

public record OrderPlacedEvent(
    String eventId,
    Instant occurredAt,
    String orderId,
    String customerId,
    LocalDate orderDate,
    LocalDate requestedDeliveryDate,
    Money totalAmount,
    Money taxAmount,
    List<OrderLineEvent> lines
) implements OrderEvent {
    @Override
    public String getAggregateId() { return orderId; }
    @Override
    public String getAggregateType() { return "Order"; }
}

public record OrderConfirmedEvent(
    String eventId,
    Instant occurredAt,
    String orderId,
    String confirmedBy,
    LocalDate confirmedDate
) implements OrderEvent {
    @Override
    public String getAggregateId() { return orderId; }
    @Override
    public String getAggregateType() { return "Order"; }
}

// 出荷イベント
public sealed interface ShipmentEvent extends SalesEvent
    permits ShipmentInstructedEvent, ShipmentStartedEvent,
            ShipmentCompletedEvent, ShipmentCancelledEvent {
}

public record ShipmentCompletedEvent(
    String eventId,
    Instant occurredAt,
    String shipmentId,
    String orderId,
    LocalDate shippedDate,
    String carrier,
    String trackingNumber,
    List<ShipmentLineEvent> lines
) implements ShipmentEvent {
    @Override
    public String getAggregateId() { return shipmentId; }
    @Override
    public String getAggregateType() { return "Shipment"; }
}
```

</details>

### 財務会計イベント

財務会計システムで発生する主要なドメインイベントを定義します。

```plantuml
@startuml
title 財務会計ドメインイベント

package "仕訳イベント" as journal_events {
    class JournalEntryCreatedEvent {
        仕訳作成
    }
    class JournalEntryPostedEvent {
        仕訳転記
    }
    class JournalEntryCancelledEvent {
        仕訳取消
    }
    class JournalEntryReversedEvent {
        仕訳訂正（赤黒）
    }
}

package "残高イベント" as balance_events {
    class DailyBalanceUpdatedEvent {
        日次残高更新
    }
    class MonthlyBalanceClosedEvent {
        月次残高確定
    }
}

package "決算イベント" as closing_events {
    class MonthEndClosedEvent {
        月次締め完了
    }
    class YearEndClosedEvent {
        年次決算完了
    }
    class TrialBalanceGeneratedEvent {
        試算表生成
    }
}

@enduml
```

#### 仕訳ライフサイクル

```plantuml
@startuml
title 仕訳ライフサイクルとイベント

[*] --> 下書き : JournalEntryCreatedEvent

下書き --> 承認待ち : JournalEntrySubmittedEvent
下書き --> 取消済 : JournalEntryCancelledEvent

承認待ち --> 承認済 : JournalEntryApprovedEvent
承認待ち --> 差戻し : JournalEntryRejectedEvent

差戻し --> 下書き : JournalEntryRevisedEvent

承認済 --> 転記済 : JournalEntryPostedEvent

転記済 --> 訂正済 : JournalEntryReversedEvent
note right of 訂正済
  赤黒処理により
  新しい仕訳が生成される
end note

転記済 --> [*]
訂正済 --> [*]
取消済 --> [*]

@enduml
```

### 生産管理イベント

生産管理システムで発生する主要なドメインイベントを定義します。

```plantuml
@startuml
title 生産管理ドメインイベント

package "製造オーダイベント" as work_order_events {
    class WorkOrderCreatedEvent {
        製造指示作成
    }
    class WorkOrderReleasedEvent {
        製造指示発行
    }
    class WorkOrderStartedEvent {
        製造開始
    }
    class WorkOrderCompletedEvent {
        製造完了
    }
}

package "購買イベント" as purchase_events {
    class PurchaseOrderCreatedEvent {
        発注作成
    }
    class GoodsReceivedEvent {
        入荷受入
    }
    class InspectionCompletedEvent {
        検査完了
    }
    class GoodsAcceptedEvent {
        検収完了
    }
}

package "在庫イベント" as inventory_events {
    class InventoryReceivedEvent {
        入庫
    }
    class InventoryIssuedEvent {
        出庫
    }
    class InventoryAdjustedEvent {
        在庫調整
    }
    class InventoryTransferredEvent {
        在庫移動
    }
}

@enduml
```

#### 製造オーダライフサイクル

```plantuml
@startuml
title 製造オーダライフサイクルとイベント

[*] --> 作成済 : WorkOrderCreatedEvent

作成済 --> 発行済 : WorkOrderReleasedEvent
作成済 --> 取消済 : WorkOrderCancelledEvent

発行済 --> 着手済 : WorkOrderStartedEvent
note right of 着手済
  材料払出開始
  作業開始
end note

着手済 --> 完了済 : WorkOrderCompletedEvent
note right of 完了済
  完成数量確定
  原価計算
end note

完了済 --> クローズ : WorkOrderClosedEvent
note right of クローズ
  差異分析完了
  会計連携完了
end note

クローズ --> [*]
取消済 --> [*]

@enduml
```

### イベントカタログ

基幹業務システム全体のイベントカタログを整理します。

| システム | イベント名 | 発生タイミング | 主な購読者 |
|---------|----------|--------------|-----------|
| 販売管理 | OrderPlacedEvent | 受注登録時 | 生産計画、与信管理 |
| 販売管理 | OrderConfirmedEvent | 受注確定時 | 出荷管理、在庫管理 |
| 販売管理 | ShipmentCompletedEvent | 出荷完了時 | 売上管理、在庫管理 |
| 販売管理 | SalesRecordedEvent | 売上計上時 | 自動仕訳、債権管理 |
| 販売管理 | PaymentReceivedEvent | 入金確認時 | 自動仕訳、債権管理 |
| 財務会計 | JournalEntryPostedEvent | 仕訳転記時 | 残高管理、試算表 |
| 財務会計 | MonthEndClosedEvent | 月次締め時 | 財務レポート |
| 生産管理 | WorkOrderReleasedEvent | 製造指示時 | 工程管理、資材管理 |
| 生産管理 | WorkOrderCompletedEvent | 製造完了時 | 在庫管理、原価計算 |
| 生産管理 | GoodsAcceptedEvent | 検収完了時 | 自動仕訳、在庫管理 |

---

## 37.3 イベントストアの設計

### イベントテーブルの構造

イベントストアは、すべてのドメインイベントを永続化するための専用データベースです。

```plantuml
@startuml
title イベントストアのテーブル構造

entity "event_store" as events {
    *event_id : UUID <<PK>>
    --
    *aggregate_id : VARCHAR(100)
    *aggregate_type : VARCHAR(100)
    *sequence_number : BIGINT
    *event_type : VARCHAR(200)
    *event_data : JSONB
    *metadata : JSONB
    *occurred_at : TIMESTAMP
    *created_at : TIMESTAMP
    --
    UNIQUE (aggregate_id, aggregate_type, sequence_number)
}

entity "snapshots" as snapshots {
    *snapshot_id : UUID <<PK>>
    --
    *aggregate_id : VARCHAR(100)
    *aggregate_type : VARCHAR(100)
    *version : BIGINT
    *state_data : JSONB
    *created_at : TIMESTAMP
}

entity "event_streams" as streams {
    *stream_id : VARCHAR(200) <<PK>>
    --
    *aggregate_type : VARCHAR(100)
    current_version : BIGINT
    created_at : TIMESTAMP
    updated_at : TIMESTAMP
}

events }o--|| streams : belongs to
snapshots }o--|| streams : belongs to

note right of events
  【event_data】
  イベントのペイロード（JSON）

  【metadata】
  ・correlationId
  ・causationId
  ・userId
  ・timestamp
end note

@enduml
```

<details>
<summary>SQL 定義</summary>

```sql
-- イベントストアテーブル
CREATE TABLE event_store (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB DEFAULT '{}',
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (aggregate_id, aggregate_type, sequence_number)
);

-- インデックス
CREATE INDEX idx_event_store_aggregate
    ON event_store (aggregate_id, aggregate_type, sequence_number);
CREATE INDEX idx_event_store_type
    ON event_store (event_type);
CREATE INDEX idx_event_store_occurred_at
    ON event_store (occurred_at);

-- スナップショットテーブル
CREATE TABLE snapshots (
    snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    state_data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (aggregate_id, aggregate_type, version)
);

-- イベントストリームテーブル
CREATE TABLE event_streams (
    stream_id VARCHAR(200) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    current_version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 楽観ロック用の関数
CREATE OR REPLACE FUNCTION append_event(
    p_aggregate_id VARCHAR,
    p_aggregate_type VARCHAR,
    p_expected_version BIGINT,
    p_event_type VARCHAR,
    p_event_data JSONB,
    p_metadata JSONB,
    p_occurred_at TIMESTAMP
) RETURNS UUID AS $$
DECLARE
    v_stream_id VARCHAR;
    v_current_version BIGINT;
    v_event_id UUID;
BEGIN
    v_stream_id := p_aggregate_type || '-' || p_aggregate_id;

    -- ストリームのロックと現在バージョン取得
    SELECT current_version INTO v_current_version
    FROM event_streams
    WHERE stream_id = v_stream_id
    FOR UPDATE;

    IF v_current_version IS NULL THEN
        -- 新規ストリーム作成
        INSERT INTO event_streams (stream_id, aggregate_type, current_version)
        VALUES (v_stream_id, p_aggregate_type, 0);
        v_current_version := 0;
    END IF;

    -- 楽観ロックチェック
    IF v_current_version != p_expected_version THEN
        RAISE EXCEPTION 'Concurrency conflict: expected %, actual %',
            p_expected_version, v_current_version;
    END IF;

    -- イベント追加
    INSERT INTO event_store (
        aggregate_id, aggregate_type, sequence_number,
        event_type, event_data, metadata, occurred_at
    ) VALUES (
        p_aggregate_id, p_aggregate_type, v_current_version + 1,
        p_event_type, p_event_data, p_metadata, p_occurred_at
    ) RETURNING event_id INTO v_event_id;

    -- ストリームバージョン更新
    UPDATE event_streams
    SET current_version = v_current_version + 1,
        updated_at = CURRENT_TIMESTAMP
    WHERE stream_id = v_stream_id;

    RETURN v_event_id;
END;
$$ LANGUAGE plpgsql;
```

</details>

### スナップショットの管理

イベント数が多くなると、状態の再構築に時間がかかります。スナップショットを定期的に保存することで、パフォーマンスを改善します。

```plantuml
@startuml
title スナップショットによる状態再構築の最適化

|イベントストア|
start
:集約の読み込み要求;

:最新スナップショット検索;

if (スナップショットあり?) then (yes)
    :スナップショットから状態復元;
    note right
      バージョン: 100
      状態: {...}
    end note

    :スナップショット以降の\nイベントのみ取得;
    note right
      イベント 101〜105
      （5件のみ）
    end note
else (no)
    :全イベント取得;
    note right
      イベント 1〜105
      （105件）
    end note
endif

:イベントを順次適用;
:現在の状態を取得;

if (イベント数 > 閾値?) then (yes)
    :新しいスナップショット保存;
endif

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// スナップショット付きリポジトリ
@Repository
public class SnapshotEventSourcedRepository<T extends EventSourcedAggregate<?>> {
    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
    private final int snapshotThreshold = 100;

    public T findById(String aggregateId, String aggregateType,
                      Supplier<T> factory) {
        // スナップショット検索
        Optional<Snapshot> snapshot = snapshotStore.findLatest(
            aggregateId, aggregateType
        );

        T aggregate = factory.get();
        int fromVersion = 0;

        if (snapshot.isPresent()) {
            // スナップショットから復元
            aggregate.restoreFromSnapshot(snapshot.get().getStateData());
            fromVersion = snapshot.get().getVersion();
        }

        // スナップショット以降のイベントを取得
        List<DomainEvent> events = eventStore.getEvents(
            aggregateId,
            aggregateType,
            fromVersion + 1
        );

        // イベントを適用
        aggregate.replayEvents(events);

        return aggregate;
    }

    public void save(T aggregate) {
        List<DomainEvent> events = aggregate.getUncommittedEvents();

        // イベント保存
        eventStore.appendEvents(
            aggregate.getId(),
            aggregate.getAggregateType(),
            aggregate.getVersion(),
            events
        );

        // スナップショット判定
        if (shouldTakeSnapshot(aggregate)) {
            Snapshot snapshot = new Snapshot(
                aggregate.getId(),
                aggregate.getAggregateType(),
                aggregate.getVersion() + events.size(),
                aggregate.getStateAsJson()
            );
            snapshotStore.save(snapshot);
        }

        aggregate.markEventsAsCommitted();
    }

    private boolean shouldTakeSnapshot(T aggregate) {
        int eventsSinceSnapshot = aggregate.getVersion() %  snapshotThreshold;
        return eventsSinceSnapshot + aggregate.getUncommittedEvents().size()
            >= snapshotThreshold;
    }
}
```

</details>

### イベントの再生とリプレイ

イベントソーシングの強力な機能の1つは、イベントを再生して任意の時点の状態を復元できることです。

```plantuml
@startuml
title イベントリプレイのユースケース

rectangle "リプレイのユースケース" as usecases {
    rectangle "1. 状態の再構築" as rebuild {
        note right
          障害復旧時に
          最新状態を再構築
        end note
    }

    rectangle "2. 過去時点の照会" as time_travel {
        note right
          特定日時の状態を
          確認（監査対応）
        end note
    }

    rectangle "3. リードモデル再構築" as projection_rebuild {
        note right
          プロジェクションの
          バグ修正後に再構築
        end note
    }

    rectangle "4. 新規ビュー追加" as new_view {
        note right
          新しい分析ビューを
          過去イベントから構築
        end note
    }
}

@enduml
```

#### 特定時点の状態照会

```plantuml
@startuml
title 特定時点の状態照会（タイムトラベル）

|クライアント|
start
:特定時点の状態照会;
note right
  集約ID: ORD-001
  時点: 2024/01/15 10:00
end note

|イベントストア|
:指定時点までの\nイベントを取得;

|集約|
:イベントを順次適用;
note right
  event1 (01/10) → apply
  event2 (01/12) → apply
  event3 (01/14) → apply
  event4 (01/16) → skip
  event5 (01/18) → skip
end note

:2024/01/15 10:00時点の\n状態を返却;

|クライアント|
:過去の状態を確認;

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// タイムトラベルクエリ
@Service
public class TimeTravelQueryService {
    private final EventStore eventStore;

    public <T extends EventSourcedAggregate<?>> T getStateAt(
            String aggregateId,
            String aggregateType,
            Instant pointInTime,
            Supplier<T> factory) {

        // 指定時点までのイベントを取得
        List<DomainEvent> events = eventStore.getEventsUntil(
            aggregateId,
            aggregateType,
            pointInTime
        );

        // 状態を再構築
        T aggregate = factory.get();
        aggregate.replayEvents(events);

        return aggregate;
    }
}

// プロジェクション再構築
@Service
public class ProjectionRebuilder {
    private final EventStore eventStore;
    private final List<Projection> projections;

    public void rebuildAll() {
        // 全プロジェクションをクリア
        projections.forEach(Projection::clear);

        // 全イベントを時系列で取得
        try (Stream<DomainEvent> events = eventStore.streamAllEvents()) {
            events.forEach(event -> {
                projections.forEach(p -> p.handle(event));
            });
        }
    }

    public void rebuildFrom(Instant from) {
        try (Stream<DomainEvent> events = eventStore.streamEventsFrom(from)) {
            events.forEach(event -> {
                projections.forEach(p -> p.handle(event));
            });
        }
    }
}
```

</details>

---

## 37.4 まとめ

本章では、イベント駆動アーキテクチャの基礎と基幹業務システムへの適用方法について解説しました。

### 学んだこと

1. **イベント駆動の基礎**

   - ドメインイベント：ビジネス上の出来事を表現
   - イベントソーシング：状態変化の履歴として保存
   - CQRS：コマンドとクエリの責務分離

2. **基幹業務システムのイベント設計**

   - 販売管理イベント（受注、出荷、売上、債権）
   - 財務会計イベント（仕訳、残高、決算）
   - 生産管理イベント（製造、購買、在庫）

3. **イベントストアの設計**

   - イベントテーブルの構造
   - スナップショットによる最適化
   - イベントの再生とリプレイ

### イベント駆動アーキテクチャの適用指針

| 項目 | 推奨 | 非推奨 |
|-----|-----|-------|
| 適用場面 | 複雑なドメインロジック | シンプルな CRUD |
| | 監査要件が厳しい | 即時一貫性が必須 |
| | 複数ビューが必要 | データ量が少ない |
| 技術選定 | Axon Framework | 自前実装（小規模） |
| | Apache Kafka | 単一サービス内 |

### 次章の予告

第38章では、API 設計とサービス連携について解説します。RESTful API の設計原則、サービス間通信のパターン、API ゲートウェイの活用方法を学びます。
