---
name: apply-docs-structure
description: ドキュメント構成ガイド（単一企業・統合戦略・複数プロジェクト）に従って docs/ と apps/ の配置を適用する日常運用スキル。文書をどのディレクトリに置くか判断する、新しいプロジェクトのディレクトリ一式（apps/<project>/ とプロジェクト別 7 カテゴリの <project>/index.md）を作る、既存構成がガイドに適合しているか検証する、といった操作を行う。「この設計書はどこに置く？」「新しいプロジェクトを始めるのでディレクトリを用意して」「docs の構成が正しいか確認して」「ドキュメントをプロジェクト別に整理して」「apps とドキュメントの対応を確認して」といった場面で発動する。analyzing-*・planning-releases・creating-adr・creating-journal・operating-setup などが docs/ 配下に文書を新規作成する直前には、明示的な依頼が無くても配置先の判断に積極的に使うこと。フロントマター・index/log の OKF 規約は apply-okf、既存文書の一括移行は migrating-okf の担当。
---

# ドキュメント構成適用

`docs/` と `apps/` の配置規約を**日常の変更**に適用する。規約の正は `docs/reference/ドキュメント構成ガイド.md`。本スキルは「文書を 1 つ置く／プロジェクトを 1 つ増やす／構成を確かめる」たびに呼ぶ判断と仕上げの工程。同じディレクトリに `PROJECT.md` があれば必ず読む（バンドルの場所・プロジェクト一覧・カテゴリの追加定義はそこにある）。

## なぜ毎回適用するのか

構成の価値は「プロジェクト識別子で docs と apps を双方向に辿れる」ことにある。1 つでもカテゴリ直下に実ドキュメントが直置きされたり、docs と apps で識別子がずれたりすると、トレーサビリティが壊れ、後から誰も直せない「なんとなくの配置」が増殖する。規約は移行時に一度守れば済むものではなく、置くたびに守って初めて意味を持つ。

## 構成の要点

| 区分 | ディレクトリ | 規約 |
| :--- | :--- | :--- |
| 単一（企業共通） | `strategy/` | 統合戦略。プロジェクト別サブディレクトリを**作らない** |
| 単一（共有リソース） | `reference/` `template/` `article/` `assets/` | プロジェクト別サブディレクトリを作らない |
| プロジェクト別 | `requirements/` `design/` `development/` `operation/` `adr/` `journal/` `review/` | `<category>/<project>/` に置く。カテゴリ直下は索引 `index.md` のみ |
| アプリケーション | `apps/<project>/` | docs 側と**同一のケバブケース識別子** |

## 3 つの操作

| 操作 | いつ | 何をするか |
| :--- | :--- | :--- |
| **place** | 文書を新規作成する直前 | 配置判断フローで置き場所を決める。プロジェクト別カテゴリなら `<category>/<project>/` 配下に置き、プロジェクトの `index.md` にエントリを追加 |
| **add-project** | 新しいプロジェクトを始める | `apps/<project>/` とプロジェクト別 7 カテゴリの `<project>/index.md` を一括作成し、各カテゴリ索引に登録 |
| **check** | 上記の後、コミット前 | 構成がガイドに適合しているか検証し ERROR 0 を確認 |

add-project と check は同梱スクリプトで行う。手で 7 カテゴリを掘らない（作成漏れ・識別子の揺れ・索引の登録漏れを起こしやすい）。

```bash
S=.claude/skills/apply-docs-structure/scripts/docs_structure.py
python $S add-project inventory-service                # docs/ と apps/ に一式作成
python $S add-project sales-portal --skip apps         # apps を別管理している場合
python $S check                                        # 検証（ERROR/WARN を列挙）
python $S check --check                                # ERROR があれば exit 1（CI・コミット前）
```

## 判断すべきこと（スクリプトは判断しない）

### place：文書をどこに置くか

1. **戦略文書か？**（企業分析・経営戦略・ビジネスアーキテクチャ・インセプションデッキ）→ `docs/strategy/` 直下。プロジェクト名でサブディレクトリを掘りたくなったら、それは戦略ではなくプロジェクトの要件か ADR。プロジェクト別カテゴリ側に置く
2. **特定プロジェクトの成果物か？** → 該当カテゴリの `<category>/<project>/` に置く。プロジェクトのディレクトリがまだ無ければ先に add-project
3. **どのプロジェクトにも属さない共有知識か？**（ガイドライン・テンプレート・記事）→ `reference/` `template/` `article/`
4. どれにも当てはまらない・複数プロジェクトにまたがる → 安易に新カテゴリを作らず、ガイドの更新が必要かをユーザーに確認する

### add-project：識別子

英小文字とハイフンのケバブケース（例：`inventory-service`）。docs と apps で完全一致させ、一度決めたら変更しない。ユーザーの呼び名が日本語・大文字混じりなら、識別子への変換案を提示して合意してから作成する。

### check の結果への対応

- **ERROR**（識別子が規約違反、docs と apps の不一致）→ その場で直す。放置してコミットしない
- **WARN**（カテゴリ直下への直置き、`index.md` 欠落、apps 側に対応ディレクトリ無し）→ 移行途中の状態でも出る。直せるものは直し、意図的な据え置きは理由をユーザーに報告する

## 他スキルとの連携

`docs/` に文書を書くスキルの**最初**に本スキル（place）、**最後**に `apply-okf` を組み込む。典型的な流れ：

1. `analyzing-data-model` が inventory-service のデータモデル設計書を書こうとする
2. place 判断 → `docs/design/inventory-service/データモデル設計書.md` に決定（ディレクトリが無ければ `add-project inventory-service`）
3. 文書を作成し、`docs/design/inventory-service/index.md` にエントリを追加
4. `apply-okf` でフロントマター付与・`log.md` 記録
5. `python $S check --check` と `gulp okf:check` で ERROR 0 を確認してコミット

構成（どこに置くか）は本スキル、来歴・信頼（フロントマター・verified・log）は `apply-okf`。両者は重ならない。

## 完了報告

```markdown
## ドキュメント構成適用結果

- 操作: place / add-project / check
- 対象: <プロジェクト識別子・配置したファイル>
- 作成・更新: <ディレクトリ・index.md の一覧>
- 検証: ERROR 0 / WARN <N>
- 要確認: <識別子の変換案、意図的に据え置いた WARN、など>
```

## 関連スキル

- `apply-okf` — フロントマター・index/log・適合性検証。文書を置いた直後に呼ぶ
- `migrating-okf` — 既存文書の一括移行
- `analyzing-*` / `planning-releases` / `creating-adr` / `creating-journal` — 文書を生む側。書く直前に本スキルで配置を判断する
- `git-commit` — 検証後のコミット
