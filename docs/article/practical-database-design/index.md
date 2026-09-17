# 実践データベース設計：基幹業務システム編

## 概要

本記事では、基幹業務システム（販売管理・財務会計・生産管理）のデータベース設計を、業務フローとデータモデルの観点から体系的に解説します。

## 対象読者

- データベース設計の基礎を学びたいエンジニア
- 基幹業務システムの全体像を理解したい開発者
- 販売管理・財務会計・生産管理の業務知識を習得したい方

## 記事構成

### 第1部：基幹業務システムの全体像

基幹業務システムを構成するサブシステムと、それぞれの業務領域について概観します。

- [第1章：基幹業務システムとは](part1/chapter01.md)
- [第2章：基幹業務システムの業務領域](part1/chapter02.md)
- [第3章：業務フローの全体像](part1/chapter03.md)

### 第2部：販売管理システム

受注から売上、債権管理までの販売業務のデータベース設計を解説します。

- [第4章：販売管理システムの全体像](part2/chapter04.md)
- [第5章：マスタ情報の設計](part2/chapter05.md)
- [第6章：受注・出荷・売上の設計](part2/chapter06.md)
- [第7章：債権管理の設計](part2/chapter07.md)
- [第8章：調達管理の設計](part2/chapter08.md)
- [第9章：在庫管理の設計](part2/chapter09.md)
- [第10章：債務管理の設計](part2/chapter10.md)
- [第11章：共通処理の設計](part2/chapter11.md)
- [第12章：販売管理データ設計（B 社事例）](part2/chapter12.md)
- [第13章：API サービスの実装](part2/chapter13.md)

### 第3部：財務会計システム

勘定科目、仕訳、自動仕訳など会計システムの核心部分を解説します。

- [第14章：財務会計システムの全体像](part3/chapter14.md)
- [第15章：勘定科目の設計](part3/chapter15.md)
- [第16章：仕訳の設計](part3/chapter16.md)
- [第17章：自動仕訳の設計](part3/chapter17.md)
- [第18章：勘定科目残高の設計](part3/chapter18.md)
- [第19章：赤黒とログの設計](part3/chapter19.md)
- [第20章：財務会計データ設計（D 社事例）](part3/chapter20.md)
- [第21章：API サービスの実装](part3/chapter21.md)

### 第4部：生産管理システム

BOM、MRP、工程管理など製造業の基幹となるデータベース設計を解説します。

- [第22章：生産管理システムの全体像](part4/chapter22.md)
- [第23章：マスタ情報の設計](part4/chapter23.md)
- [第24章：生産計画の設計](part4/chapter24.md)
- [第25章：購買管理の設計](part4/chapter25.md)
- [第26章：外注委託管理の設計](part4/chapter26.md)
- [第27章：工程管理の設計](part4/chapter27.md)
- [第28章：在庫管理の設計](part4/chapter28.md)
- [第29章：品質管理の設計](part4/chapter29.md)
- [第30章：製造原価管理の設計](part4/chapter30.md)
- [第31章：生産管理データ設計（E 社事例）](part4/chapter31.md)
- [第32章：API サービスの実装](part4/chapter32.md)

### 第5部：エンタープライズインテグレーション

各システムを統合するためのパターンとアーキテクチャを解説します。

- [第33章：システム統合の概要](part5/chapter33.md)
- [第34章：メッセージングパターン](part5/chapter34.md)
- [第35章：システム間連携パターン](part5/chapter35.md)
- [第36章：マスタデータ管理（MDM）](part5/chapter36.md)
- [第37章：イベント駆動アーキテクチャ](part5/chapter37.md)
- [第38章：API 設計とサービス連携](part5/chapter38.md)
- [第39章：データ連携の実装パターン](part5/chapter39.md)

### 研究：販売管理システム実装編

販売管理システムの各種プレゼンテーション層実装パターンを研究します。

- [研究 1：モノリスサービスの実装](study/study2-1.md)
- [研究 2：JavaFX デスクトップアプリケーションの実装](study/study2-2.md)
- [研究 3：gRPC サービスの実装](study/study2-3.md)
- [研究 4：GraphQL サービスの実装](study/study2-4.md)
- [研究 5：Axon CQRS/ES の実装](study/study2-5.md)

### 研究：財務会計システム実装編

財務会計システムの各種プレゼンテーション層実装パターンを研究します。

- [研究 1：モノリスサービスの実装](study/study3-1.md)
- [研究 2：JavaFX デスクトップアプリケーションの実装](study/study3-2.md)
- [研究 3：gRPC サービスの実装](study/study3-3.md)
- [研究 4：GraphQL サービスの実装](study/study3-4.md)
- [研究 5：Axon CQRS/ES の実装](study/study3-5.md)

### 研究：生産管理システム実装編

生産管理システムの各種プレゼンテーション層実装パターンを研究します。

- [研究 1：モノリスサービスの実装](study/study4-1.md)
- [研究 2：JavaFX デスクトップアプリケーションの実装](study/study4-2.md)
- [研究 3：gRPC サービスの実装](study/study4-3.md)
- [研究 4：GraphQL サービスの実装](study/study4-4.md)
- [研究 5：Axon CQRS/ES の実装](study/study4-5.md)

### 研究：財務会計システム ORM 実装編

財務会計システムの ORM（JPA/Spring Data JPA）実装パターンを研究します。

- [第 14 章：財務会計システムの全体像【ORM 版】](part3-orm/chapter14-orm.md)
- [第 15 章：勘定科目の設計【ORM 版】](part3-orm/chapter15-orm.md)
- [第 16 章：仕訳の設計【ORM 版】](part3-orm/chapter16-orm.md)
- [第 17 章：自動仕訳の設計【ORM 版】](part3-orm/chapter17-orm.md)
- [第 18 章：勘定科目残高の設計【ORM 版】](part3-orm/chapter18-orm.md)
- [第 19 章：赤黒とログの設計【ORM 版】](part3-orm/chapter19-orm.md)
- [第 20 章：財務会計データ設計（D 社事例）【ORM 版】](part3-orm/chapter20-orm.md)
- [第 21 章：API サービスの実装【ORM 版】](part3-orm/chapter21-orm.md)
- [第 22 章：Axon CQRS/ES の実装【ORM 版】](part3-orm/chapter22-orm.md)
