---
name: closing-iteration
description: イテレーション（IT）を漏れなくクローズするための手順を順序立てて案内する。レビュー→品質ゲート→進捗反映→ふりかえり→完了報告書→GitHub 同期→ドキュメント同期の 7 ステップ（UI に変更があった場合はユーザーマニュアル更新を含む）をチェックリストとして提示し、抜けを防ぐ。「イテレーションをクローズしたい」「IT2 を締めたい」「イテレーションの締め作業をしたい」「開発が終わったので締めの作業をしたい」「ふりかえりから報告書・同期まで一通りやりたい」といった場面で必ず発動する。実装が終わった直後・イテレーション末・「次のイテレーションに進む前に」といった局面でも積極的に提案する。クローズ作業は工程が多く順序依存があるため、抜けやすい完了報告書・GitHub 同期・ドキュメント同期を含めて標準化することで、イテレーションの区切りを確実にする。
---

# イテレーションクローズ

イテレーション（IT）の開発が一段落したあと、成果を確定し次イテレーションへ引き継ぐための締め作業を、順序立てて漏れなく実施する。

クローズ作業は工程が多く（レビュー・品質確認・ふりかえり・報告書・GitHub・ドキュメント）、かつ順序に依存がある。ひとつでも抜けると「実装は終わったのに Issue が Open のまま」「報告書がなく実績が追えない」「ドキュメントインデックスと実体が乖離」といった負債が残る。本スキルはその抜けを防ぐためのチェックリストとオーケストレーションを提供する。

## クローズの前提

以下が揃っていることを確認してから着手する。揃っていなければ、まず開発（`orchestrating-development`）に戻る。

- 対象イテレーションの全ユーザーストーリーが実装完了している
- `docs/development/iteration_plan-N.md` が存在する（クローズ対象の計画）

## クローズフロー（7 ステップ）

順序には理由がある。レビュー指摘を反映してから品質ゲートを固め（ローカル＋CI の両方で緑を確認）、実績が確定してからふりかえり・報告書を書き、ドキュメントが整ってから GitHub とインデックスに同期する。上から順に実施する。

> ステップ番号は 7 だが、品質ゲート（ステップ 2）にはローカル検証（2）・CI 確認（2.5）・SonarQube 品質ゲート（2.6）が含まれる。ローカル緑だけでは不十分で、CI が緑かつ SonarQube の Quality Gate が PASS であって初めて品質ゲートを通過とみなす。また、当該イテレーションで UI・操作手順に変更があった場合はユーザーマニュアル更新（ステップ 6.5）が加わる。

### ステップ 1: マルチパースペクティブレビュー（`developing-review`）

実装成果物を 5 つの XP 視点（programmer / tester / architect / technical-writer / user-representative）で並列レビューし、統合レポートを `docs/review/<対象名>_review_<YYYYMMDD>.md` に保存する。

- **高優先度の指摘は原則クローズ前に対応する**（受入基準のテスト漏れ・設計整合・セキュリティなど）。対応できないものは「許容 / 保留 / 次イテレーション対応」の方針を明記する
- レビュー→改善のループを 1 回まわしてからクローズに進むことで、負債を次イテレーションに持ち越さない
- `docs/review/index.md` にレビュー行を追加する

### ステップ 2: 品質ゲート

「変更を楽に安全にできる」状態を数値と自動チェックで確認する。イテレーションの成果が緑であることを、この時点で確定させる。

- 全テストが green（単体・統合・HTTP フロー / E2E）
- Lint・フォーマットがクリーン（このプロジェクトでは `cargo clippy --workspace --all-targets -- -D warnings` と `cargo fmt --check`）
- カバレッジを計測し目標との差分を把握する（静的解析の合否判定はステップ 2.6 の SonarQube 品質ゲートで確定する）
- ゲートを通らない場合はクローズを止め、修正してから再度ここに戻る
- **CI と同じツールチェーンで検証する**: ローカルの clippy が古いと CI（`.github/workflows/rust-ci.yml` は `dtolnay/rust-toolchain@stable` を使用）だけが新 lint で落ちる。ローカルの stable が CI より古い可能性があるため、`rustup update stable` してから `cargo +stable clippy --workspace --all-targets -- -D warnings` / `cargo +stable fmt --check` を実行し、ツールチェーンの乖離による「ローカル緑・CI 赤」を防ぐ

### ステップ 2.5: CI 確認（`gh run`）

ローカルのゲートが緑でも、CI で落ちていればイテレーションは緑ではない。**このステップは忘れやすい**（ローカルで達成感が出た後、push 後の CI 結果を見ないまま次に進みやすい）。

- 対象ブランチの最新 CI 結果を確認する（`gh run list --branch <branch> --limit 5`）。直近が `failure` なら `gh run view <run-id> --log-failed` で原因を特定し、修正してステップ 2 に戻る
- push 前にクローズ作業を進める場合も、過去コミットで CI が赤のまま放置されていないかを必ず確認する（赤を「完了」と宣言しない）
- 修正後は再 push し、CI が緑に戻ることを確認してからクローズを確定する

### ステップ 2.6: SonarQube 品質ゲート（`operating-qt`）

テスト・Lint が緑でも、静的解析の品質基準（Bug・Vulnerability・Code Smell・重複・カバレッジ）を満たしていなければクローズしない。`operating-qt` で SonarQube スキャンを実行し、**Quality Gate が PASS であること**を確定させる。

- **前提**: ローカル SonarQube が起動済み（`npx gulp sonar-local:status`）で `.env` に有効な `SONAR_TOKEN` が設定されていること。未起動なら `npx gulp sonar-local:setup`
- **カバレッジ連携**: スキャン前に対象言語のカバレッジレポートを生成する（例: Go は `go test -coverprofile=coverage.out -covermode=atomic ./...`、`sonar-project.properties` の `sonar.*.coverage.reportPaths` で連携）
- **実行**: `npx gulp sonar-local:scan` → `npx gulp sonar-local:gate`（または一連の `npx gulp sonar-local:check`）
- **合格基準**（[テスト戦略](../../../docs/design/test_strategy.md) の Quality Gate に準拠）:
  - Quality Gate: **PASS**
  - Bug: **0** / Vulnerability: **0**
  - 重複率: **3% 未満**
  - Code Smell: 可能な限り **0**（残す場合は方針を明記）
  - カバレッジ: 新規コード 80% 以上（ドメイン層は 90% を目標）
- **不合格なら**: `npx gulp sonar-local:issues` で指摘を特定し、修正してステップ 2 に戻る。ゲートが PASS になるまでクローズを止める

### ステップ 3: 進捗反映（`tracking-progress --update`）

実績データ（git ログ・テスト結果・カバレッジ）を集計し、計画ドキュメントを実績で更新する。

- `docs/development/release_plan.md`: 進捗状況テーブル（実績 SP・達成率・状態）、バーンダウン実績値
- `docs/development/iteration_plan-N.md`: 成功基準・DoD のチェック、タスク状態、進捗率、実績注記
- **未達・スコープ調整は正直に記録する**（「完了扱いだが受入基準の一部は次 IT」など）。ごまかすと後続の計画精度が落ちる

### ステップ 4: ふりかえり（`planning-releases --retrospective`）

KPT（Keep / Problem / Try）で振り返り、`docs/development/retrospective-N.md` に保存する。

- **Keep**: 技術的・プロセス的にうまくいったこと
- **Problem**: 未完了・見積もりのズレ・技術的負債の発生源
- **Try**: 具体的な改善アクション（担当・期限・期待効果付き）。次イテレーション計画に反映できる粒度にする
- 次イテレーションへの引き継ぎ（持ち越し事項）を明記する

### ステップ 5: 完了報告書（`planning-releases --report` → `creating-iteration-report`）

`docs/development/iteration_report-N.md` を作成する。**このステップは工程の後半にあり最も忘れやすい**。エグゼクティブサマリー・達成状況・技術的成果・品質指標・レビュー結果・課題と残作業・次イテレーション引き継ぎを、実績データから記述する。

### ステップ 6: GitHub 同期（`syncing-github-project --sync`）

`release_plan.md` を正として GitHub に反映する。

- 完了ストーリーの Issue をクローズ（完了コメント付き）
- GitHub Project のカスタムフィールド（Status → Done 等）を更新
- マイルストーンの open/closed 件数を確認し、リリース進捗を把握する

### ステップ 6.5: ユーザーマニュアル更新（`creating-manual`）

当該イテレーションで **UI・画面項目・操作手順に変更があった場合は必須**。変更がなければスキップしてよいが、その判断根拠（「本 IT はバックエンドのみの変更」など）を完了報告書に一行残す。マニュアルは業務担当者が実際の画面の前で読む唯一の手引きであり、実装とずれた瞬間に問い合わせと誤操作に変わる。

- 変更された画面に対応する章・節の「画面の説明」の項目表と「操作手順」を実装と突き合わせる
- 画面キャプチャを再生成する（手動で PNG を差し替えない。必ず生成 spec 経由で撮る）
- 章・節を追加・削除した場合は、マニュアルの索引（`docs/manual/index.md` と全体構成表）を合わせる。ナビゲーション（`mkdocs.yml`）はステップ 7 でまとめて同期する
- 新機能をフィーチャートグルの配下で提供している場合は、本番で有効化されてから記載する

ステップ 7 の前に置く理由は、マニュアルの章構成が確定していないとドキュメントインデックスとナビゲーションを一度で正しく同期できないため。

### ステップ 7: ドキュメント同期（`operating-docs --update`）

ドキュメントインデックスを成果物の実体に合わせる。

- `docs/development/index.md`: イテレーション行にふりかえり・完了報告書のリンクを追加、状態を「完了」に
- `docs/index.md`: 開発の状態・レビュー件数・ADR 件数などのサマリーを更新
- `mkdocs.yml`: ふりかえり・完了報告書・新規レビュー・新規 ADR・マニュアルの新規章をナビゲーションに追加
- 追加行のインデントが既存ナビと一致しているか確認する

### ステップ 7.5: OKF 適用（`apply-okf`）

`docs/` が OKF バンドルなら、クローズで生まれた・更新された文書に規約を適用する。クローズは 1 イテレーションで最も多くの文書が動く局面（計画の実績更新・ふりかえり・報告書・レビューレポート・ADR・マニュアル）なので、`--changed` でまとめて掛ける。

```bash
python .claude/skills/apply-okf/scripts/okf_apply.py apply docs --changed --by <agent>
gulp okf:check
```

- `retrospective-N.md`・`iteration_report-N.md`・レビューレポートは新規 → `draft` で登録。ユーザーが報告書を承認したら `verify --by human:<id>`
- `iteration_plan-N.md` は実績で本文が変わるので `generated` が更新される。計画自体は `stable` のまま残す（後継は報告書であり、計画は廃止ではない）
- 置き換えた ADR や旧手順があれば `deprecate --replaced-by` で後継に繋ぐ
- `gulp okf:check` が ERROR 0 であることを確認してからコミットする

### （任意）ステップ 8: ジャーナル（`creating-journal`）

長いセッションや区切りとして、判断・詰まり・学びを `docs/journal/YYYYMMDD.md` に残すと、コミットログに載らない意思決定の経緯を追跡できる。

## クローズ完了チェックリスト

すべて満たしたらイテレーションはクローズ済みとみなす。

- [ ] `developing-review` 実施・レポート保存・高優先度対応（または方針明記）
- [ ] 全テスト green・Lint/フォーマットクリーン（CI と同じツールチェーンで確認）・カバレッジ把握
- [ ] **CI が緑**（`gh run list` で対象ブランチの最新実行が success・赤なら修正して再確認）
- [ ] **SonarQube Quality Gate が PASS**（Bug 0・Vulnerability 0・重複 3% 未満・Code Smell 方針明記・カバレッジ目標達成）
- [ ] `release_plan.md` / `iteration_plan-N.md` を実績で更新
- [ ] `retrospective-N.md`（KPT）作成
- [ ] `iteration_report-N.md`（完了報告書）作成
- [ ] GitHub Issue クローズ・Project Status 更新
- [ ] **ユーザーマニュアルが実装と一致**（UI 変更ありなら本文・キャプチャを更新／変更なしならその旨を報告書に記録）
- [ ] `docs/index.md` / `docs/development/index.md` / `mkdocs.yml` 同期
- [ ] OKF バンドルなら `apply-okf` を変更分に適用・報告書承認後に `verify`・`gulp okf:check` ERROR 0
- [ ] 各ステップの成果を意味のある単位でコミット（`git-commit`）

## コミットの区切り

各ステップの成果は個別にコミットする（`git-commit`）。ドキュメント系は `docs(...)` タイプで、対象と目的が分かるメッセージにする。1 コミット 1 目的を守ることで、後から特定のクローズ作業だけを追跡・ロールバックできる。

## 注意事項

- **順序を守る**: レビュー指摘の反映前に報告書を書くと、報告書が実態とずれる。品質ゲートを通す前に GitHub をクローズすると、緑でない成果を「完了」と宣言してしまう
- **抜けやすいのはステップ 5-7**（報告書・GitHub・マニュアル・ドキュメント）。実装とレビューで達成感が出た後の事務作業のため後回しにされやすい。チェックリストで機械的に確認する
- **UI を変えたらマニュアルも成果物**（ステップ 6.5）。画面を変えたのにマニュアルを据え置くと、業務担当者は古い手順で操作して詰まる。イテレーションをまたいで放置するほど差分が積み上がり追従コストが跳ね上がるため、その IT のうちに合わせる
- **ローカル緑を CI 緑と混同しない**（ステップ 2.5）。ローカルのツールチェーンが CI より古いと、新しい lint がローカルをすり抜けて CI だけ赤になる。クローズ確定前に `gh run` で CI の実結果を必ず確認し、赤なら緑に戻す
- **品質ゲートは「テスト緑」で終わりにしない**（ステップ 2.6）。テストと Lint が緑でも SonarQube の Quality Gate（Bug・脆弱性・重複・カバレッジ・Code Smell）が赤ならクローズしない。スキャン→ゲート PASS までを品質ゲートの完了条件とする
- **正直さを優先する**: 未達・スコープ調整・DoD の未チェック項目は隠さず記録する。次イテレーションの計画精度とベロシティの信頼性はこの正直さに依存する

## 関連スキル

- `orchestrating-project` — 計画・進捗管理フェーズ全体のオーケストレーション（本スキルはその「イテレーション終了時」を詳細化したもの）
- `developing-review` — 開発成果物のマルチパースペクティブレビュー（ステップ 1）
- `operating-qt` — SonarQube による品質ゲート確認（ステップ 2）
- `tracking-progress` — 進捗分析・ドキュメント更新（ステップ 3）
- `planning-releases` — ふりかえり・完了報告書（ステップ 4-5）
- `creating-iteration-report` — 完了報告書の作成（ステップ 5 の委譲先）
- `syncing-github-project` — GitHub 同期（ステップ 6）
- `creating-manual` — ユーザーマニュアルの更新・画面キャプチャ再生成（ステップ 6.5）
- `operating-docs` — ドキュメントインデックス更新・Lint（ステップ 7）
- `apply-okf` — クローズで動いた文書への OKF 規約適用（ステップ 7.5）
- `creating-journal` — セッション記録（任意ステップ 8）
- `git-commit` — 各ステップ成果のコミット
