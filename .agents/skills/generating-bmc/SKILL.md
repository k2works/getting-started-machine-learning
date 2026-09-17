---
name: generating-bmc
description: ビジネスモデルキャンバス（BMC）の SVG 図を生成。テンプレート SVG を基に、ビジネスアーキテクチャ分析書のビジネスモデルキャンバスデータを反映した SVG ファイルを出力する。「BMC を生成したい」「ビジネスモデルキャンバスの図を作りたい」「BMC の SVG を更新したい」「ビジネスモデルキャンバスを可視化したい」といった場面で発動する。ビジネスアーキテクチャ分析が完了した後、またはビジネスモデルキャンバスの内容が更新された場合にも積極的に使用すること。
---

# ビジネスモデルキャンバス SVG 生成

ビジネスアーキテクチャ分析書（`business_architecture.md`）のビジネスモデルキャンバスセクションから、テンプレート SVG の構造を参考にした BMC の SVG 図を生成する。

## 参照ドキュメントと成果物

| 種類 | パス | 備考 |
|------|------|------|
| テンプレート | `docs/reference/images/BMC.drawio.svg` | BMC レイアウトのリファレンス（英語版、drawio 形式） |
| 入力（実プロジェクト） | `docs/strategy/business_architecture.md` | `analyzing-business-architecture` の成果物 |
| 入力（企業事例分析） | `docs/strategy/business_strategy.md` | `analyzing-business-strategy` の成果物 |
| 成果物 | `docs/strategy/BMC.svg` | 生成された BMC の SVG 図 |

### 入力ソースの選択

本スキルは 2 つの入力ソースに対応する。どちらも `### ビジネスモデルキャンバス` セクション内に PlantUML mindmap 形式で 9 ブロックのデータを持つ前提で動作する。

| 入力元スキル | 入力パス | 利用場面 |
|------------|---------|---------|
| `analyzing-business-architecture` | `docs/strategy/business_architecture.md` | 実プロジェクトのビジネスアーキテクチャ分析 |
| `analyzing-business-strategy` | `docs/strategy/business_strategy.md` | 企業事例（ケーススタディ）の戦略分析 |

両方のファイルが存在する場合は、`business_architecture.md` を優先する（実プロジェクトを事例分析より優先）。片方のみ存在する場合はそのファイルを使用する。どちらも存在しない場合は、先に `analyzing-business-architecture` または `analyzing-business-strategy` を実行するよう案内する。

## BMC の 9 ブロック

ビジネスモデルキャンバスは以下の 9 ブロックで構成される。入力ドキュメントのビジネスモデル系セクション内の mindmap データからこれらを抽出する。

### セクション見出しの認識

入力ドキュメントによって見出し表現が揺れるため、以下のいずれかのパターンにマッチするセクションを BMC セクションとして扱う：

- `### ビジネスモデルキャンバス`（推奨・canonical）
- `### ビジネスモデル`
- `### ビジネスモデル（BMC）`
- `### ビジネスモデル (BMC)`
- `#### ビジネスモデル` / `#### ビジネスモデルキャンバス`（見出しレベルが深い場合）

該当セクション内の最初の `@startmindmap` ブロックを BMC データとして解析する。ルートノード名（`* ビジネスモデル` や `* A 社ビジネスモデル` など）は事例名プレフィックスを含んでも構わない。9 ブロックの特定は「顧客 / 価値 / インフラ / 資金」の 4 分類配下のノード名で行う。

| # | ブロック | 英語名 | 配置 |
| :--- | :--- | :--- | :--- |
| 1 | 主要パートナー | Key Partners | 左端列 |
| 2 | 主要活動 | Key Activities | 左中列・上段 |
| 3 | 主要リソース | Key Resources | 左中列・下段 |
| 4 | 価値提案 | Value Propositions | 中央列 |
| 5 | 顧客関係 | Customer Relationships | 右中列・上段 |
| 6 | チャネル | Channels | 右中列・下段 |
| 7 | 顧客セグメント | Customer Segments | 右端列 |
| 8 | コスト構造 | Cost Structure | 下段・左半分 |
| 9 | 収益源 | Revenue Streams | 下段・右半分 |

## SVG レイアウト仕様

### 全体構造

- **サイズ**: 2253 x 1601 px（テンプレート準拠）
- **上段**: 5 列構成（y=100 〜 y=1000）
  - 列 2（主要活動 / 主要リソース）と列 4（顧客関係 / チャネル）は上下 2 分割
- **下段**: 2 列構成（y=1000 〜 y=1580）
  - 左: コスト構造、右: 収益源

### スタイル

- **フォント**: "Helvetica Neue", Arial, "Hiragino Kaku Gothic ProN", "Hiragino Sans", Meiryo, sans-serif
- **背景色**: `#FAFAFA`
- **付箋カード**: `fill: #FFF9C4; stroke: #E0D654`（黄色系）
- **ヘッダー**: 18px bold + アイコン絵文字
- **項目テキスト**: 12-13px
- **サブテキスト（補足）**: 10-11px、`fill: #888` または `#666`

### 各ブロックのアイコン

| ブロック | アイコン |
| :--- | :--- |
| 主要パートナー | 🤝 |
| 主要活動 | ⚙ |
| 主要リソース | 🏭 |
| 価値提案 | 🎁 |
| 顧客関係 | ❤ |
| チャネル | 🚚 |
| 顧客セグメント | 👥 |
| コスト構造 | 💰 |
| 収益源 | 💵 |

## 生成の進め方

### 新規生成

1. 入力ソースを選択する（`business_architecture.md` または `business_strategy.md`）。両方ある場合は `business_architecture.md` を優先
2. 選択した入力ファイルの `### ビジネスモデルキャンバス` セクションを読む
3. mindmap データから 9 ブロックの項目を抽出する
4. テンプレート SVG（`docs/reference/images/BMC.drawio.svg`）のレイアウト構造を参考にする
5. 本スキルの SVG レイアウト仕様に従い、抽出したデータで SVG を生成する
6. `docs/strategy/BMC.svg` に出力する
7. `operating-docs --update` を実行して docs/index.md、mkdocs.yml、各ディレクトリの index.md を更新する

### 更新時の再生成

1. 入力ソース（`business_architecture.md` または `business_strategy.md`）のビジネスモデルキャンバスの変更内容を確認する
2. 既存の `docs/strategy/BMC.svg` を読み、変更が必要な箇所を特定する
3. SVG を更新する（全体再生成でも差分編集でもよい）
4. 必要に応じて `operating-docs --update` を実行する

### SVG 生成時の注意事項

- テンプレートの drawio SVG は複雑なエンコーディングを含むため、そのまま編集するのではなくレイアウト構造のみを参考にする
- SVG は手書きの clean な XML として生成する（drawio メタデータは不要）
- 日本語テキストが正しく表示されるよう、UTF-8 エンコーディングと日本語フォントファミリーを指定する
- 各ブロック内の項目は付箋カード風の矩形（`<rect>` + `<text>`）で表現する
- 項目数が多い場合は付箋カードのサイズやレイアウトを調整して収まるようにする
- フッターに "Based on the Business Model Canvas by Strategyzer.com (CC BY-SA 3.0)" のクレジットを含める

## 関連スキル

- `analyzing-business-architecture` : ビジネスアーキテクチャ分析（実プロジェクト向けの入力データを作成）
- `analyzing-business-strategy` : 企業事例分析（事例ベースの戦略ドキュメントから BMC セクションを提供）
- `operating-docs` : ドキュメント管理・インデックス更新
