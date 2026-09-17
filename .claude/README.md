# Claude Code Booster - Basic Template

Claude Code をより効率的に使うための基本設定テンプレートです。

このテンプレートは最小限の構成で、プロジェクトに合わせてカスタマイズできる基盤を提供します。

## 主要機能

3 つの機能で Claude Code の動作をカスタマイズできます。

- **Skills**: タスクに応じて自動発動する専門スキル（Progressive Disclosure）
- **Agents**: XP チームロールに基づく専門エージェント
- **Hooks**: 特定のタイミングでスクリプトを自動実行

---

## 機能一覧

### Skills（スキル）

`skills/` ディレクトリ内の `SKILL.md` ファイルとして保存されています。タスク内容に応じて自動的に発動し、必要な指示をコンテキストに読み込みます。

#### オーケストレーション

| スキル | 説明 |
| :--- | :--- |
| `orchestrating-analysis` | 分析フェーズ全体のワークフローをオーケストレーション。各 analyzing-* スキルの実行順序を案内。 |
| `orchestrating-development` | 開発フェーズ全体の TDD ワークフローをオーケストレーション。Codex 分業体制を案内。 |
| `orchestrating-operation` | 運用フェーズ全体のワークフローをオーケストレーション。各 operating-* スキルの実行順序を案内。 |
| `orchestrating-project` | 計画・進捗管理フェーズ全体のワークフローをオーケストレーション。リリース計画、GitHub Project 同期、進捗追跡の実行順序を案内。 |

#### 分析系

| スキル | 説明 |
| :--- | :--- |
| `analyzing-business-case` | 企業事例（ケーススタディ）の与件文作成を支援。経営戦略・マーケティング・生産管理・財務会計の 4 パターン対応。 |
| `analyzing-business-strategy` | 企業事例を基に企業戦略・事業戦略・機能戦略の 3 階層を体系的に立案。SWOT・VRIO・BMC・価値連鎖・ケイパビリティマップを作成。 |
| `analyzing-business-architecture` | ビジネスアーキテクチャ分析を支援。ビジネスモデルキャンバス、バリューストリーム、ケイパビリティマップ等の作成。 |
| `analyzing-inception-deck` | インセプションデッキ作成。プロジェクトの「なぜ」「何を」「どうやって」を 10 の問いで整理。 |
| `analyzing-requirements` | RDRA 2.0 に基づいた体系的な要件定義を作成。 |
| `analyzing-usecases` | ユースケース・ユーザーストーリー作成を支援。 |
| `analyzing-architecture` | アーキテクチャパターンの選択と設計ドキュメント作成。 |
| `analyzing-data-model` | ER 図作成、テーブル定義、リレーション設計。 |
| `analyzing-domain-model` | エンティティ、値オブジェクト、集約の設計。 |
| `analyzing-ui-design` | 画面遷移図と画面イメージを設計。 |
| `analyzing-tech-stack` | フレームワーク、ライブラリ、インフラの選定と評価。 |
| `analyzing-test-strategy` | テストピラミッド設計、テスト種別の定義、カバレッジ目標の設定。 |
| `analyzing-non-functional` | 性能、セキュリティ、可用性、保守性の要件策定。 |
| `analyzing-operation` | 運用フロー、監視設計、障害対応手順の策定。 |

#### 開発系

| スキル | 説明 |
| :--- | :--- |
| `developing-backend` | バックエンド開発の TDD ワークフロー。インサイドアウトアプローチ。 |
| `developing-frontend` | フロントエンド開発の TDD ワークフロー。アウトサイドインアプローチ。 |
| `developing-release` | リリースワークフロー。品質ゲート、バージョンバンプ、CHANGELOG 生成、git commit + tag。 |

#### レビュー系

| スキル | 説明 |
| :--- | :--- |
| `analyzing-review` | 分析成果物のマルチパースペクティブレビュー。XP エージェント 5 名（PM、アーキテクト、ID、テスター、ユーザー代表）を並列起動。 |
| `developing-review` | 開発成果物のマルチパースペクティブレビュー。XP エージェント 5 名（プログラマー、テスター、アーキテクト、TW、ユーザー代表）を並列起動。 |
| `developing-uiux-review` | UI/UX 成果物のマルチパースペクティブレビュー。XP エージェント 2 名（インタラクションデザイナー、ユーザー代表）を並列起動。 |
| `operating-review` | 運用成果物のマルチパースペクティブレビュー。XP エージェント 3 名（アーキテクト、テスター、PM）を並列起動。 |

#### 計画・進捗系

| スキル | 説明 |
| :--- | :--- |
| `planning-releases` | アジャイルなリリース計画とイテレーション計画を作成・管理。 |
| `opening-iteration` | イテレーションを開発着手できる状態まで準備する 6 ステップ（前 IT ふりかえり反映→計画作成→詳細整合性検証→横断整合性検証→GitHub 同期→ドキュメント同期）を案内。 |
| `closing-iteration` | イテレーションを漏れなくクローズする 7 ステップ（レビュー→品質ゲート→進捗反映→ふりかえり→完了報告書→GitHub 同期→ドキュメント同期）を案内。 |
| `creating-development-strategy` | 開発戦略ドキュメントを作成。リリース計画のイテレーションを序盤・中盤・終盤に分け、TDD アプローチ（アウトサイドイン／インサイドアウト）とウォーキングスケルトン、設計ドキュメント整合方針を定義。 |
| `syncing-github-project` | リリース計画を GitHub Project・Issue・Milestone に同期。 |
| `tracking-progress` | プロジェクトの開発進捗を包括的に分析しレポート生成。 |
| `validating-iteration-plan` | イテレーション計画と上流設計ドキュメント群との整合性を検証。 |
| `validating-design` | 開発戦略・イテレーション計画・設計ドキュメント・過去計画の間で設計トピックの整合性を横断検証。 |
| `validating-operating-setup` | 環境セットアップ手順書の実施状況を監査し、未対応項目を報告。 |

#### 運用系

| スキル | 説明 |
| :--- | :--- |
| `operating-setup` | 環境構築を段階的に実行。テンプレートから手順書を生成し環境を構築。 |
| `operating-provision` | IaC（Terraform）によるインフラプロビジョニングを実行。 |
| `operating-cicd` | CI/CD パイプラインを構築。CI と CD の設計・実装。 |
| `operating-deploy` | 各環境へのデプロイを実行。ローリング、Blue/Green、ロールバック。 |
| `operating-backup` | データベースのバックアップ・リストアを実行。 |
| `operating-docs` | 設計ドキュメントの一覧表示、進捗確認、インデックス更新、Markdown Lint。 |
| `operating-qt` | SonarQube によるコード品質管理。スキャン実行、Quality Gate 確認、イシュー分析。 |
| `operating-script` | 運用スクリプト（Gulp タスク）の作成・更新。 |
| `killing-processes` | 開発サーバーや Node.js プロセスを強制終了。ポート競合の解決。 |

#### ドキュメント・Git 系

| スキル | 説明 |
| :--- | :--- |
| `generating-bmc` | ビジネスモデルキャンバスの SVG 図を生成。ビジネスアーキテクチャ分析書のデータを反映。 |
| `generating-slides` | インセプションデッキから PowerPoint スライドを生成。 |
| `creating-slides` | Marp Markdown スライドデッキの作成・拡張・再生成。PlantUML 図表、AI 画像、PPTX/HTML 出力に対応。 |
| `git-commit` | 意味のある変更単位ごとにコミットを作成。Conventional Commits 準拠。 |
| `creating-adr` | Architecture Decision Record の作成を支援。 |
| `creating-journal` | その日の Skill 実行と意思決定を物語形式で `docs/journal/YYYYMMDD.md` に保存。コミットメッセージには載らない判断と学びを記録。 |
| `creating-manual` | ユーザーマニュアル（`docs/manual`）の章・節の執筆、UI 変更への追従、E2E ツールによる画面キャプチャ再生成、HTML 公開。 |
| `creating-article` | 動くサンプルコードを伴う技術記事・連載シリーズの執筆。計画・骨子・実行環境・章ごとの執筆と実装の同期・横断解説・CI までを一貫支援。 |
| `creating-release-report` | リリース完了報告書を作成。release_plan・iteration_report・git log・CHANGELOG からデータ収集。 |
| `creating-iteration-report` | イテレーション完了報告書を作成。iteration_plan・release_plan・テスト結果からデータ収集。 |

#### 学習系

| スキル | 説明 |
| :--- | :--- |
| `practicing-getting-start-tdd` | TDD プログラミング入門の対話式チュートリアル。FizzBuzz を題材に 14 言語で TDD を体験。 |

#### 共通

| スキル | 説明 |
| :--- | :--- |
| `ai-agent-guidelines` | AI Agent の実行ガイドライン。TDD サイクル、品質保証、完了報告のルール。 |

### Agents（エージェント）

`agents/` ディレクトリ内の Markdown ファイルで定義されます。XP（エクストリームプログラミング）のチームロールに基づく専門エージェントが利用できます。

| エージェント | 役割 |
| :--- | :--- |
| `xp-programmer` | TDD でのコード実装、タスク分解、見積もり、リファクタリング |
| `xp-tester` | テスト戦略設計、テスタビリティレビュー、テスト技法コーチング |
| `xp-architect` | アーキテクチャ設計、大規模リファクタリング、ADR 作成 |
| `xp-product-manager` | ストーリー作成、優先順位付け、リリース計画 |
| `xp-project-manager` | 進捗管理、ボトルネック特定、ステークホルダー調整 |
| `xp-interaction-designer` | UI/UX 設計、画面遷移設計、システムメタファー選定 |
| `xp-technical-writer` | ユーザー向けドキュメント、API ドキュメント作成 |
| `xp-user-representative` | ユーザー視点でのストーリーレビュー、機能評価 |
| `xp-executive` | 戦略的判断、リソース配分、チーム支援 |

### Hooks（自動化スクリプト）

`settings.json` で Hooks を設定して、開発作業を自動化できます。Hooks はイベント（`PreToolUse`、`PostToolUse`、`Notification`、`Stop` など）に応じてスクリプトを自動実行する仕組みです。

現在のテンプレートでは `settings.json` に Hooks は未設定です。プロジェクトに合わせて `settings.json` の `hooks` セクションに追加してください。

#### 設定例

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": ".claude/scripts/deny-check.sh"
          }
        ]
      }
    ]
  }
}
```

#### スクリプト

`scripts/` ディレクトリにスクリプトを配置し、`settings.json` から参照します。

| ファイル | 説明 |
| :--- | :--- |
| `generate-inception-deck.mjs` | インセプションデッキの PowerPoint スライドを生成する Node.js スクリプト。 |

---

## ディレクトリ構造

```
.claude/
├── agent-memory/                          # エージェントメモリ
├── agents/                                # XP チームロール定義（.md）
├── assets/                                # 通知音などのアセット
├── scripts/                            # Hooks 用・ユーティリティスクリプト
├── skills/                             # スキル定義（SKILL.md + 参照ファイル）
│   ├── ai-agent-guidelines/SKILL.md
│   ├── git-commit/SKILL.md
│   ├── creating-adr/SKILL.md
│   ├── generating-bmc/SKILL.md
│   ├── generating-slides/SKILL.md
│   ├── analyzing-business-case/SKILL.md
│   ├── analyzing-business-strategy/SKILL.md
│   ├── analyzing-business-architecture/SKILL.md
│   ├── analyzing-inception-deck/SKILL.md
│   ├── analyzing-requirements/SKILL.md
│   ├── analyzing-usecases/SKILL.md
│   ├── analyzing-architecture/SKILL.md
│   ├── analyzing-data-model/SKILL.md
│   ├── analyzing-domain-model/SKILL.md
│   ├── analyzing-ui-design/SKILL.md
│   ├── analyzing-tech-stack/SKILL.md
│   ├── analyzing-test-strategy/SKILL.md
│   ├── analyzing-non-functional/SKILL.md
│   ├── analyzing-operation/SKILL.md
│   ├── analyzing-review/SKILL.md
│   ├── developing-backend/SKILL.md
│   ├── developing-frontend/SKILL.md
│   ├── developing-release/SKILL.md
│   ├── developing-review/SKILL.md
│   ├── developing-uiux-review/SKILL.md
│   ├── operating-setup/SKILL.md
│   ├── operating-qt/SKILL.md
│   ├── operating-script/SKILL.md
│   ├── operating-review/SKILL.md
│   ├── operating-provision/SKILL.md
│   ├── operating-cicd/SKILL.md
│   ├── operating-deploy/SKILL.md
│   ├── operating-backup/SKILL.md
│   ├── operating-docs/SKILL.md
│   ├── killing-processes/SKILL.md
│   ├── tracking-progress/SKILL.md
│   ├── syncing-github-project/SKILL.md
│   ├── planning-releases/SKILL.md
│   ├── opening-iteration/SKILL.md
│   ├── closing-iteration/SKILL.md
│   ├── creating-development-strategy/SKILL.md
│   ├── creating-journal/SKILL.md
│   ├── creating-manual/SKILL.md
│   ├── creating-article/SKILL.md
│   ├── creating-slides/SKILL.md
│   ├── creating-release-report/SKILL.md
│   ├── creating-iteration-report/SKILL.md
│   ├── orchestrating-analysis/SKILL.md
│   ├── orchestrating-development/SKILL.md
│   ├── orchestrating-operation/SKILL.md
│   ├── orchestrating-project/SKILL.md
│   ├── validating-iteration-plan/SKILL.md
│   ├── validating-design/SKILL.md
│   ├── validating-operating-setup/SKILL.md
│   └── practicing-getting-start-tdd/SKILL.md
├── README.md
├── settings.json                       # Claude Code 設定
└── settings.local.json                 # ローカル環境用設定
```

---

## カスタマイズ

- **スキルの追加**: `/skill-creator` プラグインを使用してスキルを作成・テスト・最適化します
- **エージェントの追加**: `agents/` に `.md` ファイルを追加するだけです
- **Hooks の編集**: `settings.json` を編集して、自動化処理を変更できます
- **スクリプトの追加**: `scripts/` にシェルスクリプトを追加し、`settings.json` で参照します

### skill-creator プラグインの導入

skill-creator はスキルの作成・テスト・評価・最適化を自動化するプラグインです。

#### インストール

1. Claude Code を起動
2. `/plugin` コマンドを実行
3. 検索ボックスで `skill-creator` を探して選択
4. インストールするスコープを選ぶ（ユーザー / プロジェクト / ローカル）
5. Claude Code を再起動

#### 使い方

```bash
# 新しいスキルを作成
/skill-creator 〇〇を支援するスキルを作成して

# 既存スキルの改善
/skill-creator skills/operating-deploy/SKILL.md を改善して

# スキルの説明文を最適化（トリガー精度の向上）
/skill-creator operating-deploy のdescriptionを最適化して
```

#### ワークフロー

skill-creator は以下のサイクルでスキルを作成・改善します。

1. **要件整理** — スキルの目的・トリガー条件・出力形式を整理
2. **ドラフト作成** — SKILL.md を作成
3. **テスト実行** — テストケースを作成し、with-skill / baseline の並行テストを実行
4. **評価** — アサーション（判定基準）によるグレーディングとベンチマーク比較
5. **改善** — フィードバックに基づきスキルを改善し、再テスト
6. **説明文最適化** — トリガー精度を向上させる description の自動最適化
