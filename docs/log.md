# Docs Update Log

## 2026-09-23
* **Creation**: Elixir 版の全 15 章（[トップ](/article/getting-start-ml/elixir/index.md)・第 1〜15 章）と [ADR 012](/adr/012-elixir-ml-libraries.md) を新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Elixir 版執筆計画・確認した事実・B60〜B64 の完了を反映し、ADR 012 を承認済み（stable）にした。Scholar 0.4.2 に決定木・ランダムフォレスト・ラッソが無いため、これらは自作が最終実装になった（置き換えの範囲はシリーズで最も狭い）。乱数は `java.util.Random` と同じ線形合同法を自作し、第 2〜14 章の数値が Java 版・Scala 版・Clojure 版と一致した。`ops/nix/environments/elixir/` の環境で `apps:check:elixir` と Elixir CI を整え、`.gitattributes` に `apps/elixir/**` の改行の指定を足した。Elixir 版が完了し、シリーズは 12 言語になった。
* **Creation**: Clojure 版の全 15 章（[トップ](/article/getting-start-ml/clojure/index.md)・第 1〜15 章）と [ADR 011](/adr/011-clojure-ml-libraries.md) を新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Clojure 版執筆計画・確認した事実・B55〜B59 の完了を反映し、ADR 011 を承認済み（stable）にした。機械学習は Smile 3.1.1 が GPL-3.0 だったため Java 版・Scala 版と同じ Tribuo 4.3.2 を採用し、第 2〜15 章の数値が Java 版・Scala 版と一致することを確かめた。Nix の Clojure 環境に clj-kondo と cljfmt を追加し、`.gitattributes` に `apps/clojure/**` の改行の指定を足した。Clojure 版が完了し、シリーズは 11 言語になった。

## 2026-09-22
* **Creation**: Ruby 版の第 2〜15 章を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md)・[Ruby 版トップ](/article/getting-start-ml/ruby/index.md)・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B51〜B54 の完了を、[ADR 010](/adr/010-ruby-ml-libraries.md) に各章で確かめた Rumale・Numo・Sinatra の振る舞いを反映し、ADR 010 を承認済み（stable）にした。Nix の Ruby の環境で `RUBYLIB` を外し、`bundle exec` が `Gemfile.lock` の版を読むようにした。Ruby 版が完了し、シリーズは 10 言語になった。
* **Creation**: Ruby 版の[トップ](/article/getting-start-ml/ruby/index.md)・[第 1 章](/article/getting-start-ml/ruby/01-machine-learning-and-first-test.md)・[ADR 010](/adr/010-ruby-ml-libraries.md) を新規作成し、シリーズ索引と nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Ruby 版執筆計画・確認した事実・B50 の完了を反映。
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に第 3 波（Ruby・PHP・Elixir・Clojure・Haskell）の執筆計画を追加し承認。第 2 波の実績、言語の順番（Ruby → Clojure → Elixir → PHP → Haskell）、共通の方針、確認すべき事実、Bolt 計画（B50〜B75）、第 3 波のリスクを定義。

## 2026-09-21
* **Update**: [多言語統合解説](/article/getting-start-ml/integration/index.md) の索引と全 5 章を 9 言語に拡張（B49）。乱数の節を「9 言語で 7 通り、同じ乱数生成器なら数値も一致する」構造に書き直し、「決定的でないライブラリ」の節を新設。[執筆計画](/article/getting-start-ml/outline.md) に B49 の完了を記録し、第 2 波のすべての Bolt（B24〜B49）が完了した。
* **Creation**: Rust 版の第 2〜15 章を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md)・[Rust 版トップ](/article/getting-start-ml/rust/index.md)・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B45〜B48 の完了を、[ADR 009](/adr/009-rust-ml-libraries.md) に各章で確かめた linfa（決定木の既定値・非決定性・標準化・正則化の強さ・PCA・K-means の RNG）と axum の振る舞いを反映。ADR 009 を承認済み（stable）にした。第 2 波（Java・C#・Scala・Go・Rust）が完了し、シリーズは 9 言語になった。
* **Update**: mkdocs で LaTeX の数式を表示できるようにした（`pymdownx.arithmatex` の generic モードと MathJax 3）。

## 2026-09-20
* **Creation**: Rust 版の[トップ](/article/getting-start-ml/rust/index.md)・[第 1 章](/article/getting-start-ml/rust/01-machine-learning-and-first-test.md)・[ADR 009](/adr/009-rust-ml-libraries.md) を新規作成し、シリーズ索引と nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Rust 版執筆計画と B44 の完了を反映。linfa 0.8.1 の対応範囲を確かめた結果、Rust 版の対比の軸を Java 版・C# 版（ライブラリが揃った静的型付け言語）に差し替えた。
* **Creation**: Go 版の第 2〜15 章を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md)・[Go 版トップ](/article/getting-start-ml/go/index.md)・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B40〜B43 の完了を、[ADR 008](/adr/008-go-ml-libraries.md) に各章で確かめた gonum（`stat.ROC`・`stat.PC`・`stat.StdDev`）と `net/http`・`encoding/gob` の振る舞いを反映。ADR 008 を承認済み（stable）にした。

## 2026-09-19
* **Creation**: Go 版の[トップ](/article/getting-start-ml/go/index.md)・[第 1 章](/article/getting-start-ml/go/01-machine-learning-and-first-test.md)・[ADR 008](/adr/008-go-ml-libraries.md) を新規作成し、シリーズ索引と nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Go 版執筆計画と B39 の完了を反映。
* **Creation**: Scala 版の全 15 章とトップを新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B34〜B38 の完了を、[ADR 007](/adr/007-scala-ml-libraries.md) に各章で確かめた Tribuo・http4s の振る舞いを反映。
* **Creation**: C# 版の第 2〜15 章を新規作成し、シリーズ索引・C# 版トップ・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B29〜B33 の完了を、[ADR 006](/adr/006-csharp-ml-libraries.md) に各章で確かめた ML.NET・ASP.NET Core の振る舞いを反映。
* **Creation**: C# 版の[トップ](/article/getting-start-ml/csharp/index.md)・[第 1 章](/article/getting-start-ml/csharp/01-machine-learning-and-first-test.md)・[ADR 006](/adr/006-csharp-ml-libraries.md) を新規作成し、シリーズ索引と nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に C# 版執筆計画と B29 の完了を反映。
* **Creation**: Java 版の第 2〜15 章を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md)・[Java 版トップ](/article/getting-start-ml/java/index.md)・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B25〜B28 の完了と Java 版の章別の焦点を反映し、[ADR 005](/adr/005-java-ml-libraries.md) に各章で確かめた Tribuo・Javalin の振る舞いを追記。
* **Creation**: [Java 版トップ](/article/getting-start-ml/java/index.md)・[第 1 章](/article/getting-start-ml/java/01-machine-learning-and-first-test.md)・[ADR 005](/adr/005-java-ml-libraries.md) を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md) と nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に B24 の完了を反映し、[執筆ワークフロー](/article/getting-start-ml/workflow.md) の同期チェックリストに BOM の文字の混入の検査を追加。
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に Java 版執筆計画と B24 のステップ計画を追加し、承認を記録。
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に第 2 波（Java・C#・Scala・Go・Rust）の執筆計画と Bolt 計画（B24〜B49）を追加し、承認を記録。
* **Creation**: [多言語統合解説](/article/getting-start-ml/integration/index.md)（第 1〜5 章）を新規作成し、[シリーズ索引](/article/getting-start-ml/index.md)・[執筆計画](/article/getting-start-ml/outline.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) に B23 の完了を反映。[ADR 004](/adr/004-fsharp-ml-libraries.md) に `PCA.compute` の癖を追記。
* **Update**: F# 版 B21・B22 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 004](/adr/004-fsharp-ml-libraries.md) に B21・B22 で確かめたことを追記。F# 版の全 15 章がそろった。
* **Creation**: [F# 第 15 章](/article/getting-start-ml/fsharp/15-machine-learning-api-and-module-design.md) を新規作成。
* **Creation**: [F# 第 14 章](/article/getting-start-ml/fsharp/14-k-means-clustering.md) を新規作成。
* **Creation**: [F# 第 13 章](/article/getting-start-ml/fsharp/13-principal-component-analysis.md) を新規作成。
* **Creation**: [F# 第 12 章](/article/getting-start-ml/fsharp/12-regularization-and-model-selection.md) を新規作成。
* **Creation**: [F# 第 11 章](/article/getting-start-ml/fsharp/11-evaluation-metrics-and-cross-validation.md) を新規作成。
* **Creation**: [F# 第 10 章](/article/getting-start-ml/fsharp/10-logistic-regression-and-ensemble.md) を新規作成。
* **Creation**: [F# 第 9 章](/article/getting-start-ml/fsharp/09-feature-engineering.md) を新規作成。
* **Creation**: [F# 第 8 章](/article/getting-start-ml/fsharp/08-classification-and-preprocessing-pipeline.md) を新規作成。
* **Creation**: [F# 第 7 章](/article/getting-start-ml/fsharp/07-linear-regression.md) を新規作成。
* **Update**: F# 版 B20 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 004](/adr/004-fsharp-ml-libraries.md) に B20 で確かめたことを追記。実装の場所を `apps/fsharp/` に改めた。
* **Creation**: [F# 第 6 章](/article/getting-start-ml/fsharp/06-task-runner-and-ci-cd.md) を新規作成。
* **Creation**: [F# 第 5 章](/article/getting-start-ml/fsharp/05-package-management-and-static-analysis.md) を新規作成。
* **Creation**: [F# 第 4 章](/article/getting-start-ml/fsharp/04-version-control-and-data-management.md) を新規作成。
* **Update**: F# 版 B19 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 004](/adr/004-fsharp-ml-libraries.md) に B19 で確かめたことを追記。
* **Creation**: [F# 第 3 章](/article/getting-start-ml/fsharp/03-decision-tree-and-obvious-implementation.md) を新規作成。
* **Creation**: [F# 第 2 章](/article/getting-start-ml/fsharp/02-data-preprocessing-and-triangulation.md) を新規作成。

## 2026-09-18
* **Creation**: [ADR 004](/adr/004-fsharp-ml-libraries.md) を新規作成。
* **Update**: F# 版 B18 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、F# 版トップを追加。
* **Creation**: [F# 第 1 章](/article/getting-start-ml/fsharp/01-machine-learning-and-first-test.md) を新規作成。
* **Update**: F# を第 2 波から第 1 波へ移し、Polyglot Notebooks を使う F# 版執筆計画を [執筆計画](/article/getting-start-ml/outline.md) に追加。[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新。
* **Update**: TypeScript 版 B15〜B17 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 003](/adr/003-typescript-ml-libraries.md) に第 7〜15 章で確かめた結果を追記。
* **Creation**: [TypeScript 第 15 章](/article/getting-start-ml/typescript/15-machine-learning-api-and-module-design.md) を新規作成。
* **Creation**: [TypeScript 第 11 章](/article/getting-start-ml/typescript/11-evaluation-metrics-and-cross-validation.md) を新規作成。
* **Creation**: [TypeScript 第 8 章](/article/getting-start-ml/typescript/08-classification-and-preprocessing-pipeline.md) を新規作成。
* **Creation**: [TypeScript 第 9 章](/article/getting-start-ml/typescript/09-feature-engineering.md) を新規作成。
* **Creation**: [TypeScript 第 10 章](/article/getting-start-ml/typescript/10-logistic-regression-and-ensemble.md) を新規作成。
* **Creation**: [TypeScript 第 14 章](/article/getting-start-ml/typescript/14-k-means-clustering.md) を新規作成。
* **Creation**: [TypeScript 第 12 章](/article/getting-start-ml/typescript/12-regularization-and-model-selection.md) を新規作成。
* **Creation**: [TypeScript 第 13 章](/article/getting-start-ml/typescript/13-principal-component-analysis.md) を新規作成。
* **Creation**: [TypeScript 第 7 章](/article/getting-start-ml/typescript/07-linear-regression.md) を新規作成。
* **Creation**: [TypeScript 第 6 章](/article/getting-start-ml/typescript/06-task-runner-and-ci-cd.md) を新規作成。
* **Creation**: [TypeScript 第 5 章](/article/getting-start-ml/typescript/05-package-management-and-static-analysis.md) を新規作成。
* **Creation**: [TypeScript 第 4 章](/article/getting-start-ml/typescript/04-version-control-and-data-management.md) を新規作成。

## 2026-09-17
* **Update**: TypeScript 版 B14 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 003](/adr/003-typescript-ml-libraries.md) の第 3 章の方針に ml-cart で確かめた違いを追記。
* **Creation**: [TypeScript 第 3 章](/article/getting-start-ml/typescript/03-decision-tree-and-obvious-implementation.md) を新規作成。
* **Creation**: [TypeScript 第 2 章](/article/getting-start-ml/typescript/02-data-preprocessing-and-triangulation.md) を新規作成。
* **Update**: TypeScript 版 B13 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md) の前提整備（TypeScript）と [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表を更新。
* **Creation**: [機械学習から始めるプログラミング入門 TypeScript 版トップ](/article/getting-start-ml/typescript/index.md) を新規作成。
* **Creation**: [機械学習から始めるプログラミング入門 TypeScript 第 1 章](/article/getting-start-ml/typescript/01-machine-learning-and-first-test.md) を新規作成。
* **Creation**: [ADR 003](/adr/003-typescript-ml-libraries.md) を新規作成。
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に TypeScript 版執筆計画を追加。
* **Update**: Kotlin 版 B10〜B12 の完了（全 15 章）に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新し、[ADR 002](/adr/002-kotlin-ml-libraries.md) に各章で確かめた Tribuo の振る舞いを追記。
* **Creation**: [Kotlin 第 11 章](/article/getting-start-ml/kotlin/11-evaluation-metrics-and-cross-validation.md) を新規作成。
* **Creation**: [Kotlin 第 12 章](/article/getting-start-ml/kotlin/12-regularization-and-model-selection.md) を新規作成。
* **Creation**: [Kotlin 第 15 章](/article/getting-start-ml/kotlin/15-machine-learning-api-and-module-design.md) を新規作成。
* **Creation**: [Kotlin 第 14 章](/article/getting-start-ml/kotlin/14-k-means-clustering.md) を新規作成。
* **Creation**: [Kotlin 第 13 章](/article/getting-start-ml/kotlin/13-principal-component-analysis.md) を新規作成。
* **Creation**: [Kotlin 第 8 章](/article/getting-start-ml/kotlin/08-classification-and-preprocessing-pipeline.md) を新規作成。
* **Creation**: [Kotlin 第 9 章](/article/getting-start-ml/kotlin/09-feature-engineering.md) を新規作成。
* **Creation**: [Kotlin 第 10 章](/article/getting-start-ml/kotlin/10-logistic-regression-and-ensemble.md) を新規作成。
* **Creation**: [Kotlin 第 7 章](/article/getting-start-ml/kotlin/07-linear-regression.md) を新規作成。
* **Update**: Kotlin [第 2 章](/article/getting-start-ml/kotlin/02-data-preprocessing-and-triangulation.md)・[第 3 章](/article/getting-start-ml/kotlin/03-decision-tree-and-obvious-implementation.md) のコードを、分割に型引数を持たせた実装に合わせて更新。
* **Update**: Kotlin 版 B9 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md)・[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新。
* **Update**: [Python 第 6 章](/article/getting-start-ml/python/06-task-runner-and-ci-cd.md) のワークフローから、効いていなかった Nix ストアのキャッシュのステップを削除。
* **Creation**: [Kotlin 第 6 章](/article/getting-start-ml/kotlin/06-task-runner-and-ci-cd.md) を新規作成。
* **Creation**: [Kotlin 第 5 章](/article/getting-start-ml/kotlin/05-package-management-and-static-analysis.md) を新規作成。[第 1 章](/article/getting-start-ml/kotlin/01-machine-learning-and-first-test.md) の完成コードを定数化に合わせて更新。
* **Creation**: [Kotlin 第 4 章](/article/getting-start-ml/kotlin/04-version-control-and-data-management.md) を新規作成。
* **Update**: [ADR 002](/adr/002-kotlin-ml-libraries.md) に B9 で確かめた detekt 1.23.8・Kover 0.9.9 の動作と、Gradle デーモンの JDK を 21 に固定する決定を追記。
* **Update**: Kotlin 版 B8 の完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md) の前提整備（Kotlin）・Bolt の状態と、[シリーズ索引](/article/getting-start-ml/index.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗を更新。
* **Creation**: [Kotlin 第 3 章](/article/getting-start-ml/kotlin/03-decision-tree-and-obvious-implementation.md) を新規作成。Tribuo の CART と予測が一致しない原因を調べた結果を含む。
* **Creation**: [Kotlin 第 2 章](/article/getting-start-ml/kotlin/02-data-preprocessing-and-triangulation.md) を新規作成。第 1 章に captureStdout の移動を追記。
* **Update**: [ADR 002](/adr/002-kotlin-ml-libraries.md) を訂正。Kandy 0.8.5 が DataFrame 1.0.0-rc01 に依存していたため当初の検証が 1.0.0-rc01 のものだったと判明し、DataFrame 0.15.0 と組み合わせられる Kandy 0.8.0 に改め、Kotlin Notebook を IDE なしで実行できることを追記。[執筆計画](/article/getting-start-ml/outline.md) の該当箇所も修正。
* **Update**: Kotlin 版 B7 の進行に合わせて、[執筆計画](/article/getting-start-ml/outline.md) の前提整備（Kotlin）と [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表を更新。
* **Creation**: [機械学習から始めるプログラミング入門 Kotlin 第 1 章](/article/getting-start-ml/kotlin/01-machine-learning-and-first-test.md) を新規作成。
* **Creation**: [002-kotlin-ml-libraries](/adr/002-kotlin-ml-libraries.md) を作成（claude-code/claude-opus-5）
* **Verification**: [outline](/article/getting-start-ml/outline.md) を human:kakimomokuri が検証
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に Kotlin 版執筆計画（確認した事実、ライブラリ方針、前提整備、章別計画、Python 版との数値の違い、Bolt 計画、承認事項）を追加。
* **Update**: Python 版の全章完了に合わせて、[執筆計画](/article/getting-start-ml/outline.md) の前提整備・Bolt 計画の状態と [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表を更新。
* **Creation**: [Python 第 10 章](/article/getting-start-ml/python/10-logistic-regression-and-ensemble.md) を新規作成。
* **Creation**: [Python 第 15 章](/article/getting-start-ml/python/15-machine-learning-api-and-module-design.md) を新規作成。
* **Creation**: [Python 第 13 章](/article/getting-start-ml/python/13-principal-component-analysis.md) を新規作成。
* **Creation**: [Python 第 12 章](/article/getting-start-ml/python/12-regularization-and-model-selection.md) を新規作成。
* **Creation**: [Python 付録 A 総合演習（Bank）](/article/getting-start-ml/python/appendix-a-bank-exercise.md) を新規作成。
* **Creation**: [Python 第 14 章](/article/getting-start-ml/python/14-k-means-clustering.md) を新規作成。
* **Creation**: [Python 第 9 章](/article/getting-start-ml/python/09-feature-engineering.md) を新規作成。
* **Creation**: [Python 第 11 章](/article/getting-start-ml/python/11-evaluation-metrics-and-cross-validation.md) を新規作成。
* **Creation**: [Python 第 8 章](/article/getting-start-ml/python/08-classification-and-preprocessing-pipeline.md) を新規作成。
* **Creation**: [Python 第 7 章](/article/getting-start-ml/python/07-linear-regression.md) を新規作成。
* **Creation**: [Python 第 6 章](/article/getting-start-ml/python/06-task-runner-and-ci-cd.md) を新規作成。
* **Creation**: [Python 第 5 章](/article/getting-start-ml/python/05-package-management-and-static-analysis.md) を新規作成。
* **Creation**: [Python 第 4 章](/article/getting-start-ml/python/04-version-control-and-data-management.md) を新規作成。
* **Creation**: [機械学習から始めるプログラミング入門 Python 第 3 章](/article/getting-start-ml/python/03-decision-tree-and-obvious-implementation.md) を新規作成。
* **Creation**: [機械学習から始めるプログラミング入門 Python 第 2 章](/article/getting-start-ml/python/02-data-preprocessing-and-triangulation.md) を新規作成。
* **Creation**: [001-python-ml-libraries](/adr/001-python-ml-libraries.md) を作成（claude-code/claude-opus-5）
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) の前提整備の状態と [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗を B1 完了に合わせて更新。
* **Creation**: [機械学習から始めるプログラミング入門 執筆ワークフロー](/article/getting-start-ml/workflow.md) を新規作成。
* **Creation**: [機械学習から始めるプログラミング入門 Python 第 1 章](/article/getting-start-ml/python/01-machine-learning-and-first-test.md) を新規作成。
* **Verification**: [outline](/article/getting-start-ml/outline.md) を human:kakimomokuri が検証
* **Creation**: [機械学習から始めるプログラミング入門 執筆計画](/article/getting-start-ml/outline.md) を新規作成。5 部 15 章＋付録の章構成、学習データ（スッキリわかる機械学習入門の配布データ）をコミットしない方針と配置方法、3 波に分けた対象言語（第 1 波は Python・Kotlin・TypeScript）、Python と Kotlin に限定した Notebook による可視化の方針、第 1 波の Bolt 計画を定義。

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
