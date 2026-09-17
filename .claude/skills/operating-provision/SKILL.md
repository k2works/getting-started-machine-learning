---
name: operating-provision
description: IaC（Terraform）によるインフラプロビジョニングを実行。S3 状態管理、VPC、RDS、ECR、ECS 等の AWS リソースを段階的に構築・管理。Terraform の init/plan/apply やインフラ構築時に使用。
---

# インフラプロビジョニング

IaC（Infrastructure as Code）により、ステージング・本番環境のインフラを構築・管理します。

## Instructions

### 1. Terraform ワークフロー

```
インフラコード作成 → init → plan → apply → テスト → ドキュメント更新
```

```bash
# 初期化
terraform init --backend-config=backend.hcl

# 変更確認
terraform plan

# 適用
terraform apply

# 廃棄
terraform destroy
```

aws-vault 使用時は `aws-vault exec <profile> --` プレフィックスを付与する。

### 2. プロビジョニング順序

ステージング・本番環境では以下のリソースを段階的にプロビジョニングする。

1. **状態管理**: S3 バケット + DynamoDB（Terraform State）
2. **IAM**: OIDC 認証用 IAM ロール（GitHub Actions 連携）
3. **シークレット**: SSM パラメータストア（DB 認証情報）
4. **ネットワーク**: VPC・サブネット・NAT Gateway
5. **データストア**: RDS
6. **バックアップ**: AWS Backup
7. **レジストリ**: ECR リポジトリ
8. **コンピュート**: ECS（Fargate + ALB）
9. **管理**: Application Manager（リソースグループ）
10. **DNS**: Route 53・カスタムドメイン（任意）
11. **踏み台**: EC2 踏み台サーバー（任意）

### 3. Terraform ディレクトリ構成

```text
ops/terraform/
├── live/
│   ├── global/          # S3 状態管理、IAM ロール
│   ├── stage/           # ステージング環境リソース
│   └── prod/            # 本番環境リソース
├── modules/             # 再利用可能モジュール
└── test/                # Terratest
```

### 4. 環境廃棄

廃棄は**構築時と逆の順序**で実行する。本番環境では `deletion_protection` を事前に `false` に変更する。

### 5. 注意事項

- **状態管理が先**: S3 + DynamoDB は他の全リソースより先に作成
- **IaC 優先**: 手作業を最小限に抑え、インフラをコードで管理
- **テスト**: LocalStack（ローカル）、Terratest（単体・結合）で検証
- **secret.tfvars**: Git 管理外にすること

### 関連スキル

- `operating-setup` : 環境構築の段階的実行
- `operating-cicd` : CI/CD パイプライン構築
- `operating-deploy` : デプロイ実行
