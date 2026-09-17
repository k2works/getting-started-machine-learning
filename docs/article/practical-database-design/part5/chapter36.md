---
type: Article
title: "第36章：マスタデータ管理（MDM）"
description: "本章では、複数の基幹業務システム間で共有されるマスタデータの管理方法について解説します。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第36章：マスタデータ管理（MDM）

本章では、複数の基幹業務システム間で共有されるマスタデータの管理方法について解説します。マスタデータ管理（Master Data Management: MDM）は、企業全体でのデータ品質と一貫性を確保するための重要な取り組みです。

---

## 36.1 マスタデータ統合の課題

### 各システムでのマスタ重複

基幹業務システムが個別に構築された場合、同じマスタデータが複数のシステムに存在することがあります。この「マスタ重複」は様々な問題を引き起こします。

```plantuml
@startuml
title マスタデータ重複の現状

package "販売管理システム" as sales {
    database "顧客マスタ\n----\n顧客コード\n顧客名\n住所\n与信限度額" as sales_customer
    database "商品マスタ\n----\n商品コード\n商品名\n販売単価\n在庫数" as sales_product
}

package "生産管理システム" as production {
    database "取引先マスタ\n----\n取引先コード\n取引先名\n住所\n納入条件" as prod_partner
    database "品目マスタ\n----\n品目コード\n品目名\n製造リードタイム\nBOM情報" as prod_item
}

package "財務会計システム" as accounting {
    database "取引先マスタ\n----\n取引先コード\n取引先名\n債権債務区分\n決済条件" as acc_partner
}

note bottom of sales
  【問題点】
  ・同一顧客が異なるコードで登録
  ・住所変更が反映されない
  ・データ不整合
end note

note bottom of production
  【問題点】
  ・商品と品目の紐付けが曖昧
  ・仕入先情報の重複
  ・更新タイミングのずれ
end note

@enduml
```

#### マスタ重複による具体的な問題

| 問題カテゴリ | 具体例 | ビジネスへの影響 |
|------------|-------|----------------|
| データ不整合 | 顧客住所が各システムで異なる | 請求書誤送付、配送ミス |
| 更新漏れ | 取引先の廃業が反映されない | 無効な発注、債権回収不能 |
| コード重複 | 同一顧客に複数コード付与 | 売上分析の誤り、与信管理不能 |
| 属性欠落 | 必要な属性が一部システムにのみ存在 | システム間連携の障害 |

### コード体系の不一致

各システムが独自のコード体系を持つことで、データの突合が困難になります。

```plantuml
@startuml
title コード体系の不一致

rectangle "販売管理" as sales {
    object "顧客コード" as sales_code {
        形式: "C" + 連番5桁
        例: C00001, C00002
    }
}

rectangle "生産管理" as production {
    object "取引先コード" as prod_code {
        形式: 区分2桁 + 連番4桁
        例: CU0001（顧客）
        例: SP0001（仕入先）
    }
}

rectangle "財務会計" as accounting {
    object "取引先コード" as acc_code {
        形式: 連番8桁
        例: 00000001
    }
}

sales_code -[hidden]-> prod_code
prod_code -[hidden]-> acc_code

note bottom
  【同一顧客のコード例】
  ・販売管理：C00001
  ・生産管理：CU0001
  ・財務会計：00000001

  → 突合するためにマッピングテーブルが必要
  → マッピングの維持管理コストが発生
end note

@enduml
```

### 更新タイミングの同期

マスタデータの更新がシステム間で同期されないと、一時的または恒久的なデータ不整合が発生します。

```plantuml
@startuml
title 更新タイミングの不整合

concise "販売管理" as sales
concise "生産管理" as production
concise "財務会計" as accounting

@0
sales is "住所A"
production is "住所A"
accounting is "住所A"

@100
sales is "住所B" : 住所変更
production is "住所A"
accounting is "住所A"

@200
sales is "住所B"
production is "住所B" : バッチ同期
accounting is "住所A"

@300
sales is "住所B"
production is "住所B"
accounting is "住所B" : 手動更新

@400
sales is "住所B"
production is "住所B"
accounting is "住所B"

@enduml
```

```plantuml
@startuml
title マスタ更新の同期問題

|販売管理|
start
:顧客住所変更;
:販売管理DBを更新;

|生産管理|
:夜間バッチで同期;
note right
  タイムラグ：最大24時間
end note

|財務会計|
:月次で手動確認;
note right
  タイムラグ：最大1ヶ月
end note

|問題|
:期間中のデータ不整合;
note right
  ・請求書の住所が古い
  ・配送先と請求先の不一致
  ・顧客からのクレーム
end note

stop

@enduml
```

---

## 36.2 MDM パターン

マスタデータ管理には、組織の状況や要件に応じた複数のアーキテクチャパターンがあります。

### MDM パターンの概要

```plantuml
@startuml
title MDM パターンの分類

rectangle "Registry Style\n（参照型）" as registry
note right of registry
  ・既存システムを維持
  ・インデックスのみ集約
  ・低コスト・低リスク
end note

rectangle "Consolidation Style\n（統合型）" as consolidation
note right of consolidation
  ・マスタを集約
  ・分析・レポート用途
  ・既存システムは変更なし
end note

rectangle "Coexistence Style\n（共存型）" as coexistence
note right of coexistence
  ・MDMと既存システムが共存
  ・双方向同期
  ・段階的な移行に適する
end note

rectangle "Transaction Hub Style\n（ハブ型）" as hub
note right of hub
  ・MDMが唯一の正
  ・すべての更新はハブ経由
  ・最高の一貫性
end note

registry -[hidden]down-> consolidation
consolidation -[hidden]down-> coexistence
coexistence -[hidden]down-> hub

@endumll
```

### Registry Style（参照型）

既存システムのマスタデータはそのまま維持し、MDMはマスタデータの「インデックス」として機能します。

```plantuml
@startuml
title Registry Style MDM

package "MDM（Registry）" as mdm {
    database "マスタインデックス\n----\nグローバルID\nローカルID（販売）\nローカルID（生産）\nローカルID（会計）" as index
}

package "販売管理" as sales {
    database "顧客マスタ" as sales_cust
}

package "生産管理" as production {
    database "取引先マスタ" as prod_partner
}

package "財務会計" as accounting {
    database "取引先マスタ" as acc_partner
}

rectangle "クロスリファレンス\nサービス" as xref

index <--> xref
xref <--> sales_cust : 参照
xref <--> prod_partner : 参照
xref <--> acc_partner : 参照

note bottom of mdm
  【特徴】
  ・各システムのマスタは変更なし
  ・グローバルIDによる紐付けのみ
  ・名寄せ・重複検出に活用

  【適用場面】
  ・既存システムへの影響を最小化したい
  ・分析・レポートでの統合ビューが欲しい
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// マスタインデックス
@Entity
@Table(name = "master_index")
public class MasterIndex {
    @Id
    private String globalId;

    @OneToMany(mappedBy = "masterIndex", cascade = CascadeType.ALL)
    private List<LocalIdMapping> localMappings;

    private String masterType; // CUSTOMER, SUPPLIER, PRODUCT
    private String canonicalName;
    private LocalDateTime lastUpdated;
}

// ローカルIDマッピング
@Entity
@Table(name = "local_id_mapping")
public class LocalIdMapping {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private MasterIndex masterIndex;

    private String systemCode; // SALES, PRODUCTION, ACCOUNTING
    private String localId;
    private String status; // ACTIVE, INACTIVE, PENDING
}

// クロスリファレンスサービス
@Service
public class CrossReferenceService {
    private final MasterIndexRepository indexRepository;

    // グローバルIDからローカルIDを取得
    public String getLocalId(String globalId, String systemCode) {
        return indexRepository.findById(globalId)
            .flatMap(index -> index.getLocalMappings().stream()
                .filter(m -> m.getSystemCode().equals(systemCode))
                .findFirst())
            .map(LocalIdMapping::getLocalId)
            .orElseThrow(() -> new MappingNotFoundException(globalId, systemCode));
    }

    // ローカルIDからグローバルIDを取得
    public String getGlobalId(String localId, String systemCode) {
        return indexRepository.findByLocalIdAndSystem(localId, systemCode)
            .map(MasterIndex::getGlobalId)
            .orElseThrow(() -> new MappingNotFoundException(localId, systemCode));
    }

    // 名寄せ処理
    public List<MasterIndex> findPotentialDuplicates(String name) {
        return indexRepository.findByCanonicalNameLike(normalize(name));
    }
}
```

</details>

### Consolidation Style（統合型）

各システムのマスタデータを MDM に集約し、分析やレポート用途の「ゴールデンレコード」を作成します。

```plantuml
@startuml
title Consolidation Style MDM

package "ソースシステム" as source {
    database "販売顧客" as sales_cust
    database "生産取引先" as prod_partner
    database "会計取引先" as acc_partner
}

package "MDM（Consolidation）" as mdm {
    rectangle "ETL/統合処理" as etl
    database "ゴールデンレコード\n----\n統合取引先マスタ\n品質スコア\nソース情報" as golden
}

package "分析システム" as analytics {
    rectangle "BI/レポート" as bi
    rectangle "データウェアハウス" as dwh
}

sales_cust --> etl : 抽出
prod_partner --> etl : 抽出
acc_partner --> etl : 抽出

etl --> golden : 統合・クレンジング

golden --> bi : 参照
golden --> dwh : 連携

note bottom of mdm
  【特徴】
  ・各システムからデータを収集
  ・クレンジング・統合処理
  ・ゴールデンレコード（信頼できる単一ソース）作成

  【適用場面】
  ・分析・レポートの統合ビューが必要
  ・運用系への書き戻し不要
end note

@enduml
```

#### ゴールデンレコードの構造

```plantuml
@startuml
title ゴールデンレコードの構造

class "GoldenRecord" as golden {
    +globalId: String
    +entityType: String
    +canonicalName: String
    +qualityScore: Double
    +survivorshipRule: String
    +lastConsolidated: Timestamp
}

class "AttributeValue" as attr {
    +attributeName: String
    +value: String
    +sourceSystem: String
    +confidence: Double
    +lastUpdated: Timestamp
}

class "SourceReference" as source {
    +systemCode: String
    +localId: String
    +lastSynced: Timestamp
    +syncStatus: String
}

class "MatchHistory" as history {
    +matchedGlobalId: String
    +matchScore: Double
    +matchRule: String
    +matchDate: Timestamp
}

golden "1" -- "*" attr : 属性
golden "1" -- "*" source : ソース参照
golden "1" -- "*" history : マッチ履歴

note right of golden
  【Survivorship Rule】
  複数ソースから同一属性がある場合の
  採用ルール
  ・最新優先
  ・信頼度優先
  ・特定システム優先
end note

@enduml
```

### Coexistence Style（共存型）

MDM と各システムのマスタが共存し、双方向で同期を行います。

```plantuml
@startuml
title Coexistence Style MDM

package "MDM Hub" as mdm {
    database "マスタハブ" as hub
    rectangle "同期エンジン" as sync
}

package "販売管理" as sales {
    database "顧客マスタ" as sales_cust
}

package "生産管理" as production {
    database "取引先マスタ" as prod_partner
}

package "財務会計" as accounting {
    database "取引先マスタ" as acc_partner
}

hub <--> sync
sync <--> sales_cust : 双方向同期
sync <--> prod_partner : 双方向同期
sync <--> acc_partner : 双方向同期

note bottom of mdm
  【特徴】
  ・MDMと各システムが共存
  ・双方向でデータ同期
  ・どちらでも更新可能

  【適用場面】
  ・段階的なMDM導入
  ・各システムの独自要件を維持
end note

@enduml
```

#### 同期ルールの設計

```plantuml
@startuml
title Coexistence同期ルール

|MDM Hub|
start
:変更イベント受信;

if (変更元は?) then (MDMHub)
    :各システムへ配信;
    :同期ステータス更新;
else (ローカルシステム)
    :変更内容を検証;

    if (競合あり?) then (yes)
        :競合解決ルール適用;
        note right
          ・タイムスタンプ優先
          ・システム優先度
          ・手動解決キュー
        end note
    else (no)
    endif

    :MDM Hubを更新;
    :他システムへ配信;
endif

:同期完了;

stop

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 同期設定
@Entity
@Table(name = "sync_configuration")
public class SyncConfiguration {
    @Id
    private String configId;

    private String entityType;
    private String sourceSystem;
    private String targetSystem;
    private SyncDirection direction; // INBOUND, OUTBOUND, BIDIRECTIONAL
    private ConflictResolution conflictResolution;

    public enum ConflictResolution {
        LATEST_WINS,      // 最新タイムスタンプ優先
        SOURCE_WINS,      // ソースシステム優先
        TARGET_WINS,      // ターゲットシステム優先
        MANUAL_REVIEW     // 手動確認
    }
}

// 同期サービス
@Service
public class CoexistenceSyncService {
    private final SyncConfigurationRepository configRepository;
    private final MasterHubRepository hubRepository;
    private final ConflictResolver conflictResolver;

    @EventListener
    public void handleMasterChange(MasterChangeEvent event) {
        SyncConfiguration config = configRepository
            .findByEntityTypeAndSystem(event.entityType(), event.sourceSystem());

        if (event.sourceSystem().equals("MDM_HUB")) {
            // MDM Hubからの変更：各システムへ配信
            distributeToSystems(event, config);
        } else {
            // ローカルシステムからの変更
            MasterRecord hubRecord = hubRepository.findByGlobalId(event.globalId());

            if (hasConflict(hubRecord, event)) {
                MasterRecord resolved = conflictResolver.resolve(
                    hubRecord,
                    event,
                    config.getConflictResolution()
                );
                hubRepository.save(resolved);
            } else {
                hubRepository.updateFromSource(event);
            }

            // 他システムへ配信
            distributeToOtherSystems(event, config);
        }
    }

    private boolean hasConflict(MasterRecord hubRecord, MasterChangeEvent event) {
        return hubRecord.getLastUpdated().isAfter(event.previousUpdateTime());
    }
}
```

</details>

### Transaction Hub Style（トランザクションハブ型）

MDM が唯一の正（Single Source of Truth）となり、すべてのマスタ更新は MDM 経由で行われます。

```plantuml
@startuml
title Transaction Hub Style MDM

package "MDM Hub（唯一の正）" as mdm {
    database "マスタデータ" as master
    rectangle "API Gateway" as api
    rectangle "バリデーション" as validation
    rectangle "ワークフロー" as workflow
}

package "販売管理" as sales {
    rectangle "顧客参照" as sales_ref
}

package "生産管理" as production {
    rectangle "取引先参照" as prod_ref
}

package "財務会計" as accounting {
    rectangle "取引先参照" as acc_ref
}

actor "マスタ管理者" as admin

admin --> api : マスタ更新
api --> validation : 検証
validation --> workflow : 承認フロー
workflow --> master : 更新

master --> sales_ref : 参照のみ
master --> prod_ref : 参照のみ
master --> acc_ref : 参照のみ

note bottom of mdm
  【特徴】
  ・MDMが唯一の更新ポイント
  ・各システムは参照のみ
  ・最高レベルのデータ一貫性

  【適用場面】
  ・厳格なデータガバナンスが必要
  ・規制対応（金融、医療等）
end note

@enduml
```

#### マスタ更新ワークフロー

```plantuml
@startuml
title Transaction Hub マスタ更新フロー

|申請者|
start
:マスタ変更申請;
:変更内容入力;

|MDM Hub|
:申請受付;
:自動バリデーション;

if (バリデーションOK?) then (yes)
else (no)
    :エラー通知;
    |申請者|
    :内容修正;
    |MDM Hub|
endif

:承認ワークフロー開始;

|承認者|
:変更内容確認;

if (承認?) then (yes)
    :承認;
else (no)
    :却下;
    |申請者|
    :却下通知受信;
    stop
endif

|MDM Hub|
:マスタデータ更新;
:変更イベント発行;
:監査ログ記録;

|各システム|
:変更イベント受信;
:ローカルキャッシュ更新;

|申請者|
:完了通知受信;

stop

@enduml
```

### MDM パターンの比較と選択

| パターン | データ一貫性 | 導入コスト | 運用負荷 | 適用場面 |
|---------|------------|----------|---------|---------|
| Registry | 低 | 低 | 低 | 分析用途、名寄せ |
| Consolidation | 中 | 中 | 中 | BI、レポート |
| Coexistence | 中〜高 | 中〜高 | 高 | 段階的移行 |
| Transaction Hub | 高 | 高 | 中 | 規制対応、厳格なガバナンス |

```plantuml
@startuml
title MDMパターン選択フローチャート

start

:MDM導入検討;

if (既存システムへの\n影響を最小化?) then (yes)
    if (分析用途のみ?) then (yes)
        :Consolidation Style;
        stop
    else (no)
        :Registry Style;
        stop
    endif
else (no)
    if (厳格なデータガバナンス\nが必要?) then (yes)
        :Transaction Hub Style;
        stop
    else (no)
        if (段階的な移行?) then (yes)
            :Coexistence Style;
            stop
        else (no)
            :Transaction Hub Style;
            stop
        endif
    endif
endif

@enduml
```

---

## 36.3 共通マスタの設計

基幹業務システムで共有される主要なマスタデータの統合設計について解説します。

### 取引先マスタの統合

取引先（顧客、仕入先、外注先）は、複数のシステムで参照される代表的なマスタです。

```plantuml
@startuml
title 統合取引先マスタの設計

entity "統合取引先マスタ" as partner {
    *取引先ID <<PK>>
    --
    *取引先名
    取引先名カナ
    取引先区分
    法人個人区分
    代表者名
    設立年月日
    資本金
    従業員数
    業種コード
    --
    作成日時
    更新日時
    有効フラグ
}

entity "取引先住所" as address {
    *住所ID <<PK>>
    --
    *取引先ID <<FK>>
    住所区分
    郵便番号
    都道府県
    市区町村
    町域
    番地
    建物名
    --
    主住所フラグ
}

entity "取引先連絡先" as contact {
    *連絡先ID <<PK>>
    --
    *取引先ID <<FK>>
    連絡先区分
    担当者名
    部署名
    電話番号
    FAX番号
    メールアドレス
    --
    主連絡先フラグ
}

entity "取引先口座" as account {
    *口座ID <<PK>>
    --
    *取引先ID <<FK>>
    銀行コード
    支店コード
    口座種別
    口座番号
    口座名義
    --
    主口座フラグ
}

entity "取引先システム属性" as system_attr {
    *属性ID <<PK>>
    --
    *取引先ID <<FK>>
    *システムコード
    属性名
    属性値
    --
    有効開始日
    有効終了日
}

partner ||--o{ address
partner ||--o{ contact
partner ||--o{ account
partner ||--o{ system_attr

note right of partner
  【取引先区分】
  ・CUSTOMER（顧客）
  ・SUPPLIER（仕入先）
  ・SUBCONTRACTOR（外注先）
  ・BOTH（顧客兼仕入先）
end note

note right of system_attr
  【システム固有属性の例】
  ・販売：与信限度額、締日、回収方法
  ・生産：納入リードタイム、発注ロット
  ・会計：勘定科目、補助科目
end note

@enduml
```

<details>
<summary>SQL 定義</summary>

```sql
-- 統合取引先マスタ
CREATE TABLE 統合取引先マスタ (
    取引先ID VARCHAR(20) PRIMARY KEY,
    取引先名 VARCHAR(200) NOT NULL,
    取引先名カナ VARCHAR(200),
    取引先区分 VARCHAR(20) NOT NULL,
    法人個人区分 VARCHAR(10),
    代表者名 VARCHAR(100),
    設立年月日 DATE,
    資本金 DECIMAL(15,0),
    従業員数 INTEGER,
    業種コード VARCHAR(10),
    作成日時 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    更新日時 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    有効フラグ BOOLEAN DEFAULT TRUE
);

-- 取引先住所
CREATE TABLE 取引先住所 (
    住所ID VARCHAR(20) PRIMARY KEY,
    取引先ID VARCHAR(20) NOT NULL,
    住所区分 VARCHAR(20) NOT NULL,
    郵便番号 VARCHAR(10),
    都道府県 VARCHAR(10),
    市区町村 VARCHAR(50),
    町域 VARCHAR(100),
    番地 VARCHAR(100),
    建物名 VARCHAR(100),
    主住所フラグ BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (取引先ID) REFERENCES 統合取引先マスタ(取引先ID)
);

-- 取引先システム属性
CREATE TABLE 取引先システム属性 (
    属性ID VARCHAR(20) PRIMARY KEY,
    取引先ID VARCHAR(20) NOT NULL,
    システムコード VARCHAR(20) NOT NULL,
    属性名 VARCHAR(50) NOT NULL,
    属性値 VARCHAR(500),
    有効開始日 DATE,
    有効終了日 DATE,
    FOREIGN KEY (取引先ID) REFERENCES 統合取引先マスタ(取引先ID),
    UNIQUE (取引先ID, システムコード, 属性名, 有効開始日)
);
```

</details>

### 商品/品目マスタの統合

販売管理の「商品」と生産管理の「品目」を統合的に管理します。

```plantuml
@startuml
title 統合商品/品目マスタの設計

entity "統合商品マスタ" as product {
    *商品ID <<PK>>
    --
    *商品名
    商品名カナ
    商品区分
    販売単位
    在庫管理単位
    製造単位
    基準換算率
    --
    JANコード
    製造元コード
    ブランドコード
    --
    有効開始日
    有効終了日
}

entity "商品分類" as category {
    *分類ID <<PK>>
    --
    *商品ID <<FK>>
    *分類体系コード
    分類コード
    分類階層
}

entity "商品販売属性" as sales_attr {
    *属性ID <<PK>>
    --
    *商品ID <<FK>>
    標準販売単価
    最低販売単価
    販売開始日
    販売終了日
    --
    課税区分
    消費税率
}

entity "商品製造属性" as mfg_attr {
    *属性ID <<PK>>
    --
    *商品ID <<FK>>
    製造リードタイム
    安全在庫数
    最小ロット数
    発注点
    --
    歩留率
    検査要否
}

entity "商品原価属性" as cost_attr {
    *属性ID <<PK>>
    --
    *商品ID <<FK>>
    標準原価
    原価計算方式
    有効開始日
    有効終了日
}

product ||--o{ category
product ||--o| sales_attr
product ||--o| mfg_attr
product ||--o{ cost_attr

note right of product
  【商品区分】
  ・FINISHED（製品）
  ・SEMI_FINISHED（半製品）
  ・WIP（仕掛品）
  ・RAW_MATERIAL（原材料）
  ・PURCHASED（購入品）
  ・SERVICE（サービス）
end note

@enduml
```

#### 商品コード体系の統一

```plantuml
@startuml
title 統一商品コード体系

object "コード構成" as code_structure {
    形式: PPPP-CCCC-SSSS-VV
    --
    PPPP: 商品区分（4桁）
    CCCC: 分類コード（4桁）
    SSSS: 連番（4桁）
    VV: バージョン（2桁）
}

object "商品区分コード" as product_type {
    PROD: 製品
    SEMI: 半製品
    RAWM: 原材料
    PART: 部品
    SUPP: 消耗品
    SERV: サービス
}

object "コード例" as examples {
    PROD-ELEC-0001-01: 電子機器製品001
    SEMI-MECH-0023-01: 機械半製品023
    RAWM-METAL-0102-01: 金属原材料102
}

@enduml
```

### 部門・組織マスタの統合

組織構造は、権限管理や実績集計に使用される重要なマスタです。

```plantuml
@startuml
title 統合組織マスタの設計

entity "組織マスタ" as org {
    *組織ID <<PK>>
    --
    *組織名
    組織名略称
    組織区分
    組織レベル
    親組織ID <<FK>>
    組織パス
    --
    有効開始日
    有効終了日
}

entity "組織階層" as hierarchy {
    *階層ID <<PK>>
    --
    *組織ID <<FK>>
    *階層種別
    上位組織ID
    下位組織ID
    階層順序
}

entity "組織属性" as org_attr {
    *属性ID <<PK>>
    --
    *組織ID <<FK>>
    *システムコード
    属性名
    属性値
    有効開始日
    有効終了日
}

entity "組織担当者" as staff {
    *担当者ID <<PK>>
    --
    *組織ID <<FK>>
    社員ID
    役職コード
    主担当フラグ
    有効開始日
    有効終了日
}

org ||--o{ hierarchy
org ||--o{ org_attr
org ||--o{ staff
org ||--o| org : 親組織

note right of org
  【組織区分】
  ・COMPANY（会社）
  ・DIVISION（事業部）
  ・DEPARTMENT（部）
  ・SECTION（課）
  ・TEAM（チーム）
end note

note right of hierarchy
  【階層種別】
  ・REPORTING（レポート階層）
  ・COST_CENTER（原価センター階層）
  ・SALES_TERRITORY（営業テリトリー階層）
end note

@enduml
```

#### 組織パスによる階層表現

```plantuml
@startuml
title 組織パスによる階層表現

object "株式会社ABC" as company {
    組織ID = "ORG-001"
    組織パス = "/ORG-001"
    組織レベル = 1
}

object "営業本部" as sales_div {
    組織ID = "ORG-010"
    組織パス = "/ORG-001/ORG-010"
    組織レベル = 2
}

object "東日本営業部" as east_dept {
    組織ID = "ORG-011"
    組織パス = "/ORG-001/ORG-010/ORG-011"
    組織レベル = 3
}

object "東京営業課" as tokyo_sec {
    組織ID = "ORG-111"
    組織パス = "/ORG-001/ORG-010/ORG-011/ORG-111"
    組織レベル = 4
}

company --> sales_div
sales_div --> east_dept
east_dept --> tokyo_sec

note bottom
  【組織パスの利点】
  ・階層検索が高速（LIKE '/ORG-001/%'）
  ・任意の深さに対応
  ・親子関係の判定が容易
end note

@enduml
```

<details>
<summary>SQL クエリ例</summary>

```sql
-- 特定組織の配下をすべて取得
SELECT * FROM 組織マスタ
WHERE 組織パス LIKE '/ORG-001/ORG-010/%'
ORDER BY 組織パス;

-- 特定組織の直接の子組織を取得
SELECT * FROM 組織マスタ
WHERE 親組織ID = 'ORG-010'
AND 有効終了日 IS NULL;

-- 組織の全階層を展開（再帰CTE）
WITH RECURSIVE 組織階層 AS (
    -- 基点
    SELECT 組織ID, 組織名, 親組織ID, 1 as レベル
    FROM 組織マスタ
    WHERE 組織ID = 'ORG-001'

    UNION ALL

    -- 再帰部分
    SELECT o.組織ID, o.組織名, o.親組織ID, h.レベル + 1
    FROM 組織マスタ o
    INNER JOIN 組織階層 h ON o.親組織ID = h.組織ID
    WHERE o.有効終了日 IS NULL
)
SELECT * FROM 組織階層
ORDER BY レベル, 組織ID;
```

</details>

---

## 36.4 MDM 導入のベストプラクティス

### データガバナンスの確立

```plantuml
@startuml
title データガバナンス体制

rectangle "データガバナンス委員会" as committee {
    rectangle "データスチュワード" as steward
    rectangle "データオーナー" as owner
    rectangle "データ品質管理者" as quality
}

rectangle "ポリシー・ルール" as policy {
    rectangle "データ標準" as standard
    rectangle "品質基準" as quality_std
    rectangle "セキュリティポリシー" as security
}

rectangle "プロセス" as process {
    rectangle "登録プロセス" as register
    rectangle "変更プロセス" as change
    rectangle "監査プロセス" as audit
}

rectangle "ツール" as tools {
    rectangle "MDMプラットフォーム" as platform
    rectangle "データ品質ツール" as dq_tool
    rectangle "ワークフローツール" as workflow
}

committee --> policy : 策定
committee --> process : 管理
process --> tools : 実装

@enduml
```

### 段階的な導入アプローチ

```plantuml
@startuml
title MDM段階的導入ロードマップ

|Phase 1|
:アセスメント;
note right
  ・現状分析
  ・課題特定
  ・ROI試算
end note

|Phase 2|
:パイロット;
note right
  ・対象マスタ選定
  ・小規模で検証
  ・課題抽出
end note

|Phase 3|
:展開;
note right
  ・対象マスタ拡大
  ・システム連携
  ・運用プロセス確立
end note

|Phase 4|
:最適化;
note right
  ・データ品質向上
  ・プロセス改善
  ・新規マスタ追加
end note

@enduml
```

---

## 36.5 まとめ

本章では、マスタデータ管理（MDM）の概念と実践的なパターンについて解説しました。

### 学んだこと

1. **マスタデータ統合の課題**

   - 各システムでのマスタ重複
   - コード体系の不一致
   - 更新タイミングの同期問題

2. **MDM パターン**

   - Registry Style：インデックスとしての軽量 MDM
   - Consolidation Style：分析用ゴールデンレコード
   - Coexistence Style：双方向同期による共存
   - Transaction Hub Style：唯一の正としての厳格な MDM

3. **共通マスタの設計**

   - 取引先マスタ：顧客・仕入先・外注先の統合
   - 商品/品目マスタ：販売と製造の属性統合
   - 部門・組織マスタ：階層構造の表現

### MDM 成功のポイント

- ビジネス要件に合ったパターンの選択
- 段階的な導入アプローチ
- データガバナンス体制の確立
- 継続的なデータ品質管理

### 次章の予告

第37章では、イベント駆動アーキテクチャについて解説します。ドメインイベント、イベントソーシング、CQRS など、モダンなシステム統合の基盤となる概念を学びます。
