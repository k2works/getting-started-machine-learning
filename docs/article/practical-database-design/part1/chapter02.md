---
type: Article
title: "第2章：基幹業務システムの業務領域"
description: "本章では、基幹業務システムを構成する各業務領域について詳しく解説します。"
tags: [article, practical-database-design, part1]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第2章：基幹業務システムの業務領域

本章では、基幹業務システムを構成する各業務領域について詳しく解説します。各領域がどのような業務を担当し、他の領域とどのように連携するかを理解することで、システム全体の設計に役立てることができます。

```plantuml
@startuml

'=======================
' 業務領域（状態）の定義
'=======================
state "得意先\n(Customer)" as Customer
state "販売管理\n(Sales Management)" as SalesManagement
SalesManagement : ・受注 \n・出荷 \n・売上

state "購買管理\n(Procurement Management)" as ProcurementManagement
ProcurementManagement : ・発注\n・入荷/受入\n・仕入(検収)

state "在庫\n(Inventory)" as Inventory

state "仕入先\n(Supplier)" as Supplier
state "委託先\n(Subcontractor)" as Subcontractor

state "生産管理\n(Production Management)" as ProductionManagement
ProductionManagement : ・生産計画\n・品質管理\n・工程管理\n・製造原価管理

state "債権管理\n(Accounts Receivable)" as AccountsReceivable
AccountsReceivable : ・請求 \n・入金

state "債務管理\n(Accounts Payable)" as AccountsPayable
AccountsPayable : ・支払\n・出金

state "会計\n(Accounting)" as Accounting
Accounting : ・仕訳 \n・決算

state "人事\n(HR)" as HR
HR : ・給与計算\n ・人事管理

'=======================
' 業務フロー（遷移）
'=======================
[*] -> Customer

'--- 販売管理まわり ---
Customer --> SalesManagement
SalesManagement --> AccountsReceivable
SalesManagement --> ProcurementManagement : 調達依頼
SalesManagement --> ProductionManagement : 需要予測 \n製造指示
SalesManagement --> Accounting : 仕訳
SalesManagement --> Customer
Inventory --> SalesManagement

'--- 購買管理まわり ---
ProcurementManagement --> Inventory
ProcurementManagement --> AccountsPayable
ProcurementManagement --> Supplier
Supplier --> ProcurementManagement
ProcurementManagement --> Subcontractor
Subcontractor --> ProcurementManagement
ProductionManagement --> ProcurementManagement : 調達依頼

'--- 在庫・生産まわり ---
ProductionManagement --> Inventory : 入荷
Inventory --> ProductionManagement : 払出

'--- 会計・債権・債務まわり ---
AccountsReceivable --> Accounting
AccountsPayable --> Accounting
ProductionManagement --> Accounting : 仕訳
ProcurementManagement --> Accounting : 仕訳

'--- 会計・人事まわり ---
Accounting --> HR
HR --> Accounting

'--- 開始・終了 ---
Customer -> [*]

@enduml
```

---

## 2.1 販売管理領域

販売管理領域は、企業の収益を生み出す中核的な業務領域です。得意先からの注文を受け、商品を出荷し、売上を計上するまでの一連のプロセスを管理します。

### 受注・出荷・売上

販売管理の基本的な業務フローは「受注→出荷→売上」の流れで進みます。

```plantuml
@startuml

state "受注" as Order {
    Order : 受注登録
    Order : 受注確認
    Order : 在庫引当
}

state "出荷" as Shipment {
    Shipment : 出荷指示
    Shipment : ピッキング
    Shipment : 出荷検品
    Shipment : 配送
}

state "売上" as Sales {
    Sales : 売上計上
    Sales : 納品書発行
    Sales : 請求データ作成
}

[*] --> Order
Order --> Shipment : 出荷指示
Shipment --> Sales : 出荷完了
Sales --> [*]

@enduml
```

| 業務 | 説明 | 主なデータ |
|---|---|---|
| 受注 | 得意先からの注文を受け付け、登録する | 受注データ、受注明細 |
| 出荷 | 受注に基づき商品を出荷する | 出荷指示、出荷実績 |
| 売上 | 出荷完了後に売上を計上する | 売上データ、売上明細 |

### 得意先との関係

販売管理では、得意先（顧客）との関係を適切に管理することが重要です。

- **得意先マスタ**：得意先の基本情報（名称、住所、連絡先等）
- **取引条件**：締日、支払条件、与信限度額
- **出荷先**：商品の届け先（得意先と異なる場合がある）
- **請求先**：請求書の送付先

---

## 2.2 購買管理領域

購買管理領域は、企業が必要とする商品や資材を仕入先から調達する業務を管理します。

### 発注・入荷/受入・仕入（検収）

購買管理の基本的な業務フローは「発注→入荷→検収」の流れで進みます。

```plantuml
@startuml

state "発注" as PO {
    PO : 発注登録
    PO : 発注書発行
    PO : 納期管理
}

state "入荷/受入" as Receiving {
    Receiving : 入荷検品
    Receiving : 数量確認
    Receiving : 品質検査
}

state "仕入（検収）" as Inspection {
    Inspection : 検収処理
    Inspection : 仕入計上
    Inspection : 買掛金計上
}

[*] --> PO
PO --> Receiving : 納品
Receiving --> Inspection : 検収
Inspection --> [*]

@enduml
```

| 業務 | 説明 | 主なデータ |
|---|---|---|
| 発注 | 仕入先に商品・資材を発注する | 発注データ、発注明細 |
| 入荷/受入 | 仕入先から届いた商品を受け入れる | 入荷データ、入荷明細 |
| 仕入（検収） | 受入後に検収を行い、仕入を計上する | 検収データ、仕入明細 |

### 仕入先・委託先との関係

- **仕入先マスタ**：仕入先の基本情報、取引条件
- **委託先マスタ**：外注委託先の情報（有償支給/無償支給の区分）
- **単価マスタ**：品目ごとの仕入単価、適用期間

---

## 2.3 在庫管理領域

在庫管理領域は、企業が保有する商品・資材の在庫を管理する業務領域です。

### 在庫の受払

在庫管理では、入庫（受）と出庫（払）を記録し、現在の在庫数量を把握します。

```plantuml
@startuml

state "在庫" as Inventory {
    state "入庫（受）" as Receive
    state "出庫（払）" as Issue
    state "在庫残高" as Balance

    Receive --> Balance : 加算
    Issue --> Balance : 減算
}

[*] --> Receive : 仕入\n製造完成
Receive --> Issue
Issue --> [*] : 出荷\n製造払出

@enduml
```

| 取引種別 | 入庫（受） | 出庫（払） |
|---|---|---|
| 販売管理 | 返品受入 | 出荷 |
| 購買管理 | 仕入入庫 | 返品出庫 |
| 生産管理 | 製造完成 | 製造払出 |
| 在庫調整 | 棚卸増 | 棚卸減 |

### 販売管理・購買管理・生産管理との連携

在庫管理は、他の3つの業務領域と密接に連携します。

```plantuml
@startuml

rectangle "在庫管理" as Inventory #LightBlue

rectangle "販売管理" as Sales
rectangle "購買管理" as Procurement
rectangle "生産管理" as Production

Sales --> Inventory : 出荷引当\n出荷実績
Inventory --> Sales : 在庫照会\n引当可能数

Procurement --> Inventory : 仕入入庫
Inventory --> Procurement : 在庫照会\n発注点管理

Production --> Inventory : 製造完成入庫
Inventory --> Production : 資材払出\n在庫引当

@enduml
```

---

## 2.4 生産管理領域

生産管理領域は、製造業における生産活動全体を管理する業務領域です。

### 生産計画・品質管理・工程管理・製造原価管理

```plantuml
@startuml

state "生産計画" as Planning {
    Planning : MPS（基準生産計画）
    Planning : MRP（資材所要量計画）
    Planning : スケジューリング
}

state "工程管理" as Process {
    Process : 製造指示
    Process : 製造実績
    Process : 工数管理
}

state "品質管理" as Quality {
    Quality : 受入検査
    Quality : 工程内検査
    Quality : 出荷検査
}

state "製造原価管理" as Cost {
    Cost : 標準原価
    Cost : 実際原価
    Cost : 原価差異分析
}

[*] --> Planning
Planning --> Process : 製造オーダー
Process --> Quality : 検査依頼
Quality --> Process : 検査結果
Process --> Cost : 実績データ
Cost --> [*]

@enduml
```

| 業務 | 説明 |
|---|---|
| 生産計画 | 需要予測に基づき、何を・いつ・どれだけ生産するかを計画 |
| 工程管理 | 製造指示の発行、製造実績の収集、工数の管理 |
| 品質管理 | 受入・工程内・出荷の各段階での品質検査 |
| 製造原価管理 | 製品の製造原価を計算し、標準原価との差異を分析 |

### 購買管理・在庫管理との連携

生産管理は、資材の調達（購買管理）と在庫管理と連携して動作します。

- **購買管理との連携**：MRP から発注依頼を生成、外注委託の管理
- **在庫管理との連携**：資材の払出、製品の入庫、在庫状態管理

---

## 2.5 債権管理領域

債権管理領域は、販売活動によって発生した売掛金（債権）を管理する業務領域です。

### 請求・入金

```plantuml
@startuml

state "請求" as Billing {
    Billing : 締処理
    Billing : 請求書発行
    Billing : 請求残高管理
}

state "入金" as Payment {
    Payment : 入金登録
    Payment : 入金消込
    Payment : 過入金・不足処理
}

[*] --> Billing : 売上データ
Billing --> Payment : 請求
Payment --> [*] : 消込完了

@enduml
```

| 業務 | 説明 | 主なデータ |
|---|---|---|
| 請求 | 売上データを集計し、得意先に請求する | 請求データ、請求明細 |
| 入金 | 得意先からの入金を登録し、請求と消込する | 入金データ、消込明細 |

### 販売管理との連携

- 売上データから請求データを生成
- 都度請求と締め請求の使い分け
- 与信管理（与信限度額のチェック）

---

## 2.6 債務管理領域

債務管理領域は、購買活動によって発生した買掛金（債務）を管理する業務領域です。

### 支払・出金

```plantuml
@startuml

state "支払締" as Closing {
    Closing : 締処理
    Closing : 支払予定作成
    Closing : 支払残高管理
}

state "支払" as Payment {
    Payment : 支払実行
    Payment : 振込データ作成
    Payment : 支払消込
}

[*] --> Closing : 仕入データ
Closing --> Payment : 支払予定
Payment --> [*] : 支払完了

@enduml
```

| 業務 | 説明 | 主なデータ |
|---|---|---|
| 支払締 | 仕入データを集計し、支払予定を作成する | 支払予定データ |
| 支払 | 仕入先への支払を実行する | 支払データ、振込データ |

### 購買管理との連携

- 検収データから買掛金を計上
- 支払条件に基づく支払予定の作成
- 振込データの作成と銀行連携

---

## 2.7 会計領域

会計領域は、企業のすべての経済活動を仕訳として記録し、財務諸表を作成する業務領域です。

### 仕訳・決算

```plantuml
@startuml

state "仕訳" as Journal {
    Journal : 手動仕訳
    Journal : 自動仕訳
    Journal : 仕訳承認
}

state "決算" as Closing {
    Closing : 月次決算
    Closing : 年次決算
    Closing : 財務諸表作成
}

[*] --> Journal
Journal --> Closing : 締処理
Closing --> [*]

@enduml
```

| 業務 | 説明 |
|---|---|
| 仕訳 | 日々の取引を借方・貸方に分けて記録 |
| 決算 | 期間損益を確定し、財務諸表を作成 |

### 債権・債務・生産管理からの仕訳連携

会計領域は、他の業務領域から仕訳データを受け取ります。

```plantuml
@startuml

rectangle "会計" as Accounting #LightYellow

rectangle "販売管理" as Sales
rectangle "債権管理" as AR
rectangle "購買管理" as Procurement
rectangle "債務管理" as AP
rectangle "生産管理" as Production
rectangle "人事" as HR

Sales --> Accounting : 売上仕訳
AR --> Accounting : 入金仕訳
Procurement --> Accounting : 仕入仕訳
AP --> Accounting : 支払仕訳
Production --> Accounting : 製造原価仕訳
HR --> Accounting : 給与仕訳

@enduml
```

| 連携元 | 仕訳内容 |
|---|---|
| 販売管理 | 売上計上（売掛金/売上） |
| 債権管理 | 入金消込（現金預金/売掛金） |
| 購買管理 | 仕入計上（仕入/買掛金） |
| 債務管理 | 支払消込（買掛金/現金預金） |
| 生産管理 | 製造原価計上 |
| 人事 | 給与計上（給与/未払金） |

---

## 2.8 人事領域

人事領域は、企業の従業員に関する情報を管理し、給与計算を行う業務領域です。

### 給与計算・人事管理

```plantuml
@startuml

state "人事管理" as HR {
    HR : 従業員情報管理
    HR : 組織管理
    HR : 異動・昇格管理
}

state "給与計算" as Payroll {
    Payroll : 勤怠集計
    Payroll : 給与計算
    Payroll : 賞与計算
    Payroll : 年末調整
}

[*] --> HR
HR --> Payroll : 従業員情報
Payroll --> [*] : 給与支払

@enduml
```

| 業務 | 説明 |
|---|---|
| 人事管理 | 従業員の入社・退社、異動、昇格等を管理 |
| 給与計算 | 勤怠データに基づき給与・賞与を計算 |

### 会計との連携

- 給与データから給与仕訳を生成
- 社会保険料・税金の計上
- 未払給与・預り金の管理

---

## まとめ

本章では、基幹業務システムを構成する8つの業務領域について解説しました。

| 領域 | 主な業務 | 連携先 |
|---|---|---|
| 販売管理 | 受注・出荷・売上 | 在庫管理、債権管理、会計 |
| 購買管理 | 発注・入荷・検収 | 在庫管理、債務管理、会計 |
| 在庫管理 | 入出庫・棚卸 | 販売管理、購買管理、生産管理 |
| 生産管理 | 生産計画・工程管理 | 購買管理、在庫管理、会計 |
| 債権管理 | 請求・入金 | 販売管理、会計 |
| 債務管理 | 支払締・支払 | 購買管理、会計 |
| 会計 | 仕訳・決算 | 全領域 |
| 人事 | 給与計算・人事管理 | 会計 |

次章では、これらの業務領域を横断する業務フローの全体像について解説します。
