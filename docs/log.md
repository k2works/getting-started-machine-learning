# Docs Update Log

## 2026-09-12
* **Verification**: [AI-DLC用語集](/reference/AI-DLC用語集.md) を human:kakimomokuri が検証
* **Verification**: [コーディングとテストガイド_AI-DLC版](/reference/コーディングとテストガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [ユースケース作成ガイド_AI-DLC版](/reference/ユースケース作成ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [リリース・イテレーション計画ガイド_AI-DLC版](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [開発ガイド_AI-DLC版](/reference/開発ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [AI-DLC導入ガイド](/reference/AI-DLC導入ガイド.md) を human:kakimomokuri が検証
* **Creation**: [AI-DLC 用語集](/reference/AI-DLC用語集.md) を新規作成。論文・参照実装・本プロジェクトの AI-DLC 版ガイドの用語を 6 区分で定義し、XP・Scrum との対応表と参照先を追加。`CLAUDE.md` の参照表にも登録。
* **Creation**: [開発ガイド（AI-DLC 版）](/reference/開発ガイド_AI-DLC版.md) を新規作成。XP 版の開発ライフサイクル（分析・開発・運用・構築・配置）を AI-DLC の 3 フェーズで読み替え、各活動で AI が生成し人が検証するものを定義。AI-DLC 版ガイド 4 本と XP 版ガイドの対応表を追加。あわせて `CLAUDE.md` に AI-DLC を採用する旨と守るべき 6 原則を明記し、ペルソナの参照先を開発ガイド（AI-DLC 版）に変更。
* **Creation**: [コーディングとテストガイド（AI-DLC 版）](/reference/コーディングとテストガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、TDD の三原則を AI に守らせる Bolt 開発フロー、承認ゲートの密度選択、AI 向けのアプローチ指示、ステップ計画、Red/Green/Refactor 各フェーズの人の検証観点、AI 生成コード固有の品質観点、ソース束縛レビュー、テスト戦略の水準、ガードレール化、quick-cement としての技術的負債、開発スキルの読み替え表を定義。
* **Creation**: [ユースケース作成ガイド（AI-DLC 版）](/reference/ユースケース作成ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Mob Elaboration によるユースケース作成手順、12 ステップの AI と人の分担、トレーサビリティ項目を加えたテンプレート、セマンティクス密度、AI 生成ユースケースの検証観点、ブラウンフィールドのリバースエンジニアリング、プロンプトパターン、分析スキルの読み替え表を定義。
* **Creation**: [リリース・イテレーション計画ガイド（AI-DLC 版）](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Intent → Unit → Bolt の計画階層、承認駆動の Bolt 計画、エントロピー評価による見積もり、完了 Unit 数・ゲート通過数・リードタイムによる進捗管理、リスク台帳、Bolt 終了報告テンプレート、計画スキルの読み替え表を定義。
* **Update**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を一次情報（Method Definition Paper・aidlc-workflows ユーザーガイド）で精査し拡充。10 の基本原則、成果物定義（Intent・Unit・Bolt・Domain/Logical Design・Deployment Unit）、Inception の 6 成果物、グリーンフィールド／ブラウンフィールド実践例、付録 A のプロンプトパターン、参照実装の 5 フェーズ 33 ステージ・スコープ・エージェント・承認ゲート・監査ログ、33 ステージと Skills の対応表を追加。
* **Creation**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を新規作成。AI-DLC の概要・コア原則・3 フェーズ・ベストプラクティスを整理し、XP と Skills 体系への対応表と段階的な導入ステップを定義。

## 2026-08-26
* **Verification**: [ドキュメント構成ガイド](/reference/ドキュメント構成ガイド.md) を human:kakimomokuri が検証
* **Update**: ドキュメント構成ガイドを更新。docs/review を共通からプロジェクト別カテゴリに変更（プロジェクト別は 7 カテゴリに）。
* **Creation**: ドキュメント構成ガイドを新規作成。単一企業・統合戦略・複数プロジェクトのコンセプトと apps/ との対応規約を定義。

## 2026-08-25
* **Update**: リンク切れ 53 件を修正。`grokking-concurrency` のサンプルコード参照をインラインコード表記に統一、`functional-desgin-ppp/elixir` の目次 6〜10 章を実際の章構成に合わせて書き直し、[Codex CLI MCP アプリケーション開発フロー](/reference/CodexCLIMCPアプリケーション開発フロー.md) の関連ドキュメントを実在ガイドに付け替え、未執筆の付録は「未作成」と明記。`template/まずこれを読もうリスト.md` の 10 件はコピー先基準のパスのため据え置き。
* **Migration**: `docs/` を OKF v0.2 の知識バンドルに移行。601 件のコンセプト（Article 552 件・Reference 31 件・Template 18 件）に `type`・`title`・`description`・`tags`・`generated` を付与し、ルート `index.md` に `okf_version: "0.2"` を宣言。本文は変更していない。Wiki.js 由来のフロントマターは OKF 形式に併合した。
