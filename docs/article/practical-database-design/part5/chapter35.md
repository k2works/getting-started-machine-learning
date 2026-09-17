---
type: Article
title: "第35章：システム間連携パターン"
description: "本章では、第33章で解説した境界づけられたコンテキストと、第34章で解説したメッセージングパターンを活用して、販売管理・財務会計・生産管理の3つのシステム間の具体的な連携パターンを解説します。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第35章：システム間連携パターン

本章では、第33章で解説した境界づけられたコンテキストと、第34章で解説したメッセージングパターンを活用して、販売管理・財務会計・生産管理の3つのシステム間の具体的な連携パターンを解説します。

---

## 35.1 販売管理と財務会計の連携

販売管理システムと財務会計システムの連携は、基幹業務システム統合の中核となる部分です。売上、請求、入金などの取引データを正確に会計仕訳へ変換することが求められます。

### 連携の全体像

```plantuml
@startuml
title 販売管理と財務会計の連携全体像

package "販売管理システム" as sales {
    rectangle "受注管理" as order
    rectangle "出荷管理" as shipment
    rectangle "売上管理" as sales_mgmt
    rectangle "請求管理" as billing
    rectangle "入金管理" as receipt
}

package "イベントバス" as events {
    collections "売上イベント" as sales_event
    collections "請求イベント" as billing_event
    collections "入金イベント" as receipt_event
}

package "財務会計システム" as accounting {
    rectangle "自動仕訳" as auto_journal
    rectangle "仕訳管理" as journal
    rectangle "残高管理" as balance
    rectangle "決算処理" as closing
}

order --> shipment
shipment --> sales_mgmt
sales_mgmt --> billing
billing --> receipt

sales_mgmt --> sales_event : 売上計上
billing --> billing_event : 請求確定
receipt --> receipt_event : 入金確認

sales_event --> auto_journal
billing_event --> auto_journal
receipt_event --> auto_journal

auto_journal --> journal
journal --> balance

@enduml
```

### 売上データから仕訳データへの変換

売上計上時に発生する仕訳は、売上の種類や取引条件によって異なります。

```plantuml
@startuml
title 売上から仕訳への変換フロー

|販売管理|
start
:売上計上;
:売上イベント発行;

|イベントバス|
:売上イベント受信;

|財務会計|
:仕訳パターン判定;
note right
  ・商品グループ
  ・顧客グループ
  ・取引条件
end note

:仕訳明細生成;
note right
  借方：売掛金
  貸方：売上高
  貸方：仮受消費税
end note

:貸借一致検証;
:仕訳登録;
:残高更新;

stop

@enduml
```

#### 売上仕訳の基本パターン

```plantuml
@startuml
title 売上仕訳の基本パターン

object "売上データ" as sales {
    売上番号 = "SLS-2024-001"
    売上日 = "2024/01/15"
    顧客コード = "CUS-001"
    売上金額 = 110,000円
    消費税額 = 10,000円
    税抜金額 = 100,000円
}

object "仕訳伝票" as journal {
    伝票番号 = "JRN-2024-001"
    起票日 = "2024/01/15"
    伝票区分 = "売上仕訳"
}

object "借方明細" as debit {
    勘定科目 = "売掛金"
    金額 = 110,000円
    摘要 = "CUS-001 売上"
}

object "貸方明細1" as credit1 {
    勘定科目 = "売上高"
    金額 = 100,000円
    摘要 = "商品売上"
}

object "貸方明細2" as credit2 {
    勘定科目 = "仮受消費税"
    金額 = 10,000円
    摘要 = "消費税10%"
}

sales --> journal : 変換
journal --> debit
journal --> credit1
journal --> credit2

note bottom of journal
  【貸借一致の検証】
  借方合計：110,000円
  貸方合計：110,000円
  差額：0円 ✓
end note

@enduml
```

#### 売上仕訳パターンマスタ

```plantuml
@startuml
title 自動仕訳パターンマスタの構造

entity "自動仕訳パターン" as pattern {
    *パターンID : string
    --
    *取引種別 : string
    商品グループ : string
    顧客グループ : string
    借方科目コード : string
    貸方科目コード : string
    消費税科目コード : string
    適用開始日 : date
    適用終了日 : date
}

entity "勘定科目" as account {
    *科目コード : string
    --
    科目名 : string
    BSPL区分 : string
    貸借区分 : string
}

pattern }o--|| account : 借方科目
pattern }o--|| account : 貸方科目
pattern }o--|| account : 消費税科目

@enduml
```

<details>
<summary>仕訳パターンテーブル定義</summary>

```sql
-- 自動仕訳パターンマスタ
CREATE TABLE 自動仕訳パターン (
    パターンID VARCHAR(20) PRIMARY KEY,
    取引種別 VARCHAR(20) NOT NULL,
    商品グループ VARCHAR(10),
    顧客グループ VARCHAR(10),
    借方科目コード VARCHAR(10) NOT NULL,
    貸方科目コード VARCHAR(10) NOT NULL,
    消費税科目コード VARCHAR(10),
    適用開始日 DATE NOT NULL,
    適用終了日 DATE,
    作成日時 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    更新日時 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (借方科目コード) REFERENCES 勘定科目(科目コード),
    FOREIGN KEY (貸方科目コード) REFERENCES 勘定科目(科目コード),
    FOREIGN KEY (消費税科目コード) REFERENCES 勘定科目(科目コード)
);

-- サンプルデータ
INSERT INTO 自動仕訳パターン VALUES
('PTN-SALES-001', '売上', NULL, NULL, '1310', '4110', '2191', '2024-01-01', NULL),
('PTN-SALES-002', '売上', 'FOOD', NULL, '1310', '4120', '2191', '2024-01-01', NULL),
('PTN-RETURN-001', '売上返品', NULL, NULL, '4110', '1310', '2191', '2024-01-01', NULL);
```

</details>

### 自動仕訳パターンの適用

商品グループや顧客グループに応じて、適切な仕訳パターンを自動選択します。

```plantuml
@startuml
title 自動仕訳パターン判定ロジック

start

:売上イベント受信;

:商品グループ取得;
:顧客グループ取得;

if (特定商品グループ?) then (yes)
    :商品グループ専用\nパターン検索;
else (no)
    if (特定顧客グループ?) then (yes)
        :顧客グループ専用\nパターン検索;
    else (no)
        :汎用パターン検索;
    endif
endif

if (パターン見つかった?) then (yes)
    :仕訳明細生成;
    :仕訳登録;
else (no)
    :エラー通知;
    :手動仕訳待ち\nキューに投入;
endif

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 自動仕訳パターンリポジトリ
public interface JournalPatternRepository {
    Optional<JournalPattern> findByTransactionTypeAndGroups(
        TransactionType type,
        String productGroup,
        String customerGroup,
        LocalDate effectiveDate
    );
}

// 自動仕訳サービス
@Service
@Transactional
public class AutoJournalService {
    private final JournalPatternRepository patternRepository;
    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;

    @EventListener
    public void handleSalesCompleted(SalesCompletedEvent event) {
        // パターン検索（優先順位：商品グループ > 顧客グループ > 汎用）
        JournalPattern pattern = findPattern(event);

        // 仕訳伝票生成
        JournalEntry journal = createJournalEntry(event, pattern);

        // 貸借一致検証
        validateBalance(journal);

        // 仕訳登録
        journalRepository.save(journal);

        // 残高更新イベント発行
        publishBalanceUpdateEvent(journal);
    }

    private JournalPattern findPattern(SalesCompletedEvent event) {
        // 1. 商品グループ + 顧客グループで検索
        Optional<JournalPattern> pattern = patternRepository
            .findByTransactionTypeAndGroups(
                TransactionType.SALES,
                event.productGroup(),
                event.customerGroup(),
                event.salesDate()
            );

        if (pattern.isPresent()) return pattern.get();

        // 2. 商品グループのみで検索
        pattern = patternRepository.findByTransactionTypeAndGroups(
            TransactionType.SALES,
            event.productGroup(),
            null,
            event.salesDate()
        );

        if (pattern.isPresent()) return pattern.get();

        // 3. 顧客グループのみで検索
        pattern = patternRepository.findByTransactionTypeAndGroups(
            TransactionType.SALES,
            null,
            event.customerGroup(),
            event.salesDate()
        );

        if (pattern.isPresent()) return pattern.get();

        // 4. 汎用パターンで検索
        return patternRepository.findByTransactionTypeAndGroups(
            TransactionType.SALES,
            null,
            null,
            event.salesDate()
        ).orElseThrow(() -> new PatternNotFoundException(
            "仕訳パターンが見つかりません: " + event.salesId()
        ));
    }

    private JournalEntry createJournalEntry(
            SalesCompletedEvent event,
            JournalPattern pattern) {

        List<JournalLine> lines = new ArrayList<>();

        // 借方：売掛金
        lines.add(JournalLine.debit(
            pattern.debitAccountCode(),
            event.totalAmount(),
            "売掛金計上 " + event.customerName()
        ));

        // 貸方：売上高
        lines.add(JournalLine.credit(
            pattern.creditAccountCode(),
            event.netAmount(),
            "売上計上 " + event.salesId()
        ));

        // 貸方：仮受消費税
        if (event.taxAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(JournalLine.credit(
                pattern.taxAccountCode(),
                event.taxAmount(),
                "仮受消費税"
            ));
        }

        return new JournalEntry(
            generateJournalId(),
            event.salesDate(),
            JournalType.AUTO_SALES,
            event.salesId(),
            lines
        );
    }

    private void validateBalance(JournalEntry journal) {
        BigDecimal debitTotal = journal.lines().stream()
            .filter(l -> l.debitCredit() == DebitCredit.DEBIT)
            .map(JournalLine::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditTotal = journal.lines().stream()
            .filter(l -> l.debitCredit() == DebitCredit.CREDIT)
            .map(JournalLine::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new BalanceMismatchException(
                "貸借不一致: 借方=" + debitTotal + ", 貸方=" + creditTotal
            );
        }
    }
}
```

</details>

### イベント駆動による仕訳生成

販売管理システムで発生する各種イベントに応じて、自動的に仕訳を生成します。

```plantuml
@startuml
title 販売イベントと仕訳の対応

' 配置を制御するための設定
skinparam nodesep 50
skinparam ranksep 50

rectangle "販売管理イベント" as sales_events {
    rectangle "売上計上" as sales_completed
    rectangle "売上返品" as sales_return
    rectangle "請求確定" as billing_confirmed
    rectangle "入金確認" as payment_received
    rectangle "貸倒発生" as bad_debt
}

rectangle "自動仕訳" as auto_journal {
    rectangle "売上仕訳\n生成" as sales_journal
    rectangle "返品仕訳\n生成" as return_journal
    rectangle "（請求時仕訳\nなし）" as billing_journal
    rectangle "入金仕訳\n生成" as payment_journal
    rectangle "貸倒仕訳\n生成" as bad_debt_journal
}

rectangle "仕訳パターン" as patterns {
    rectangle "==売上仕訳\n\n借方: 売掛金\n貸方: 売上高\n貸方: 仮受消費税" as p1
    rectangle "==返品仕訳\n\n借方: 売上高\n借方: 仮受消費税\n貸方: 売掛金" as p2
    rectangle "==入金仕訳\n\n借方: 普通預金\n貸方: 売掛金" as p3
    rectangle "==貸倒仕訳\n\n借方: 貸倒損失\n貸方: 売掛金" as p4
}

sales_completed --> sales_journal
sales_return --> return_journal
billing_confirmed --> billing_journal
payment_received --> payment_journal
bad_debt --> bad_debt_journal

sales_journal --> p1
return_journal --> p2
payment_journal --> p3
bad_debt_journal --> p4

@enduml
```

#### 入金消込と仕訳生成

```plantuml
@startuml
title 入金消込と仕訳生成フロー

|販売管理|
start
:入金データ受信;
:請求データ照合;

if (消込対象あり?) then (yes)
    :入金消込処理;
    :消込イベント発行;
else (no)
    :前受金計上;
    :前受金イベント発行;
endif

|財務会計|
if (消込イベント?) then (yes)
    :入金仕訳生成;
    note right
      借方: 普通預金
      貸方: 売掛金
    end note
else (no)
    :前受金仕訳生成;
    note right
      借方: 普通預金
      貸方: 前受金
    end note
endif

:仕訳登録;
:売掛金残高更新;

stop

@enduml
```

---

## 35.2 販売管理と生産管理の連携

販売管理システムと生産管理システムの連携は、需要と供給のバランスを取るために重要です。受注情報を生産計画に反映し、在庫情報を双方向で同期します。

### 連携の全体像

```plantuml
@startuml
title 販売管理と生産管理の連携全体像

package "販売管理システム" as sales {
    rectangle "受注管理" as order
    rectangle "出荷管理" as shipment
    rectangle "在庫照会" as sales_inv
}

package "イベントバス" as events {
    collections "受注イベント" as order_event
    collections "在庫イベント" as inv_event
    collections "完成イベント" as complete_event
}

package "生産管理システム" as production {
    rectangle "生産計画" as plan
    rectangle "MRP" as mrp
    rectangle "製造管理" as manufacturing
    rectangle "在庫管理" as prod_inv
}

order --> order_event : 受注確定
order_event --> plan : 需要情報

plan --> mrp : 所要量展開
mrp --> manufacturing : 製造指示

manufacturing --> complete_event : 完成報告
complete_event --> prod_inv : 在庫計上
prod_inv --> inv_event : 在庫更新

inv_event --> sales_inv : 在庫同期
sales_inv --> shipment : 引当可能数

@enduml
```

### 受注情報から生産計画への連携

```plantuml
@startuml
title 受注から生産計画への連携フロー

|販売管理|
start
:受注登録;
:受注確定;
:受注イベント発行;

|生産計画|
:受注イベント受信;
:需要情報登録;

:在庫・発注残確認;
if (在庫で充足?) then (yes)
    :引当処理;
else (no)
    :正味所要量計算;
    if (製品?) then (yes)
        :製造オーダ作成;
    else (no)
        :発注提案作成;
    endif
endif

:計画確定;
:計画確定イベント発行;

|販売管理|
:納期回答更新;

stop

@enduml
```

#### 受注イベントの構造

```plantuml
@startuml
title 受注イベントの構造

class "OrderConfirmedEvent" as event {
    +eventId: String
    +timestamp: Instant
    +orderId: String
    +customerId: String
    +requestedDeliveryDate: LocalDate
    +priority: Priority
    +lines: List<OrderLineEvent>
}

class "OrderLineEvent" as line {
    +lineNumber: int
    +productId: String
    +quantity: BigDecimal
    +unit: String
    +requestedDate: LocalDate
}

event "1" -- "*" line

note right of event
  【生産計画に必要な情報】
  ・製品コード
  ・必要数量
  ・希望納期
  ・優先度
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 受注確定イベント
public record OrderConfirmedEvent(
    String eventId,
    Instant timestamp,
    String orderId,
    String customerId,
    LocalDate requestedDeliveryDate,
    Priority priority,
    List<OrderLineEvent> lines
) {
    public enum Priority {
        URGENT,   // 緊急
        HIGH,     // 高
        NORMAL,   // 通常
        LOW       // 低
    }
}

// 生産計画サービス
@Service
public class ProductionPlanningService {
    private final DemandRepository demandRepository;
    private final InventoryRepository inventoryRepository;
    private final MrpService mrpService;

    @EventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        // 需要情報として登録
        for (OrderLineEvent line : event.lines()) {
            Demand demand = Demand.fromOrder(
                event.orderId(),
                line.productId(),
                line.quantity(),
                line.requestedDate(),
                event.priority()
            );
            demandRepository.save(demand);

            // 在庫引当を試行
            AllocationResult result = tryAllocate(demand);

            if (!result.isFullyAllocated()) {
                // MRP実行して製造/発注オーダを生成
                mrpService.execute(demand);
            }
        }

        // 納期回答イベント発行
        publishDeliveryDateResponse(event.orderId());
    }

    private AllocationResult tryAllocate(Demand demand) {
        BigDecimal available = inventoryRepository
            .findAvailableQuantity(demand.productId());

        if (available.compareTo(demand.quantity()) >= 0) {
            // 引当実行
            inventoryRepository.allocate(
                demand.productId(),
                demand.quantity(),
                demand.demandId()
            );
            return AllocationResult.fullyAllocated(demand.quantity());
        } else {
            // 部分引当
            if (available.compareTo(BigDecimal.ZERO) > 0) {
                inventoryRepository.allocate(
                    demand.productId(),
                    available,
                    demand.demandId()
                );
            }
            return AllocationResult.partiallyAllocated(
                available,
                demand.quantity().subtract(available)
            );
        }
    }
}
```

</details>

### 需要予測データの共有

```plantuml
@startuml
title 需要予測データの共有

package "販売管理" as sales {
    rectangle "販売実績" as sales_history
    rectangle "受注残" as backlog
    rectangle "見積案件" as quotation
}

package "需要予測エンジン" as forecast {
    rectangle "時系列分析" as time_series
    rectangle "季節調整" as seasonal
    rectangle "トレンド分析" as trend
}

package "生産計画" as planning {
    rectangle "MPS\n(基準生産計画)" as mps
    rectangle "MRP\n(所要量計画)" as mrp
}

sales_history --> forecast
backlog --> forecast
quotation --> forecast

forecast --> mps : 需要予測データ
mps --> mrp : 計画生産量

note right of forecast
  【予測データの内容】
  ・製品別月次予測数量
  ・予測精度（信頼区間）
  ・季節変動係数
  ・トレンド方向
end note

@enduml
```

#### 需要予測イベント

```plantuml
@startuml
title 需要予測の連携サイクル

|販売管理|
start
:月次販売実績集計;
:受注残・見積集計;
:需要予測更新;
:予測データ発行;

|生産計画|
:予測データ受信;
:MPS更新;
note right
  ・月次生産計画
  ・安全在庫調整
  ・生産能力確認
end note

:MRP実行;
:発注提案生成;
:製造計画生成;

if (計画変更あり?) then (yes)
    :計画変更通知;
endif

|販売管理|
:納期情報更新;

stop

@enduml
```

### 在庫情報の同期

販売管理と生産管理の両方で在庫を管理する場合、整合性を保つ必要があります。

```plantuml
@startuml
title 在庫情報の同期パターン

package "販売管理" as sales {
    database "販売在庫ビュー\n----\n在庫数\n引当数\n有効在庫数" as sales_inv
}

package "生産管理" as production {
    database "生産在庫（マスタ）\n----\n在庫数\n引当数\n品質状態\nロット情報" as prod_inv
}

package "同期メカニズム" as sync {
    collections "在庫更新\nイベント" as inv_event
    rectangle "在庫同期\nサービス" as sync_svc
}

prod_inv --> inv_event : 在庫変動時
inv_event --> sync_svc
sync_svc --> sales_inv : ビュー更新

note bottom of sync
  【同期タイミング】
  ・完成時（入庫）
  ・出荷時（出庫）
  ・棚卸差異発生時
  ・品質状態変更時

  【同期内容】
  ・製品別在庫数
  ・引当可能数
  ・入庫予定
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 在庫更新イベント
public record InventoryUpdatedEvent(
    String eventId,
    Instant timestamp,
    String productId,
    String warehouseId,
    BigDecimal previousQuantity,
    BigDecimal currentQuantity,
    BigDecimal allocatedQuantity,
    UpdateReason reason
) {
    public enum UpdateReason {
        COMPLETION,     // 製造完成
        SHIPMENT,       // 出荷
        RECEIPT,        // 入荷
        ADJUSTMENT,     // 棚卸調整
        QUALITY_CHANGE  // 品質状態変更
    }

    public BigDecimal getAvailableQuantity() {
        return currentQuantity.subtract(allocatedQuantity);
    }
}

// 在庫同期サービス
@Service
public class InventorySyncService {
    private final SalesInventoryRepository salesInventoryRepository;

    @EventListener
    public void handleInventoryUpdated(InventoryUpdatedEvent event) {
        // 販売在庫ビューを更新
        SalesInventory salesInv = salesInventoryRepository
            .findByProductId(event.productId())
            .orElse(new SalesInventory(event.productId()));

        salesInv.updateQuantity(
            event.currentQuantity(),
            event.allocatedQuantity()
        );

        salesInventoryRepository.save(salesInv);

        // 低在庫アラート
        if (salesInv.getAvailableQuantity()
                .compareTo(salesInv.getSafetyStock()) < 0) {
            publishLowStockAlert(event.productId());
        }
    }
}
```

</details>

---

## 35.3 生産管理と財務会計の連携

生産管理システムと財務会計システムの連携は、製造原価の正確な把握と会計処理に不可欠です。

### 連携の全体像

```plantuml
@startuml
title 生産管理と財務会計の連携全体像

package "生産管理システム" as production {
    rectangle "購買管理" as purchase
    rectangle "製造管理" as manufacturing
    rectangle "在庫管理" as inventory
    rectangle "原価管理" as cost
}

package "イベントバス" as events {
    collections "検収イベント" as receipt_event
    collections "完成イベント" as complete_event
    collections "棚卸イベント" as inventory_event
    collections "原価イベント" as cost_event
}

package "財務会計システム" as accounting {
    rectangle "自動仕訳" as auto_journal
    rectangle "仕訳管理" as journal
    rectangle "原価会計" as cost_acc
}

purchase --> receipt_event : 検収確定
manufacturing --> complete_event : 製造完成
inventory --> inventory_event : 棚卸差異
cost --> cost_event : 原価計算完了

receipt_event --> auto_journal
complete_event --> auto_journal
inventory_event --> auto_journal
cost_event --> cost_acc

auto_journal --> journal

@enduml
```

### 製造原価から仕訳への変換

製造原価は、材料費・労務費・製造間接費の3要素で構成されます。

```plantuml
@startuml
title 製造原価の構成と仕訳

object "製造原価" as cost {
    製造オーダ = "WO-2024-001"
    製品コード = "PRD-001"
    完成数量 = 100
}

object "材料費" as material {
    直接材料費 = 50,000円
    間接材料費 = 5,000円
}

object "労務費" as labor {
    直接労務費 = 30,000円
    間接労務費 = 10,000円
}

object "製造間接費" as overhead {
    配賦額 = 15,000円
}

object "原価仕訳" as journal {
    借方: 製品 110,000円
    貸方: 仕掛品 110,000円
}

cost --> material
cost --> labor
cost --> overhead

material --> journal : 55,000円
labor --> journal : 40,000円
overhead --> journal : 15,000円

note bottom of journal
  【製造完成時の仕訳】
  借方：製品（資産）
  貸方：仕掛品（資産）

  製品原価 = 材料費 + 労務費 + 製造間接費
           = 55,000 + 40,000 + 15,000
           = 110,000円
end note

@enduml
```

#### 製造原価計算フロー

```plantuml
@startuml
title 製造原価計算と仕訳生成フロー

|生産管理|
start
:製造完成報告;

:材料消費実績集計;
note right
  ・直接材料費
  ・間接材料費
end note

:工数実績集計;
note right
  ・直接労務費
  ・間接労務費
end note

:製造間接費配賦;
note right
  ・配賦基準で按分
  （直接作業時間等）
end note

:製品原価計算;
:完成原価イベント発行;

|財務会計|
:原価イベント受信;
:製造完成仕訳生成;
note right
  借方: 製品
  貸方: 仕掛品
end note

:仕訳登録;
:在庫評価額更新;

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 製造完成原価イベント
public record ManufacturingCompletedEvent(
    String eventId,
    Instant timestamp,
    String workOrderId,
    String productId,
    BigDecimal completedQuantity,
    CostBreakdown costBreakdown
) {
    public record CostBreakdown(
        BigDecimal directMaterialCost,
        BigDecimal indirectMaterialCost,
        BigDecimal directLaborCost,
        BigDecimal indirectLaborCost,
        BigDecimal manufacturingOverhead
    ) {
        public BigDecimal getTotalCost() {
            return directMaterialCost
                .add(indirectMaterialCost)
                .add(directLaborCost)
                .add(indirectLaborCost)
                .add(manufacturingOverhead);
        }

        public BigDecimal getMaterialCost() {
            return directMaterialCost.add(indirectMaterialCost);
        }

        public BigDecimal getLaborCost() {
            return directLaborCost.add(indirectLaborCost);
        }
    }
}

// 製造原価仕訳サービス
@Service
public class ManufacturingJournalService {
    private final JournalRepository journalRepository;

    @EventListener
    public void handleManufacturingCompleted(ManufacturingCompletedEvent event) {
        CostBreakdown cost = event.costBreakdown();

        List<JournalLine> lines = new ArrayList<>();

        // 借方：製品
        lines.add(JournalLine.debit(
            AccountCode.FINISHED_GOODS,
            cost.getTotalCost(),
            "製造完成 " + event.workOrderId()
        ));

        // 貸方：仕掛品
        lines.add(JournalLine.credit(
            AccountCode.WORK_IN_PROCESS,
            cost.getTotalCost(),
            "仕掛品振替"
        ));

        JournalEntry journal = new JournalEntry(
            generateJournalId(),
            LocalDate.now(),
            JournalType.MANUFACTURING_COMPLETION,
            event.workOrderId(),
            lines
        );

        journalRepository.save(journal);
    }
}
```

</details>

### 検収データの会計連携

仕入先からの購買品検収時に、買掛金を計上します。

```plantuml
@startuml
title 検収から仕訳への連携

|生産管理|
start
:入荷受入;
:受入検査;

if (検査合格?) then (yes)
    :検収処理;
    :在庫計上;
    :検収イベント発行;
else (no)
    :返品処理;
    stop
endif

|財務会計|
:検収イベント受信;
:仕入仕訳生成;
note right
  借方: 材料/仕掛品
  借方: 仮払消費税
  貸方: 買掛金
end note

:仕訳登録;
:買掛金残高更新;

stop

@enduml
```

#### 検収仕訳のパターン

```plantuml
@startuml
title 検収仕訳のパターン

object "検収データ" as receipt {
    検収番号 = "RCV-2024-001"
    検収日 = "2024/01/15"
    仕入先 = "SUP-001"
    検収金額 = 55,000円
    消費税額 = 5,000円
}

object "材料検収仕訳" as material_journal {
    借方: 材料 50,000円
    借方: 仮払消費税 5,000円
    貸方: 買掛金 55,000円
}

object "外注検収仕訳" as subcontract_journal {
    借方: 外注加工費 50,000円
    借方: 仮払消費税 5,000円
    貸方: 買掛金 55,000円
}

receipt --> material_journal : 材料の場合
receipt --> subcontract_journal : 外注の場合

@enduml
```

### 棚卸差異の会計処理

棚卸で発見された差異は、適切に会計処理する必要があります。

```plantuml
@startuml
title 棚卸差異の会計処理フロー

|生産管理|
start
:棚卸実施;
:帳簿在庫と\n実地在庫の比較;

if (差異あり?) then (yes)
    :差異原因分析;

    if (帳簿 > 実地?) then (棚卸減耗)
        :減耗損計上;
        :棚卸減耗\nイベント発行;
    else (帳簿 < 実地)
        :在庫増加調整;
        :棚卸増加\nイベント発行;
    endif
else (no)
    :差異なし\n処理終了;
    stop
endif

|財務会計|
:棚卸イベント受信;

if (減耗?) then (yes)
    :減耗仕訳生成;
    note right
      借方: 棚卸減耗損
      貸方: 材料/製品
    end note
else (no)
    :増加仕訳生成;
    note right
      借方: 材料/製品
      貸方: 雑収入
    end note
endif

:仕訳登録;
:在庫評価額更新;

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 棚卸差異イベント
public record InventoryAdjustmentEvent(
    String eventId,
    Instant timestamp,
    String productId,
    String warehouseId,
    BigDecimal bookQuantity,
    BigDecimal actualQuantity,
    BigDecimal differenceQuantity,
    BigDecimal unitCost,
    AdjustmentType type,
    String reason
) {
    public enum AdjustmentType {
        SHRINKAGE,  // 減耗（帳簿 > 実地）
        SURPLUS     // 過剰（帳簿 < 実地）
    }

    public BigDecimal getAdjustmentAmount() {
        return differenceQuantity.abs().multiply(unitCost);
    }
}

// 棚卸仕訳サービス
@Service
public class InventoryAdjustmentJournalService {
    private final JournalRepository journalRepository;

    @EventListener
    public void handleInventoryAdjustment(InventoryAdjustmentEvent event) {
        List<JournalLine> lines = new ArrayList<>();

        if (event.type() == AdjustmentType.SHRINKAGE) {
            // 減耗の場合
            lines.add(JournalLine.debit(
                AccountCode.INVENTORY_SHRINKAGE_LOSS,
                event.getAdjustmentAmount(),
                "棚卸減耗 " + event.productId() + " " + event.reason()
            ));
            lines.add(JournalLine.credit(
                determineInventoryAccount(event.productId()),
                event.getAdjustmentAmount(),
                "在庫減少"
            ));
        } else {
            // 過剰の場合
            lines.add(JournalLine.debit(
                determineInventoryAccount(event.productId()),
                event.getAdjustmentAmount(),
                "在庫増加"
            ));
            lines.add(JournalLine.credit(
                AccountCode.MISCELLANEOUS_INCOME,
                event.getAdjustmentAmount(),
                "棚卸差益 " + event.productId()
            ));
        }

        JournalEntry journal = new JournalEntry(
            generateJournalId(),
            LocalDate.now(),
            JournalType.INVENTORY_ADJUSTMENT,
            event.eventId(),
            lines
        );

        journalRepository.save(journal);
    }

    private String determineInventoryAccount(String productId) {
        // 製品か材料かで勘定科目を決定
        Product product = productRepository.findById(productId).orElseThrow();
        return switch (product.type()) {
            case FINISHED_GOODS -> AccountCode.FINISHED_GOODS;
            case WORK_IN_PROCESS -> AccountCode.WORK_IN_PROCESS;
            case RAW_MATERIAL -> AccountCode.RAW_MATERIALS;
            default -> AccountCode.SUPPLIES;
        };
    }
}
```

</details>

---

## 35.4 三システム統合の全体像

販売・生産・会計の3システムを統合した全体像を整理します。

```plantuml
@startuml
title 基幹業務システム統合の全体像

package "販売管理" as sales {
    rectangle "受注" as order
    rectangle "出荷" as shipment
    rectangle "売上" as sales_tx
    rectangle "請求" as billing
    rectangle "入金" as receipt
}

package "生産管理" as production {
    rectangle "生産計画" as plan
    rectangle "購買" as purchase
    rectangle "製造" as manufacturing
    rectangle "在庫" as inventory
    rectangle "原価" as cost
}

package "財務会計" as accounting {
    rectangle "仕訳" as journal
    rectangle "売掛金" as ar
    rectangle "買掛金" as ap
    rectangle "在庫資産" as inv_asset
}

' 販売→生産
order --> plan : 受注情報
inventory --> shipment : 在庫引当

' 販売→会計
sales_tx --> journal : 売上仕訳
billing --> ar : 請求
receipt --> ar : 入金消込

' 生産→会計
purchase --> ap : 検収仕訳
manufacturing --> inv_asset : 完成仕訳
cost --> journal : 原価仕訳

' 在庫
inventory --> inv_asset : 在庫評価

@enduml
```

### イベントカタログ

```plantuml
@startuml
title システム間連携イベントカタログ

class "販売イベント" as sales_events {
    OrderConfirmed（受注確定）
    ShipmentCompleted（出荷完了）
    SalesCompleted（売上計上）
    BillingConfirmed（請求確定）
    PaymentReceived（入金確認）
}

class "生産イベント" as prod_events {
    DemandRegistered（需要登録）
    WorkOrderReleased（製造指示）
    ProductionCompleted（製造完成）
    ReceiptConfirmed（検収確定）
    InventoryAdjusted（在庫調整）
}

class "会計イベント" as acc_events {
    JournalPosted（仕訳計上）
    BalanceUpdated（残高更新）
    MonthEndClosed（月次締め）
}

note right of sales_events
  【発行元】販売管理
  【購読者】生産計画、自動仕訳
end note

note right of prod_events
  【発行元】生産管理
  【購読者】販売在庫、自動仕訳
end note

note right of acc_events
  【発行元】財務会計
  【購読者】経営ダッシュボード
end note

@enduml
```

---

## 35.5 まとめ

本章では、基幹業務システムの具体的な連携パターンについて解説しました。

### 学んだ連携パターン

| 連携パターン | 発生元 | 連携先 | 主な処理 |
|------------|-------|-------|---------|
| 売上→仕訳 | 販売管理 | 財務会計 | 売掛金・売上計上 |
| 入金→消込 | 販売管理 | 財務会計 | 売掛金消込 |
| 受注→計画 | 販売管理 | 生産管理 | 需要登録・MRP |
| 完成→在庫 | 生産管理 | 販売管理 | 在庫同期 |
| 検収→仕訳 | 生産管理 | 財務会計 | 買掛金計上 |
| 完成→仕訳 | 生産管理 | 財務会計 | 製品・仕掛品振替 |
| 棚卸→仕訳 | 生産管理 | 財務会計 | 棚卸損益計上 |

### 連携設計のポイント

1. **イベント駆動の採用**

   - システム間の疎結合を実現
   - 非同期処理による可用性向上
   - 監査証跡の確保

2. **自動仕訳パターンの標準化**

   - 取引種別ごとの仕訳パターン定義
   - 柔軟な条件分岐（商品/顧客グループ）
   - 例外処理の明確化

3. **データ整合性の確保**

   - イベント順序の保証
   - 補償トランザクション
   - 定期的な突合処理

### 次章の予告

第36章では、マスタデータ管理（MDM）について解説します。複数システムで共有されるマスタデータの一元管理方法と、MDMパターンの選択基準を学びます。
