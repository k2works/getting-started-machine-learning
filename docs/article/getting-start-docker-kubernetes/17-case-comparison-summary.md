---
type: Article
title: "第 17 章 ケーススタディ実装比較まとめ"
description: "第 13〜16 章では、同一ドメイン（国際貨物輸送システム = Cargo Tracker）を 4 つの異なるアーキテクチャで実装し、それぞれを Docker Compose・Kustomize・Helm でデプロイして比較しました。"
tags: [article, getting-start-docker-kubernetes]
status: stable
generated: { by: human:kakimomokuri, at: 2026-06-26T00:29:42Z }
---

# 第 17 章 ケーススタディ実装比較まとめ

## はじめに

第 13〜16 章では、同一ドメイン（国際貨物輸送システム = Cargo Tracker）を 4 つの異なるアーキテクチャで実装し、それぞれを Docker Compose・Kustomize・Helm でデプロイして比較しました。この章では 4 ケースを横断し、**アーキテクチャの複雑さとデプロイ手段の選択がどう関係するか**を総括します。

実装はすべて [`apps/case-studies/`](https://github.com/k2works/getting-started-docker-kubernetes/tree/main/apps/case-studies) にあり、ローカル Kubernetes（Docker Desktop）で実際に動作検証しています。

---

## 1. 4 ケースの概観

| 章 | ケース | アーキテクチャ | 構成要素 | 比較した手段 |
|----|--------|--------------|---------|------------|
| 13 | case-1 | モノリス | アプリ 1 + PostgreSQL | Compose 対 Kustomize |
| 14 | case-2 | イベント駆動 MS | 8 アプリ + PostgreSQL + RabbitMQ | Kustomize 対 Helm |
| 15 | case-3 | ES/CQRS（Axon） | 8 アプリ + PostgreSQL + Axon Server | Kustomize 対 Helm |
| 16 | case-4 | ES/CQRS（Kafka） | 8 アプリ + PostgreSQL + Kafka + ZooKeeper | Kustomize 対 Helm |

ワークロード数（Pod 種別）は、モノリスの 2 個から、マイクロサービスの 10〜11 個へと大きく増えます。

| ケース | デプロイされる Pod（種別） |
|--------|--------------------------|
| case-1 | 2（app, postgres） |
| case-2 | 10（gateway, 6 サービス, frontend, postgres, rabbitmq） |
| case-3 | 10（gateway, 6 サービス, frontend, postgres, axonserver） |
| case-4 | 11（gateway, 6 サービス, frontendms, postgres, kafka, zookeeper） |

---

## 2. 動作検証結果のまとめ

すべてのケースで、実際にデプロイして疎通を確認しました。

| ケース | 手段 | 結果 |
|--------|------|------|
| case-1 | Docker Compose | app + postgres healthy、`/actuator/health` UP、Flyway で 12 テーブル作成 |
| case-1 | Kustomize | app/postgres 1/1 Running、health UP |
| case-2 | Kustomize / Helm | 10 Pod すべて 1/1 Running、gateway 経由で全 6 サービス疎通（401）、frontend 200 |
| case-3 | Kustomize / Helm | 10 Pod すべて 1/1 Running、Axon Server ヘルス 200、gateway 疎通、frontend 200 |
| case-4 | Kustomize / Helm | 11 Pod すべて 1/1 Running、read エンドポイント 200、Kafka started、frontend 200 |

マイクロサービスの 3 ケース（case-2/3/4）は、Kustomize と Helm の**両方**で同一システムをデプロイし、同じ疎通結果が得られることを確認しています。

---

## 3. デプロイ手段の比較軸まとめ

### Docker Compose 対 Kustomize（第 13 章の知見）

| 観点 | Docker Compose | Kustomize |
|------|----------------|-----------|
| 対象 | 単一ホスト | Kubernetes クラスタ |
| 機密 | 環境変数 / `.env` | Secret |
| 永続化 | named volume | PVC |
| 公開 | ポート公開 | Service + Ingress |
| 自己修復・スケール | 限定的 | Deployment が標準提供 |

Compose は「ローカルで素早く全体を起動する」用途に最適で、Kustomize（Kubernetes）は「本番でスケール・自己修復・公開を宣言的に管理する」用途に向きます。

### Kustomize 対 Helm（第 14〜16 章の知見）

| 観点 | Kustomize | Helm |
|------|-----------|------|
| 同型サービスの繰り返し | サービスごとにファイル | `values` のリスト + ループ |
| サービスの性質差（DB/Axon/JWT） | env を書く / 書かない | フラグ + `{{- if }}` |
| 環境差分 | base + overlay の `patches` | `values-<env>.yaml` / `--set` |
| 命名・ラベル共通化 | `commonLabels` | `_helpers.tpl` |
| 単発インフラ（Kafka/Axon Server） | 専用ファイル | テンプレートに固定記述 |
| リリース管理 | なし（kubectl apply） | リビジョン・ロールバック |
| 透明性 | 生 YAML に近い | `helm template` で展開して確認 |

---

## 4. 横断的な考察 — 複雑さとデプロイ手段

4 ケースを並べると、一貫した傾向が見えます。

### (1) 構成要素が増えるほどテンプレート化（Helm）の価値が高まる

モノリス（case-1）では、デプロイ対象が少なく、Compose の簡潔さと Kustomize の明示性のどちらも十分でした。手段の差は「好みと運用要件」の範囲です。

マイクロサービス（case-2/3/4）になると、6〜7 個の**同型サービス**が現れます。Kustomize ではこれを個別ファイルに書き下すため重複が増え、Helm では `values` のリスト + ループ 1 つに集約できます。**同型コンポーネントが多いほど、Helm のテンプレート化が記述量と保守性で有利**になります。

### (2) サービスの「性質の差」はデータとして表現できる

case-3 では「Axon を使う / 使わない」「JWT を持つ / 持たない」、case-4 では「追加 Secret 環境変数の有無」といったサービスごとの差がありました。Kustomize はファイルの書き分けで、Helm は `axon: true` のようなフラグ + 条件分岐で表現します。**性質をデータ化してテンプレートに構造を生成させる**Helm のアプローチは、サービスが増えるほど効きます。

### (3) 単発の「特別なインフラ」では差が縮まる

Axon Server（case-3）や Kafka + ZooKeeper（case-4）のような単発のステートフル基盤は、ループの恩恵がないため、Kustomize と Helm で記述量はほぼ同じです。繊細で固有な設定（Axon の standalone/DCB、Kafka のリスナー設定など）は、生 YAML に近い Kustomize のほうが意図を追いやすいという見方もできます。

### (4) 環境差分は overlay（Kustomize）と values（Helm）で思想が異なる

case-4 で見たように、Kustomize は「base を変えずに overlay でパッチを重ねる」、Helm は「1 つのテンプレートを values で変化させる」という異なる思想で環境差分を扱います。構成自体が環境で変わるなら overlay、値だけが変わるなら values が簡潔です。

### (5) 二者択一とは限らない

case-4 は Kustomize と Helm の**両方**を保守していました。GitOps で環境別に宣言的管理したいなら Kustomize の overlay、チャートとしてバージョン管理・配布したいなら Helm、と**併用して使い分ける**のも現実的な選択です。

---

## 5. 選択の指針

これまでの検証を踏まえた、実務的な選択の目安です。

| 状況 | 推奨 |
|------|------|
| ローカル開発で全体を素早く起動したい | Docker Compose |
| 少数のコンポーネントを Kubernetes へ | Kustomize（base のみ） |
| 同型サービスが多いマイクロサービス | Helm（ループ + フラグ） |
| 環境ごとに構成が変わる | Kustomize（overlay）または併用 |
| チャートとして配布・バージョン管理したい | Helm |
| GitOps で宣言的に環境別管理 | Kustomize の overlay（+ Helm 併用も可） |

アーキテクチャが複雑になるほど、デプロイ手段は「動けばよい」から「重複を抑え、環境差分を安全に管理し、リリースを追跡できる」ことが重要になります。本ケーススタディは、その移行を 1 つのドメインの 4 実装で具体的に示しました。

---

## まとめ

- 同一ドメインの 4 アーキテクチャ（モノリス / イベント駆動 / ES・CQRS Axon / ES・CQRS Kafka）を、Compose・Kustomize・Helm で実装・デプロイ・検証しました
- 構成要素が増えるほど、同型サービスのテンプレート化（Helm）と環境差分の宣言的管理（Kustomize overlay）の価値が高まります
- 単発インフラでは両手段の差は縮まり、繊細な設定はむしろ Kustomize の透明性が活きます
- 「Kustomize か Helm か」は二者択一ではなく、GitOps と配布で併用する選択も現実的です
- これでシリーズ全体（基礎 12 章 + ケーススタディ 5 章 + 付録 3）を通じ、コンテナの基礎から実システムのデプロイ手段の選択までを一貫して学べます

---

- 前の章: [第 16 章 ES/CQRS マイクロサービス（Kafka）のデプロイ — Kustomize 対 Helm](16-case-escqrs-kafka-kustomize-vs-helm.md)
- 次の章: [第 18 章 ケーススタディの負荷テスト](18-case-load-testing.md)
- シリーズ目次: [Docker/Kubernetes 実践コンテナ解説](index.md)
