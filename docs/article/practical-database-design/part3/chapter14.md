---
type: Article
title: "第14章：財務会計システムの全体像"
description: "本章では、財務会計システムの全体像を把握し、販売管理システムとの連携や、財務会計と管理会計の違い、システムアーキテクチャについて解説します。"
tags: [article, practical-database-design, part3]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第14章：財務会計システムの全体像

本章では、財務会計システムの全体像を把握し、販売管理システムとの連携や、財務会計と管理会計の違い、システムアーキテクチャについて解説します。

---

## 14.1 財務会計システムのスコープ

### 基幹業務システムにおける位置づけ

財務会計システムは、基幹業務システムの中で他のシステムから仕訳データを受け取り、企業の財務状態を管理する重要なシステムです。

```plantuml
@startuml

title 基幹業務システムの全体像

state "得意先\n(Customer)" as Customer
state "販売管理\n(Sales Management)" as SalesManagement
SalesManagement : ・受注 \n・出荷 \n・売上

state "購買管理\n(Procurement Management)" as ProcurementManagement
ProcurementManagement : ・発注\n・入荷/受入\n・仕入(検収)

state "在庫\n(Inventory)" as Inventory

state "仕入先\n(Supplier)" as Supplier

state "生産管理\n(Production Management)" as ProductionManagement
ProductionManagement : ・生産計画\n・品質管理\n・工程管理\n・製造原価管理

state "債権管理\n(Accounts Receivable)" as AccountsReceivable
AccountsReceivable : ・請求 \n・入金

state "債務管理\n(Accounts Payable)" as AccountsPayable
AccountsPayable : ・支払\n・出金

state "財務会計\n(Financial Accounting)" as Accounting #LightBlue
Accounting : ・勘定科目管理\n・仕訳処理\n・残高管理\n・財務諸表\n・決算処理

[*] -> Customer

Customer --> SalesManagement
SalesManagement --> AccountsReceivable
SalesManagement --> ProcurementManagement : 調達依頼
SalesManagement --> ProductionManagement : 需要予測 \n製造指示
SalesManagement --> Accounting : 仕訳
SalesManagement --> Customer
Inventory --> SalesManagement

ProcurementManagement --> Inventory
ProcurementManagement --> AccountsPayable
ProcurementManagement --> Supplier
Supplier --> ProcurementManagement
ProductionManagement --> ProcurementManagement : 調達依頼

ProductionManagement --> Inventory : 入荷
Inventory --> ProductionManagement : 払出

AccountsReceivable --> Accounting
AccountsPayable --> Accounting
ProductionManagement --> Accounting : 仕訳
ProcurementManagement --> Accounting : 仕訳

Customer -> [*]

@enduml
```

### 財務会計システムの業務領域

財務会計システムは、以下の業務領域をカバーします。

```plantuml
@startuml

[*] -> 販売管理
販売管理 --> 財務会計 : 売上仕訳
販売管理 : 売掛金 / 売上

[*] -> 購買管理
購買管理 --> 財務会計 : 仕入仕訳
購買管理 : 仕入 / 買掛金

[*] -> 債権管理
債権管理 --> 財務会計 : 入金仕訳
債権管理 : 現金預金 / 売掛金

[*] -> 債務管理
債務管理 --> 財務会計 : 支払仕訳
債務管理 : 買掛金 / 現金預金

財務会計 --> 帳票出力
財務会計 : 勘定科目管理 \n仕訳処理 \n残高管理
帳票出力 : 日計表 \n合計残高試算表 \n財務諸表

帳票出力 -> [*]

@enduml
```

### 業務機能の概要

| 業務 | 説明 |
|------|------|
| **債権管理（請求・入金）** | 売上に基づく請求書発行と入金消込処理 |
| **債務管理（支払・出金）** | 仕入に基づく支払処理と出金管理 |
| **経理（仕訳・決算）** | 仕訳入力・自動仕訳・残高管理・決算処理 |

### ユースケース図

財務会計システム全体のユースケースは以下の通りです。

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle
skinparam linetype ortho
title 財務会計システム ユースケース図

actor 経理担当者 as accounting
actor 財務担当者 as finance
actor 営業担当者 as sales
actor 購買担当者 as buyer
actor 管理者 as admin

rectangle 財務会計システム {
    usecase "勘定科目管理" as UC_ACCOUNT
    usecase "仕訳入力" as UC_JOURNAL
    usecase "自動仕訳" as UC_AUTO_JOURNAL
    usecase "残高照会" as UC_BALANCE
    usecase "日計表出力" as UC_DAILY
    usecase "試算表出力" as UC_TRIAL
    usecase "財務諸表出力" as UC_FINANCIAL
    usecase "決算処理" as UC_CLOSING
}

admin -- UC_ACCOUNT
accounting -- UC_JOURNAL
accounting -- UC_BALANCE
accounting -- UC_DAILY
accounting -- UC_TRIAL
accounting -- UC_CLOSING
finance -- UC_FINANCIAL
sales -- UC_AUTO_JOURNAL
buyer -- UC_AUTO_JOURNAL
@enduml
```

### 会計組織の役割分担

| 部門 | 役割 | 主な業務 |
|------|------|----------|
| **経理部門** | 日常の会計処理 | 仕訳入力、残高照会、日計表出力 |
| **財務部門** | 財務諸表の作成 | 試算表出力、財務諸表作成、決算処理 |
| **営業部門** | 売上データの提供 | 売上仕訳の元データ作成 |
| **購買部門** | 仕入データの提供 | 仕入仕訳の元データ作成 |
| **管理者** | マスタ管理 | 勘定科目マスタの管理 |

---

## 14.2 財務会計と管理会計の違い

### 財務会計：外部報告目的

財務会計は、株主・債権者・税務署などの外部利害関係者に対して、企業の財政状態や経営成績を報告することを目的とします。

```plantuml
@startuml
title 財務会計の目的と特徴

package "財務会計" {
    rectangle "貸借対照表\n(B/S)" as BS
    rectangle "損益計算書\n(P/L)" as PL
    rectangle "キャッシュフロー計算書\n(C/F)" as CF
    rectangle "株主資本等変動計算書" as SE
}

actor "株主" as shareholder
actor "債権者" as creditor
actor "税務署" as tax
actor "監査法人" as auditor

BS --> shareholder
BS --> creditor
PL --> shareholder
PL --> tax
CF --> creditor
CF --> auditor
SE --> shareholder

note right of BS
  財政状態の報告
  （資産・負債・純資産）
end note

note right of PL
  経営成績の報告
  （収益・費用・利益）
end note
@enduml
```

**財務会計の特徴**

| 項目 | 内容 |
|------|------|
| 目的 | 外部への報告（法定開示） |
| 対象 | 株主、債権者、税務署 |
| 規則 | 会計基準、税法に準拠 |
| 期間 | 会計期間（通常1年） |
| 形式 | 財務諸表（B/S, P/L, C/F） |

### 管理会計：内部意思決定目的

管理会計は、経営者や管理者が意思決定を行うための情報を提供することを目的とします。

```plantuml
@startuml
title 管理会計の目的と特徴

package "管理会計" {
    rectangle "予算管理" as budget
    rectangle "原価計算" as cost
    rectangle "業績評価" as performance
    rectangle "経営分析" as analysis
}

actor "経営者" as ceo
actor "部門長" as manager
actor "プロジェクトリーダー" as leader

budget --> ceo
budget --> manager
cost --> manager
cost --> leader
performance --> ceo
performance --> manager
analysis --> ceo

note right of budget
  計画と実績の比較
  予実差異分析
end note

note right of cost
  製品別・部門別原価
  標準原価・実際原価
end note

note right of analysis
  収益性分析
  安全性分析
  効率性分析
end note
@enduml
```

**管理会計の特徴**

| 項目 | 内容 |
|------|------|
| 目的 | 内部の意思決定支援 |
| 対象 | 経営者、管理者 |
| 規則 | 自由（社内ルール） |
| 期間 | 任意（月次、週次、日次） |
| 形式 | 自由（セグメント別、製品別など） |

### 財務会計と管理会計の比較

```plantuml
@startuml
title 財務会計と管理会計の比較

rectangle "財務会計" as FA #LightBlue {
    (外部報告)
    (法定開示)
    (過去指向)
    (会計基準準拠)
}

rectangle "管理会計" as MA #LightGreen {
    (内部報告)
    (任意開示)
    (将来指向)
    (自由形式)
}

FA -[hidden]right- MA

note bottom of FA
  <b>共通の基盤</b>
  ・仕訳データ
  ・勘定科目体系
  ・残高情報
end note
@enduml
```

| 比較項目 | 財務会計 | 管理会計 |
|----------|----------|----------|
| 利用者 | 外部利害関係者 | 経営者・管理者 |
| 目的 | 報告義務の履行 | 意思決定の支援 |
| 規制 | 会計基準・税法 | なし（任意） |
| 時間軸 | 過去（実績） | 過去・現在・将来 |
| 詳細度 | 法定様式 | 必要に応じて詳細 |
| 更新頻度 | 期末（四半期・年次） | 随時（日次・週次） |

---

## 14.3 財務会計システムのアーキテクチャ

### 技術スタックと環境設定

財務会計システム（FAS）は、販売管理システム（SMS）と同じ技術スタックを使用しますが、独立した環境で動作します。

| 項目 | 財務会計システム (FAS) | 販売管理システム (SMS) |
|------|------------------------|------------------------|
| アプリケーションポート | 8081 | 8080 |
| PostgreSQL ポート | 5433 | 5432 |
| データベース名 | fas | sms |
| Java バージョン | 25 | 25 |
| Spring Boot | 4.0.0 | 4.0.0 |
| MyBatis | 4.0.0 | 4.0.0 |

### プロジェクト構成

財務会計システムは、ヘキサゴナルアーキテクチャ（ポート&アダプターパターン）を採用します。

```
apps/fas/
├── backend/
│   ├── src/main/java/com/example/fas/
│   │   ├── domain/                     # ドメイン層（純粋なビジネスロジック）
│   │   │   ├── model/                 # ドメインモデル（エンティティ、値オブジェクト）
│   │   │   │   ├── account/           # 勘定科目関連
│   │   │   │   ├── journal/           # 仕訳関連
│   │   │   │   └── balance/           # 残高関連
│   │   │   ├── type/                  # 基本型（通貨、金額等）
│   │   │   └── exception/             # ドメイン例外
│   │   │
│   │   ├── application/               # アプリケーション層
│   │   │   └── port/
│   │   │       └── out/              # Output Port（リポジトリインターフェース）
│   │   │
│   │   └── infrastructure/            # インフラストラクチャ層
│   │       ├── config/               # 設定クラス
│   │       ├── in/                   # Input Adapter（受信アダプター）
│   │       │   ├── rest/             # REST API（Web実装）
│   │       │   │   ├── controller/   # REST Controller
│   │       │   │   ├── dto/          # Data Transfer Object
│   │       │   │   └── exception/    # Exception Handler
│   │       │   └── seed/             # Seed データ投入
│   │       └── out/                  # Output Adapter（送信アダプター）
│   │           └── persistence/      # DB実装
│   │               ├── mapper/       # MyBatis Mapper
│   │               ├── repository/   # Repository実装
│   │               └── typehandler/  # 型ハンドラー
│   │
│   ├── src/main/resources/
│   │   ├── application.yml            # アプリケーション設定
│   │   └── db/migration/              # Flyway マイグレーション
│   │       └── V001__create_enum_types.sql
│   │
│   ├── src/test/java/com/example/fas/
│   │   └── testsetup/
│   │       └── BaseIntegrationTest.java  # 統合テスト基底クラス
│   │
│   └── config/                        # 品質ツール設定
│       ├── checkstyle/
│       ├── pmd/
│       └── spotbugs/
│
└── docker-compose.yml                 # PostgreSQL コンテナ定義
```

### ヘキサゴナルアーキテクチャ（ポート&アダプター）

```plantuml
@startuml hexagonal_architecture_accounting
!define RECTANGLE class

package "Hexagonal Architecture (財務会計API)" {

  RECTANGLE "Application Core\n(Domain + Use Cases)" as core {
    - Account (勘定科目)
    - Journal (仕訳)
    - JournalEntry (仕訳明細)
    - DailyBalance (日次残高)
    - MonthlyBalance (月次残高)
    - AccountUseCase
    - JournalUseCase
    - BalanceUseCase
  }

  RECTANGLE "Input Adapters\n(Driving Side)" as input {
    - Spring Controllers
    - REST API Endpoints
    - Request Validation
    - Error Handling
  }

  RECTANGLE "Output Adapters\n(Driven Side)" as output {
    - MyBatis Repository
    - Database Access
    - Entity Mapping
  }
}

input --> core : "Input Ports\n(Use Cases)"
core --> output : "Output Ports\n(Repository Interfaces)"

note top of core
  純粋なビジネスロジック
  MyBatis に依存しない
  高速でテスト可能
end note

note left of input
  外部からアプリケーションを
  駆動するアダプター
  HTTP, REST等
end note

note right of output
  アプリケーションが外部の
  技術を使うためのアダプター
  PostgreSQL, MyBatis等
end note
@enduml
```

### アーキテクチャの特徴

| 特徴 | 説明 |
|------|------|
| **ドメイン中心** | ビジネスロジックを中心に据え、外部技術から分離 |
| **依存性の逆転** | ドメイン層は外部に依存せず、外部がドメイン層に依存 |
| **テスト容易性** | モックやスタブを使った単体テストが容易 |
| **技術変更の容易さ** | アダプターを差し替えるだけで技術を変更可能 |

### ドメイン駆動設計の適用

#### 集約とリポジトリ

```plantuml
@startuml
title 財務会計システムの集約

package "勘定科目集約" {
    class Account <<Aggregate Root>> {
        accountCode: String
        accountName: String
        bsPlType: BsPlType
        debitCreditType: DebitCreditType
    }
    class AccountStructure {
        accountCode: String
        parentAccountCode: String
        accountPath: String
    }
    Account "1" *-- "*" AccountStructure
}

package "仕訳集約" {
    class Journal <<Aggregate Root>> {
        journalNumber: String
        journalDate: LocalDate
        journalType: JournalType
    }
    class JournalDetail {
        lineNumber: Integer
        lineSummary: String
    }
    class JournalEntry {
        debitCreditType: DebitCreditType
        accountCode: String
        amount: BigDecimal
    }
    Journal "1" *-- "*" JournalDetail
    JournalDetail "1" *-- "*" JournalEntry
}

package "残高集約" {
    class DailyBalance <<Aggregate Root>> {
        accountCode: String
        balanceDate: LocalDate
        debitAmount: BigDecimal
        creditAmount: BigDecimal
    }
    class MonthlyBalance <<Aggregate Root>> {
        accountCode: String
        fiscalYear: Integer
        fiscalMonth: Integer
        openingBalance: BigDecimal
        closingBalance: BigDecimal
    }
}

@enduml
```

#### ドメインサービス

| サービス | 責務 |
|----------|------|
| **JournalValidationService** | 仕訳の貸借一致検証 |
| **BalanceCalculationService** | 残高計算・集計 |
| **AutoJournalService** | 自動仕訳生成 |
| **ClosingService** | 月次・年次決算処理 |

#### アプリケーションサービス

| サービス | 責務 |
|----------|------|
| **AccountUseCase** | 勘定科目の登録・照会・更新 |
| **JournalUseCase** | 仕訳の登録・照会・更新 |
| **BalanceUseCase** | 残高照会・帳票出力 |
| **ClosingUseCase** | 決算処理の実行 |

### API 設計

#### RESTful API の基本方針

| 方針 | 説明 |
|------|------|
| リソース指向 | 勘定科目、仕訳、残高をリソースとして設計 |
| HTTP メソッド | GET（照会）、POST（登録）、PUT（更新）、DELETE（削除） |
| ステータスコード | 200（成功）、201（作成）、400（バリデーションエラー）、404（未検出）、409（競合） |
| エラーレスポンス | ProblemDetail 形式（RFC 7807） |

#### エンドポイント設計

| メソッド | パス | 説明 |
|----------|------|------|
| GET | `/api/v1/accounts` | 勘定科目一覧の取得 |
| GET | `/api/v1/accounts/{code}` | 勘定科目の取得 |
| POST | `/api/v1/accounts` | 勘定科目の登録 |
| PUT | `/api/v1/accounts/{code}` | 勘定科目の更新 |
| DELETE | `/api/v1/accounts/{code}` | 勘定科目の削除 |
| GET | `/api/v1/journals` | 仕訳一覧の取得 |
| POST | `/api/v1/journals` | 仕訳の登録 |
| GET | `/api/v1/balances/daily` | 日次残高の取得 |
| GET | `/api/v1/balances/monthly` | 月次残高の取得 |
| GET | `/api/v1/reports/trial-balance` | 試算表の出力 |

### マスタ情報とトランザクション情報

財務会計システムのデータは、大きく「マスタ情報」と「トランザクション情報」に分類されます。

```plantuml
@startuml
title 財務会計システム ER図（概要）

' マスタ系
package "マスタ情報" {
  entity 勘定科目マスタ
  entity 勘定科目構成マスタ
  entity 課税取引マスタ
  entity 部門マスタ
  entity 通貨マスタ
  entity 為替レートマスタ
}

' 仕訳系
package "仕訳情報" {
  entity 仕訳データ
  entity 仕訳明細データ
  entity 仕訳貸借明細データ
}

' 自動仕訳系
package "自動仕訳情報" {
  entity 自動仕訳パターンマスタ
  entity 自動仕訳管理データ
}

' 残高系
package "残高情報" {
  entity 日次勘定科目残高
  entity 月次勘定科目残高
}

' リレーション（簡略）
勘定科目マスタ ||--o{ 勘定科目構成マスタ
勘定科目マスタ }o--|| 課税取引マスタ
勘定科目マスタ ||--o{ 仕訳貸借明細データ
仕訳データ ||--o{ 仕訳明細データ
仕訳明細データ ||--o{ 仕訳貸借明細データ
勘定科目マスタ ||--o{ 日次勘定科目残高
勘定科目マスタ ||--o{ 月次勘定科目残高
部門マスタ ||--o{ 仕訳貸借明細データ
部門マスタ ||--o{ 日次勘定科目残高
部門マスタ ||--o{ 月次勘定科目残高

@enduml
```

| 分類 | 説明 | 例 |
|-----|------|-----|
| **マスタ情報** | 基本的に変更が少ない、システムの基盤となるデータ | 勘定科目マスタ、勘定科目構成マスタ、課税取引マスタ |
| **トランザクション情報** | 日々の業務で発生するデータ | 仕訳データ、日次勘定科目残高、月次勘定科目残高 |

### PostgreSQL ENUM 型定義

財務会計システムでは、業務で使用する区分値を PostgreSQL の ENUM 型で定義します。日本語のカラム名・テーブル名と同様に、ENUM 型名も日本語で定義します。

```sql
-- BSPL区分（貸借対照表/損益計算書区分）
CREATE TYPE "BSPL区分" AS ENUM ('BS', 'PL');

-- 貸借区分
CREATE TYPE "貸借区分" AS ENUM ('借方', '貸方');

-- 集計区分
CREATE TYPE "集計区分" AS ENUM ('集計', '明細');

-- 課税区分
CREATE TYPE "課税区分" AS ENUM ('課税', '非課税', '免税', '対象外');

-- 仕訳区分
CREATE TYPE "仕訳区分" AS ENUM ('通常', '振替', '決算', '自動');

-- 仕訳ステータス
CREATE TYPE "仕訳ステータス" AS ENUM ('入力中', '確定', '承認済', '取消');

-- 自動仕訳パターン区分
CREATE TYPE "自動仕訳パターン区分" AS ENUM ('売上', '仕入', '入金', '出金', '減価償却', '引当');

-- 決算区分
CREATE TYPE "決算区分" AS ENUM ('月次', '四半期', '中間', '年次');
```

| ENUM 型名 | 説明 | 値 |
|-----------|------|-----|
| BSPL区分 | 貸借対照表/損益計算書の区分 | BS, PL |
| 貸借区分 | 勘定科目の借方/貸方区分 | 借方, 貸方 |
| 集計区分 | 勘定科目の集計/明細区分 | 集計, 明細 |
| 課税区分 | 消費税の課税区分 | 課税, 非課税, 免税, 対象外 |
| 仕訳区分 | 仕訳の種類 | 通常, 振替, 決算, 自動 |
| 仕訳ステータス | 仕訳の処理状態 | 入力中, 確定, 承認済, 取消 |
| 自動仕訳パターン区分 | 自動仕訳の発生パターン | 売上, 仕入, 入金, 出金, 減価償却, 引当 |
| 決算区分 | 決算処理の種類 | 月次, 四半期, 中間, 年次 |

---

## 本章のまとめ

本章では、財務会計システムの全体像を把握しました。

### 学んだこと

| カテゴリ | 内容 |
|----------|------|
| スコープ | 債権管理・債務管理・経理（仕訳・決算） |
| 連携 | 販売管理・購買管理・生産管理からの仕訳データ受領 |
| 財務会計 | 外部報告目的（法定開示） |
| 管理会計 | 内部意思決定目的（任意） |
| アーキテクチャ | ヘキサゴナルアーキテクチャ（ポート&アダプター） |
| 設計手法 | ドメイン駆動設計（集約・リポジトリ・ドメインサービス） |
| 技術スタック | Java 25、Spring Boot 4.0.0、MyBatis 4.0.0、PostgreSQL 16 |
| 環境設定 | FAS: ポート 8081、PostgreSQL 5433 / SMS: ポート 8080、PostgreSQL 5432 |
| ENUM 型 | 8 種類の業務区分を PostgreSQL ENUM で日本語定義 |

### 次章の予告

第15章では、財務会計システムの基盤となる勘定科目マスタの設計を行います。勘定科目コード、BSPL区分、貸借区分、集計区分などの詳細を解説します。
