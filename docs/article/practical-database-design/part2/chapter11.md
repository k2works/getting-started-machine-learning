---
type: Article
title: "第11章：共通処理の設計"
description: "販売管理システムでは、赤黒処理、自動採番、監査カラム、履歴管理といった共通的な処理が多くの業務で横断的に使用されます。"
tags: [article, practical-database-design, part2]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第11章：共通処理の設計

販売管理システムでは、赤黒処理、自動採番、監査カラム、履歴管理といった共通的な処理が多くの業務で横断的に使用されます。本章では、これらの共通処理のデータベース設計と実装を行います。

## 共通処理の全体像

共通処理は、業務データの整合性、追跡可能性、運用効率を高めるための基盤機能です。

```plantuml
@startuml

title 共通処理の位置づけ

rectangle "業務処理" {
  card "受注管理" as order
  card "出荷管理" as shipment
  card "売上管理" as sales
  card "請求管理" as billing
  card "支払管理" as payment
}

rectangle "共通処理" #LightGreen {
  card "赤黒処理" as redblack
  card "自動採番" as numbering
  card "監査カラム" as audit
  card "履歴管理" as history
}

order --> numbering : 受注番号生成
order --> audit : 監査情報付与
sales --> redblack : 伝票訂正
sales --> history : 変更履歴
billing --> numbering : 請求番号生成
payment --> numbering : 支払番号生成

@enduml
```

### 共通処理の役割

| 処理 | 役割 | 主な利用場面 |
|-----|------|------------|
| **赤黒処理** | 確定済み伝票の訂正 | 売上・仕入の金額訂正、取消 |
| **自動採番** | 一意な番号の発行 | 伝票番号の自動生成 |
| **監査カラム** | 変更者・日時の記録 | 全テーブル共通 |
| **履歴管理** | 変更前後の状態保存 | マスタ変更、重要データ |

---

## 11.1 赤黒処理の設計

### 赤黒処理とは

赤黒処理は、確定済みの伝票を訂正する際に使用する会計的手法です。元の伝票を「赤伝票」でマイナス計上し、正しい内容を「黒伝票」で再計上します。

```plantuml
@startuml

title 赤黒処理の概念

|経理部門|
start
:確定済み伝票の誤り発見;
note right: 例: 売上金額の誤り\n100,000円 → 120,000円

:赤伝票作成;
note right: 元伝票と同額で\nマイナス計上\n-100,000円

:黒伝票作成;
note right: 正しい金額で\nプラス計上\n+120,000円

:結果確認;
note right: 差額 +20,000円が\n純増として計上

stop

@enduml
```

### なぜ赤黒処理が必要か

| 方式 | メリット | デメリット |
|-----|---------|-----------|
| **物理削除** | シンプル | 監査証跡なし、復元不可 |
| **論理削除** | 証跡保持 | 集計時に除外処理必要 |
| **赤黒処理** | 監査証跡完全、会計的に正確 | 伝票数増加 |

会計領域では、一度確定した伝票を直接修正することは認められません。赤黒処理により、すべての変更履歴が伝票として残り、監査対応が可能になります。

### 伝票区分の定義

```plantuml
@startuml

title 伝票区分の遷移

[*] --> 通常
通常 : 通常の業務伝票
通常 --> 赤伝 : 訂正が必要
通常 --> 赤伝 : 取消が必要

赤伝 : 元伝票のマイナス計上
赤伝 --> 黒伝 : 訂正内容で再計上
赤伝 --> [*] : 取消のみの場合

黒伝 : 正しい内容で計上
黒伝 --> [*]

note right of 赤伝
  金額は元伝票の負数
  （マイナス値）
end note

note right of 黒伝
  訂正後の正しい金額
  （プラス値）
end note

@enduml
```

| 伝票区分 | 説明 | 金額の符号 |
|---------|------|----------|
| **通常** | 通常の業務伝票 | 正 |
| **赤伝** | 元伝票を打ち消す伝票 | 負（マイナス） |
| **黒伝** | 訂正後の内容を計上する伝票 | 正 |

### 赤黒処理の ER 図

```plantuml
@startuml

title 赤黒処理のER図

entity 赤黒処理履歴データ {
  ID <<PK>>
  --
  処理番号 <<UK>>
  処理日時
  伝票種別
  元伝票番号
  赤伝票番号
  黒伝票番号
  処理理由
  処理者
  作成日時
}

entity 売上データ {
  ID <<PK>>
  --
  売上番号 <<UK>>
  顧客コード <<FK>>
  売上日
  売上金額
  消費税額
  合計金額
  伝票区分
  元伝票番号
  ステータス
}

赤黒処理履歴データ }o--|| 売上データ : 元伝票番号
赤黒処理履歴データ }o--o| 売上データ : 赤伝票番号
赤黒処理履歴データ }o--o| 売上データ : 黒伝票番号

note right of 売上データ
  伝票区分:
  - 通常
  - 赤伝
  - 黒伝
end note

@enduml
```

### マイグレーション：赤黒処理関連テーブルの作成

<details>
<summary>V018__create_redblack_tables.sql</summary>

```sql
-- src/main/resources/db/migration/V018__create_redblack_tables.sql

-- 伝票区分
CREATE TYPE 伝票区分 AS ENUM ('通常', '赤伝', '黒伝');

-- 赤黒処理履歴データ
CREATE TABLE "赤黒処理履歴データ" (
    "ID" SERIAL PRIMARY KEY,
    "処理番号" VARCHAR(20) UNIQUE NOT NULL,
    "処理日時" TIMESTAMP NOT NULL,
    "伝票種別" VARCHAR(20) NOT NULL,
    "元伝票番号" VARCHAR(20) NOT NULL,
    "赤伝票番号" VARCHAR(20) NOT NULL,
    "黒伝票番号" VARCHAR(20),
    "処理理由" TEXT NOT NULL,
    "処理者" VARCHAR(50) NOT NULL,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 売上データに伝票区分を追加
ALTER TABLE "売上データ" ADD COLUMN IF NOT EXISTS "伝票区分" 伝票区分 DEFAULT '通常' NOT NULL;
ALTER TABLE "売上データ" ADD COLUMN IF NOT EXISTS "元伝票番号" VARCHAR(20);

-- インデックス
CREATE INDEX "idx_赤黒処理履歴_元伝票番号" ON "赤黒処理履歴データ"("元伝票番号");
CREATE INDEX "idx_赤黒処理履歴_処理日時" ON "赤黒処理履歴データ"("処理日時");

-- テーブルコメント
COMMENT ON TABLE "赤黒処理履歴データ" IS '確定済み伝票の訂正履歴を管理するテーブル';
COMMENT ON COLUMN "赤黒処理履歴データ"."伝票種別" IS '対象伝票の種類（売上、仕入、請求、支払）';
```

</details>

### 赤黒処理エンティティの実装

<details>
<summary>伝票区分 ENUM</summary>

```java
// src/main/java/com/example/sms/domain/model/common/SlipType.java
package com.example.sms.domain.model.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 伝票区分.
 */
@Getter
@RequiredArgsConstructor
public enum SlipType {
    NORMAL("通常"),
    RED("赤伝"),
    BLACK("黒伝");

    private final String displayName;

    /**
     * 表示名から伝票区分を取得.
     *
     * @param displayName 表示名
     * @return 伝票区分
     */
    public static SlipType fromDisplayName(String displayName) {
        for (SlipType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown slip type: " + displayName);
    }

    /**
     * 赤伝かどうか.
     *
     * @return 赤伝の場合true
     */
    public boolean isRed() {
        return this == RED;
    }

    /**
     * 符号を取得（赤伝はマイナス）.
     *
     * @return 赤伝は-1、それ以外は1
     */
    public int getSign() {
        return this == RED ? -1 : 1;
    }
}
```

</details>

<details>
<summary>赤黒処理履歴エンティティ</summary>

```java
// src/main/java/com/example/sms/domain/model/common/RedBlackHistory.java
package com.example.sms.domain.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 赤黒処理履歴.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedBlackHistory {
    /** ID. */
    private Integer id;
    /** 処理番号. */
    private String processNumber;
    /** 処理日時. */
    private LocalDateTime processDateTime;
    /** 伝票種別. */
    private String slipCategory;
    /** 元伝票番号. */
    private String originalSlipNumber;
    /** 赤伝票番号. */
    private String redSlipNumber;
    /** 黒伝票番号. */
    private String blackSlipNumber;
    /** 処理理由. */
    private String processReason;
    /** 処理者. */
    private String processedBy;
    /** 作成日時. */
    private LocalDateTime createdAt;
}
```

</details>

### 赤黒処理サービスの実装

> **Note:** 以下のサービス層コードは参考実装例です。実際のシステムでは要件に応じてカスタマイズしてください。

<details>
<summary>赤黒処理サービス</summary>

```java
// src/main/java/com/example/sms/application/service/RedBlackService.java
package com.example.sms.application.service;

import com.example.sms.domain.model.common.*;
import com.example.sms.domain.model.sales.*;
import com.example.sms.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedBlackService {

    private final RedBlackHistoryMapper redBlackHistoryMapper;
    private final SalesMapper salesMapper;
    private final SalesDetailMapper salesDetailMapper;
    private final NumberingService numberingService;

    /**
     * 処理番号を生成
     */
    private String generateProcessNumber() {
        String prefix = "RB-" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String latestNumber = redBlackHistoryMapper.findLatestProcessNumber(prefix + "%");

        int sequence = 1;
        if (latestNumber != null) {
            int currentSequence = Integer.parseInt(
                    latestNumber.substring(latestNumber.length() - 4));
            sequence = currentSequence + 1;
        }

        return prefix + String.format("%04d", sequence);
    }

    /**
     * 売上の赤黒処理を実行
     */
    @Transactional
    public RedBlackResult processSalesRedBlack(RedBlackInput input) {
        // 元伝票を取得
        Sales originalSales = salesMapper.findBySalesNumber(
                input.getOriginalSlipNumber());
        if (originalSales == null) {
            throw new IllegalArgumentException(
                    "元伝票が見つかりません: " + input.getOriginalSlipNumber());
        }

        if (originalSales.getSlipType() != SlipType.NORMAL) {
            throw new IllegalStateException(
                    "通常伝票以外は赤黒処理できません");
        }

        List<SalesDetail> originalDetails = salesDetailMapper
                .findBySalesNumber(input.getOriginalSlipNumber());

        // 赤伝票を作成（元伝票のマイナス）
        String redSlipNumber = numberingService.generateSalesNumber();
        Sales redSales = createRedSales(originalSales, redSlipNumber);
        salesMapper.insert(redSales);

        for (SalesDetail detail : originalDetails) {
            SalesDetail redDetail = createRedDetail(detail, redSlipNumber);
            salesDetailMapper.insert(redDetail);
        }

        // 黒伝票を作成（訂正後の内容）
        String blackSlipNumber = null;
        if (input.getCorrectedData() != null) {
            blackSlipNumber = numberingService.generateSalesNumber();
            Sales blackSales = createBlackSales(
                    originalSales, blackSlipNumber, input.getCorrectedData());
            salesMapper.insert(blackSales);

            for (CorrectedDetailData correctedDetail :
                    input.getCorrectedData().getDetails()) {
                SalesDetail blackDetail = createBlackDetail(
                        correctedDetail, blackSlipNumber);
                salesDetailMapper.insert(blackDetail);
            }
        }

        // 赤黒処理履歴を記録
        String processNumber = generateProcessNumber();
        RedBlackHistory history = RedBlackHistory.builder()
                .processNumber(processNumber)
                .processDateTime(LocalDateTime.now())
                .slipCategory("売上")
                .originalSlipNumber(input.getOriginalSlipNumber())
                .redSlipNumber(redSlipNumber)
                .blackSlipNumber(blackSlipNumber)
                .processReason(input.getProcessReason())
                .processedBy(input.getProcessedBy())
                .build();
        redBlackHistoryMapper.insert(history);

        return RedBlackResult.builder()
                .processNumber(processNumber)
                .originalSlipNumber(input.getOriginalSlipNumber())
                .redSlipNumber(redSlipNumber)
                .blackSlipNumber(blackSlipNumber)
                .build();
    }

    private Sales createRedSales(Sales original, String redSlipNumber) {
        return Sales.builder()
                .salesNumber(redSlipNumber)
                .customerCode(original.getCustomerCode())
                .salesDate(original.getSalesDate())
                .salesAmount(original.getSalesAmount().negate())
                .taxAmount(original.getTaxAmount().negate())
                .totalAmount(original.getTotalAmount().negate())
                .slipType(SlipType.RED)
                .originalSlipNumber(original.getSalesNumber())
                .status(SalesStatus.CONFIRMED)
                .build();
    }

    private SalesDetail createRedDetail(SalesDetail original,
                                         String redSlipNumber) {
        return SalesDetail.builder()
                .salesNumber(redSlipNumber)
                .detailNumber(original.getDetailNumber())
                .productCode(original.getProductCode())
                .quantity(original.getQuantity().negate())
                .unitPrice(original.getUnitPrice())
                .amount(original.getAmount().negate())
                .build();
    }

    private Sales createBlackSales(Sales original, String blackSlipNumber,
                                    CorrectedSalesData correctedData) {
        return Sales.builder()
                .salesNumber(blackSlipNumber)
                .customerCode(correctedData.getCustomerCode() != null
                        ? correctedData.getCustomerCode()
                        : original.getCustomerCode())
                .salesDate(correctedData.getSalesDate() != null
                        ? correctedData.getSalesDate()
                        : original.getSalesDate())
                .salesAmount(correctedData.getSalesAmount())
                .taxAmount(correctedData.getTaxAmount())
                .totalAmount(correctedData.getTotalAmount())
                .slipType(SlipType.BLACK)
                .originalSlipNumber(original.getSalesNumber())
                .status(SalesStatus.CONFIRMED)
                .build();
    }

    private SalesDetail createBlackDetail(CorrectedDetailData correctedDetail,
                                           String blackSlipNumber) {
        return SalesDetail.builder()
                .salesNumber(blackSlipNumber)
                .detailNumber(correctedDetail.getDetailNumber())
                .productCode(correctedDetail.getProductCode())
                .quantity(correctedDetail.getQuantity())
                .unitPrice(correctedDetail.getUnitPrice())
                .amount(correctedDetail.getAmount())
                .build();
    }

    /**
     * 赤黒処理履歴を取得
     */
    public RedBlackHistory findByProcessNumber(String processNumber) {
        return redBlackHistoryMapper.findByProcessNumber(processNumber);
    }

    /**
     * 元伝票番号で赤黒処理履歴を検索
     */
    public List<RedBlackHistory> findByOriginalSlipNumber(
            String originalSlipNumber) {
        return redBlackHistoryMapper.findByOriginalSlipNumber(originalSlipNumber);
    }
}
```

</details>

---

## 11.2 自動採番の設計

### 採番の要件

| 要件 | 説明 |
|-----|------|
| **一意性** | 同一種別内で重複しない |
| **連番性** | 基本的に連番で発行 |
| **可読性** | 年月や種別が識別可能 |
| **拡張性** | 将来の要件変更に対応 |

### 採番パターン

```plantuml
@startuml

title 採番パターンの例

note as N1
  **売上伝票番号**
  SL-202501-0001
  ├── SL: 売上
  ├── 202501: 年月
  └── 0001: 連番

  **受注番号**
  ORD-2025-00001
  ├── ORD: 受注
  ├── 2025: 年度
  └── 00001: 連番

  **請求番号**
  INV-202501-0001
  ├── INV: 請求
  ├── 202501: 年月
  └── 0001: 連番
end note

@enduml
```

### 採番形式の種類

| 採番形式 | 説明 | 例 |
|---------|------|-----|
| **YEARLY** | 年次でリセット | ORD-2025-00001 |
| **MONTHLY** | 月次でリセット | SL-202501-0001 |
| **DAILY** | 日次でリセット | SL-20250115-0001 |
| **SEQUENTIAL** | リセットなし連番 | SEQ-000001 |

### 採番テーブルの ER 図

```plantuml
@startuml

title 採番管理のER図

entity 採番マスタ {
  採番コード <<PK>>
  --
  採番名
  プレフィックス
  採番形式
  桁数
  現在値
  最終採番日
  リセット対象
  作成日時
  更新日時
}

entity 採番履歴データ {
  ID <<PK>>
  --
  採番コード <<FK>>
  採番年月
  最終番号
  作成日時
  更新日時
}

採番マスタ ||--o{ 採番履歴データ : 採番コード

note right of 採番マスタ
  採番形式:
  - YEARLY (年次)
  - MONTHLY (月次)
  - DAILY (日次)
  - SEQUENTIAL (連番)
end note

@enduml
```

### マイグレーション：採番テーブルの作成

<details>
<summary>V019__create_numbering_tables.sql</summary>

```sql
-- src/main/resources/db/migration/V019__create_numbering_tables.sql

-- 採番マスタ
CREATE TABLE "採番マスタ" (
    "採番コード" VARCHAR(20) PRIMARY KEY,
    "採番名" VARCHAR(100) NOT NULL,
    "プレフィックス" VARCHAR(10) NOT NULL,
    "採番形式" VARCHAR(20) NOT NULL,  -- YEARLY, MONTHLY, DAILY, SEQUENTIAL
    "桁数" INTEGER NOT NULL,
    "現在値" BIGINT DEFAULT 0 NOT NULL,
    "最終採番日" DATE,
    "リセット対象" BOOLEAN DEFAULT false NOT NULL,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 採番履歴データ
CREATE TABLE "採番履歴データ" (
    "ID" SERIAL PRIMARY KEY,
    "採番コード" VARCHAR(20) NOT NULL,
    "採番年月" VARCHAR(8) NOT NULL,
    "最終番号" BIGINT NOT NULL,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "fk_採番履歴_採番マスタ"
        FOREIGN KEY ("採番コード") REFERENCES "採番マスタ"("採番コード"),
    UNIQUE ("採番コード", "採番年月")
);

-- 初期データ
INSERT INTO "採番マスタ" ("採番コード", "採番名", "プレフィックス", "採番形式", "桁数") VALUES
('SALES', '売上番号', 'SL', 'MONTHLY', 4),
('ORDER', '受注番号', 'ORD', 'YEARLY', 5),
('SHIPMENT', '出荷番号', 'SHP', 'MONTHLY', 4),
('INVOICE', '請求番号', 'INV', 'MONTHLY', 4),
('RECEIPT', '入金番号', 'RC', 'MONTHLY', 4),
('PURCHASE_ORDER', '発注番号', 'PO', 'MONTHLY', 4),
('PURCHASE', '仕入番号', 'PU', 'MONTHLY', 4),
('PAYMENT', '支払番号', 'PAY', 'MONTHLY', 4),
('STOCKTAKING', '棚卸番号', 'ST', 'MONTHLY', 4);

-- インデックス
CREATE INDEX "idx_採番履歴_採番年月" ON "採番履歴データ"("採番年月");

-- テーブルコメント
COMMENT ON TABLE "採番マスタ" IS '伝票番号の採番ルールを管理するマスタテーブル';
COMMENT ON TABLE "採番履歴データ" IS '年月別の採番状況を記録するテーブル';
```

</details>

### 採番サービスの実装

> **Note:** 以下のサービス層コードは参考実装例です。実際のシステムでは要件に応じてカスタマイズしてください。

<details>
<summary>採番形式 ENUM</summary>

```java
// src/main/java/com/example/sms/domain/model/common/NumberingFormat.java
package com.example.sms.domain.model.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NumberingFormat {
    YEARLY("年次"),
    MONTHLY("月次"),
    DAILY("日次"),
    SEQUENTIAL("連番");

    private final String displayName;

    public static NumberingFormat fromDisplayName(String displayName) {
        for (NumberingFormat format : values()) {
            if (format.displayName.equals(displayName)) {
                return format;
            }
        }
        throw new IllegalArgumentException(
                "Unknown numbering format: " + displayName);
    }
}
```

</details>

<details>
<summary>採番マスタエンティティ</summary>

```java
// src/main/java/com/example/sms/domain/model/common/NumberingMaster.java
package com.example.sms.domain.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 採番マスタ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NumberingMaster {
    /** 採番コード. */
    private String numberingCode;
    /** 採番名. */
    private String numberingName;
    /** プレフィックス. */
    private String prefix;
    /** 採番形式. */
    private String format;
    /** 桁数. */
    private Integer digits;
    /** 現在値. */
    private Long currentValue;
    /** 最終採番日. */
    private LocalDate lastNumberingDate;
    /** リセット対象. */
    private Boolean resetTarget;
    /** 作成日時. */
    private LocalDateTime createdAt;
    /** 更新日時. */
    private LocalDateTime updatedAt;
}
```

</details>

<details>
<summary>採番履歴エンティティ</summary>

```java
// src/main/java/com/example/sms/domain/model/common/NumberingHistory.java
package com.example.sms.domain.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 採番履歴.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NumberingHistory {
    /** ID. */
    private Integer id;
    /** 採番コード. */
    private String numberingCode;
    /** 採番年月. */
    private String yearMonth;
    /** 最終番号. */
    private Long lastNumber;
    /** 作成日時. */
    private LocalDateTime createdAt;
    /** 更新日時. */
    private LocalDateTime updatedAt;
}
```

</details>

<details>
<summary>採番サービス</summary>

```java
// src/main/java/com/example/sms/application/service/NumberingService.java
package com.example.sms.application.service;

import com.example.sms.domain.model.common.*;
import com.example.sms.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class NumberingService {

    private final NumberingMasterMapper numberingMasterMapper;
    private final NumberingHistoryMapper numberingHistoryMapper;

    /**
     * 次の番号を取得
     */
    @Transactional
    public String getNextNumber(String numberingCode) {
        NumberingMaster master = numberingMasterMapper
                .findByNumberingCode(numberingCode);
        if (master == null) {
            throw new IllegalArgumentException(
                    "採番マスタが見つかりません: " + numberingCode);
        }

        LocalDate today = LocalDate.now();
        String yearMonth = getYearMonth(today, master.getFormat());

        // 履歴を取得または作成
        NumberingHistory history = numberingHistoryMapper
                .findByCodeAndYearMonth(numberingCode, yearMonth);

        long nextNumber;
        if (history == null) {
            // 新規年月の場合、1から開始
            nextNumber = 1;
            history = NumberingHistory.builder()
                    .numberingCode(numberingCode)
                    .yearMonth(yearMonth)
                    .lastNumber(nextNumber)
                    .build();
            numberingHistoryMapper.insert(history);
        } else {
            // 既存の場合、インクリメント
            nextNumber = history.getLastNumber() + 1;
            numberingHistoryMapper.incrementLastNumber(history.getId());
        }

        // 番号をフォーマット
        return formatNumber(master, yearMonth, nextNumber);
    }

    private String getYearMonth(LocalDate date, NumberingFormat format) {
        return switch (format) {
            case YEARLY -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            case MONTHLY -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAILY -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case SEQUENTIAL -> "0000";
        };
    }

    private String formatNumber(NumberingMaster master, String yearMonth,
                                 long number) {
        String sequenceFormat = "%0" + master.getDigits() + "d";
        String sequence = String.format(sequenceFormat, number);

        if (master.getFormat() == NumberingFormat.SEQUENTIAL) {
            return master.getPrefix() + "-" + sequence;
        } else {
            return master.getPrefix() + "-" + yearMonth + "-" + sequence;
        }
    }

    /** 売上番号を生成 */
    public String generateSalesNumber() {
        return getNextNumber("SALES");
    }

    /** 受注番号を生成 */
    public String generateOrderNumber() {
        return getNextNumber("ORDER");
    }

    /** 出荷番号を生成 */
    public String generateShipmentNumber() {
        return getNextNumber("SHIPMENT");
    }

    /** 請求番号を生成 */
    public String generateInvoiceNumber() {
        return getNextNumber("INVOICE");
    }

    /** 入金番号を生成 */
    public String generateReceiptNumber() {
        return getNextNumber("RECEIPT");
    }

    /** 発注番号を生成 */
    public String generatePurchaseOrderNumber() {
        return getNextNumber("PURCHASE_ORDER");
    }

    /** 仕入番号を生成 */
    public String generatePurchaseNumber() {
        return getNextNumber("PURCHASE");
    }

    /** 支払番号を生成 */
    public String generatePaymentNumber() {
        return getNextNumber("PAYMENT");
    }

    /** 棚卸番号を生成 */
    public String generateStocktakingNumber() {
        return getNextNumber("STOCKTAKING");
    }
}
```

</details>

---

## 11.3 監査カラムの設計

### 監査カラムとは

すべてのテーブルに共通して設ける、データの作成・更新に関する情報を記録するカラムです。

```plantuml
@startuml

title 監査カラムの構成

class "業務テーブル" as base {
  + ID: SERIAL <<PK>>
  --
  + 業務カラム1
  + 業務カラム2
  + ...
  --
  + 作成日時: TIMESTAMP
  + 作成者: VARCHAR
  + 更新日時: TIMESTAMP
  + 更新者: VARCHAR
  + 削除フラグ: BOOLEAN
  + 削除日時: TIMESTAMP
  + 削除者: VARCHAR
}

note right of base
  監査カラム:
  - いつ作成されたか
  - 誰が作成したか
  - いつ更新されたか
  - 誰が更新したか
  - 論理削除されたか
end note

@enduml
```

### 監査カラムの種類

| カラム | 型 | 説明 |
|-------|-----|------|
| **作成日時** | TIMESTAMP | レコード作成日時 |
| **作成者** | VARCHAR | レコード作成者 |
| **更新日時** | TIMESTAMP | 最終更新日時 |
| **更新者** | VARCHAR | 最終更新者 |
| **削除フラグ** | BOOLEAN | 論理削除フラグ |
| **削除日時** | TIMESTAMP | 論理削除日時 |
| **削除者** | VARCHAR | 論理削除実行者 |

### 論理削除の考え方

```plantuml
@startuml

title 物理削除 vs 論理削除

rectangle "物理削除" #FFCCCC {
  card "DELETE FROM 顧客\nWHERE ID = 1" as phys
  note bottom: データ完全消去\n復元不可
}

rectangle "論理削除" #CCFFCC {
  card "UPDATE 顧客\nSET 削除フラグ = true\nWHERE ID = 1" as logic
  note bottom: データ保持\n必要に応じて復元可能
}

note as N1
  論理削除の注意点:
  - 検索時に削除フラグを考慮
  - 一意制約との整合性
  - ストレージ肥大化
end note

@enduml
```

### 監査カラム用エンティティ基底クラス

<details>
<summary>Auditable 基底クラス</summary>

```java
// src/main/java/com/example/sms/domain/model/common/Auditable.java
package com.example.sms.domain.model.common;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
public abstract class Auditable {
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private Boolean deletedFlag;
    private LocalDateTime deletedAt;
    private String deletedBy;

    /**
     * 論理削除されているかどうか
     */
    public boolean isDeleted() {
        return deletedFlag != null && deletedFlag;
    }

    /**
     * 論理削除を実行
     */
    public void softDelete(String deletedBy) {
        this.deletedFlag = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    /**
     * 論理削除を復元
     */
    public void restore() {
        this.deletedFlag = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
```

</details>

### MyBatis インターセプターによる自動設定

<details>
<summary>監査インターセプター</summary>

```java
// src/main/java/com/example/sms/infrastructure/persistence/AuditInterceptor.java
package com.example.sms.infrastructure.persistence;

import com.example.sms.domain.model.common.Auditable;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Properties;

@Intercepts({
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class})
})
@Component
public class AuditInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        if (parameter instanceof Auditable auditable) {
            String currentUser = getCurrentUser();
            LocalDateTime now = LocalDateTime.now();

            if (ms.getSqlCommandType() == SqlCommandType.INSERT) {
                auditable.setCreatedAt(now);
                auditable.setCreatedBy(currentUser);
                auditable.setUpdatedAt(now);
                auditable.setUpdatedBy(currentUser);
                if (auditable.getDeletedFlag() == null) {
                    auditable.setDeletedFlag(false);
                }
            } else if (ms.getSqlCommandType() == SqlCommandType.UPDATE) {
                auditable.setUpdatedAt(now);
                auditable.setUpdatedBy(currentUser);
            }
        }

        return invocation.proceed();
    }

    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 設定プロパティがあれば処理
    }
}
```

</details>

---

## 11.4 履歴テーブルの設計

### 履歴管理の種類

| 種類 | 説明 | 用途 |
|-----|------|-----|
| **変更履歴** | 変更前後の値を記録 | 監査、デバッグ |
| **スナップショット** | 特定時点の状態を保存 | 帳票再発行、分析 |
| **イベントソーシング** | すべてのイベントを記録 | 完全な追跡、再構築 |

```plantuml
@startuml

title 履歴管理の種類

rectangle "変更履歴" #LightBlue {
  card "変更前/変更後を\nJSONBで保存" as change
  note bottom
    UPDATE時に
    - 変更前データ
    - 変更後データ
    - 変更理由
    を記録
  end note
}

rectangle "スナップショット" #LightGreen {
  card "特定時点の\n状態を全保存" as snapshot
  note bottom
    マスタ履歴
    - 有効開始日
    - 有効終了日
    - 全カラム値
  end note
}

rectangle "イベントソーシング" #LightCoral {
  card "すべての\nイベントを記録" as event
  note bottom
    - 作成イベント
    - 更新イベント
    - 削除イベント
    から状態を再構築
  end note
}

@enduml
```

### 変更履歴テーブルの ER 図

```plantuml
@startuml

title 変更履歴のER図

entity 変更履歴データ {
  ID <<PK>>
  --
  テーブル名
  レコードID
  操作種別
  変更日時
  変更者
  変更前データ (JSONB)
  変更後データ (JSONB)
  変更理由
  作成日時
}

entity 商品マスタ履歴 {
  ID <<PK>>
  --
  商品コード
  有効開始日
  有効終了日
  商品名
  商品区分
  単価
  税区分
  作成日時
  作成者
}

note right of 変更履歴データ
  操作種別:
  - INSERT
  - UPDATE
  - DELETE
end note

note right of 商品マスタ履歴
  スナップショット方式
  有効期間で履歴管理
end note

@enduml
```

### マイグレーション：履歴テーブルの作成

<details>
<summary>V020__create_history_tables.sql</summary>

```sql
-- src/main/resources/db/migration/V020__create_history_tables.sql

-- 変更履歴データ
CREATE TABLE "変更履歴データ" (
    "ID" SERIAL PRIMARY KEY,
    "テーブル名" VARCHAR(100) NOT NULL,
    "レコードID" VARCHAR(100) NOT NULL,
    "操作種別" VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    "変更日時" TIMESTAMP NOT NULL,
    "変更者" VARCHAR(50) NOT NULL,
    "変更前データ" JSONB,
    "変更後データ" JSONB,
    "変更理由" TEXT,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- マスタ履歴データ（スナップショット方式の例）
CREATE TABLE "商品マスタ履歴" (
    "ID" SERIAL PRIMARY KEY,
    "商品コード" VARCHAR(20) NOT NULL,
    "有効開始日" DATE NOT NULL,
    "有効終了日" DATE,
    "商品名" VARCHAR(100) NOT NULL,
    "商品区分" 商品区分 NOT NULL,
    "単価" DECIMAL(15, 2),
    "税区分" 税区分,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "作成者" VARCHAR(50),
    UNIQUE ("商品コード", "有効開始日")
);

-- インデックス
CREATE INDEX "idx_変更履歴_テーブル名" ON "変更履歴データ"("テーブル名");
CREATE INDEX "idx_変更履歴_レコードID" ON "変更履歴データ"("レコードID");
CREATE INDEX "idx_変更履歴_変更日時" ON "変更履歴データ"("変更日時");
CREATE INDEX "idx_商品マスタ履歴_商品コード" ON "商品マスタ履歴"("商品コード");
CREATE INDEX "idx_商品マスタ履歴_有効期間"
    ON "商品マスタ履歴"("有効開始日", "有効終了日");

-- テーブルコメント
COMMENT ON TABLE "変更履歴データ" IS 'データ変更の履歴をJSONBで保存するテーブル';
COMMENT ON TABLE "商品マスタ履歴" IS '商品マスタのスナップショット履歴';
```

</details>

### 履歴サービスの実装

> **Note:** 以下のサービス層コードは参考実装例です。実際のシステムでは要件に応じてカスタマイズしてください。

<details>
<summary>変更履歴エンティティ</summary>

```java
// src/main/java/com/example/sms/domain/model/common/ChangeHistory.java
package com.example.sms.domain.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 変更履歴.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeHistory {
    /** ID. */
    private Integer id;
    /** テーブル名. */
    private String tableName;
    /** レコードID. */
    private String recordId;
    /** 操作種別. */
    private String operationType;
    /** 変更日時. */
    private LocalDateTime changedAt;
    /** 変更者. */
    private String changedBy;
    /** 変更前データ（JSON）. */
    private String beforeData;
    /** 変更後データ（JSON）. */
    private String afterData;
    /** 変更理由. */
    private String changeReason;
    /** 作成日時. */
    private LocalDateTime createdAt;
}
```

</details>

<details>
<summary>商品マスタ履歴エンティティ</summary>

```java
// src/main/java/com/example/sms/domain/model/common/ProductHistory.java
package com.example.sms.domain.model.common;

import com.example.sms.domain.model.product.ProductCategory;
import com.example.sms.domain.model.product.TaxCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品マスタ履歴.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductHistory {
    /** ID. */
    private Integer id;
    /** 商品コード. */
    private String productCode;
    /** 有効開始日. */
    private LocalDate validFromDate;
    /** 有効終了日. */
    private LocalDate validToDate;
    /** 商品名. */
    private String productName;
    /** 商品区分. */
    private ProductCategory productCategory;
    /** 単価. */
    private BigDecimal unitPrice;
    /** 税区分. */
    private TaxCategory taxCategory;
    /** 作成日時. */
    private LocalDateTime createdAt;
    /** 作成者. */
    private String createdBy;

    /**
     * 指定日時点で有効かどうか.
     *
     * @param targetDate 対象日
     * @return 有効な場合true
     */
    public boolean isValidOn(LocalDate targetDate) {
        if (targetDate.isBefore(validFromDate)) {
            return false;
        }
        return validToDate == null || !targetDate.isAfter(validToDate);
    }
}
```

</details>

<details>
<summary>履歴サービス</summary>

```java
// src/main/java/com/example/sms/application/service/HistoryService.java
package com.example.sms.application.service;

import com.example.sms.domain.model.common.ChangeHistory;
import com.example.sms.infrastructure.persistence.mapper.ChangeHistoryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ChangeHistoryMapper changeHistoryMapper;
    private final ObjectMapper objectMapper;

    /**
     * 変更履歴を記録
     */
    @Transactional
    public void recordChange(String tableName, String recordId,
                             String operationType, Object beforeData,
                             Object afterData, String changedBy,
                             String changeReason) {
        try {
            String beforeJson = beforeData != null
                    ? objectMapper.writeValueAsString(beforeData) : null;
            String afterJson = afterData != null
                    ? objectMapper.writeValueAsString(afterData) : null;

            ChangeHistory history = ChangeHistory.builder()
                    .tableName(tableName)
                    .recordId(recordId)
                    .operationType(operationType)
                    .changedAt(LocalDateTime.now())
                    .changedBy(changedBy)
                    .beforeData(beforeJson)
                    .afterData(afterJson)
                    .changeReason(changeReason)
                    .build();

            changeHistoryMapper.insert(history);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "履歴データのシリアライズに失敗しました", e);
        }
    }

    /**
     * INSERT履歴を記録
     */
    @Transactional
    public void recordInsert(String tableName, String recordId,
                             Object data, String changedBy) {
        recordChange(tableName, recordId, "INSERT", null, data, changedBy, null);
    }

    /**
     * UPDATE履歴を記録
     */
    @Transactional
    public void recordUpdate(String tableName, String recordId,
                             Object beforeData, Object afterData,
                             String changedBy, String changeReason) {
        recordChange(tableName, recordId, "UPDATE",
                beforeData, afterData, changedBy, changeReason);
    }

    /**
     * DELETE履歴を記録
     */
    @Transactional
    public void recordDelete(String tableName, String recordId,
                             Object data, String changedBy,
                             String changeReason) {
        recordChange(tableName, recordId, "DELETE",
                data, null, changedBy, changeReason);
    }

    /**
     * テーブルとレコードIDで履歴を検索
     */
    public List<ChangeHistory> findByTableAndRecordId(String tableName,
                                                       String recordId) {
        return changeHistoryMapper.findByTableAndRecordId(tableName, recordId);
    }

    /**
     * 期間で履歴を検索
     */
    public List<ChangeHistory> findByDateRange(LocalDateTime fromDateTime,
                                                LocalDateTime toDateTime) {
        return changeHistoryMapper.findByDateRange(fromDateTime, toDateTime);
    }
}
```

</details>

---

## 業務システムでの使い分け

赤黒処理、論理削除、変更履歴は、業務の性質に応じて使い分けます。

```plantuml
@startuml

title 3つのアプローチの使い分け

rectangle "会計系データ" #LightBlue {
  card "売上・仕入・請求・支払" as accounting
  note bottom: 赤黒処理\n（監査証跡必須）
}

rectangle "マスタデータ" #LightGreen {
  card "商品・顧客・仕入先" as master
  note bottom: 変更履歴（JSONB）\n+ スナップショット
}

rectangle "トランザクション" #LightCoral {
  card "受注・出荷・在庫" as transaction
  note bottom: 論理削除\n+ 監査カラム
}

accounting --> 赤黒処理履歴
master --> 変更履歴データ
master --> マスタ履歴
transaction --> 監査カラム

@enduml
```

| データ種別 | 推奨方式 | 理由 |
|-----------|---------|------|
| **会計系** | 赤黒処理 | 監査対応、会計原則 |
| **マスタ** | 変更履歴 + スナップショット | 変更追跡、過去参照 |
| **トランザクション** | 論理削除 + 監査カラム | 運用効率、復元可能 |

---

## 第11章のまとめ

本章では、販売管理システム全般で使用される共通処理の DB 設計と実装について学びました。

### 学んだこと

1. **赤黒処理の設計**
   - 確定済み伝票の訂正方法
   - 赤伝票（マイナス計上）と黒伝票（プラス計上）
   - 監査証跡の確保

2. **自動採番の設計**
   - 採番マスタによる柔軟な設定
   - 年次・月次・日次・連番の対応
   - 履歴による採番管理

3. **監査カラムの設計**
   - 作成日時・作成者・更新日時・更新者
   - 論理削除（削除フラグ・削除日時・削除者）
   - インターセプターによる自動設定

4. **履歴テーブルの設計**
   - 変更履歴（変更前後の JSON 保存）
   - スナップショット方式の履歴
   - 有効期間による履歴管理

### テーブル一覧

| テーブル名（日本語） | Java エンティティ | 説明 |
|---|---|---|
| 赤黒処理履歴データ | RedBlackHistory | 赤黒処理の履歴 |
| 採番マスタ | NumberingMaster | 採番ルールの定義 |
| 採番履歴データ | NumberingHistory | 年月別の採番状況 |
| 変更履歴データ | ChangeHistory | データ変更の履歴 |
| 商品マスタ履歴 | ProductHistory | 商品マスタのスナップショット |

### ENUM 一覧

| DB ENUM 型（日本語） | Java Enum | 値 |
|---|---|---|
| 伝票区分 | SlipType | 通常→NORMAL, 赤伝→RED, 黒伝→BLACK |

### 共通処理 ER 図

```plantuml
@startuml

title 共通処理 ER図

entity 採番マスタ {
  採番コード <<PK>>
  --
  採番名
  プレフィックス
  採番形式
  桁数
  現在値
}

entity 採番履歴データ {
  ID <<PK>>
  --
  採番コード <<FK>>
  採番年月
  最終番号
}

entity 赤黒処理履歴データ {
  ID <<PK>>
  --
  処理番号 <<UK>>
  処理日時
  伝票種別
  元伝票番号
  赤伝票番号
  黒伝票番号
  処理理由
  処理者
}

entity 変更履歴データ {
  ID <<PK>>
  --
  テーブル名
  レコードID
  操作種別
  変更日時
  変更者
  変更前データ (JSONB)
  変更後データ (JSONB)
}

採番マスタ ||--o{ 採番履歴データ

note right of 採番マスタ
  採番形式:
  - YEARLY (年次)
  - MONTHLY (月次)
  - DAILY (日次)
  - SEQUENTIAL (連番)
end note

note right of 赤黒処理履歴データ
  伝票種別:
  - 売上
  - 仕入
  - 請求
  - 支払
end note

@enduml
```

### 次章の予告

第12章では、B 社事例を用いた財務会計データの作成に進みます。販売管理システムで設計したマスタ・トランザクションデータを、実際のビジネスシナリオに基づいて構築します。

---

[← 第10章：債務管理の設計](./chapter10.md) | [第12章：財務会計データ作成（B社事例） →](./chapter12.md)
