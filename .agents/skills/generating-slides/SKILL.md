---
name: generating-slides
description: インセプションデッキの Markdown から PowerPoint スライド（.pptx）を自動生成。pptxgenjs を使用し、テンプレート準拠の 12 枚構成プレゼンテーションを出力する。「スライドを生成したい」「インセプションデッキの pptx を作りたい」「プレゼン資料を出力したい」「スライドを更新したい」といった場面で発動する。Markdown から自動生成することで、内容更新のたびに手動でスライドを作り直す手間を排除する。
---

# スライド生成

インセプションデッキの Markdown ドキュメントから pptxgenjs を使用して PowerPoint スライド（.pptx）を生成する。テンプレートのスライド構成・テーマに準拠した 12 枚構成のプレゼンテーションを出力する。

## 参照ドキュメントと成果物

| 種類 | パス | 備考 |
|------|------|------|
| テンプレート | @docs/template/インセプションデッキ.pptx | スライド構成のリファレンス（15 枚、4:3） |
| 生成スクリプト | @.claude/scripts/generate-inception-deck.mjs | 内容の編集は可能 |
| 入力 | @docs/strategy/inception-deck.md | `analyzing-inception-deck` の成果物 |
| 入力（補足） | @docs/strategy/business_architecture.md | ビジネスアーキテクチャ分析書 |
| 成果物 | `docs/strategy/slide/xxxxx_v0.1.0.pptx` | 生成された PowerPoint スライド |

## スライド構成（12 枚）

| # | スライドタイトル | SLIDE_DATA キー | データソース |
| :--- | :--- | :--- | :--- |
| 1 | タイトル | `titleSlide` | プロジェクト基本情報 |
| 2 | 我われはなぜここにいるのか | `whyAreWeHere` | なぜやるのか？ |
| 3 | エレベーターピッチ | `elevatorPitch` | どんなビジョンなのか？ |
| 4 | どんな価値をもたらすのか？ | `values` | どんな価値をもたらすのか？ |
| 5 | やらないことリスト | `scope` | スコープの範囲はどこか？ |
| 6 | プロジェクトコミュニティ | `stakeholders` | 主なステークホルダーは？ |
| 7 | 技術的な解決策の概要 | `technicalSolution` | 基本的な解決策 |
| 8 | 夜も眠れなくなるような問題 | `risks` | 主なリスクは何か？ |
| 9 | 俺たちの "A チーム" | `team` | どのくらい作業があり費用はいくらか？ |
| 10 | 期間を見極める | `timeline` | 初回リリースはいつか？ |
| 11 | トレードオフ・スライダー | `tradeoffs` | トレードオフにどう向き合うか？ |
| 12 | 初回のリリースに必要なもの | `initialRelease` | MVP スコープ・リリース戦略 |

## テーマ設定

テンプレートから抽出したテーマに準拠する。

- **フォント**: Yu Gothic（游ゴシック）— Windows / Mac 両対応
- **テーマカラー**: ダークブルー `#333399`、ティール `#009999`、ライトティール `#BBE0E3`
- **スライドサイズ**: 4:3（10" x 7.5"）

## 生成の進め方

### 新規生成

1. `npm install pptxgenjs`（初回のみ）
2. `docs/strategy/inception-deck.md` の内容を確認する
3. `.claude/scripts/generate-inception-deck.mjs` の `SLIDE_DATA` をプロジェクト固有の内容に書き換える
4. `node .claude/scripts/generate-inception-deck.mjs` を実行する
5. `docs/strategy/slide/` に .pptx が生成されたことを確認する

### 更新時の再生成

1. `docs/strategy/inception-deck.md` の更新内容を確認する
2. `.claude/scripts/generate-inception-deck.mjs` の `SLIDE_DATA` を更新する
3. `node .claude/scripts/generate-inception-deck.mjs` を実行する

## スクリプト構成

| セクション | 内容 | 編集対象 |
| :--- | :--- | :--- |
| `SLIDE_DATA` | プロジェクト固有のデータ（テキスト・数値） | 毎回更新 |
| テーマ設定 | カラー・フォント定義 | 通常変更不要 |
| ヘルパー関数 | スライド部品の描画ロジック | 通常変更不要 |
| スライド生成 | `SLIDE_DATA` を読み取り 12 枚を生成 | 通常変更不要 |

## 途中から再開

スライドの一部を修正したい場合は、`SLIDE_DATA` の該当キーのみを更新して再生成する。

**Example:**

```
ユーザー: 「エレベーターピッチの内容が変わった。スライドを更新したい」
回答: inception-deck.md の更新内容を確認し、
      SLIDE_DATA.elevatorPitch のみを更新して再生成する。
      出力ファイル名の meta.outputFileName のバージョンも更新する。
```

## 注意事項

- `docs/strategy/inception-deck.md` が作成済みであること（`analyzing-inception-deck` を先に実行する）
- テンプレート `docs/template/インセプションデッキ.pptx` は編集しない
- 日本語フォントは Yu Gothic を使用する（Gill Sans 等の欧文フォントは文字化けする）
- 出力ファイル名は `SLIDE_DATA.meta.outputFileName` で指定する。`meta.title` と同時に更新する
- 生成後は PowerPoint で開いてレイアウトを目視確認し、必要に応じてスクリプトのレイアウトパラメータを微調整する

## 関連スキル

- `analyzing-inception-deck` — 入力となるインセプションデッキの作成
- `analyzing-business-architecture` — ビジネスアーキテクチャ分析（補足情報の参照元）
