---
type: Article
title: "第24章：生産計画の設計"
description: "本章では、生産管理システムの核心部分である「生産計画」を設計します。"
tags: [article, practical-database-design, part4]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第24章：生産計画の設計

本章では、生産管理システムの核心部分である「生産計画」を設計します。生産計画は、製造業における意思決定の起点となる重要な機能です。

---

## 24.1 生産計画の特徴

生産計画は、「いつ」「何を」「どれだけ」生産するかを決定する計画系の中核機能です。

### 計画系と実績系の分離

生産管理システムのデータは、大きく「計画系」と「実績系」に分類されます。

```plantuml
@startuml

title 計画系と実績系の関係

package "計画系データ" as plan {
  entity "生産計画" as pp
  entity "所要量計画" as mrp
  entity "購買オーダ" as po
  entity "製造オーダ" as mo
}

package "実績系データ" as actual {
  entity "発注実績" as ord
  entity "入荷実績" as rcv
  entity "製造実績" as mfg
  entity "在庫実績" as inv
}

pp --> mrp : 展開
mrp --> po : 生成
mrp --> mo : 生成

po ..> ord : 実行
mo ..> mfg : 実行
ord --> rcv : 入荷
mfg --> inv : 完成

note right of plan
計画系の特徴：
・何度でも再計算可能
・将来の予測値
・変更が頻繁に発生
end note

note right of actual
実績系の特徴：
・一度確定したら変更不可
・過去の事実
・監査証跡として保存
end note

@enduml
```

#### 計画系データの特徴

| 特性 | 説明 |
|-----|------|
| **再計算可能** | 条件が変わればいつでも再計算できる |
| **仮の数値** | 確定前は何度でも変更可能 |
| **将来指向** | 未来の予定を表す |
| **柔軟性重視** | ビジネス状況に応じて調整される |

#### 実績系データの特徴

| 特性 | 説明 |
|-----|------|
| **不可逆** | 一度記録したら削除・変更しない |
| **事実の記録** | 実際に起きたことを記録 |
| **過去指向** | 過去の事実を表す |
| **証跡性重視** | 監査・トレーサビリティに使用 |

### 計画の階層構造

生産計画は、長期から短期へと段階的に詳細化されていきます。

```plantuml
@startuml

title 生産計画の階層構造

start
:①ビジネスプラン(中期経営計画);
note right
対象期間：3〜5年
管理単位：年
end note

:②年間販売計画;
note right
対象期間：1年
管理単位：月
end note

:③月次販売/生産計画;
note right
対象期間：1〜3ヶ月
管理単位：月
end note

:④基準生産計画(MPS);
note right
対象期間：1週間〜1ヶ月
管理単位：日
end note

:⑤所要量展開(MRP) <-> スケジューラ;
note right
対象期間：1日〜1週間
管理単位：日/時間/分
end note

fork
:⑥購買オーダ;
fork again
:⑦製造オーダ;
fork end

@enduml
```

#### 各計画レベルの概要

| レベル | 計画名 | 対象期間 | 管理単位 | 主な責任者 |
|-------|--------|---------|---------|-----------|
| 1 | ビジネスプラン | 3〜5年 | 年 | 経営層 |
| 2 | 年間販売計画 | 1年 | 月 | 営業部門 |
| 3 | 月次生産計画 | 1〜3ヶ月 | 月 | 生産管理部門 |
| 4 | 基準生産計画（MPS） | 1週〜1ヶ月 | 日 | 生産管理部門 |
| 5 | 所要量展開（MRP） | 1日〜1週間 | 日/時間 | 生産管理部門 |

### 基準生産計画（MPS）の理解

基準生産計画（Master Production Schedule）は、最終製品の生産計画です。

```plantuml
@startuml

title 基準生産計画（MPS）の位置づけ

rectangle "需要情報" as demand {
  card "受注情報" as order
  card "需要予測" as forecast
  card "安全在庫" as safety
}

rectangle "供給情報" as supply {
  card "在庫情報" as inventory
  card "仕掛情報" as wip
  card "発注残" as po
}

rectangle "MPS（基準生産計画）" as mps #Yellow {
  card "製品A: 100個 @ 1/15" as mps1
  card "製品B: 50個 @ 1/20" as mps2
  card "製品C: 200個 @ 1/25" as mps3
}

demand --> mps
supply --> mps
mps --> MRP展開

@enduml
```

### 計画データのスキーマ設計

<details>
<summary>DDL（生産計画関連テーブル）</summary>

```sql
-- V008__create_planning_tables.sql

-- 計画ステータス
CREATE TYPE 計画ステータス AS ENUM ('草案', '確定', '展開済', '取消');

-- オーダ種別
CREATE TYPE オーダ種別 AS ENUM ('購買', '製造');

-- 引当区分
CREATE TYPE 引当区分 AS ENUM ('在庫', '発注残', '製造残');

-- 基準生産計画
CREATE TABLE "基準生産計画" (
    "ID" SERIAL PRIMARY KEY,
    "MPS番号" VARCHAR(20) UNIQUE NOT NULL,
    "計画日" DATE NOT NULL,
    "品目コード" VARCHAR(20) NOT NULL,
    "計画数量" DECIMAL(15, 2) NOT NULL,
    "納期" DATE NOT NULL,
    "ステータス" 計画ステータス DEFAULT '草案' NOT NULL,
    "場所コード" VARCHAR(20),
    "備考" TEXT,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "作成者" VARCHAR(50),
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新者" VARCHAR(50),
    CONSTRAINT "fk_基準生産計画_品目"
        FOREIGN KEY ("品目コード") REFERENCES "品目マスタ"("品目コード")
);

-- オーダ情報（購買・製造共通）
CREATE TABLE "オーダ情報" (
    "ID" SERIAL PRIMARY KEY,
    "オーダNO" VARCHAR(20) UNIQUE NOT NULL,
    "オーダ種別" オーダ種別 NOT NULL,
    "品目コード" VARCHAR(20) NOT NULL,
    "着手予定日" DATE NOT NULL,
    "納期" DATE NOT NULL,
    "有効期限" DATE,
    "計画数量" DECIMAL(15, 2) NOT NULL,
    "場所コード" VARCHAR(20) NOT NULL,
    "ステータス" 計画ステータス DEFAULT '草案' NOT NULL,
    "MPS_ID" INTEGER,
    "親オーダID" INTEGER,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "作成者" VARCHAR(50),
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新者" VARCHAR(50),
    CONSTRAINT "fk_オーダ情報_品目"
        FOREIGN KEY ("品目コード") REFERENCES "品目マスタ"("品目コード"),
    CONSTRAINT "fk_オーダ情報_MPS"
        FOREIGN KEY ("MPS_ID") REFERENCES "基準生産計画"("ID"),
    CONSTRAINT "fk_オーダ情報_親オーダ"
        FOREIGN KEY ("親オーダID") REFERENCES "オーダ情報"("ID")
);

-- 所要情報
CREATE TABLE "所要情報" (
    "ID" SERIAL PRIMARY KEY,
    "所要NO" VARCHAR(20) UNIQUE NOT NULL,
    "オーダID" INTEGER NOT NULL,
    "品目コード" VARCHAR(20) NOT NULL,
    "納期" DATE NOT NULL,
    "必要数量" DECIMAL(15, 2) NOT NULL,
    "引当済数量" DECIMAL(15, 2) DEFAULT 0 NOT NULL,
    "不足数量" DECIMAL(15, 2) DEFAULT 0 NOT NULL,
    "場所コード" VARCHAR(20) NOT NULL,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "fk_所要情報_オーダ"
        FOREIGN KEY ("オーダID") REFERENCES "オーダ情報"("ID"),
    CONSTRAINT "fk_所要情報_品目"
        FOREIGN KEY ("品目コード") REFERENCES "品目マスタ"("品目コード")
);

-- 引当情報
CREATE TABLE "引当情報" (
    "ID" SERIAL PRIMARY KEY,
    "所要ID" INTEGER NOT NULL,
    "引当区分" 引当区分 NOT NULL,
    "オーダID" INTEGER,
    "引当日" DATE NOT NULL,
    "引当数量" DECIMAL(15, 2) NOT NULL,
    "場所コード" VARCHAR(20) NOT NULL,
    "作成日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "更新日時" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "fk_引当情報_所要"
        FOREIGN KEY ("所要ID") REFERENCES "所要情報"("ID"),
    CONSTRAINT "fk_引当情報_オーダ"
        FOREIGN KEY ("オーダID") REFERENCES "オーダ情報"("ID")
);

-- インデックス
CREATE INDEX "idx_基準生産計画_品目コード" ON "基準生産計画"("品目コード");
CREATE INDEX "idx_基準生産計画_納期" ON "基準生産計画"("納期");
CREATE INDEX "idx_オーダ情報_品目コード" ON "オーダ情報"("品目コード");
CREATE INDEX "idx_オーダ情報_納期" ON "オーダ情報"("納期");
CREATE INDEX "idx_オーダ情報_MPS_ID" ON "オーダ情報"("MPS_ID");
CREATE INDEX "idx_所要情報_オーダID" ON "所要情報"("オーダID");
CREATE INDEX "idx_所要情報_品目コード" ON "所要情報"("品目コード");
CREATE INDEX "idx_引当情報_所要ID" ON "引当情報"("所要ID");
```

</details>

### アーキテクチャ構造

生産計画でも、ヘキサゴナルアーキテクチャ（ポート＆アダプター）を採用します。

```plantuml
@startuml
skinparam componentStyle rectangle

package "domain.model.plan" {
  [PlanStatus]
  [OrderType]
  [AllocationType]
  [MasterProductionSchedule]
  [Order]
  [Requirement]
  [Allocation]
}

package "application.port.out" {
  [MpsRepository] <<interface>>
  [OrderRepository] <<interface>>
  [RequirementRepository] <<interface>>
  [AllocationRepository] <<interface>>
}

package "application.service" {
  [MrpService]
}

package "infrastructure.out.persistence" {
  package "mapper" {
    [MasterProductionScheduleMapper]
    [OrderMapper]
    [RequirementMapper]
    [AllocationMapper]
  }
  package "repository" {
    [MpsRepositoryImpl]
    [OrderRepositoryImpl]
    [RequirementRepositoryImpl]
    [AllocationRepositoryImpl]
  }
  package "typehandler" {
    [PlanStatusTypeHandler]
    [OrderTypeTypeHandler]
    [AllocationTypeTypeHandler]
  }
}

[MrpService] --> [MpsRepository]
[MrpService] --> [OrderRepository]
[MrpService] --> [RequirementRepository]
[MrpService] --> [AllocationRepository]

[MpsRepositoryImpl] ..|> [MpsRepository]
[OrderRepositoryImpl] ..|> [OrderRepository]
[RequirementRepositoryImpl] ..|> [RequirementRepository]
[AllocationRepositoryImpl] ..|> [AllocationRepository]

[MpsRepositoryImpl] --> [MasterProductionScheduleMapper]
[OrderRepositoryImpl] --> [OrderMapper]
[RequirementRepositoryImpl] --> [RequirementMapper]
[AllocationRepositoryImpl] --> [AllocationMapper]

@enduml
```

### ドメインモデル

<details>
<summary>計画ステータス Enum（PlanStatus.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/PlanStatus.java
package com.example.pms.domain.model.plan;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanStatus {
    DRAFT("草案"),
    CONFIRMED("確定"),
    EXPANDED("展開済"),
    CANCELLED("取消");

    private final String displayName;

    public static PlanStatus fromDisplayName(String displayName) {
        for (PlanStatus status : values()) {
            if (status.displayName.equals(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown plan status: " + displayName);
    }
}
```

</details>

<details>
<summary>オーダ種別 Enum（OrderType.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/OrderType.java
package com.example.pms.domain.model.plan;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderType {
    PURCHASE("購買"),
    MANUFACTURING("製造");

    private final String displayName;

    public static OrderType fromDisplayName(String displayName) {
        for (OrderType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown order type: " + displayName);
    }
}
```

</details>

<details>
<summary>引当区分 Enum（AllocationType.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/AllocationType.java
package com.example.pms.domain.model.plan;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AllocationType {
    INVENTORY("在庫"),
    PURCHASE_ORDER("発注残"),
    MANUFACTURING_ORDER("製造残");

    private final String displayName;

    public static AllocationType fromDisplayName(String displayName) {
        for (AllocationType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown allocation type: " + displayName);
    }
}
```

</details>

<details>
<summary>基準生産計画エンティティ（MasterProductionSchedule.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/MasterProductionSchedule.java
package com.example.pms.domain.model.plan;

import com.example.pms.domain.model.item.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterProductionSchedule {
    private Integer id;
    private String mpsNumber;
    private LocalDate planDate;
    private String itemCode;
    private BigDecimal planQuantity;
    private LocalDate dueDate;
    private PlanStatus status;
    private String locationCode;
    private String remarks;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // リレーション
    private Item item;
}
```

</details>

<details>
<summary>オーダ情報エンティティ（Order.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/Order.java
package com.example.pms.domain.model.plan;

import com.example.pms.domain.model.item.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Integer id;
    private String orderNumber;
    private OrderType orderType;
    private String itemCode;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate expirationDate;
    private BigDecimal planQuantity;
    private String locationCode;
    private PlanStatus status;
    private Integer mpsId;
    private Integer parentOrderId;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // リレーション
    private Item item;
    private MasterProductionSchedule mps;
    private Order parentOrder;
    private List<Order> childOrders;
    private List<Requirement> requirements;
}
```

</details>

<details>
<summary>所要情報エンティティ（Requirement.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/Requirement.java
package com.example.pms.domain.model.plan;

import com.example.pms.domain.model.item.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Requirement {
    private Integer id;
    private String requirementNumber;
    private Integer orderId;
    private String itemCode;
    private LocalDate dueDate;
    private BigDecimal requiredQuantity;
    private BigDecimal allocatedQuantity;
    private BigDecimal shortageQuantity;
    private String locationCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // リレーション
    private Order order;
    private Item item;
    private List<Allocation> allocations;
}
```

</details>

<details>
<summary>引当情報エンティティ（Allocation.java）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/Allocation.java
package com.example.pms.domain.model.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Allocation {
    private Integer id;
    private Integer requirementId;
    private AllocationType allocationType;
    private Integer orderId;
    private LocalDate allocationDate;
    private BigDecimal allocatedQuantity;
    private String locationCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // リレーション
    private Requirement requirement;
    private Order order;
}
```

</details>

### TypeHandler

<details>
<summary>PlanStatusTypeHandler</summary>

```java
// src/main/java/com/example/pms/infrastructure/out/persistence/typehandler/PlanStatusTypeHandler.java
package com.example.pms.infrastructure.out.persistence.typehandler;

import com.example.pms.domain.model.plan.PlanStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(PlanStatus.class)
public class PlanStatusTypeHandler extends BaseTypeHandler<PlanStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PlanStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getDisplayName());
    }

    @Override
    public PlanStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : PlanStatus.fromDisplayName(value);
    }

    @Override
    public PlanStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : PlanStatus.fromDisplayName(value);
    }

    @Override
    public PlanStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : PlanStatus.fromDisplayName(value);
    }
}
```

</details>

<details>
<summary>OrderTypeTypeHandler</summary>

```java
// src/main/java/com/example/pms/infrastructure/out/persistence/typehandler/OrderTypeTypeHandler.java
package com.example.pms.infrastructure.out.persistence.typehandler;

import com.example.pms.domain.model.plan.OrderType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(OrderType.class)
public class OrderTypeTypeHandler extends BaseTypeHandler<OrderType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OrderType parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getDisplayName());
    }

    @Override
    public OrderType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : OrderType.fromDisplayName(value);
    }

    @Override
    public OrderType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : OrderType.fromDisplayName(value);
    }

    @Override
    public OrderType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : OrderType.fromDisplayName(value);
    }
}
```

</details>

<details>
<summary>AllocationTypeTypeHandler</summary>

```java
// src/main/java/com/example/pms/infrastructure/out/persistence/typehandler/AllocationTypeTypeHandler.java
package com.example.pms.infrastructure.out.persistence.typehandler;

import com.example.pms.domain.model.plan.AllocationType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(AllocationType.class)
public class AllocationTypeTypeHandler extends BaseTypeHandler<AllocationType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, AllocationType parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getDisplayName());
    }

    @Override
    public AllocationType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : AllocationType.fromDisplayName(value);
    }

    @Override
    public AllocationType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : AllocationType.fromDisplayName(value);
    }

    @Override
    public AllocationType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : AllocationType.fromDisplayName(value);
    }
}
```

</details>

### MyBatis Mapper

<details>
<summary>MasterProductionScheduleMapper.xml</summary>

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.pms.infrastructure.out.persistence.mapper.MasterProductionScheduleMapper">

    <resultMap id="MasterProductionScheduleResultMap"
               type="com.example.pms.domain.model.plan.MasterProductionSchedule">
        <id property="id" column="ID"/>
        <result property="mpsNumber" column="MPS番号"/>
        <result property="planDate" column="計画日"/>
        <result property="itemCode" column="品目コード"/>
        <result property="planQuantity" column="計画数量"/>
        <result property="dueDate" column="納期"/>
        <result property="status" column="ステータス"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler"/>
        <result property="locationCode" column="場所コード"/>
        <result property="remarks" column="備考"/>
        <result property="createdAt" column="作成日時"/>
        <result property="createdBy" column="作成者"/>
        <result property="updatedAt" column="更新日時"/>
        <result property="updatedBy" column="更新者"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="ID">
        INSERT INTO "基準生産計画" (
            "MPS番号", "計画日", "品目コード", "計画数量", "納期",
            "ステータス", "場所コード", "備考", "作成者"
        ) VALUES (
            #{mpsNumber},
            #{planDate},
            #{itemCode},
            #{planQuantity},
            #{dueDate},
            #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
            #{locationCode},
            #{remarks},
            #{createdBy}
        )
    </insert>

    <select id="findById" resultMap="MasterProductionScheduleResultMap">
        SELECT * FROM "基準生産計画" WHERE "ID" = #{id}
    </select>

    <select id="findByMpsNumber" resultMap="MasterProductionScheduleResultMap">
        SELECT * FROM "基準生産計画" WHERE "MPS番号" = #{mpsNumber}
    </select>

    <select id="findByStatus" resultMap="MasterProductionScheduleResultMap">
        SELECT * FROM "基準生産計画"
        WHERE "ステータス" = #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス
        ORDER BY "納期"
    </select>

    <update id="updateStatus">
        UPDATE "基準生産計画"
        SET "ステータス" = #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
            "更新日時" = CURRENT_TIMESTAMP
        WHERE "ID" = #{id}
    </update>

    <delete id="deleteAll">
        DELETE FROM "基準生産計画"
    </delete>
</mapper>
```

</details>

<details>
<summary>OrderMapper.xml</summary>

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.pms.infrastructure.out.persistence.mapper.OrderMapper">

    <resultMap id="OrderResultMap" type="com.example.pms.domain.model.plan.Order">
        <id property="id" column="ID"/>
        <result property="orderNumber" column="オーダNO"/>
        <result property="orderType" column="オーダ種別"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler"/>
        <result property="itemCode" column="品目コード"/>
        <result property="startDate" column="着手予定日"/>
        <result property="dueDate" column="納期"/>
        <result property="expirationDate" column="有効期限"/>
        <result property="planQuantity" column="計画数量"/>
        <result property="locationCode" column="場所コード"/>
        <result property="status" column="ステータス"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler"/>
        <result property="mpsId" column="MPS_ID"/>
        <result property="parentOrderId" column="親オーダID"/>
        <result property="createdAt" column="作成日時"/>
        <result property="createdBy" column="作成者"/>
        <result property="updatedAt" column="更新日時"/>
        <result property="updatedBy" column="更新者"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="ID">
        INSERT INTO "オーダ情報" (
            "オーダNO", "オーダ種別", "品目コード", "着手予定日", "納期",
            "有効期限", "計画数量", "場所コード", "ステータス", "MPS_ID", "親オーダID", "作成者"
        ) VALUES (
            #{orderNumber},
            #{orderType, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler}::オーダ種別,
            #{itemCode},
            #{startDate},
            #{dueDate},
            #{expirationDate},
            #{planQuantity},
            #{locationCode},
            #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
            #{mpsId},
            #{parentOrderId},
            #{createdBy}
        )
    </insert>

    <select id="findById" resultMap="OrderResultMap">
        SELECT * FROM "オーダ情報" WHERE "ID" = #{id}
    </select>

    <select id="findByOrderNumber" resultMap="OrderResultMap">
        SELECT * FROM "オーダ情報" WHERE "オーダNO" = #{orderNumber}
    </select>

    <select id="findByMpsId" resultMap="OrderResultMap">
        SELECT * FROM "オーダ情報" WHERE "MPS_ID" = #{mpsId}
    </select>

    <select id="findByParentOrderId" resultMap="OrderResultMap">
        SELECT * FROM "オーダ情報" WHERE "親オーダID" = #{parentOrderId}
    </select>

    <update id="updateParentOrderId">
        UPDATE "オーダ情報"
        SET "親オーダID" = #{parentOrderId}, "更新日時" = CURRENT_TIMESTAMP
        WHERE "ID" = #{id}
    </update>

    <update id="updateStatus">
        UPDATE "オーダ情報"
        SET "ステータス" = #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
            "更新日時" = CURRENT_TIMESTAMP
        WHERE "ID" = #{id}
    </update>

    <delete id="deleteAll">
        DELETE FROM "オーダ情報"
    </delete>
</mapper>
```

</details>

<details>
<summary>RequirementMapper.xml</summary>

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.pms.infrastructure.out.persistence.mapper.RequirementMapper">

    <resultMap id="RequirementResultMap" type="com.example.pms.domain.model.plan.Requirement">
        <id property="id" column="ID"/>
        <result property="requirementNumber" column="所要NO"/>
        <result property="orderId" column="オーダID"/>
        <result property="itemCode" column="品目コード"/>
        <result property="dueDate" column="納期"/>
        <result property="requiredQuantity" column="必要数量"/>
        <result property="allocatedQuantity" column="引当済数量"/>
        <result property="shortageQuantity" column="不足数量"/>
        <result property="locationCode" column="場所コード"/>
        <result property="createdAt" column="作成日時"/>
        <result property="updatedAt" column="更新日時"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="ID">
        INSERT INTO "所要情報" (
            "所要NO", "オーダID", "品目コード", "納期", "必要数量",
            "引当済数量", "不足数量", "場所コード"
        ) VALUES (
            #{requirementNumber},
            #{orderId},
            #{itemCode},
            #{dueDate},
            #{requiredQuantity},
            #{allocatedQuantity},
            #{shortageQuantity},
            #{locationCode}
        )
    </insert>

    <select id="findById" resultMap="RequirementResultMap">
        SELECT * FROM "所要情報" WHERE "ID" = #{id}
    </select>

    <select id="findByOrderId" resultMap="RequirementResultMap">
        SELECT * FROM "所要情報" WHERE "オーダID" = #{orderId}
    </select>

    <update id="updateAllocation">
        UPDATE "所要情報"
        SET "引当済数量" = #{allocatedQuantity},
            "不足数量" = #{shortageQuantity},
            "更新日時" = CURRENT_TIMESTAMP
        WHERE "ID" = #{id}
    </update>

    <delete id="deleteAll">
        DELETE FROM "所要情報"
    </delete>
</mapper>
```

</details>

<details>
<summary>AllocationMapper.xml</summary>

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.pms.infrastructure.out.persistence.mapper.AllocationMapper">

    <resultMap id="AllocationResultMap" type="com.example.pms.domain.model.plan.Allocation">
        <id property="id" column="ID"/>
        <result property="requirementId" column="所要ID"/>
        <result property="allocationType" column="引当区分"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.AllocationTypeTypeHandler"/>
        <result property="orderId" column="オーダID"/>
        <result property="allocationDate" column="引当日"/>
        <result property="allocatedQuantity" column="引当数量"/>
        <result property="locationCode" column="場所コード"/>
        <result property="createdAt" column="作成日時"/>
        <result property="updatedAt" column="更新日時"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="ID">
        INSERT INTO "引当情報" (
            "所要ID", "引当区分", "オーダID", "引当日", "引当数量", "場所コード"
        ) VALUES (
            #{requirementId},
            #{allocationType, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.AllocationTypeTypeHandler}::引当区分,
            #{orderId},
            #{allocationDate},
            #{allocatedQuantity},
            #{locationCode}
        )
    </insert>

    <select id="findByRequirementId" resultMap="AllocationResultMap">
        SELECT * FROM "引当情報" WHERE "所要ID" = #{requirementId}
    </select>

    <delete id="deleteAll">
        DELETE FROM "引当情報"
    </delete>
</mapper>
```

</details>

### リポジトリ

<details>
<summary>MpsRepository インターフェース</summary>

```java
// src/main/java/com/example/pms/application/port/out/MpsRepository.java
package com.example.pms.application.port.out;

import com.example.pms.domain.model.plan.MasterProductionSchedule;
import com.example.pms.domain.model.plan.PlanStatus;

import java.util.List;
import java.util.Optional;

/**
 * 基準生産計画リポジトリ（Output Port）
 */
public interface MpsRepository {
    void save(MasterProductionSchedule mps);
    Optional<MasterProductionSchedule> findById(Integer id);
    Optional<MasterProductionSchedule> findByMpsNumber(String mpsNumber);
    List<MasterProductionSchedule> findByStatus(PlanStatus status);
    void updateStatus(Integer id, PlanStatus status);
    void deleteAll();
}
```

</details>

<details>
<summary>MpsRepositoryImpl 実装</summary>

```java
// src/main/java/com/example/pms/infrastructure/out/persistence/repository/MpsRepositoryImpl.java
package com.example.pms.infrastructure.out.persistence.repository;

import com.example.pms.application.port.out.MpsRepository;
import com.example.pms.domain.model.plan.MasterProductionSchedule;
import com.example.pms.domain.model.plan.PlanStatus;
import com.example.pms.infrastructure.out.persistence.mapper.MasterProductionScheduleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MpsRepositoryImpl implements MpsRepository {

    private final MasterProductionScheduleMapper mpsMapper;

    public MpsRepositoryImpl(MasterProductionScheduleMapper mpsMapper) {
        this.mpsMapper = mpsMapper;
    }

    @Override
    public void save(MasterProductionSchedule mps) {
        mpsMapper.insert(mps);
    }

    @Override
    public Optional<MasterProductionSchedule> findById(Integer id) {
        return Optional.ofNullable(mpsMapper.findById(id));
    }

    @Override
    public Optional<MasterProductionSchedule> findByMpsNumber(String mpsNumber) {
        return Optional.ofNullable(mpsMapper.findByMpsNumber(mpsNumber));
    }

    @Override
    public List<MasterProductionSchedule> findByStatus(PlanStatus status) {
        return mpsMapper.findByStatus(status);
    }

    @Override
    public void updateStatus(Integer id, PlanStatus status) {
        mpsMapper.updateStatus(id, status);
    }

    @Override
    public void deleteAll() {
        mpsMapper.deleteAll();
    }
}
```

</details>

---

## 24.2 MRP（所要量展開）

所要量展開（Material Requirements Planning）は、基準生産計画（MPS）を起点として、必要な部品・材料の所要量を計算し、購買オーダや製造オーダを生成する処理です。

### MRP の基本概念

```plantuml
@startuml

title 所要量展開（MRP）の概要

entity オーダ情報 {
  オーダNO
  --
  品目コード
  着手予定日
  使用可能予定日
  有効期限
  計画数量
  場所コード
}
note top of オーダ情報 : 不足分について新規オーダ作成\n計画数量はロットサイズ考慮

entity 引当情報 {
  所要NO
  引当区分
  オーダNO
  引当予定日
  引当数量
  場所コード
}
note left of 引当情報 : 所要に対する引当情報を管理

entity 所要情報 {
  所要NO
  --
  オーダNO
  品目コード
  納期
  必要数量
  場所コード
}

BOM情報 .> オーダ情報 : 展開
オーダ情報 }o-- 引当情報
引当情報 <.. 在庫推移情報 : 引当元
引当情報 <.. 発注情報残 : 引当元
引当情報 <.. 製造残情報 : 引当元
オーダ情報 --o{ 所要情報 : 生成
所要情報 --o{ 引当情報 : 引当

@enduml
```

### MRP 処理の流れ

```plantuml
@startuml

title MRP処理フロー

start

:MPSから製品オーダを取得;

repeat
  :オーダに対して所要量を計算;

  :在庫・発注残・製造残から引当;

  if (不足がある？) then (はい)
    :新規オーダを作成;
    note right
      ・ロットサイズを考慮
      ・リードタイムを考慮
    end note

    :BOMを展開して子品目の所要を生成;
  else (いいえ)
    :処理完了;
  endif

repeat while (未処理のオーダがある？) is (はい)

stop

@enduml
```

### 総所要量・正味所要量の計算

MRP では以下の計算を行います：

| 項目 | 計算式 |
|------|--------|
| **総所要量** | 親オーダ数量 × (必要数量 / 基準数量) × (1 + 不良率) |
| **引当可能量** | 在庫残 + 発注残 + 製造残 |
| **正味所要量** | 総所要量 - 引当可能量 |
| **オーダ数量** | ロットサイズを考慮した発注・製造数量 |

### ロットまとめ

ロットサイズの計算ロジック：

1. **最小ロット数**: 必要数量が最小ロット数未満の場合、最小ロット数を採用
2. **刻みロット数**: 最小ロット数を超える場合、刻みロット数で切り上げ
3. **最大ロット数**: 計算結果が最大ロット数を超える場合、最大ロット数で制限

### MRP サービス

<details>
<summary>MrpService.java</summary>

```java
// src/main/java/com/example/pms/application/service/MrpService.java
package com.example.pms.application.service;

import com.example.pms.application.port.out.*;
import com.example.pms.domain.model.bom.Bom;
import com.example.pms.domain.model.item.Item;
import com.example.pms.domain.model.item.ItemCategory;
import com.example.pms.domain.model.plan.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MrpService {

    private final ItemRepository itemRepository;
    private final BomRepository bomRepository;
    private final OrderRepository orderRepository;
    private final RequirementRepository requirementRepository;
    private final AllocationRepository allocationRepository;

    /**
     * オーダから所要量を展開する
     */
    @Transactional
    public List<Requirement> explodeRequirements(Integer orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        var bomList = bomRepository.findByParentItemCodeEffective(
                order.getItemCode(),
                order.getDueDate()
        );

        List<Requirement> requirements = new ArrayList<>();

        for (var bom : bomList) {
            var childItem = itemRepository.findByItemCodeAndDate(
                    bom.getChildItemCode(),
                    order.getDueDate()
            );

            if (childItem.isEmpty()) continue;

            // 所要量 = 親オーダ数量 × (必要数量 / 基準数量) × (1 + 不良率)
            var requiredQuantity = order.getPlanQuantity()
                    .multiply(bom.getRequiredQuantity())
                    .divide(bom.getBaseQuantity(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(
                            bom.getDefectRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    ));

            // 子品目のリードタイムを考慮した納期
            var item = childItem.get();
            int leadTime = item.getLeadTime() != null ? item.getLeadTime() : 0;
            int safetyLeadTime = item.getSafetyLeadTime() != null ? item.getSafetyLeadTime() : 0;
            var childDueDate = calculateStartDate(order.getStartDate(), leadTime, safetyLeadTime);

            var requirement = Requirement.builder()
                    .requirementNumber("REQ-" + System.currentTimeMillis() + "-" + bom.getChildItemCode())
                    .orderId(order.getId())
                    .itemCode(bom.getChildItemCode())
                    .dueDate(childDueDate)
                    .requiredQuantity(requiredQuantity)
                    .allocatedQuantity(BigDecimal.ZERO)
                    .shortageQuantity(requiredQuantity)
                    .locationCode(order.getLocationCode())
                    .build();
            requirementRepository.save(requirement);

            requirements.add(requirement);
        }

        return requirements;
    }

    /**
     * 在庫から引当を行う
     */
    @Transactional
    public Allocation allocateFromInventory(Integer requirementId, int inventoryQuantity) {
        var requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("Requirement not found: " + requirementId));

        var requiredQuantity = requirement.getRequiredQuantity();
        var allocatedQuantity = requiredQuantity.min(new BigDecimal(inventoryQuantity));
        var shortageQuantity = requiredQuantity.subtract(allocatedQuantity);

        var allocation = Allocation.builder()
                .requirementId(requirementId)
                .allocationType(AllocationType.INVENTORY)
                .allocationDate(LocalDate.now())
                .allocatedQuantity(allocatedQuantity)
                .locationCode(requirement.getLocationCode())
                .build();
        allocationRepository.save(allocation);

        requirementRepository.updateAllocation(requirementId, allocatedQuantity, shortageQuantity);

        return allocation;
    }

    /**
     * ロットサイズを考慮したオーダ数量を計算する
     */
    public BigDecimal calculateOrderQuantity(
            BigDecimal requiredQuantity,
            BigDecimal minimumLotSize,
            BigDecimal incrementLotSize,
            BigDecimal maximumLotSize
    ) {
        if (minimumLotSize == null) minimumLotSize = BigDecimal.ONE;
        if (incrementLotSize == null) incrementLotSize = BigDecimal.ONE;

        // 最小ロットに満たない場合
        if (requiredQuantity.compareTo(minimumLotSize) <= 0) {
            if (maximumLotSize != null && minimumLotSize.compareTo(maximumLotSize) > 0) {
                return maximumLotSize;
            }
            return minimumLotSize;
        }

        // 刻みロットで切り上げ
        BigDecimal difference = requiredQuantity.subtract(minimumLotSize);
        BigDecimal lots = difference.divide(incrementLotSize, 0, RoundingMode.CEILING);
        BigDecimal orderQuantity = minimumLotSize.add(lots.multiply(incrementLotSize));

        // 最大ロットを超える場合は制限
        if (maximumLotSize != null && orderQuantity.compareTo(maximumLotSize) > 0) {
            return maximumLotSize;
        }

        return orderQuantity;
    }

    /**
     * リードタイムから着手日を計算する
     */
    public LocalDate calculateStartDate(LocalDate dueDate, int leadTime, int safetyLeadTime) {
        return dueDate.minusDays(leadTime + safetyLeadTime);
    }

    /**
     * 不足分に対して新規オーダを生成する
     */
    @Transactional
    public Order createShortageOrder(
            String itemCode,
            BigDecimal shortageQuantity,
            LocalDate dueDate,
            String locationCode,
            OrderType orderType
    ) {
        var item = itemRepository.findByItemCodeAndDate(itemCode, LocalDate.now())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemCode));

        var orderQuantity = calculateOrderQuantity(
                shortageQuantity,
                item.getMinLotSize(),
                item.getLotIncrement(),
                item.getMaxLotSize()
        );

        int leadTime = item.getLeadTime() != null ? item.getLeadTime() : 0;
        int safetyLeadTime = item.getSafetyLeadTime() != null ? item.getSafetyLeadTime() : 0;
        var startDate = calculateStartDate(dueDate, leadTime, safetyLeadTime);

        var order = Order.builder()
                .orderNumber((orderType == OrderType.PURCHASE ? "PO" : "MO") + "-" + System.currentTimeMillis())
                .orderType(orderType)
                .itemCode(itemCode)
                .startDate(startDate)
                .dueDate(dueDate)
                .planQuantity(orderQuantity)
                .locationCode(locationCode)
                .status(PlanStatus.DRAFT)
                .build();
        orderRepository.save(order);

        return order;
    }

    /**
     * MRP の完全実行
     */
    @Transactional
    public void executeMrp(Integer mpsId, MpsRepository mpsRepository) {
        var mps = mpsRepository.findById(mpsId)
                .orElseThrow(() -> new IllegalArgumentException("MPS not found: " + mpsId));

        var orders = orderRepository.findByMpsId(mpsId);

        Order productOrder;
        if (orders.isEmpty()) {
            productOrder = Order.builder()
                    .orderNumber("MO-" + System.currentTimeMillis())
                    .orderType(OrderType.MANUFACTURING)
                    .itemCode(mps.getItemCode())
                    .startDate(calculateStartDate(mps.getDueDate(), 5, 0))
                    .dueDate(mps.getDueDate())
                    .planQuantity(mps.getPlanQuantity())
                    .locationCode(mps.getLocationCode() != null ? mps.getLocationCode() : "WH-001")
                    .status(PlanStatus.CONFIRMED)
                    .mpsId(mpsId)
                    .build();
            orderRepository.save(productOrder);
        } else {
            productOrder = orders.get(0);
        }

        recursiveMrpExplosion(productOrder.getId());
    }

    /**
     * 再帰的な所要量展開
     */
    private void recursiveMrpExplosion(Integer orderId) {
        var requirements = explodeRequirements(orderId);

        for (var requirement : requirements) {
            var itemOpt = itemRepository.findByItemCodeAndDate(requirement.getItemCode(), LocalDate.now());
            if (itemOpt.isEmpty()) continue;
            var item = itemOpt.get();

            var orderType = (item.getItemCategory() == ItemCategory.MATERIAL ||
                    item.getItemCategory() == ItemCategory.RAW_MATERIAL)
                    ? OrderType.PURCHASE : OrderType.MANUFACTURING;

            var newOrder = createShortageOrder(
                    requirement.getItemCode(),
                    requirement.getShortageQuantity(),
                    requirement.getDueDate(),
                    requirement.getLocationCode(),
                    orderType
            );

            orderRepository.updateParentOrderId(newOrder.getId(), orderId);

            recursiveMrpExplosion(newOrder.getId());
        }
    }

    /**
     * 歩留率と不良率を考慮した所要量計算
     */
    public BigDecimal calculateRequiredQuantity(
            BigDecimal parentQuantity,
            BigDecimal baseQuantity,
            BigDecimal requiredQuantity,
            BigDecimal defectRate,
            BigDecimal yieldRate
    ) {
        // 基本所要量
        BigDecimal basicQuantity = parentQuantity.multiply(requiredQuantity)
                .divide(baseQuantity, 4, RoundingMode.HALF_UP);

        // 不良率を考慮
        BigDecimal afterDefect = basicQuantity.multiply(
                BigDecimal.ONE.add(defectRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
        );

        // 歩留率を考慮
        BigDecimal afterYield = afterDefect.divide(
                yieldRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP),
                0,
                RoundingMode.CEILING
        );

        return afterYield;
    }
}
```

</details>

### MRP 処理の ER 図

```plantuml
@startuml

title MRP関連エンティティのER図

entity 基準生産計画 {
  MPS番号 <<PK>>
  --
  計画日
  品目コード <<FK>>
  計画数量
  納期
  ステータス
  場所コード
}

entity オーダ情報 {
  オーダNO <<PK>>
  --
  オーダ種別
  品目コード <<FK>>
  着手予定日
  納期
  計画数量
  場所コード
  ステータス
  MPS_ID <<FK>>
  親オーダID <<FK>>
}

entity 所要情報 {
  所要NO <<PK>>
  --
  オーダNO <<FK>>
  品目コード <<FK>>
  納期
  必要数量
  引当済数量
  不足数量
  場所コード
}

entity 引当情報 {
  引当ID <<PK>>
  --
  所要NO <<FK>>
  引当区分
  オーダNO <<FK>>
  引当日
  引当数量
  場所コード
}

entity 品目マスタ {
  品目コード <<PK>>
  適用開始日 <<PK>>
  --
  品名
  品目区分
  リードタイム
  安全リードタイム
  最小ロット数
  刻みロット数
  最大ロット数
}

entity BOM {
  親品目コード <<PK>> <<FK>>
  子品目コード <<PK>> <<FK>>
  適用開始日 <<PK>>
  --
  基準数量
  必要数量
  不良率
}

基準生産計画 ||--o{ オーダ情報 : 展開
オーダ情報 ||--o{ 所要情報 : 生成
オーダ情報 ||--o{ オーダ情報 : 親子関係
所要情報 ||--o{ 引当情報 : 引当
オーダ情報 }o--|| 品目マスタ
所要情報 }o--|| 品目マスタ
BOM }o--|| 品目マスタ : 親品目
BOM }o--|| 品目マスタ : 子品目

@enduml
```

---

## 24.3 リレーションと楽観ロックの設計

### MyBatis ネストした ResultMap によるリレーション設定

生産計画データは、MPS → オーダ → 所要 → 引当 という階層構造と、オーダ間の親子関係（自己参照）を持ちます。MyBatis でこれらの複雑なリレーションを効率的に取得するための設定を実装します。

#### ネストした ResultMap の定義

<details>
<summary>OrderMapper.xml（リレーション設定）</summary>

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<!-- src/main/resources/mapper/OrderMapper.xml -->
<mapper namespace="com.example.pms.infrastructure.out.persistence.mapper.OrderMapper">

    <!-- オーダ情報 ResultMap（親オーダ・MPS・子オーダ込み） -->
    <resultMap id="orderWithRelationsResultMap" type="com.example.pms.domain.model.plan.Order">
        <id property="id" column="o_ID"/>
        <result property="orderNumber" column="o_オーダNO"/>
        <result property="orderType" column="o_オーダ種別"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler"/>
        <result property="itemCode" column="o_品目コード"/>
        <result property="startDate" column="o_着手予定日"/>
        <result property="dueDate" column="o_納期"/>
        <result property="expirationDate" column="o_有効期限"/>
        <result property="planQuantity" column="o_計画数量"/>
        <result property="locationCode" column="o_場所コード"/>
        <result property="status" column="o_ステータス"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler"/>
        <result property="mpsId" column="o_MPS_ID"/>
        <result property="parentOrderId" column="o_親オーダID"/>
        <result property="version" column="o_バージョン"/>
        <result property="createdAt" column="o_作成日時"/>
        <result property="createdBy" column="o_作成者"/>
        <result property="updatedAt" column="o_更新日時"/>
        <result property="updatedBy" column="o_更新者"/>
        <!-- MPS との N:1 関連 -->
        <association property="mps" javaType="com.example.pms.domain.model.plan.MasterProductionSchedule">
            <id property="id" column="m_ID"/>
            <result property="mpsNumber" column="m_MPS番号"/>
            <result property="planDate" column="m_計画日"/>
            <result property="itemCode" column="m_品目コード"/>
            <result property="planQuantity" column="m_計画数量"/>
            <result property="dueDate" column="m_納期"/>
            <result property="status" column="m_ステータス"
                    typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler"/>
        </association>
        <!-- 親オーダとの自己参照 N:1 関連 -->
        <association property="parentOrder" javaType="com.example.pms.domain.model.plan.Order">
            <id property="id" column="p_ID"/>
            <result property="orderNumber" column="p_オーダNO"/>
            <result property="orderType" column="p_オーダ種別"
                    typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler"/>
            <result property="itemCode" column="p_品目コード"/>
            <result property="planQuantity" column="p_計画数量"/>
        </association>
        <!-- 子オーダとの 1:N 関連 -->
        <collection property="childOrders" ofType="com.example.pms.domain.model.plan.Order"
                    resultMap="childOrderResultMap"/>
        <!-- 所要情報との 1:N 関連 -->
        <collection property="requirements" ofType="com.example.pms.domain.model.plan.Requirement"
                    resultMap="requirementNestedResultMap"/>
    </resultMap>

    <!-- 子オーダの ResultMap -->
    <resultMap id="childOrderResultMap" type="com.example.pms.domain.model.plan.Order">
        <id property="id" column="c_ID"/>
        <result property="orderNumber" column="c_オーダNO"/>
        <result property="orderType" column="c_オーダ種別"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler"/>
        <result property="itemCode" column="c_品目コード"/>
        <result property="startDate" column="c_着手予定日"/>
        <result property="dueDate" column="c_納期"/>
        <result property="planQuantity" column="c_計画数量"/>
        <result property="status" column="c_ステータス"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler"/>
    </resultMap>

    <!-- 所要情報のネスト ResultMap -->
    <resultMap id="requirementNestedResultMap" type="com.example.pms.domain.model.plan.Requirement">
        <id property="id" column="r_ID"/>
        <result property="requirementNumber" column="r_所要NO"/>
        <result property="orderId" column="r_オーダID"/>
        <result property="itemCode" column="r_品目コード"/>
        <result property="dueDate" column="r_納期"/>
        <result property="requiredQuantity" column="r_必要数量"/>
        <result property="allocatedQuantity" column="r_引当済数量"/>
        <result property="shortageQuantity" column="r_不足数量"/>
        <result property="locationCode" column="r_場所コード"/>
        <result property="version" column="r_バージョン"/>
        <!-- 引当情報との 1:N 関連 -->
        <collection property="allocations" ofType="com.example.pms.domain.model.plan.Allocation"
                    resultMap="allocationNestedResultMap"/>
    </resultMap>

    <!-- 引当情報のネスト ResultMap -->
    <resultMap id="allocationNestedResultMap" type="com.example.pms.domain.model.plan.Allocation">
        <id property="id" column="a_ID"/>
        <result property="requirementId" column="a_所要ID"/>
        <result property="allocationType" column="a_引当区分"
                typeHandler="com.example.pms.infrastructure.out.persistence.typehandler.AllocationTypeTypeHandler"/>
        <result property="orderId" column="a_オーダID"/>
        <result property="allocationDate" column="a_引当日"/>
        <result property="allocatedQuantity" column="a_引当数量"/>
        <result property="locationCode" column="a_場所コード"/>
    </resultMap>

    <!-- JOIN による一括取得クエリ -->
    <select id="findWithRelationsByOrderNumber" resultMap="orderWithRelationsResultMap">
        SELECT
            -- オーダ情報
            o."ID" AS o_ID,
            o."オーダNO" AS o_オーダNO,
            o."オーダ種別" AS o_オーダ種別,
            o."品目コード" AS o_品目コード,
            o."着手予定日" AS o_着手予定日,
            o."納期" AS o_納期,
            o."有効期限" AS o_有効期限,
            o."計画数量" AS o_計画数量,
            o."場所コード" AS o_場所コード,
            o."ステータス" AS o_ステータス,
            o."MPS_ID" AS o_MPS_ID,
            o."親オーダID" AS o_親オーダID,
            o."バージョン" AS o_バージョン,
            o."作成日時" AS o_作成日時,
            o."作成者" AS o_作成者,
            o."更新日時" AS o_更新日時,
            o."更新者" AS o_更新者,
            -- MPS情報
            m."ID" AS m_ID,
            m."MPS番号" AS m_MPS番号,
            m."計画日" AS m_計画日,
            m."品目コード" AS m_品目コード,
            m."計画数量" AS m_計画数量,
            m."納期" AS m_納期,
            m."ステータス" AS m_ステータス,
            -- 親オーダ情報
            p."ID" AS p_ID,
            p."オーダNO" AS p_オーダNO,
            p."オーダ種別" AS p_オーダ種別,
            p."品目コード" AS p_品目コード,
            p."計画数量" AS p_計画数量,
            -- 子オーダ情報
            c."ID" AS c_ID,
            c."オーダNO" AS c_オーダNO,
            c."オーダ種別" AS c_オーダ種別,
            c."品目コード" AS c_品目コード,
            c."着手予定日" AS c_着手予定日,
            c."納期" AS c_納期,
            c."計画数量" AS c_計画数量,
            c."ステータス" AS c_ステータス,
            -- 所要情報
            r."ID" AS r_ID,
            r."所要NO" AS r_所要NO,
            r."オーダID" AS r_オーダID,
            r."品目コード" AS r_品目コード,
            r."納期" AS r_納期,
            r."必要数量" AS r_必要数量,
            r."引当済数量" AS r_引当済数量,
            r."不足数量" AS r_不足数量,
            r."場所コード" AS r_場所コード,
            r."バージョン" AS r_バージョン,
            -- 引当情報
            a."ID" AS a_ID,
            a."所要ID" AS a_所要ID,
            a."引当区分" AS a_引当区分,
            a."オーダID" AS a_オーダID,
            a."引当日" AS a_引当日,
            a."引当数量" AS a_引当数量,
            a."場所コード" AS a_場所コード
        FROM "オーダ情報" o
        LEFT JOIN "基準生産計画" m ON o."MPS_ID" = m."ID"
        LEFT JOIN "オーダ情報" p ON o."親オーダID" = p."ID"
        LEFT JOIN "オーダ情報" c ON o."ID" = c."親オーダID"
        LEFT JOIN "所要情報" r ON o."ID" = r."オーダID"
        LEFT JOIN "引当情報" a ON r."ID" = a."所要ID"
        WHERE o."オーダNO" = #{orderNumber}
        ORDER BY c."ID", r."ID", a."ID"
    </select>

</mapper>
```

</details>

#### リレーション設定のポイント

| 設定項目 | 説明 |
|---------|------|
| `<association>` | N:1 関連のマッピング（MPS、親オーダ） |
| `<collection>` | 1:N 関連のマッピング（子オーダ、所要、引当） |
| 自己参照 | オーダの親子関係を同一テーブルで管理 |
| エイリアス | `o_`（オーダ）、`m_`（MPS）、`p_`（親）、`c_`（子）、`r_`（所要）、`a_`（引当） |
| 複数 LEFT JOIN | 階層構造を一度のクエリで取得 |

### 楽観ロックの実装

MRP 処理や計画変更時に複数ユーザーが同時に編集する可能性があるため、楽観ロックを実装します。

#### Flyway マイグレーション: バージョンカラム追加

<details>
<summary>V009__add_planning_version_columns.sql</summary>

```sql
-- src/main/resources/db/migration/V009__add_planning_version_columns.sql

-- 基準生産計画テーブルにバージョンカラムを追加
ALTER TABLE "基準生産計画" ADD COLUMN "バージョン" INTEGER DEFAULT 1 NOT NULL;

-- オーダ情報テーブルにバージョンカラムを追加
ALTER TABLE "オーダ情報" ADD COLUMN "バージョン" INTEGER DEFAULT 1 NOT NULL;

-- 所要情報テーブルにバージョンカラムを追加
ALTER TABLE "所要情報" ADD COLUMN "バージョン" INTEGER DEFAULT 1 NOT NULL;

-- 引当情報テーブルにバージョンカラムを追加
ALTER TABLE "引当情報" ADD COLUMN "バージョン" INTEGER DEFAULT 1 NOT NULL;

-- コメント追加
COMMENT ON COLUMN "基準生産計画"."バージョン" IS '楽観ロック用バージョン番号';
COMMENT ON COLUMN "オーダ情報"."バージョン" IS '楽観ロック用バージョン番号';
COMMENT ON COLUMN "所要情報"."バージョン" IS '楽観ロック用バージョン番号';
COMMENT ON COLUMN "引当情報"."バージョン" IS '楽観ロック用バージョン番号';
```

</details>

#### エンティティへのバージョンフィールド追加

<details>
<summary>Order.java（バージョンフィールド追加）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/Order.java
package com.example.pms.domain.model.plan;

import com.example.pms.domain.model.item.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Integer id;
    private String orderNumber;
    private OrderType orderType;
    private String itemCode;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate expirationDate;
    private BigDecimal planQuantity;
    private String locationCode;
    private PlanStatus status;
    private Integer mpsId;
    private Integer parentOrderId;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // 楽観ロック用バージョン
    @Builder.Default
    private Integer version = 1;

    // リレーション
    private Item item;
    private MasterProductionSchedule mps;
    private Order parentOrder;
    private List<Order> childOrders;
    private List<Requirement> requirements;
}
```

</details>

<details>
<summary>Requirement.java（バージョンフィールド追加）</summary>

```java
// src/main/java/com/example/pms/domain/model/plan/Requirement.java
package com.example.pms.domain.model.plan;

import com.example.pms.domain.model.item.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Requirement {
    private Integer id;
    private String requirementNumber;
    private Integer orderId;
    private String itemCode;
    private LocalDate dueDate;
    private BigDecimal requiredQuantity;
    private BigDecimal allocatedQuantity;
    private BigDecimal shortageQuantity;
    private String locationCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 楽観ロック用バージョン
    @Builder.Default
    private Integer version = 1;

    // リレーション
    private Order order;
    private Item item;
    private List<Allocation> allocations;
}
```

</details>

#### MyBatis Mapper: 楽観ロック対応の更新

<details>
<summary>OrderMapper.xml（楽観ロック対応 UPDATE）</summary>

```xml
<!-- 楽観ロック対応の更新（バージョンチェック付き） -->
<update id="updateWithOptimisticLock" parameterType="com.example.pms.domain.model.plan.Order">
    UPDATE "オーダ情報"
    SET
        "オーダ種別" = #{orderType, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.OrderTypeTypeHandler}::オーダ種別,
        "品目コード" = #{itemCode},
        "着手予定日" = #{startDate},
        "納期" = #{dueDate},
        "有効期限" = #{expirationDate},
        "計画数量" = #{planQuantity},
        "場所コード" = #{locationCode},
        "ステータス" = #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
        "MPS_ID" = #{mpsId},
        "親オーダID" = #{parentOrderId},
        "更新日時" = CURRENT_TIMESTAMP,
        "更新者" = #{updatedBy},
        "バージョン" = "バージョン" + 1
    WHERE "ID" = #{id}
    AND "バージョン" = #{version}
</update>

<!-- ステータス更新（楽観ロック対応） -->
<update id="updateStatusWithOptimisticLock">
    UPDATE "オーダ情報"
    SET
        "ステータス" = #{status, typeHandler=com.example.pms.infrastructure.out.persistence.typehandler.PlanStatusTypeHandler}::計画ステータス,
        "更新日時" = CURRENT_TIMESTAMP,
        "バージョン" = "バージョン" + 1
    WHERE "ID" = #{id}
    AND "バージョン" = #{version}
</update>

<!-- 現在のバージョン取得 -->
<select id="findVersionById" resultType="java.lang.Integer">
    SELECT "バージョン" FROM "オーダ情報" WHERE "ID" = #{id}
</select>
```

</details>

<details>
<summary>RequirementMapper.xml（楽観ロック対応 UPDATE）</summary>

```xml
<!-- 引当更新（楽観ロック対応） -->
<update id="updateAllocationWithOptimisticLock">
    UPDATE "所要情報"
    SET
        "引当済数量" = #{allocatedQuantity},
        "不足数量" = #{shortageQuantity},
        "更新日時" = CURRENT_TIMESTAMP,
        "バージョン" = "バージョン" + 1
    WHERE "ID" = #{id}
    AND "バージョン" = #{version}
</update>

<!-- 現在のバージョン取得 -->
<select id="findVersionById" resultType="java.lang.Integer">
    SELECT "バージョン" FROM "所要情報" WHERE "ID" = #{id}
</select>
```

</details>

#### Repository 実装: 楽観ロック対応

<details>
<summary>OrderRepositoryImpl.java（楽観ロック対応）</summary>

```java
// src/main/java/com/example/pms/infrastructure/out/persistence/repository/OrderRepositoryImpl.java
package com.example.pms.infrastructure.out.persistence.repository;

import com.example.pms.application.port.out.OrderRepository;
import com.example.pms.domain.exception.OptimisticLockException;
import com.example.pms.domain.model.plan.Order;
import com.example.pms.domain.model.plan.PlanStatus;
import com.example.pms.infrastructure.out.persistence.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper mapper;

    @Override
    @Transactional
    public void update(Order order) {
        int updatedCount = mapper.updateWithOptimisticLock(order);

        if (updatedCount == 0) {
            // バージョン不一致または削除済み
            Integer currentVersion = mapper.findVersionById(order.getId());
            if (currentVersion == null) {
                throw new OptimisticLockException("オーダ", order.getId());
            } else {
                throw new OptimisticLockException("オーダ", order.getId(),
                        order.getVersion(), currentVersion);
            }
        }
    }

    @Override
    @Transactional
    public void updateStatus(Integer id, PlanStatus status, Integer version) {
        int updatedCount = mapper.updateStatusWithOptimisticLock(id, status, version);

        if (updatedCount == 0) {
            Integer currentVersion = mapper.findVersionById(id);
            if (currentVersion == null) {
                throw new OptimisticLockException("オーダ", id);
            } else {
                throw new OptimisticLockException("オーダ", id, version, currentVersion);
            }
        }
    }

    @Override
    public Optional<Order> findWithRelationsByOrderNumber(String orderNumber) {
        return Optional.ofNullable(mapper.findWithRelationsByOrderNumber(orderNumber));
    }

    // その他のメソッド...
}
```

</details>

#### TDD: 楽観ロックのテスト

<details>
<summary>OrderRepositoryOptimisticLockTest.java</summary>

```java
// src/test/java/com/example/pms/infrastructure/out/persistence/repository/OrderRepositoryOptimisticLockTest.java
package com.example.pms.infrastructure.out.persistence.repository;

import com.example.pms.application.port.out.OrderRepository;
import com.example.pms.domain.exception.OptimisticLockException;
import com.example.pms.domain.model.plan.Order;
import com.example.pms.domain.model.plan.OrderType;
import com.example.pms.domain.model.plan.PlanStatus;
import com.example.pms.testsetup.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("オーダリポジトリ - 楽観ロック")
class OrderRepositoryOptimisticLockTest extends BaseIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Nested
    @DisplayName("楽観ロック")
    class OptimisticLocking {

        @Test
        @DisplayName("同じバージョンで更新できる")
        void canUpdateWithSameVersion() {
            // Arrange
            var order = Order.builder()
                    .orderNumber("MO-2025-0001")
                    .orderType(OrderType.MANUFACTURING)
                    .itemCode("PROD-001")
                    .startDate(LocalDate.of(2025, 1, 15))
                    .dueDate(LocalDate.of(2025, 1, 20))
                    .planQuantity(new BigDecimal("100"))
                    .locationCode("WH-001")
                    .status(PlanStatus.DRAFT)
                    .build();
            orderRepository.save(order);

            // Act
            var fetched = orderRepository.findByOrderNumber("MO-2025-0001").get();
            fetched.setPlanQuantity(new BigDecimal("150"));
            orderRepository.update(fetched);

            // Assert
            var updated = orderRepository.findByOrderNumber("MO-2025-0001").get();
            assertThat(updated.getPlanQuantity()).isEqualByComparingTo(new BigDecimal("150"));
            assertThat(updated.getVersion()).isEqualTo(2); // バージョンがインクリメント
        }

        @Test
        @DisplayName("異なるバージョンで更新すると楽観ロック例外が発生する")
        void throwsExceptionWhenVersionMismatch() {
            // Arrange
            var order = Order.builder()
                    .orderNumber("MO-2025-0002")
                    .orderType(OrderType.MANUFACTURING)
                    .itemCode("PROD-001")
                    .startDate(LocalDate.of(2025, 1, 15))
                    .dueDate(LocalDate.of(2025, 1, 20))
                    .planQuantity(new BigDecimal("100"))
                    .locationCode("WH-001")
                    .status(PlanStatus.DRAFT)
                    .build();
            orderRepository.save(order);

            // ユーザーAが取得
            var orderA = orderRepository.findByOrderNumber("MO-2025-0002").get();
            // ユーザーBが取得
            var orderB = orderRepository.findByOrderNumber("MO-2025-0002").get();

            // ユーザーAが更新（成功）
            orderA.setPlanQuantity(new BigDecimal("150"));
            orderRepository.update(orderA);

            // Act & Assert: ユーザーBが古いバージョンで更新（失敗）
            orderB.setPlanQuantity(new BigDecimal("200"));
            assertThatThrownBy(() -> orderRepository.update(orderB))
                    .isInstanceOf(OptimisticLockException.class)
                    .hasMessageContaining("他のユーザーによって更新されています");
        }

        @Test
        @DisplayName("ステータス更新も楽観ロックが適用される")
        void statusUpdateWithOptimisticLock() {
            // Arrange
            var order = Order.builder()
                    .orderNumber("MO-2025-0003")
                    .orderType(OrderType.MANUFACTURING)
                    .itemCode("PROD-001")
                    .startDate(LocalDate.of(2025, 1, 15))
                    .dueDate(LocalDate.of(2025, 1, 20))
                    .planQuantity(new BigDecimal("100"))
                    .locationCode("WH-001")
                    .status(PlanStatus.DRAFT)
                    .build();
            orderRepository.save(order);

            // ユーザーAとBが同時に取得
            var orderA = orderRepository.findByOrderNumber("MO-2025-0003").get();
            var orderB = orderRepository.findByOrderNumber("MO-2025-0003").get();

            // ユーザーAがステータス確定（成功）
            orderRepository.updateStatus(orderA.getId(), PlanStatus.CONFIRMED, orderA.getVersion());

            // Act & Assert: ユーザーBが古いバージョンでステータス更新（失敗）
            assertThatThrownBy(() ->
                    orderRepository.updateStatus(orderB.getId(), PlanStatus.CANCELLED, orderB.getVersion()))
                    .isInstanceOf(OptimisticLockException.class);
        }
    }
}
```

</details>

### MRP 処理における楽観ロックの考慮

MRP 処理は複数のオーダや所要を一括で更新するため、楽観ロックの扱いに注意が必要です。

```plantuml
@startuml

title MRP処理と楽観ロック

participant "MRPサービス" as MRP
participant "オーダリポジトリ" as OrderRepo
participant "所要リポジトリ" as ReqRepo
database "DB" as DB

MRP -> OrderRepo: findByMpsId(mpsId)
OrderRepo -> DB: SELECT ... WHERE MPS_ID = ?
DB --> OrderRepo: オーダ一覧（バージョン込み）
OrderRepo --> MRP: List<Order>

loop 各オーダに対して
    MRP -> ReqRepo: explodeRequirements(orderId)
    ReqRepo -> DB: INSERT INTO 所要情報
    DB --> ReqRepo: OK

    MRP -> OrderRepo: updateStatus(id, EXPANDED, version)
    OrderRepo -> DB: UPDATE ... WHERE ID = ? AND バージョン = ?
    alt バージョン一致
        DB --> OrderRepo: 更新成功（1件）
        OrderRepo --> MRP: OK
    else バージョン不一致
        DB --> OrderRepo: 更新失敗（0件）
        OrderRepo --> MRP: OptimisticLockException
        MRP -> MRP: ロールバック・リトライ判定
    end
end

@enduml
```

#### 一括処理時の楽観ロック戦略

<details>
<summary>MrpService.java（楽観ロック対応の改善版）</summary>

```java
/**
 * MRP の完全実行（楽観ロック対応）
 */
@Transactional
public MrpExecutionResult executeMrpWithOptimisticLock(Integer mpsId, MpsRepository mpsRepository) {
    var mps = mpsRepository.findById(mpsId)
            .orElseThrow(() -> new IllegalArgumentException("MPS not found: " + mpsId));

    List<String> warnings = new ArrayList<>();
    int processedCount = 0;
    int skippedCount = 0;

    var orders = orderRepository.findByMpsId(mpsId);

    for (var order : orders) {
        try {
            // 個別オーダの処理
            var requirements = explodeRequirements(order.getId());

            // ステータス更新（楽観ロック付き）
            orderRepository.updateStatus(
                    order.getId(),
                    PlanStatus.EXPANDED,
                    order.getVersion()
            );

            processedCount++;

            // 子オーダの再帰処理
            for (var requirement : requirements) {
                processChildOrder(requirement, warnings);
            }

        } catch (OptimisticLockException e) {
            // 楽観ロック競合時はスキップして続行
            warnings.add(String.format(
                    "オーダ %s は他のユーザーによって更新されたためスキップしました",
                    order.getOrderNumber()
            ));
            skippedCount++;
        }
    }

    return MrpExecutionResult.builder()
            .processedCount(processedCount)
            .skippedCount(skippedCount)
            .warnings(warnings)
            .build();
}

@Data
@Builder
public static class MrpExecutionResult {
    private int processedCount;
    private int skippedCount;
    private List<String> warnings;
}
```

</details>

#### 楽観ロックのベストプラクティス（生産計画向け）

| ポイント | 説明 |
|---------|------|
| **一括処理時の例外処理** | 個別レコードの競合でバッチ全体を止めない |
| **リトライ戦略** | 競合時は最新データを再取得して再試行 |
| **警告ログ** | スキップしたレコードを明示的に記録 |
| **ステータス遷移** | 草案→確定→展開済の順序を厳守 |
| **親子整合性** | 親オーダ更新時に子オーダも整合性チェック |

---

## 24.4 まとめ

### 学んだこと

1. **計画系と実績系の分離**
   - 計画データは再計算可能、実績データは不可逆
   - 両者を明確に分離することで保守性が向上

2. **計画の階層構造**
   - 長期計画から短期計画へ段階的に詳細化
   - MPS が MRP の入力となる

3. **MRP の基本処理**
   - BOM 展開による所要量計算
   - 在庫・発注残・製造残からの引当
   - ロットサイズとリードタイムの考慮
   - 不足分に対する新規オーダ生成

4. **日本語DB ↔ 英語Javaのマッピング**
   - MyBatis の resultMap を使用して日本語カラム名を英語プロパティにマッピング
   - TypeHandler を使用して日本語 ENUM 値を英語 Enum にマッピング

### 設計のポイント

| ポイント | 説明 |
|---------|------|
| **オーダの親子関係** | 製品オーダから部品オーダへの階層構造を管理 |
| **所要-引当の関係** | 多対多の関係を中間テーブルで管理 |
| **ロットサイズ計算** | 最小・刻み・最大の3パラメータで柔軟に対応 |
| **リードタイム逆算** | 納期から着手日を自動計算 |

### テーブル定義

#### 基準生産計画テーブル

| カラム名 | データ型 | NULL | 説明 |
|---------|---------|------|------|
| ID | SERIAL | NO | 主キー |
| MPS番号 | VARCHAR(20) | NO | MPS番号（ユニーク） |
| 計画日 | DATE | NO | 計画日 |
| 品目コード | VARCHAR(20) | NO | 品目コード |
| 計画数量 | DECIMAL(15,2) | NO | 計画数量 |
| 納期 | DATE | NO | 納期 |
| ステータス | 計画ステータス | NO | ステータス |
| 場所コード | VARCHAR(20) | YES | 場所コード |
| 備考 | TEXT | YES | 備考 |

#### オーダ情報テーブル

| カラム名 | データ型 | NULL | 説明 |
|---------|---------|------|------|
| ID | SERIAL | NO | 主キー |
| オーダNO | VARCHAR(20) | NO | オーダ番号（ユニーク） |
| オーダ種別 | オーダ種別 | NO | オーダ種別 |
| 品目コード | VARCHAR(20) | NO | 品目コード |
| 着手予定日 | DATE | NO | 着手予定日 |
| 納期 | DATE | NO | 納期 |
| 有効期限 | DATE | YES | 有効期限 |
| 計画数量 | DECIMAL(15,2) | NO | 計画数量 |
| 場所コード | VARCHAR(20) | NO | 場所コード |
| ステータス | 計画ステータス | NO | ステータス |
| MPS_ID | INTEGER | YES | MPS ID（外部キー） |
| 親オーダID | INTEGER | YES | 親オーダID（自己参照） |

#### 所要情報テーブル

| カラム名 | データ型 | NULL | 説明 |
|---------|---------|------|------|
| ID | SERIAL | NO | 主キー |
| 所要NO | VARCHAR(20) | NO | 所要番号（ユニーク） |
| オーダID | INTEGER | NO | オーダID（外部キー） |
| 品目コード | VARCHAR(20) | NO | 品目コード |
| 納期 | DATE | NO | 納期 |
| 必要数量 | DECIMAL(15,2) | NO | 必要数量 |
| 引当済数量 | DECIMAL(15,2) | NO | 引当済数量 |
| 不足数量 | DECIMAL(15,2) | NO | 不足数量 |
| 場所コード | VARCHAR(20) | NO | 場所コード |

#### 引当情報テーブル

| カラム名 | データ型 | NULL | 説明 |
|---------|---------|------|------|
| ID | SERIAL | NO | 主キー |
| 所要ID | INTEGER | NO | 所要ID（外部キー） |
| 引当区分 | 引当区分 | NO | 引当区分 |
| オーダID | INTEGER | YES | オーダID（外部キー） |
| 引当日 | DATE | NO | 引当日 |
| 引当数量 | DECIMAL(15,2) | NO | 引当数量 |
| 場所コード | VARCHAR(20) | NO | 場所コード |

---

[← 第23章：マスタ情報の設計](chapter23.md) | [第25章：購買管理の設計 →](chapter25.md)
