# Docker/Kubernetes 実践コンテナ解説

Docker と Kubernetes を使って、コンテナによるアプリケーションの開発・デプロイ・運用を実践的に学ぶシリーズです。

書籍『Docker/Kubernetes 実践コンテナ開発入門（第 2 版）』の章立てに沿って、コンテナの基礎から複数コンテナ構成、Kubernetes、継続的デリバリーまでを段階的に解説します。各章は実際に動作するサンプルコードに紐づいており、手を動かしながら理解できます。

## 章構成

### 第 1 部: コンテナと Docker

| 章 | テーマ |
|----|--------|
| [第 1 章 コンテナと Docker の基礎](01-container-and-docker-basics.md) | コンテナとは、Docker とは、利用意義、ローカル実行環境の構築 |
| [第 2 章 コンテナのデプロイ](02-container-deployment.md) | アプリの実行、イメージ作成、イメージ・コンテナの操作、Docker Compose |
| [第 3 章 実用的なコンテナの構築とデプロイ](03-practical-container-build-deploy.md) | 粒度、ポータビリティ、クレデンシャル、永続化データ |

### 第 2 部: 複数コンテナと Kubernetes 入門

| 章 | テーマ |
|----|--------|
| [第 4 章 複数コンテナ構成でのアプリケーション構築](04-multi-container-application.md) | Web/API/MySQL/マイグレータ/リバースプロキシ、Tilt |
| [第 5 章 Kubernetes 入門](05-kubernetes-introduction.md) | Pod/ReplicaSet/Deployment/Service/Ingress |
| [第 6 章 Kubernetes のデプロイ・クラスタ構築](06-kubernetes-deploy-cluster.md) | タスクアプリのデプロイ、インターネット公開 |

### 第 3 部: Kubernetes の実践

| 章 | テーマ |
|----|--------|
| [第 7 章 Kubernetes の発展的な利用](07-kubernetes-advanced.md) | デプロイ戦略、CronJob、RBAC |
| [第 8 章 Kubernetes アプリケーションのパッケージング](08-kubernetes-packaging.md) | Kustomize、Helm |
| [第 9 章 コンテナの運用](09-container-operations.md) | ロギング、可用性の高い運用 |

### 第 4 部: イメージ最適化と継続的デリバリー

| 章 | テーマ |
|----|--------|
| [第 10 章 最適なコンテナイメージ作成と運用](10-optimal-container-image.md) | 軽量ベースイメージ、Multi-stage builds、BuildKit、セキュリティ |
| [第 11 章 コンテナにおける継続的デリバリー](11-continuous-delivery.md) | Flux、Argo CD、PipeCD |
| [第 12 章 コンテナのさまざまな活用方法](12-container-use-cases.md) | 開発環境統一、CLI、負荷テスト |

### 第 5 部: 国際貨物輸送システムのケーススタディ

同一ドメイン（Cargo Tracker）の 4 アーキテクチャを題材に、Compose・Kustomize・Helm を実装・検証して比較します。

| 章 | テーマ |
|----|--------|
| [第 13 章 モノリスのデプロイ](13-case-monolith-compose-vs-kustomize.md) | Docker Compose 対 Kustomize |
| [第 14 章 イベント駆動マイクロサービスのデプロイ](14-case-event-driven-kustomize-vs-helm.md) | Kustomize 対 Helm |
| [第 15 章 ES/CQRS マイクロサービス（Axon）のデプロイ](15-case-escqrs-axon-kustomize-vs-helm.md) | Kustomize 対 Helm |
| [第 16 章 ES/CQRS マイクロサービス（Kafka）のデプロイ](16-case-escqrs-kafka-kustomize-vs-helm.md) | Kustomize 対 Helm |
| [第 17 章 ケーススタディ実装比較まとめ](17-case-comparison-summary.md) | 全アーキテクチャ × デプロイ手段の総括 |
| [第 18 章 ケーススタディの負荷テスト](18-case-load-testing.md) | Locust（Compose 版 / Kubernetes 版）で 4 ケースに負荷をかける |

### 付録

| 付録 | テーマ |
|------|--------|
| [付録 A 開発ツールのセットアップ](appendix-a-dev-tools-setup.md) | WSL2、asdf、kind、Rancher Desktop |
| [付録 B さまざまなコンテナオーケストレーション環境](appendix-b-orchestration-environments.md) | GKE、EKS、AKS、オンプレミス、ECS |
| [付録 C コンテナ開発・運用の Tips](appendix-c-tips.md) | コンテナランタイム、Kubernetes Tips、生成 AI 活用、apk |

## サンプルコード

本シリーズは、以下のサンプルリポジトリのコードを題材に解説します。

| リポジトリ | 内容 |
|-----------|------|
| echo | 単一コンテナの最小サンプル（Go 製 Hello サーバ） |
| taskapp | 複数コンテナ構成のタスク管理アプリ |
| container-kit | デバッグ・プロキシ・ジョブ用の補助コンテナ集 |
| image-bootstrap | distroless + 非 root + Trivy のセキュアイメージ |
| echo-bootstrap | echo を Kubernetes へデプロイする Kustomize マニフェスト |
| argocd-example-apps | Argo CD 用サンプルアプリ |
| pipecd-examples | PipeCD 用デプロイ定義 |

## 執筆計画

本シリーズの執筆方針と章・ソースの対応は [執筆計画アウトライン](outline.md) を参照してください。

## 参考文献

- 『Docker/Kubernetes 実践コンテナ開発入門（第 2 版）』 — 山田明憲
