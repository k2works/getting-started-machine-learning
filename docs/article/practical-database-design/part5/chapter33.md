---
type: Article
title: "第33章：システム統合の概要"
description: "本章から第5部「エンタープライズインテグレーション」の解説に入ります。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第33章：システム統合の概要

本章から第5部「エンタープライズインテグレーション」の解説に入ります。これまで解説してきた販売管理システム、財務会計システム、生産管理システムを統合し、企業全体として整合性のあるシステムを構築するための考え方とパターンについて解説します。

---

## 33.1 なぜシステム統合が必要か

### サイロ化した基幹業務システムの課題

企業の基幹業務システムは、歴史的に各業務部門のニーズに応じて個別に構築されてきました。この結果、システム間の連携が不十分な「サイロ化」した状態に陥りやすくなっています。

```plantuml
@startuml
title サイロ化した基幹業務システムの課題

rectangle "営業部門" as sales_dept {
    database "販売管理DB" as sales_db
    rectangle "販売管理\nシステム" as sales_sys
}

rectangle "経理部門" as finance_dept {
    database "会計DB" as finance_db
    rectangle "財務会計\nシステム" as finance_sys
}

rectangle "製造部門" as mfg_dept {
    database "生産管理DB" as mfg_db
    rectangle "生産管理\nシステム" as mfg_sys
}

sales_sys --> sales_db
finance_sys --> finance_db
mfg_sys --> mfg_db

note right of sales_dept
  ・顧客マスタの重複管理
  ・売上データの手動転記
  ・在庫情報の不整合
end note

note right of finance_dept
  ・仕訳入力の二重作業
  ・月次締めの遅延
  ・データ不一致の調整作業
end note

note right of mfg_dept
  ・受注情報の遅延
  ・在庫の二重管理
  ・原価情報の不正確さ
end note

@enduml
```

サイロ化によって発生する主な課題は以下の通りです。

| 課題カテゴリ | 具体的な問題 | 影響 |
|------------|------------|------|
| データ重複 | 同じマスタデータが複数システムに存在 | 更新漏れ、データ不整合 |
| 手作業連携 | システム間のデータ転記が手動 | 作業コスト増、転記ミス |
| 整合性欠如 | 各システムのデータが一致しない | 経営判断の遅延、誤り |
| リアルタイム性欠如 | 情報の伝達に時間がかかる | 機会損失、過剰在庫 |

### データの一貫性と整合性の確保

システム統合の最大の目的は、企業全体でのデータの一貫性と整合性を確保することです。

```plantuml
@startuml
title データの一貫性確保の全体像

rectangle "統合された基幹業務システム" {
    rectangle "販売管理" as sales
    rectangle "財務会計" as finance
    rectangle "生産管理" as production

    database "共通マスタ" as master {
        rectangle "取引先マスタ"
        rectangle "商品/品目マスタ"
        rectangle "部門マスタ"
    }
}

rectangle "統合レイヤー" as integration {
    rectangle "イベントバス" as events
    rectangle "API Gateway" as api
    rectangle "MDM" as mdm
}

sales <--> integration
finance <--> integration
production <--> integration
integration <--> master

note bottom of integration
  ・リアルタイムイベント連携
  ・マスタデータの一元管理
  ・トランザクション整合性
end note

@enduml
```

#### データ一貫性の3つのレベル

```plantuml
@startuml
title データ一貫性のレベル

package "強い一貫性 (Strong Consistency)" {
    note as N1
    ・分散トランザクション
    ・2フェーズコミット
    ・リアルタイム同期

    適用例：
    - 在庫引当と受注確定
    - 請求と売掛金計上
    end note
}

package "結果整合性 (Eventual Consistency)" {
    note as N2
    ・非同期メッセージング
    ・補償トランザクション
    ・最終的な整合性

    適用例：
    - 売上から仕訳への自動転記
    - 実績から原価への集計
    end note
}

package "弱い一貫性 (Weak Consistency)" {
    note as N3
    ・バッチ処理による同期
    ・定期的な突合
    ・差異分析と調整

    適用例：
    - 月次締め処理
    - マスタ同期バッチ
    end note
}

N1 -[hidden]-> N2
N2 -[hidden]-> N3

@enduml
```

### リアルタイム連携とバッチ連携

システム統合における連携方式は、業務要件に応じて適切に選択する必要があります。

```plantuml
@startuml
title リアルタイム連携とバッチ連携の比較

rectangle "リアルタイム連携" as realtime {
    rectangle "イベント駆動" as event
    rectangle "API 呼び出し" as api
    rectangle "メッセージング" as messaging
}

rectangle "バッチ連携" as batch {
    rectangle "ファイル連携" as file
    rectangle "ETL 処理" as etl
    rectangle "DB 連携" as db
}

note right of realtime
  【特徴】
  ・即座にデータ反映
  ・トランザクション単位
  ・高い整合性

  【適用例】
  ・受注→在庫引当
  ・出荷→売上計上
  ・仕入→買掛金計上
end note

note right of batch
  【特徴】
  ・一括処理で効率的
  ・大量データに適する
  ・システム負荷を分散

  【適用例】
  ・月次締め処理
  ・日次集計処理
  ・マスタ同期
end note

@enduml
```

#### 連携方式の選択基準

| 要件 | リアルタイム連携 | バッチ連携 |
|-----|----------------|-----------|
| データ鮮度 | 即座に反映が必要 | 定期的な反映で可 |
| 処理量 | トランザクション単位 | 大量データの一括処理 |
| 可用性要件 | 高可用性が必要 | 一時的な遅延許容可 |
| 整合性要件 | 強い一貫性が必要 | 結果整合性で可 |
| システム負荷 | 常時負荷発生 | 負荷を時間帯で分散 |

---

## 33.2 境界づけられたコンテキスト

### 境界づけられたコンテキスト（Bounded Context）とは

ドメイン駆動設計（DDD）における「境界づけられたコンテキスト」は、特定のドメインモデルが適用される範囲を明確に定義する概念です。同じ用語でも、コンテキストによって意味や属性が異なることがあります。

```plantuml
@startuml
title 境界づけられたコンテキストの概念

package "販売コンテキスト" as sales_ctx {
    class "商品" as sales_product {
        +商品コード
        +商品名
        +販売単価
        +在庫数
    }
    class "顧客" as customer {
        +顧客コード
        +顧客名
        +与信限度額
    }
}

package "生産コンテキスト" as prod_ctx {
    class "品目" as item {
        +品目コード
        +品目名
        +製造リードタイム
        +安全在庫数
    }
    class "BOM" as bom {
        +親品目
        +子品目
        +必要量
    }
}

package "会計コンテキスト" as acc_ctx {
    class "勘定科目" as account {
        +科目コード
        +科目名
        +BSPL区分
    }
    class "取引先" as partner {
        +取引先コード
        +取引先名
        +債権債務区分
    }
}

note bottom of sales_ctx
  「商品」は販売価格と
  在庫数で管理
end note

note bottom of prod_ctx
  「品目」は製造観点で
  リードタイムとBOMで管理
end note

note bottom of acc_ctx
  「取引先」は会計観点で
  債権・債務で管理
end note

@enduml
```

### 基幹業務システムにおけるコンテキストの識別

本書で扱う基幹業務システムは、以下の3つの主要な境界づけられたコンテキストで構成されます。

```plantuml
@startuml
title 基幹業務システムのコンテキスト識別

package "販売コンテキスト (Sales Context)" as sales {
    rectangle "受注管理" as order
    rectangle "出荷管理" as shipment
    rectangle "売上管理" as sales_mgmt
    rectangle "債権管理" as receivable
    rectangle "調達管理" as procurement
    rectangle "在庫管理\n(販売)" as sales_inv
    rectangle "債務管理" as payable
}

package "会計コンテキスト (Accounting Context)" as accounting {
    rectangle "勘定科目管理" as account_mgmt
    rectangle "仕訳管理" as journal
    rectangle "自動仕訳" as auto_journal
    rectangle "残高管理" as balance
    rectangle "決算処理" as closing
}

package "生産コンテキスト (Production Context)" as production {
    rectangle "生産計画" as plan
    rectangle "購買管理" as purchase
    rectangle "工程管理" as process
    rectangle "在庫管理\n(生産)" as prod_inv
    rectangle "品質管理" as quality
    rectangle "原価管理" as cost
}

sales --> accounting : 売上仕訳
sales --> production : 受注情報
production --> accounting : 原価仕訳
production --> sales : 完成品在庫

@enduml
```

#### 販売コンテキスト

販売コンテキストは、顧客との取引に関するすべての業務を管理します。

```plantuml
@startuml
title 販売コンテキストのドメインモデル

class "受注" as Order {
    +受注番号
    +受注日
    +顧客コード
    +合計金額
    +ステータス
    --
    +受注確定()
    +キャンセル()
}

class "受注明細" as OrderLine {
    +行番号
    +商品コード
    +数量
    +単価
    +金額
}

class "出荷指示" as ShipmentOrder {
    +出荷番号
    +出荷日
    +出荷先
    +ステータス
    --
    +出荷確定()
}

class "売上" as Sales {
    +売上番号
    +売上日
    +売上金額
    +消費税額
    --
    +計上()
    +取消()
}

Order "1" -- "*" OrderLine
Order "1" -- "0..1" ShipmentOrder
ShipmentOrder "1" -- "0..1" Sales

note right of Order
  集約ルート
  受注全体のライフサイクル管理
end note

@enduml
```

#### 会計コンテキスト

会計コンテキストは、企業の財務情報を正確に記録・管理します。

```plantuml
@startuml
title 会計コンテキストのドメインモデル

class "仕訳伝票" as Journal {
    +伝票番号
    +起票日
    +伝票区分
    +ステータス
    --
    +登録()
    +承認()
    +取消()
}

class "仕訳明細" as JournalLine {
    +行番号
    +摘要
}

class "仕訳貸借明細" as JournalEntry {
    +貸借区分
    +勘定科目
    +金額
    +部門
}

class "勘定科目" as Account {
    +科目コード
    +科目名
    +BSPL区分
    +貸借区分
}

class "月次残高" as MonthlyBalance {
    +年月
    +科目コード
    +借方合計
    +貸方合計
    +残高
}

Journal "1" -- "*" JournalLine
JournalLine "1" -- "2..*" JournalEntry
JournalEntry "*" -- "1" Account
Account "1" -- "*" MonthlyBalance

note right of Journal
  集約ルート
  貸借一致の整合性を保証
end note

@enduml
```

#### 生産コンテキスト

生産コンテキストは、製造業務の計画から実績管理までを担当します。

```plantuml
@startuml
title 生産コンテキストのドメインモデル

class "製造オーダ" as WorkOrder {
    +オーダ番号
    +品目コード
    +計画数量
    +開始日
    +完了日
    +ステータス
    --
    +発行()
    +着手()
    +完了()
}

class "作業指示" as WorkInstruction {
    +指示番号
    +工程コード
    +作業日
    +計画工数
}

class "完成実績" as CompletionResult {
    +実績番号
    +完成数量
    +良品数
    +不良数
    +実績工数
}

class "品目" as Item {
    +品目コード
    +品目名
    +品目区分
    +リードタイム
}

class "BOM" as BOM {
    +親品目
    +子品目
    +必要量
    +歩留率
}

WorkOrder "1" -- "*" WorkInstruction
WorkInstruction "1" -- "0..1" CompletionResult
WorkOrder "*" -- "1" Item
Item "1" -- "*" BOM : 親品目

note right of WorkOrder
  集約ルート
  製造ライフサイクル管理
end note

@enduml
```

### コンテキストマップの作成

コンテキストマップは、複数の境界づけられたコンテキスト間の関係を視覚化したものです。

```plantuml
@startuml
title 基幹業務システムのコンテキストマップ

skinparam rectangle {
    BackgroundColor<<upstream>> LightBlue
    BackgroundColor<<downstream>> LightYellow
    BackgroundColor<<shared>> LightGreen
}

rectangle "販売コンテキスト" as sales <<upstream>> {
    rectangle "受注" as order
    rectangle "売上" as revenue
    rectangle "債権" as receivable
}

rectangle "会計コンテキスト" as accounting <<downstream>> {
    rectangle "仕訳" as journal
    rectangle "残高" as balance
}

rectangle "生産コンテキスト" as production <<upstream>> {
    rectangle "製造" as manufacturing
    rectangle "原価" as cost
}

rectangle "共有カーネル" as shared <<shared>> {
    rectangle "取引先マスタ"
    rectangle "部門マスタ"
}

sales -down-> accounting : "U/D\n自動仕訳\n(Published Language)"
production -down-> accounting : "U/D\n原価仕訳\n(Published Language)"
sales -right-> production : "Customer/Supplier\n受注連携"

sales --> shared
accounting --> shared
production --> shared

note right of sales
  【Upstream】
  売上イベントを発行
end note

note right of accounting
  【Downstream】
  売上/原価イベントを購読
  自動仕訳を生成
end note

note bottom of shared
  【Shared Kernel】
  全コンテキスト共通の
  マスタデータ
end note

@enduml
```

### コンテキスト間の関係パターン

ドメイン駆動設計では、コンテキスト間の関係を表す以下のパターンが定義されています。

#### 共有カーネル（Shared Kernel）

```plantuml
@startuml
title 共有カーネルパターン

package "販売コンテキスト" as sales {
    class "販売サービス"
}

package "生産コンテキスト" as production {
    class "生産サービス"
}

package "共有カーネル" as kernel {
    class "取引先" {
        +取引先コード
        +取引先名
        +住所
    }
    class "部門" {
        +部門コード
        +部門名
        +階層
    }
}

"販売サービス" --> "取引先"
"販売サービス" --> "部門"
"生産サービス" --> "取引先"
"生産サービス" --> "部門"

note bottom of kernel
  両コンテキストで
  共有するコアモデル
  変更は協調して行う
end note

@enduml
```

#### 顧客/供給者（Customer/Supplier）

```plantuml
@startuml
title 顧客/供給者パターン

package "販売コンテキスト (Upstream/Supplier)" as sales {
    class "受注サービス" {
        +受注確定()
        +受注情報取得()
    }
    class "受注" {
        +受注番号
        +商品コード
        +数量
        +納期
    }
}

package "生産コンテキスト (Downstream/Customer)" as production {
    class "生産計画サービス" {
        +受注取込()
        +計画立案()
    }
    class "生産計画" {
        +計画番号
        +品目コード
        +計画数量
    }
}

"受注サービス" --> "受注"
"生産計画サービス" --> "受注サービス" : 受注情報取得
"生産計画サービス" --> "生産計画"

note right of sales
  【Supplier】
  下流の要件を考慮して
  インターフェースを提供
end note

note right of production
  【Customer】
  上流に要件を伝え
  提供されたAPIを利用
end note

@enduml
```

#### 腐敗防止層（Anti-Corruption Layer）

```plantuml
@startuml
title 腐敗防止層パターン

package "会計コンテキスト" as accounting {
    class "仕訳サービス" {
        +仕訳登録()
    }
    class "仕訳" {
        +伝票番号
        +科目コード
        +金額
    }
}

package "腐敗防止層 (ACL)" as acl {
    class "売上仕訳変換サービス" {
        +変換(売上イベント)
    }
    class "売上仕訳DTO" {
        +売上番号
        +売上日
        +明細リスト
    }
}

package "販売コンテキスト" as sales {
    class "売上" {
        +売上番号
        +売上日
        +顧客コード
        +商品情報
        +金額
    }
}

"仕訳サービス" --> "仕訳"
"仕訳サービス" <-- "売上仕訳変換サービス"
"売上仕訳変換サービス" --> "売上仕訳DTO"
"売上仕訳変換サービス" ..> "売上" : 変換

note right of acl
  【ACL】
  外部コンテキストのモデルを
  自コンテキストのモデルに変換
  外部の変更から保護
end note

@enduml
```

#### 公開ホストサービス（Open Host Service）

```plantuml
@startuml
title 公開ホストサービスパターン

package "販売コンテキスト" as sales {
    class "販売API" <<Open Host Service>> {
        +GET /orders/{id}
        +POST /orders
        +GET /sales/{id}
    }

    class "受注リソース" <<Published Language>> {
        +orderId: String
        +orderDate: Date
        +customer: Customer
        +lines: List<OrderLine>
    }
}

package "会計コンテキスト" as accounting {
    class "自動仕訳サービス"
}

package "生産コンテキスト" as production {
    class "生産計画サービス"
}

package "外部システム" as external {
    class "分析システム"
}

"販売API" --> "受注リソース"
"自動仕訳サービス" --> "販売API"
"生産計画サービス" --> "販売API"
"分析システム" --> "販売API"

note right of sales
  【Open Host Service】
  標準化されたAPIを公開

  【Published Language】
  共通のデータフォーマット
  (JSON/XML Schema)
end note

@enduml
```

#### コンテキスト関係パターンの選択ガイド

| パターン | 適用場面 | メリット | デメリット |
|---------|---------|---------|----------|
| 共有カーネル | 密接に連携するコンテキスト | モデルの一貫性 | 変更の調整が必要 |
| 顧客/供給者 | 上流が下流の要件に対応可能 | 明確な責務分担 | 上流への依存 |
| 適合者 | 上流が変更不可能な場合 | 導入が容易 | 上流に完全依存 |
| 腐敗防止層 | レガシーシステムとの連携 | 独立性確保 | 実装コスト |
| 公開ホストサービス | 複数の消費者が存在 | 再利用性 | API設計・維持コスト |

---

## 33.3 統合パターンの選択基準

### 同期 vs 非同期

```plantuml
@startuml
title 同期通信と非同期通信の比較

rectangle "同期通信" as sync {
    actor "クライアント" as client1
    rectangle "サービスA" as serviceA1
    rectangle "サービスB" as serviceB1

    client1 -> serviceA1 : 1.リクエスト
    serviceA1 -> serviceB1 : 2.API呼び出し
    serviceB1 -> serviceA1 : 3.レスポンス
    serviceA1 -> client1 : 4.レスポンス
}

rectangle "非同期通信" as async {
    actor "クライアント" as client2
    rectangle "サービスA" as serviceA2
    queue "メッセージ\nキュー" as queue
    rectangle "サービスB" as serviceB2

    client2 -> serviceA2 : 1.リクエスト
    serviceA2 -> queue : 2.メッセージ発行
    serviceA2 -> client2 : 3.即時レスポンス
    queue -> serviceB2 : 4.メッセージ購読
}

note bottom of sync
  ・即座に結果を取得
  ・強い一貫性
  ・カップリングが強い
  ・障害伝播のリスク
end note

note bottom of async
  ・疎結合
  ・高い回復力
  ・スケーラビリティ
  ・結果整合性
end note

@enduml
```

#### 選択の判断基準

| 観点 | 同期を選択 | 非同期を選択 |
|-----|----------|------------|
| 応答要件 | 即座に結果が必要 | 遅延が許容される |
| 一貫性要件 | 強い一貫性が必須 | 結果整合性で可 |
| 可用性要件 | サービス間依存を許容 | 高可用性が必要 |
| 処理量 | 少量のトランザクション | 大量のトランザクション |
| 障害分離 | 障害伝播を許容 | 障害を分離したい |

### ポイントツーポイント vs ハブ&スポーク

```plantuml
@startuml
title 統合トポロジーの比較

rectangle "ポイントツーポイント" as p2p {
    rectangle "販売" as sales1
    rectangle "会計" as acc1
    rectangle "生産" as prod1
    rectangle "在庫" as inv1

    sales1 <--> acc1
    sales1 <--> prod1
    sales1 <--> inv1
    acc1 <--> prod1
    acc1 <--> inv1
    prod1 <--> inv1
}

rectangle "ハブ&スポーク" as hub {
    rectangle "販売" as sales2
    rectangle "会計" as acc2
    rectangle "生産" as prod2
    rectangle "在庫" as inv2

    rectangle "統合ハブ\n(ESB/API GW)" as hub_center

    sales2 <--> hub_center
    acc2 <--> hub_center
    prod2 <--> hub_center
    inv2 <--> hub_center
}

note bottom of p2p
  接続数: n(n-1)/2
  4システムで6接続

  ・シンプルな初期構成
  ・システム数増加で複雑化
  ・個別の変換ロジック
end note

note bottom of hub
  接続数: n
  4システムで4接続

  ・一元的な管理
  ・変換ロジックの集約
  ・ハブが単一障害点
end note

@enduml
```

### データ統合 vs プロセス統合

```plantuml
@startuml
title データ統合とプロセス統合の比較

rectangle "データ統合" as data_int {
    database "販売DB" as sales_db
    database "統合DB" as int_db
    database "会計DB" as acc_db

    sales_db --> int_db : ETL
    int_db --> acc_db : ETL

    note right of int_db
      ・データウェアハウス
      ・マスタデータ管理
      ・データレイク
    end note
}

rectangle "プロセス統合" as proc_int {
    rectangle "受注プロセス" as order_proc
    rectangle "出荷プロセス" as ship_proc
    rectangle "請求プロセス" as bill_proc
    rectangle "仕訳プロセス" as journal_proc

    order_proc --> ship_proc : イベント
    ship_proc --> bill_proc : イベント
    bill_proc --> journal_proc : イベント

    note right of journal_proc
      ・ワークフロー
      ・サービスオーケストレーション
      ・イベント駆動
    end note
}

@enduml
```

#### 統合アプローチの比較

| 観点 | データ統合 | プロセス統合 |
|-----|----------|------------|
| 主な目的 | データの一元化・分析 | 業務プロセスの自動化 |
| 更新頻度 | バッチ（日次/週次） | リアルタイム/準リアルタイム |
| 一貫性モデル | 結果整合性 | トランザクション整合性 |
| 技術要素 | ETL、DWH、MDM | ESB、BPM、イベントバス |
| 適用場面 | BI・レポーティング | 業務自動化・ワークフロー |

### 統合パターン選択のフローチャート

```plantuml
@startuml
title 統合パターン選択フローチャート

start

:連携要件の確認;

if (即座にデータ反映が必要?) then (はい)
  :リアルタイム連携;
  if (強い一貫性が必要?) then (はい)
    :同期API連携;
    :分散トランザクション検討;
  else (いいえ)
    :非同期メッセージング;
    :イベント駆動アーキテクチャ;
  endif
else (いいえ)
  :バッチ連携;
  if (大量データの移動?) then (はい)
    :ETL/ファイル連携;
  else (いいえ)
    :DB直接連携;
  endif
endif

:コンテキスト間関係の確認;

if (既存システムとの連携?) then (はい)
  :腐敗防止層（ACL）;
else (いいえ)
  if (複数の消費者?) then (はい)
    :公開ホストサービス;
    :Published Language;
  else (いいえ)
    :顧客/供給者パターン;
  endif
endif

:統合トポロジーの決定;

if (システム数が多い?) then (はい)
  :ハブ&スポーク;
  :API Gateway/ESB;
else (いいえ)
  :ポイントツーポイント;
endif

stop

@enduml
```

---

## 33.4 まとめ

本章では、エンタープライズインテグレーションの基本概念について解説しました。

### 学んだこと

1. **システム統合の必要性**

   - サイロ化した基幹業務システムの課題
   - データの一貫性・整合性確保の重要性
   - リアルタイム連携とバッチ連携の使い分け

2. **境界づけられたコンテキスト**

   - 販売・会計・生産の3つのコンテキスト
   - 各コンテキストの責務とドメインモデル
   - コンテキストマップによる関係の可視化

3. **コンテキスト間の関係パターン**

   - 共有カーネル：共通モデルの共有
   - 顧客/供給者：上流・下流の明確な関係
   - 腐敗防止層：外部システムからの保護
   - 公開ホストサービス：標準APIの提供

4. **統合パターンの選択基準**

   - 同期 vs 非同期の判断
   - ポイントツーポイント vs ハブ&スポーク
   - データ統合 vs プロセス統合

### 次章の予告

第34章では、メッセージングパターンについて詳しく解説します。メッセージチャネル、ルーティング、変換といったEnterprise Integration Patternsの基本パターンを学び、基幹業務システムへの適用方法を理解します。
