---
type: Article
title: "第 9 章: SOLID 原則とモジュール設計"
description: "前章までに、FizzBuzz をオブジェクト指向設計で洗練させてきました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: SOLID 原則とモジュール設計

## 9.1 はじめに

前章までに、FizzBuzz をオブジェクト指向設計で洗練させてきました。この章では、**SOLID 原則** の観点で設計を検証し、**モジュール分割** を行ってパッケージ構造を整理します。

## 9.2 SOLID 原則の確認

### 単一責任の原則（SRP）

| クラス | 責務 | 変更理由 |
|--------|------|---------|
| `FizzBuzzType01/02/03` | タイプ別の変換ルール | ルール変更時 |
| `FizzBuzzValue` | 値の保持と等価性 | 値表現の変更時 |
| `FizzBuzzList` | コレクション操作 | コレクション仕様変更時 |
| `FizzBuzzValueCommand` | 単一値の実行 | 実行方法の変更時 |
| `FizzBuzzListCommand` | リスト生成の実行 | 実行方法の変更時 |

各クラスが 1 つの責務のみを持っていることが確認できます。

### 開放閉鎖の原則（OCP）

新しいタイプ（たとえばタイプ 4）を追加する場合:

1. `FizzBuzzType04` クラスを新規作成
2. `FizzBuzzType.create()` に 1 行追加

既存のクラスを修正する必要はありません。

### 依存性逆転の原則（DIP）

```
FizzBuzzValueCommand → FizzBuzzType (抽象) ← FizzBuzzType01/02/03
FizzBuzzListCommand  → FizzBuzzType (抽象) ← FizzBuzzType01/02/03
```

上位モジュール（Command）は抽象（FizzBuzzType）に依存し、具象クラスには直接依存しません。

## 9.3 パッケージ設計

責務に基づいてモジュールを分割します。

### ディレクトリ構成

```
apps/python/
├── lib/
│   ├── __init__.py
│   ├── domain/
│   │   ├── __init__.py
│   │   ├── model/
│   │   │   ├── __init__.py
│   │   │   ├── fizz_buzz_value.py
│   │   │   └── fizz_buzz_list.py
│   │   └── type/
│   │       ├── __init__.py
│   │       ├── fizz_buzz_type.py
│   │       ├── fizz_buzz_type_01.py
│   │       ├── fizz_buzz_type_02.py
│   │       ├── fizz_buzz_type_03.py
│   │       └── fizz_buzz_type_not_defined.py
│   └── application/
│       ├── __init__.py
│       ├── fizz_buzz_command.py
│       ├── fizz_buzz_value_command.py
│       └── fizz_buzz_list_command.py
├── test/
│   ├── __init__.py
│   ├── domain/
│   │   ├── __init__.py
│   │   ├── model/
│   │   │   ├── __init__.py
│   │   │   ├── test_fizz_buzz_value.py
│   │   │   └── test_fizz_buzz_list.py
│   │   └── type/
│   │       ├── __init__.py
│   │       └── test_fizz_buzz_type.py
│   └── application/
│       ├── __init__.py
│       └── test_fizz_buzz_command.py
└── main.py
```

### パッケージ依存関係

```
application → domain.type → domain.model
```

- `domain` は `application` に依存しない（単方向依存）
- ビジネスロジック（domain）がアプリケーション層から独立

## 9.4 エントリポイントの更新

```python
# main.py
from lib.application.fizz_buzz_list_command import FizzBuzzListCommand
from lib.domain.type.fizz_buzz_type import FizzBuzzType

if __name__ == "__main__":
    type_ = FizzBuzzType.create(1)
    command = FizzBuzzListCommand(type_)
    result = command.execute()
    for i in range(result.size()):
        print(result.get(i))
```

## 9.5 まとめ

### 第 3 部の振り返り

| 章 | テーマ | 適用した技法 |
|----|--------|------------|
| 7 | カプセル化とポリモーフィズム | `@property`、`abc.ABC`、`@abstractmethod`、Factory Method |
| 8 | デザインパターン | Value Object、First-Class Collection、Command、Null Object |
| 9 | SOLID 原則とモジュール設計 | SRP、OCP、DIP、パッケージ分割 |

### リファクタリングの進化

```
第 1 部: 手続き的な FizzBuzz（1 クラス）
    ↓
第 3 部 章 7: ポリモーフィズム（FizzBuzzType 階層）
    ↓
第 3 部 章 8: デザインパターン（Value Object、Command）
    ↓
第 3 部 章 9: モジュール設計（domain/application 分離）
```

次の第 4 部では、関数型プログラミング（高階関数、デコレータ、ジェネレータ）を FizzBuzz に適用します。
