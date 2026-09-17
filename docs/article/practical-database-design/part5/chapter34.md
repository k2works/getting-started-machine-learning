---
type: Article
title: "第34章：メッセージングパターン"
description: "本章では、エンタープライズインテグレーションの基盤となるメッセージングパターンについて解説します。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第34章：メッセージングパターン

本章では、エンタープライズインテグレーションの基盤となるメッセージングパターンについて解説します。Gregor Hohpe と Bobby Woolf による「Enterprise Integration Patterns」で体系化されたパターンを、基幹業務システムの文脈で理解していきます。

---

## 34.1 メッセージングの基礎

### メッセージングシステムの全体像

メッセージングシステムは、分散システム間の非同期通信を実現するための基盤です。送信側と受信側が直接接続する必要がなく、疎結合なシステム統合を可能にします。

```plantuml
@startuml
title メッセージングシステムの基本構造

rectangle "送信側アプリケーション" as sender {
    rectangle "ビジネスロジック" as sender_logic
    rectangle "メッセージ\nエンドポイント" as sender_endpoint
}

rectangle "メッセージングシステム" as messaging {
    queue "メッセージ\nチャネル" as channel
    rectangle "メッセージ\nルーター" as router
    rectangle "メッセージ\n変換" as transformer
}

rectangle "受信側アプリケーション" as receiver {
    rectangle "メッセージ\nエンドポイント" as receiver_endpoint
    rectangle "ビジネスロジック" as receiver_logic
}

sender_logic --> sender_endpoint
sender_endpoint --> channel
channel --> router
router --> transformer
transformer --> receiver_endpoint
receiver_endpoint --> receiver_logic

note bottom of messaging
  ・非同期通信
  ・疎結合
  ・信頼性のある配信
  ・スケーラビリティ
end note

@enduml
```

### メッセージチャネル（Message Channel）

メッセージチャネルは、送信側と受信側をつなぐ論理的なパイプです。チャネルの種類によって、メッセージの配信方法が異なります。

```plantuml
@startuml
title メッセージチャネルの種類

rectangle "Point-to-Point Channel" as p2p {
    rectangle "送信者A" as senderA
    queue "キュー" as queue1
    rectangle "受信者A" as receiverA

    senderA --> queue1
    queue1 --> receiverA
}

note right of p2p
  ・1対1の通信
  ・メッセージは1つの受信者のみが受信
  ・負荷分散に適用可能

  例：受注処理キュー
end note

rectangle "Publish-Subscribe Channel" as pubsub {
    rectangle "発行者" as publisher
    collections "トピック" as topic
    rectangle "購読者A" as subA
    rectangle "購読者B" as subB
    rectangle "購読者C" as subC

    publisher --> topic
    topic --> subA
    topic --> subB
    topic --> subC
}

note right of pubsub
  ・1対多の通信
  ・すべての購読者がメッセージを受信
  ・イベント通知に最適

  例：売上イベントの通知
end note

@enduml
```

#### 基幹業務システムにおけるチャネル設計

```plantuml
@startuml
title 基幹業務システムのメッセージチャネル設計

package "販売管理" as sales {
    rectangle "受注サービス" as order_svc
    rectangle "出荷サービス" as ship_svc
    rectangle "売上サービス" as sales_svc
}

package "メッセージチャネル" as channels {
    queue "受注確定キュー" as order_queue
    collections "売上イベント\nトピック" as sales_topic
    queue "仕訳生成キュー" as journal_queue
}

package "会計" as accounting {
    rectangle "自動仕訳\nサービス" as auto_journal
}

package "生産管理" as production {
    rectangle "生産計画\nサービス" as prod_plan
}

package "分析" as analytics {
    rectangle "売上分析\nサービス" as sales_analytics
}

order_svc --> order_queue : 受注確定
order_queue --> ship_svc : 出荷指示作成
sales_svc --> sales_topic : 売上計上イベント
sales_topic --> auto_journal : 仕訳生成
sales_topic --> prod_plan : 需要情報更新
sales_topic --> sales_analytics : 分析データ収集

@enduml
```

### メッセージ（Message）の構造

メッセージは、ヘッダーとボディで構成されます。ヘッダーにはルーティングや処理に必要なメタデータを、ボディにはビジネスデータを含めます。

```plantuml
@startuml
title メッセージの構造

class "Message" as msg {
    +Header header
    +Body body
}

class "Header" as header {
    +String messageId
    +String correlationId
    +String replyTo
    +Date timestamp
    +String messageType
    +Map<String, Object> properties
}

class "Body" as body {
    +Object payload
    +String contentType
    +String encoding
}

msg --> header
msg --> body

note right of header
  【メタデータ】
  ・messageId: 一意識別子
  ・correlationId: 関連メッセージの追跡
  ・replyTo: 返信先チャネル
  ・timestamp: 送信日時
  ・messageType: メッセージ種別
end note

note right of body
  【ビジネスデータ】
  ・payload: 実際のデータ
  ・contentType: JSON/XML等
  ・encoding: UTF-8等
end note

@enduml
```

#### 売上イベントメッセージの例

```plantuml
@startuml
title 売上イベントメッセージの例

object "売上イベントメッセージ" as sales_event {
    <b>Header</b>
    messageId = "MSG-20240115-001"
    correlationId = "ORD-2024-00123"
    messageType = "SalesCompleted"
    timestamp = "2024-01-15T10:30:00Z"
    source = "sales-service"
    --
    <b>Body</b>
    eventType = "売上計上"
    salesId = "SLS-2024-00456"
    orderNumber = "ORD-2024-00123"
    customerId = "CUS-001"
    salesDate = "2024-01-15"
    totalAmount = 108000
    taxAmount = 8000
    lines = [...]
}

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// メッセージヘッダー
public record MessageHeader(
    String messageId,
    String correlationId,
    String replyTo,
    Instant timestamp,
    String messageType,
    String source,
    Map<String, Object> properties
) {
    public static MessageHeader create(String messageType, String source) {
        return new MessageHeader(
            UUID.randomUUID().toString(),
            null,
            null,
            Instant.now(),
            messageType,
            source,
            new HashMap<>()
        );
    }

    public MessageHeader withCorrelationId(String correlationId) {
        return new MessageHeader(
            messageId, correlationId, replyTo, timestamp,
            messageType, source, properties
        );
    }
}

// メッセージ本体
public record Message<T>(
    MessageHeader header,
    T payload
) {
    public static <T> Message<T> of(String messageType, String source, T payload) {
        return new Message<>(
            MessageHeader.create(messageType, source),
            payload
        );
    }
}

// 売上イベント
public record SalesCompletedEvent(
    String salesId,
    String orderNumber,
    String customerId,
    LocalDate salesDate,
    BigDecimal totalAmount,
    BigDecimal taxAmount,
    List<SalesLineEvent> lines
) {}

// 使用例
Message<SalesCompletedEvent> message = Message.of(
    "SalesCompleted",
    "sales-service",
    new SalesCompletedEvent(
        "SLS-2024-00456",
        "ORD-2024-00123",
        "CUS-001",
        LocalDate.of(2024, 1, 15),
        new BigDecimal("108000"),
        new BigDecimal("8000"),
        lines
    )
).header().withCorrelationId("ORD-2024-00123");
```

</details>

### パイプとフィルター（Pipes and Filters）

パイプとフィルターパターンは、複雑な処理を小さな独立したステップ（フィルター）に分解し、パイプでつなげて処理する方式です。

```plantuml
@startuml
title パイプとフィルターパターン

rectangle "入力" as input
rectangle "フィルターA\n（検証）" as filterA
rectangle "フィルターB\n（変換）" as filterB
rectangle "フィルターC\n（エンリッチ）" as filterC
rectangle "フィルターD\n（ルーティング）" as filterD
rectangle "出力" as output

input --> filterA : パイプ
filterA --> filterB : パイプ
filterB --> filterC : パイプ
filterC --> filterD : パイプ
filterD --> output : パイプ

note bottom of filterA
  各フィルターは
  ・独立して動作
  ・単一責任
  ・再利用可能
  ・テスト容易
end note

@enduml
```

#### 売上仕訳生成パイプライン

```plantuml
@startuml
title 売上から仕訳生成へのパイプライン

rectangle "売上\nイベント" as input

rectangle "バリデーション\nフィルター" as validate {
    note right
      ・必須項目チェック
      ・金額整合性確認
    end note
}

rectangle "仕訳パターン\n判定フィルター" as pattern {
    note right
      ・商品グループ判定
      ・顧客グループ判定
      ・科目決定
    end note
}

rectangle "仕訳明細\n生成フィルター" as generate {
    note right
      ・借方明細生成
      ・貸方明細生成
      ・消費税計算
    end note
}

rectangle "貸借一致\n検証フィルター" as balance {
    note right
      ・借方合計 = 貸方合計
      ・検証エラー処理
    end note
}

rectangle "仕訳\nイベント" as output

input --> validate
validate --> pattern
pattern --> generate
generate --> balance
balance --> output

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// フィルターインターフェース
@FunctionalInterface
public interface Filter<T> {
    T process(T input) throws FilterException;

    default Filter<T> andThen(Filter<T> next) {
        return input -> next.process(this.process(input));
    }
}

// パイプラインビルダー
public class Pipeline<T> {
    private final List<Filter<T>> filters = new ArrayList<>();

    public Pipeline<T> addFilter(Filter<T> filter) {
        filters.add(filter);
        return this;
    }

    public T execute(T input) throws FilterException {
        T result = input;
        for (Filter<T> filter : filters) {
            result = filter.process(result);
        }
        return result;
    }
}

// 売上仕訳変換コンテキスト
public class SalesJournalContext {
    private SalesCompletedEvent salesEvent;
    private JournalPattern pattern;
    private List<JournalLine> journalLines;
    private JournalEntry journalEntry;
    private List<String> errors = new ArrayList<>();

    // getter/setter
}

// バリデーションフィルター
public class ValidationFilter implements Filter<SalesJournalContext> {
    @Override
    public SalesJournalContext process(SalesJournalContext ctx) {
        SalesCompletedEvent event = ctx.getSalesEvent();

        if (event.salesId() == null || event.salesId().isBlank()) {
            ctx.getErrors().add("売上IDは必須です");
        }
        if (event.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            ctx.getErrors().add("売上金額は正の値である必要があります");
        }

        if (!ctx.getErrors().isEmpty()) {
            throw new ValidationException(ctx.getErrors());
        }
        return ctx;
    }
}

// 仕訳パターン判定フィルター
public class PatternDeterminationFilter implements Filter<SalesJournalContext> {
    private final JournalPatternRepository patternRepository;

    @Override
    public SalesJournalContext process(SalesJournalContext ctx) {
        JournalPattern pattern = patternRepository.findByProductAndCustomerGroup(
            ctx.getSalesEvent().productGroup(),
            ctx.getSalesEvent().customerGroup()
        ).orElseThrow(() -> new PatternNotFoundException("仕訳パターンが見つかりません"));

        ctx.setPattern(pattern);
        return ctx;
    }
}

// パイプラインの構築と実行
public class SalesJournalPipeline {
    private final Pipeline<SalesJournalContext> pipeline;

    public SalesJournalPipeline(
            ValidationFilter validationFilter,
            PatternDeterminationFilter patternFilter,
            JournalLineGenerationFilter generationFilter,
            BalanceValidationFilter balanceFilter) {

        this.pipeline = new Pipeline<SalesJournalContext>()
            .addFilter(validationFilter)
            .addFilter(patternFilter)
            .addFilter(generationFilter)
            .addFilter(balanceFilter);
    }

    public JournalEntry process(SalesCompletedEvent event) {
        SalesJournalContext ctx = new SalesJournalContext();
        ctx.setSalesEvent(event);

        SalesJournalContext result = pipeline.execute(ctx);
        return result.getJournalEntry();
    }
}
```

</details>

---

## 34.2 メッセージルーティング

メッセージルーティングは、メッセージの内容や属性に基づいて、適切な宛先にメッセージを振り分ける機能です。

### Content-Based Router（内容ベースルーター）

メッセージの内容を検査し、条件に基づいて異なるチャネルにルーティングします。

```plantuml
@startuml
title Content-Based Router パターン

rectangle "入力\nチャネル" as input

rectangle "Content-Based\nRouter" as router

queue "売上仕訳\nチャネル" as sales_channel
queue "仕入仕訳\nチャネル" as purchase_channel
queue "経費仕訳\nチャネル" as expense_channel

input --> router

router --> sales_channel : 伝票区分 = "売上"
router --> purchase_channel : 伝票区分 = "仕入"
router --> expense_channel : 伝票区分 = "経費"

note right of router
  メッセージ内容を検査し
  条件に基づいて
  適切なチャネルへ振り分け
end note

@enduml
```

#### 基幹業務システムでの適用例

```plantuml
@startuml
title 取引イベントのルーティング

rectangle "取引イベント" as input

rectangle "取引種別\nルーター" as router

package "販売系処理" as sales_pkg {
    queue "受注処理" as order_q
    queue "出荷処理" as ship_q
    queue "売上処理" as sales_q
}

package "調達系処理" as purchase_pkg {
    queue "発注処理" as po_q
    queue "入荷処理" as receipt_q
    queue "仕入処理" as purchase_q
}

package "在庫系処理" as inv_pkg {
    queue "入庫処理" as stock_in_q
    queue "出庫処理" as stock_out_q
    queue "棚卸処理" as inventory_q
}

input --> router

router --> order_q : type = ORDER
router --> ship_q : type = SHIPMENT
router --> sales_q : type = SALES
router --> po_q : type = PURCHASE_ORDER
router --> receipt_q : type = RECEIPT
router --> purchase_q : type = PURCHASE
router --> stock_in_q : type = STOCK_IN
router --> stock_out_q : type = STOCK_OUT
router --> inventory_q : type = INVENTORY

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// ルーティング条件
public interface RoutingCondition<T> {
    boolean matches(T message);
    String getDestination();
}

// Content-Based Router
public class ContentBasedRouter<T> {
    private final List<RoutingCondition<T>> conditions = new ArrayList<>();
    private String defaultDestination;

    public ContentBasedRouter<T> when(Predicate<T> predicate, String destination) {
        conditions.add(new RoutingCondition<>() {
            @Override
            public boolean matches(T message) {
                return predicate.test(message);
            }
            @Override
            public String getDestination() {
                return destination;
            }
        });
        return this;
    }

    public ContentBasedRouter<T> otherwise(String destination) {
        this.defaultDestination = destination;
        return this;
    }

    public String route(T message) {
        return conditions.stream()
            .filter(c -> c.matches(message))
            .findFirst()
            .map(RoutingCondition::getDestination)
            .orElse(defaultDestination);
    }
}

// 取引イベントルーター
public class TransactionEventRouter {
    private final ContentBasedRouter<TransactionEvent> router;
    private final MessageSender messageSender;

    public TransactionEventRouter(MessageSender messageSender) {
        this.messageSender = messageSender;
        this.router = new ContentBasedRouter<TransactionEvent>()
            .when(e -> e.type() == TransactionType.ORDER, "order-queue")
            .when(e -> e.type() == TransactionType.SHIPMENT, "shipment-queue")
            .when(e -> e.type() == TransactionType.SALES, "sales-queue")
            .when(e -> e.type() == TransactionType.PURCHASE_ORDER, "po-queue")
            .when(e -> e.type() == TransactionType.RECEIPT, "receipt-queue")
            .when(e -> e.type() == TransactionType.PURCHASE, "purchase-queue")
            .otherwise("dead-letter-queue");
    }

    public void route(TransactionEvent event) {
        String destination = router.route(event);
        messageSender.send(destination, event);
    }
}
```

</details>

### Message Filter（メッセージフィルター）

条件に合致するメッセージのみを通過させ、それ以外は破棄または別のチャネルに送ります。

```plantuml
@startuml
title Message Filter パターン

rectangle "入力\nチャネル" as input

rectangle "Message\nFilter" as filter

rectangle "出力\nチャネル" as output

rectangle "破棄/DLQ" as discard

input --> filter
filter --> output : 条件に合致
filter --> discard : 条件に不合致

note right of filter
  【フィルター条件の例】
  ・金額が閾値以上
  ・特定の顧客のみ
  ・特定期間のデータのみ
  ・重複メッセージの排除
end note

@enduml
```

#### 高額取引フィルターの例

```plantuml
@startuml
title 高額取引アラートフィルター

rectangle "売上イベント" as input

rectangle "高額取引\nフィルター" as filter {
    note right
      条件：金額 >= 100万円
    end note
}

rectangle "通常処理\nチャネル" as normal
rectangle "高額取引\nアラートチャネル" as alert

input --> filter
filter --> normal : 金額 < 100万円
filter --> alert : 金額 >= 100万円

@enduml
```

### Splitter / Aggregator（分割 / 集約）

大きなメッセージを複数の小さなメッセージに分割し、処理後に再び集約するパターンです。

```plantuml
@startuml
title Splitter / Aggregator パターン

rectangle "受注\nメッセージ" as input
note right of input
  受注ヘッダー
  + 明細10件
end note

rectangle "Splitter\n（分割）" as splitter

rectangle "明細1" as line1
rectangle "明細2" as line2
rectangle "..." as dots
rectangle "明細10" as line10

rectangle "在庫引当\n処理" as process

rectangle "Aggregator\n（集約）" as aggregator

rectangle "引当結果\nメッセージ" as output
note right of output
  受注ヘッダー
  + 引当結果10件
end note

input --> splitter
splitter --> line1
splitter --> line2
splitter --> dots
splitter --> line10

line1 --> process
line2 --> process
dots --> process
line10 --> process

process --> aggregator
aggregator --> output

note bottom of splitter
  【Splitter】
  複合メッセージを
  個別メッセージに分割
end note

note bottom of aggregator
  【Aggregator】
  関連メッセージを
  1つに集約
  ・correlationIdで関連付け
  ・完了条件の判定
end note

@enduml
```

#### 基幹業務システムでの適用例

```plantuml
@startuml
title 一括仕訳処理のSplitter/Aggregator

rectangle "月次締め\nリクエスト" as input

rectangle "売上データ\n取得" as fetch

rectangle "Splitter" as splitter

rectangle "仕訳変換\n処理" as transform

rectangle "Aggregator" as aggregator

rectangle "仕訳バッチ\n登録" as register

rectangle "月次締め\n完了" as output

input --> fetch : 期間指定
fetch --> splitter : 売上リスト

splitter --> transform : 売上1
splitter --> transform : 売上2
splitter --> transform : 売上N

transform --> aggregator : 仕訳1
transform --> aggregator : 仕訳2
transform --> aggregator : 仕訳N

aggregator --> register : 仕訳リスト
register --> output

note right of aggregator
  【集約条件】
  ・すべての売上が処理完了
  ・タイムアウト時は部分集約
  ・エラー時は補償処理
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// Splitter
public class OrderLineSplitter {
    public List<Message<OrderLineEvent>> split(Message<OrderEvent> orderMessage) {
        OrderEvent order = orderMessage.payload();
        return order.lines().stream()
            .map(line -> {
                OrderLineEvent lineEvent = new OrderLineEvent(
                    order.orderId(),
                    line.lineNumber(),
                    line.productId(),
                    line.quantity(),
                    line.unitPrice()
                );
                return Message.of("OrderLine", "splitter", lineEvent)
                    .withCorrelationId(order.orderId());
            })
            .toList();
    }
}

// Aggregator
public class AllocationResultAggregator {
    private final Map<String, AggregationState> states = new ConcurrentHashMap<>();

    public record AggregationState(
        String correlationId,
        int expectedCount,
        List<AllocationResult> results,
        Instant startTime
    ) {}

    public void aggregate(Message<AllocationResult> message) {
        String correlationId = message.header().correlationId();
        AllocationResult result = message.payload();

        states.compute(correlationId, (id, state) -> {
            if (state == null) {
                // 初期状態
                state = new AggregationState(
                    id,
                    getExpectedCount(id),
                    new ArrayList<>(),
                    Instant.now()
                );
            }
            state.results().add(result);
            return state;
        });

        checkCompletion(correlationId);
    }

    private void checkCompletion(String correlationId) {
        AggregationState state = states.get(correlationId);
        if (state == null) return;

        // 完了条件：すべての結果が揃った
        if (state.results().size() >= state.expectedCount()) {
            AggregatedAllocationResult aggregated = new AggregatedAllocationResult(
                correlationId,
                state.results()
            );
            publishAggregatedResult(aggregated);
            states.remove(correlationId);
        }
        // タイムアウトチェック
        else if (Duration.between(state.startTime(), Instant.now()).toMinutes() > 5) {
            handleTimeout(state);
        }
    }
}
```

</details>

### Resequencer（再順序付け）

順序が乱れて到着したメッセージを、正しい順序に並べ替えてから処理します。

```plantuml
@startuml
title Resequencer パターン

rectangle "入力チャネル" as input

rectangle "Resequencer" as reseq {
    rectangle "バッファ" as buffer
    rectangle "順序判定" as order
}

rectangle "出力チャネル" as output

input --> reseq : メッセージ(3)
input --> reseq : メッセージ(1)
input --> reseq : メッセージ(5)
input --> reseq : メッセージ(2)
input --> reseq : メッセージ(4)

reseq --> output : メッセージ(1)
reseq --> output : メッセージ(2)
reseq --> output : メッセージ(3)
reseq --> output : メッセージ(4)
reseq --> output : メッセージ(5)

note right of reseq
  【Resequencer の動作】
  ・シーケンス番号でバッファリング
  ・欠番の待機
  ・タイムアウト処理

  【適用例】
  ・在庫移動の順序保証
  ・仕訳の時系列順序
end note

@enduml
```

---

## 34.3 メッセージ変換

異なるシステム間でデータをやり取りする際、データ形式やスキーマの変換が必要になります。

### Message Translator（メッセージ変換）

あるシステムのメッセージ形式を、別のシステムが理解できる形式に変換します。

```plantuml
@startuml
title Message Translator パターン

rectangle "送信側\nシステム" as sender

rectangle "Message\nTranslator" as translator

rectangle "受信側\nシステム" as receiver

sender --> translator : フォーマットA
translator --> receiver : フォーマットB

note bottom of translator
  【変換内容】
  ・データ形式（JSON ↔ XML）
  ・スキーマ変換
  ・フィールド名マッピング
  ・値の変換（コード変換等）
  ・データ型変換
end note

@enduml
```

#### 売上から仕訳への変換例

```plantuml
@startuml
title 売上イベントから仕訳への変換

class "売上イベント" as sales {
    +salesId: String
    +salesDate: Date
    +customerId: String
    +totalAmount: Decimal
    +taxAmount: Decimal
    +lines: List<SalesLine>
}

class "SalesLine" as sales_line {
    +productId: String
    +quantity: int
    +unitPrice: Decimal
    +amount: Decimal
}

class "仕訳伝票" as journal {
    +journalId: String
    +journalDate: Date
    +journalType: String
    +entries: List<JournalEntry>
}

class "仕訳明細" as journal_entry {
    +debitCredit: String
    +accountCode: String
    +amount: Decimal
    +description: String
}

class "SalesJournalTranslator" as translator {
    +translate(SalesEvent): JournalEntry
    -determineAccounts()
    -calculateTax()
    -createDebitEntries()
    -createCreditEntries()
}

sales "1" -- "*" sales_line
journal "1" -- "*" journal_entry
translator ..> sales : 入力
translator ..> journal : 出力

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// メッセージ変換インターフェース
public interface MessageTranslator<S, T> {
    T translate(S source);
}

// 売上から仕訳への変換
@Component
public class SalesJournalTranslator
        implements MessageTranslator<SalesCompletedEvent, JournalEntryCommand> {

    private final JournalPatternRepository patternRepository;
    private final AccountRepository accountRepository;

    @Override
    public JournalEntryCommand translate(SalesCompletedEvent sales) {
        // 仕訳パターン取得
        JournalPattern pattern = patternRepository.findBySalesType(
            sales.salesType()
        ).orElseThrow();

        // 借方明細（売掛金）
        List<JournalLineCommand> debitLines = List.of(
            new JournalLineCommand(
                DebitCredit.DEBIT,
                pattern.debitAccountCode(),
                sales.totalAmount(),
                "売掛金計上 " + sales.customerName()
            )
        );

        // 貸方明細（売上 + 仮受消費税）
        List<JournalLineCommand> creditLines = new ArrayList<>();
        creditLines.add(new JournalLineCommand(
            DebitCredit.CREDIT,
            pattern.creditAccountCode(),
            sales.totalAmount().subtract(sales.taxAmount()),
            "売上計上 " + sales.salesId()
        ));
        creditLines.add(new JournalLineCommand(
            DebitCredit.CREDIT,
            pattern.taxAccountCode(),
            sales.taxAmount(),
            "仮受消費税"
        ));

        // 仕訳伝票作成
        return new JournalEntryCommand(
            generateJournalId(),
            sales.salesDate(),
            JournalType.SALES,
            sales.salesId(),
            Stream.concat(debitLines.stream(), creditLines.stream()).toList()
        );
    }
}
```

</details>

### Envelope Wrapper（エンベロープラッパー）

メッセージに追加のメタデータを付与するためのラッパーです。

```plantuml
@startuml
title Envelope Wrapper パターン

rectangle "アプリケーション\nデータ" as app_data

rectangle "Envelope\nWrapper" as wrapper

rectangle "エンベロープ付き\nメッセージ" as envelope {
    rectangle "ルーティング情報" as routing
    rectangle "セキュリティ情報" as security
    rectangle "トレース情報" as trace
    rectangle "アプリケーション\nデータ" as data
}

app_data --> wrapper
wrapper --> envelope

note right of envelope
  【エンベロープの内容】
  ・送信元/宛先情報
  ・認証トークン
  ・トレースID（分散トレーシング）
  ・タイムスタンプ
  ・バージョン情報
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// エンベロープ
public record MessageEnvelope<T>(
    RoutingInfo routing,
    SecurityInfo security,
    TraceInfo trace,
    T payload
) {
    public record RoutingInfo(
        String source,
        String destination,
        String replyTo,
        int priority
    ) {}

    public record SecurityInfo(
        String authToken,
        String userId,
        List<String> roles
    ) {}

    public record TraceInfo(
        String traceId,
        String spanId,
        String parentSpanId,
        Instant timestamp
    ) {}

    public static <T> MessageEnvelope<T> wrap(
            T payload,
            String source,
            String destination) {
        return new MessageEnvelope<>(
            new RoutingInfo(source, destination, null, 0),
            null,
            new TraceInfo(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                Instant.now()
            ),
            payload
        );
    }
}

// Envelope Wrapper
@Component
public class EnvelopeWrapper {
    private final SecurityContext securityContext;
    private final TraceContext traceContext;

    public <T> MessageEnvelope<T> wrap(T payload, String destination) {
        return new MessageEnvelope<>(
            new RoutingInfo(
                "current-service",
                destination,
                null,
                0
            ),
            new SecurityInfo(
                securityContext.getToken(),
                securityContext.getUserId(),
                securityContext.getRoles()
            ),
            new TraceInfo(
                traceContext.getTraceId(),
                traceContext.newSpanId(),
                traceContext.getCurrentSpanId(),
                Instant.now()
            ),
            payload
        );
    }

    public <T> T unwrap(MessageEnvelope<T> envelope) {
        // セキュリティ検証
        validateSecurity(envelope.security());
        // トレース情報の伝播
        propagateTrace(envelope.trace());
        return envelope.payload();
    }
}
```

</details>

### Content Enricher（コンテンツエンリッチャー）

メッセージに不足している情報を外部ソースから取得して追加します。

```plantuml
@startuml
title Content Enricher パターン

rectangle "入力メッセージ" as input
note right of input
  customerId: "CUS-001"
  productId: "PRD-001"
  quantity: 10
end note

rectangle "Content\nEnricher" as enricher

database "顧客マスタ" as customer_db
database "商品マスタ" as product_db

rectangle "出力メッセージ" as output
note right of output
  customerId: "CUS-001"
  <b>customerName: "株式会社A"</b>
  <b>creditLimit: 1000000</b>
  productId: "PRD-001"
  <b>productName: "商品A"</b>
  <b>unitPrice: 1000</b>
  quantity: 10
  <b>amount: 10000</b>
end note

input --> enricher
enricher --> customer_db : 顧客情報取得
enricher --> product_db : 商品情報取得
enricher --> output

@enduml
```

#### 受注エンリッチメントの例

```plantuml
@startuml
title 受注データのエンリッチメント

rectangle "受注イベント\n（基本情報のみ）" as input

rectangle "Customer\nEnricher" as cust_enricher
rectangle "Product\nEnricher" as prod_enricher
rectangle "Price\nEnricher" as price_enricher
rectangle "Inventory\nEnricher" as inv_enricher

database "顧客マスタ" as cust_db
database "商品マスタ" as prod_db
database "単価マスタ" as price_db
database "在庫情報" as inv_db

rectangle "受注イベント\n（完全情報）" as output

input --> cust_enricher
cust_enricher --> cust_db
cust_enricher --> prod_enricher
prod_enricher --> prod_db
prod_enricher --> price_enricher
price_enricher --> price_db
price_enricher --> inv_enricher
inv_enricher --> inv_db
inv_enricher --> output

note bottom of output
  追加された情報：
  ・顧客名、与信情報
  ・商品名、商品区分
  ・適用単価
  ・在庫数、引当可能数
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// Enricherインターフェース
public interface ContentEnricher<T> {
    T enrich(T message);
}

// 受注エンリッチャーチェーン
@Component
public class OrderEnricherChain {
    private final List<ContentEnricher<OrderEvent>> enrichers;

    public OrderEnricherChain(
            CustomerEnricher customerEnricher,
            ProductEnricher productEnricher,
            PriceEnricher priceEnricher,
            InventoryEnricher inventoryEnricher) {
        this.enrichers = List.of(
            customerEnricher,
            productEnricher,
            priceEnricher,
            inventoryEnricher
        );
    }

    public OrderEvent enrich(OrderEvent order) {
        OrderEvent enriched = order;
        for (ContentEnricher<OrderEvent> enricher : enrichers) {
            enriched = enricher.enrich(enriched);
        }
        return enriched;
    }
}

// 顧客情報エンリッチャー
@Component
public class CustomerEnricher implements ContentEnricher<OrderEvent> {
    private final CustomerRepository customerRepository;

    @Override
    public OrderEvent enrich(OrderEvent order) {
        Customer customer = customerRepository.findById(order.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(order.customerId()));

        return order.toBuilder()
            .customerName(customer.name())
            .customerAddress(customer.address())
            .creditLimit(customer.creditLimit())
            .build();
    }
}

// 商品情報エンリッチャー
@Component
public class ProductEnricher implements ContentEnricher<OrderEvent> {
    private final ProductRepository productRepository;

    @Override
    public OrderEvent enrich(OrderEvent order) {
        List<OrderLineEvent> enrichedLines = order.lines().stream()
            .map(line -> {
                Product product = productRepository.findById(line.productId())
                    .orElseThrow();
                return line.toBuilder()
                    .productName(product.name())
                    .productCategory(product.category())
                    .taxRate(product.taxRate())
                    .build();
            })
            .toList();

        return order.toBuilder()
            .lines(enrichedLines)
            .build();
    }
}
```

</details>

### Canonical Data Model（標準データモデル）

複数のシステム間で共通に使用するデータモデルを定義し、変換の複雑さを軽減します。

```plantuml
@startuml
title Canonical Data Model パターン

rectangle "販売管理\nシステム" as sales {
    rectangle "販売固有\nモデル" as sales_model
}

rectangle "生産管理\nシステム" as production {
    rectangle "生産固有\nモデル" as prod_model
}

rectangle "会計\nシステム" as accounting {
    rectangle "会計固有\nモデル" as acc_model
}

rectangle "標準データモデル\n(Canonical Model)" as canonical {
    rectangle "標準取引先" as std_partner
    rectangle "標準商品/品目" as std_product
    rectangle "標準取引" as std_transaction
}

sales_model <--> canonical : 変換
prod_model <--> canonical : 変換
acc_model <--> canonical : 変換

note bottom of canonical
  【標準データモデルの利点】
  ・変換数の削減（N×N → 2×N）
  ・一貫したデータ定義
  ・システム追加時の影響最小化

  【定義すべき項目】
  ・共通エンティティ（取引先、商品等）
  ・共通イベント（取引、在庫移動等）
  ・共通コード体系
end note

@enduml
```

#### 基幹業務システムの標準データモデル

```plantuml
@startuml
title 基幹業務システムの標準データモデル

package "標準マスタモデル" as master {
    class "StandardPartner" as partner {
        +partnerId: String
        +partnerType: PartnerType
        +name: String
        +address: Address
        +contacts: List<Contact>
    }

    class "StandardProduct" as product {
        +productId: String
        +productType: ProductType
        +name: String
        +unit: Unit
        +prices: List<Price>
    }

    class "StandardDepartment" as dept {
        +departmentId: String
        +name: String
        +parentId: String
        +level: int
    }
}

package "標準トランザクションモデル" as transaction {
    class "StandardTransaction" as trans {
        +transactionId: String
        +transactionType: TransactionType
        +transactionDate: Date
        +partnerId: String
        +totalAmount: Money
        +lines: List<TransactionLine>
    }

    class "TransactionLine" as line {
        +lineNumber: int
        +productId: String
        +quantity: Quantity
        +unitPrice: Money
        +amount: Money
    }
}

package "標準イベントモデル" as event {
    class "StandardEvent" as evt {
        +eventId: String
        +eventType: EventType
        +timestamp: Instant
        +source: String
        +payload: Object
    }
}

trans "1" -- "*" line

note bottom of master
  【コード体系の標準化】
  ・取引先コード：PTN-XXXXXXXX
  ・商品コード：PRD-XXXXXXXX
  ・部門コード：DPT-XXXX
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 標準取引先モデル
public record StandardPartner(
    String partnerId,
    PartnerType partnerType,
    String name,
    Address address,
    List<Contact> contacts,
    Map<String, String> attributes
) {
    public enum PartnerType {
        CUSTOMER,    // 顧客
        SUPPLIER,    // 仕入先
        SUBCONTRACTOR // 外注先
    }
}

// 標準トランザクションモデル
public record StandardTransaction(
    String transactionId,
    TransactionType transactionType,
    LocalDate transactionDate,
    String partnerId,
    Money totalAmount,
    List<TransactionLine> lines,
    Map<String, String> attributes
) {
    public enum TransactionType {
        ORDER,           // 受注
        SHIPMENT,        // 出荷
        SALES,           // 売上
        PURCHASE_ORDER,  // 発注
        RECEIPT,         // 入荷
        PURCHASE,        // 仕入
        WORK_ORDER,      // 製造指示
        COMPLETION       // 完成
    }
}

// 販売固有モデルから標準モデルへの変換
@Component
public class SalesOrderToCanonicalTranslator
        implements MessageTranslator<SalesOrder, StandardTransaction> {

    @Override
    public StandardTransaction translate(SalesOrder order) {
        return new StandardTransaction(
            "TXN-" + order.orderId(),
            TransactionType.ORDER,
            order.orderDate(),
            "PTN-" + order.customerId(),
            Money.of(order.totalAmount(), "JPY"),
            order.lines().stream()
                .map(this::translateLine)
                .toList(),
            Map.of(
                "originalOrderId", order.orderId(),
                "salesRepId", order.salesRepId()
            )
        );
    }

    private TransactionLine translateLine(SalesOrderLine line) {
        return new TransactionLine(
            line.lineNumber(),
            "PRD-" + line.productCode(),
            Quantity.of(line.quantity(), line.unit()),
            Money.of(line.unitPrice(), "JPY"),
            Money.of(line.amount(), "JPY")
        );
    }
}

// 標準モデルから会計固有モデルへの変換
@Component
public class CanonicalToJournalTranslator
        implements MessageTranslator<StandardTransaction, JournalEntry> {

    @Override
    public JournalEntry translate(StandardTransaction transaction) {
        // 取引種別に応じた仕訳パターンを適用
        JournalPattern pattern = determinePattern(transaction.transactionType());

        return new JournalEntry(
            generateJournalId(),
            transaction.transactionDate(),
            pattern.journalType(),
            createJournalLines(transaction, pattern)
        );
    }
}
```

</details>

---

## 34.4 まとめ

本章では、エンタープライズインテグレーションの基盤となるメッセージングパターンについて解説しました。

### 学んだパターン一覧

| カテゴリ | パターン | 用途 |
|---------|---------|------|
| **基礎** | Message Channel | メッセージの伝送経路 |
| | Message | データとメタデータの構造 |
| | Pipes and Filters | 処理の分解と連結 |
| **ルーティング** | Content-Based Router | 内容に基づく振り分け |
| | Message Filter | 条件による通過/破棄 |
| | Splitter/Aggregator | 分割と集約 |
| | Resequencer | 順序の再整列 |
| **変換** | Message Translator | 形式変換 |
| | Envelope Wrapper | メタデータ付与 |
| | Content Enricher | 情報の追加 |
| | Canonical Data Model | 標準データ形式 |

### 基幹業務システムへの適用ポイント

1. **チャネル設計**

   - Point-to-Point：受注処理、仕訳生成など1対1の処理
   - Pub/Sub：イベント通知、複数システムへの連携

2. **ルーティング設計**

   - 取引種別による振り分け
   - 金額閾値によるフィルタリング
   - 月次締めでの分割/集約

3. **変換設計**

   - 売上→仕訳の自動変換
   - マスタ情報のエンリッチメント
   - 標準データモデルによる統合

### 次章の予告

第35章では、システム間連携パターンについて詳しく解説します。販売管理と財務会計、販売管理と生産管理、生産管理と財務会計の具体的な連携方法を学びます。
