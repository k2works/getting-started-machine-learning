# Docs Update Log

## 2026-09-28
* **Update**: [ADR 015](/adr/015-nadesiko3-toolchain.md) を承認済みにした（なでしこ3 版の第 1〜15 章の執筆で確かめた。実データでの実測と CI の初回実行はまだ）
* **Update**: [トップ](/index.md)・[記事の一覧](/article/index.md)・`mkdocs.yml` の nav を現在の構成に合わせた。nav に無かったリファレンス 8 件（AI-DLC 版の 4 ガイド・AI-DLC 導入ガイド・AI-DLC 用語集・ドキュメント構成ガイド・OKF 導入ガイド）と更新履歴を加え、トップと記事の一覧になでしこ3 版（番外編）を載せた
* **Update**: [多言語統合解説](/article/getting-start-ml/integration/index.md) の索引に「番外編」の節を、[第 5 章 学習ロードマップ](/article/getting-start-ml/integration/05-learning-roadmap.md) に「5.8 番外編: なでしこ3 版」を追加し、[執筆計画](/article/getting-start-ml/outline.md)・[進捗表](/article/getting-start-ml/workflow.md) を更新（B81。なでしこ3 版の番外編 B76〜B81 が完了）。14 言語の表・レーダーチャート・総合スコアには含めず、理由を書いた
* **Creation**: なでしこ3 版の [第 15 章](/article/getting-start-ml/nadesiko3/15-machine-learning-api-and-module-design.md) を新規作成し、[トップ](/article/getting-start-ml/nadesiko3/index.md)・シリーズ索引・nav・[ADR 015](/adr/015-nadesiko3-toolchain.md)・[執筆計画](/article/getting-start-ml/outline.md)・[進捗表](/article/getting-start-ml/workflow.md) を更新（B80。なでしこ3 版の全 15 章がそろった）。HTTP サーバーの命令が無いので、予測 API を JSON の標準入出力でやりとりするコマンドにし、経路・検証の理由・ステータスコードをほかの言語版とそろえた。約束は無名関数の辞書、失敗は結果の辞書で表した。実データでの実測は未確認
* **Creation**: なでしこ3 版の [第 11 章](/article/getting-start-ml/nadesiko3/11-evaluation-metrics-and-cross-validation.md)・[第 12 章](/article/getting-start-ml/nadesiko3/12-regularization-and-model-selection.md) を章ごとのサブエージェントで作り、取り込んだ（B79 の第 2 段。B79 完了）。[トップ](/article/getting-start-ml/nadesiko3/index.md)・シリーズ索引・nav・[ADR 015](/adr/015-nadesiko3-toolchain.md)・[執筆計画](/article/getting-start-ml/outline.md)・[進捗表](/article/getting-start-ml/workflow.md) を更新。評価関数は無名関数で渡し（名前を付けた関数は値の位置でその場で呼ばれる）、リッジ回帰とラッソ回帰は性質のテストで支えた。自作データで参照実装と一致し、命令の数は第 11 章約 1945 万・第 12 章約 223 万。実データでの実測は未確認
* **Creation**: なでしこ3 版の [第 7 章](/article/getting-start-ml/nadesiko3/07-linear-regression.md)・[第 8 章](/article/getting-start-ml/nadesiko3/08-classification-and-preprocessing-pipeline.md)・[第 10 章](/article/getting-start-ml/nadesiko3/10-logistic-regression-and-ensemble.md)・[第 13 章](/article/getting-start-ml/nadesiko3/13-principal-component-analysis.md)・[第 14 章](/article/getting-start-ml/nadesiko3/14-k-means-clustering.md) を章ごとのサブエージェントで並行して作り、取り込んだ（B79 の第 1 段）。線形代数（ガウスの消去法・ヤコビ法）も自作した。**gonako に 1 回の実行につき 2 億命令の変えられない上限がある**ことが分かり、第 10 章の報告（約 5.6 億命令）が収まらないため、**計算の結果を変えずに書き方で上限に収める**方針にした（ユーザーが決定）。`近似確認` が NaN を合格にしていた不具合を直した。[ADR 015](/adr/015-nadesiko3-toolchain.md)・[執筆計画](/article/getting-start-ml/outline.md)・トップ・nav・章の選び方を更新。
* **Creation**: なでしこ3 版の [第 4 章](/article/getting-start-ml/nadesiko3/04-version-control-and-data-management.md)・[第 5 章](/article/getting-start-ml/nadesiko3/05-package-management-and-static-analysis.md)・[第 6 章](/article/getting-start-ml/nadesiko3/06-task-runner-and-ci-cd.md) を新規作成し、[トップ](/article/getting-start-ml/nadesiko3/index.md)・シリーズ索引・nav を更新（B78）。**カバレッジの仕組みが無いので、テストから届く関数を静的に数える「関数の網羅」をなでしこ3 で自作し**、`check.sh` に加えた（48 件中 48 件）。作る途中で、**命令の引数が足りないと足りない引数が別の値で埋められ、呼ぶ場所によって結果が変わる**落とし穴（`文字検索` の「から」の省略。lint は捕まえない）を見つけた。`gonako` は CRLF を捕まえないので改行は `.gitattributes` だけが守ること、再現性のテストが空振りしていないこと、5 つの検査がすべて壊すと落ちることを確かめた。[ADR 015](/adr/015-nadesiko3-toolchain.md)・[執筆計画](/article/getting-start-ml/outline.md)・[執筆ワークフロー](/article/getting-start-ml/workflow.md) を更新。CI はまだ一度も走っていない。
* **Creation**: なでしこ3 版の [第 2 章](/article/getting-start-ml/nadesiko3/02-data-preprocessing-and-triangulation.md)・[第 3 章](/article/getting-start-ml/nadesiko3/03-decision-tree-and-obvious-implementation.md) を新規作成し、[トップ](/article/getting-start-ml/nadesiko3/index.md)・シリーズ索引・nav を更新（B77）。[ADR 015](/adr/015-nadesiko3-toolchain.md) に「各章で確かめた結果」を、[執筆計画](/article/getting-start-ml/outline.md) に B77 のステップ計画と完了の記録を追加。数値が float64 だけなので、`java.util.Random` と同じ線形合同法を**状態と乗数を 24 ビットずつに分けて**書き、シード 0 の並びがほかの言語版と一致した。学習データの無い環境で進めたため、**自作のアヤメ風データと Python の参照実装で突き合わせ**、分割・平均・両章の報告が最後の桁まで一致することを確かめた。その過程で **`0 = 「」` が真になるゆるい比較**（`===` に直した）と、**名前を `終了` にするとプログラムが黙って終わる**落とし穴（`check.sh` で検査結果報告まで届いたかを確かめるようにした）を見つけた。実データの数値はまだ実測していない（テストは保留、記事に注記）。
* **Creation**: なでしこ3 版の [トップ](/article/getting-start-ml/nadesiko3/index.md)・[第 1 章](/article/getting-start-ml/nadesiko3/01-machine-learning-and-first-test.md) と [ADR 015](/adr/015-nadesiko3-toolchain.md) を新規作成し、シリーズ索引（番外編の節）・nav・ADR 索引に登録。[執筆計画](/article/getting-start-ml/outline.md) の Extra の節を承認済みにし（第 15 章は案 1）、B76 のステップ 1 で確かめた事実・ステップ計画・完了の記録を追加。[執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表になでしこ3 版の行を追加。`apps/nadesiko3/` に第 1 章を TDD で実装し、**テスティングフレームワークが無いためテスト補助を自作**した（`ASSERT等` は最初の失敗で止まる）。nadesiko3go は Go 1.26 を要求するが Nix の Go は 1.25.5 なので `GOTOOLCHAIN=go1.26.8` で切り替え、タグ 3.8.8 は `v` が無く Go の版として読めないため疑似版で固定した。**識別子に助詞を入れられない**（`ルールで判定` が「ルール」の定義に割られる）、**lint はかっこの中の未定義の名前を素通りさせる**、`「170cm」+0` が 170 になる、などを記事にした。学習データの無い環境で書いたため、**実データの正解率 0.7368 はまだ実測していない**（テストは保留、記事に注記）。Nix の `nadesiko3` 環境・`apps:check:nadesiko3`・Nadesiko3 CI を追加した。
* **Update**: [執筆計画](/article/getting-start-ml/outline.md) に「Extra: なでしこ3（nadesiko3go）版執筆計画」を追加（承認待ち）。nadesiko3go 3.8.8 を手元でビルドして確かめた事実（`go 1.26.0` の要求、テストは `ASSERT等` と終了コードで行いカバレッジも型検査も無いこと、CSV が BOM を取り除かないこと、数値が float64 だけで乱数にシードを渡せないこと、HTTP サーバーの命令が無いこと）をもとに、対比の軸・方針・章ごとの見通し・Bolt 計画（B76〜B81）・リスク・承認事項を定義した。Unit 表に U16 を追加した。

## 2026-09-24
* **Creation**: PHP 版の全 15 章（[トップ](/article/getting-start-ml/php/index.md)・第 1〜15 章）と [ADR 013](/adr/013-php-ml-libraries.md) を新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に PHP 版執筆計画・確認した事実・B65〜B69 の完了を反映し、ADR 013 を承認済み（stable）にした。Rubix ML 2.6.0 には決定木もランダムフォレストもあり、**直前の Elixir 版（Scholar に決定木が無い）と正反対に置き換えの範囲が広い版**になった（自作が最終実装なのはラッソ・ROC/AUC・クラスの重み・グループ別の補完・15×15 の固有値分解）。ただし `ClassificationTree` は `array_rand` で列を選びシードを渡す口が無いため、既定のままでは実行ごとに結果が揺れる。**「ライブラリにある」と「そのまま比べられる」は別である**ことがこの版の主題になった。乱数は `java.util.Random` と同じ線形合同法を自作し（**PHP の整数は溢れると float に化けるので乗算を上下 24 ビットに分ける**）、第 2〜14 章の数値が Java 版・Scala 版・Clojure 版・Elixir 版と一致した。型は Ruby 版と逆に使い切る選択をし、`declare(strict_types=1)` と PHPStan のレベル 9 を通した。`ops/nix/environments/php/` の PHP に **pcov を足し**（素の環境にカバレッジドライバが無かった）、composer も同じ PHP から取って版ずれを解消し、**PHPUnit に最低カバレッジのしきい値の機能が無い**ため clover の XML を読む判定を自作した。`apps:check:php` と PHP CI を整え、`.gitattributes` に `apps/php/**` の改行の指定を足した。PHP 版が完了し、シリーズは 13 言語になった。
* **Creation**: Haskell 版の [トップ](/article/getting-start-ml/haskell/index.md)・[第 1 章](/article/getting-start-ml/haskell/01-machine-learning-and-first-test.md) と [ADR 014](/adr/014-haskell-ml-libraries.md) を新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Haskell 版執筆計画・確認した事実・B70 の完了を反映した。**素の Nix 環境に fourmolu も hlint も無く手元の `/usr/local/bin` が見えていた**ので環境定義に足し（PHP 版の pcov と同じ形で第 3 波 2 回目）、**hmatrix が BLAS/LAPACK の C ライブラリを要求して configure で止まる**ため `openblas` も足した。失敗は `Either`、欠損は `Maybe` で表し例外を投げない。**`ByteString` のリテラルに日本語を書くと 1 文字が 1 バイトに詰められて壊れる**ことを実装とテストで 2 回踏み、列名と値を `Text` で持つ形に直した。cassava も BOM を取り除かない。`Int` は溢れると折り返すので自作の線形合同法を仕様どおりに書け、シード 0 の並びが PHP 版と一致した。**cabal にも最低カバレッジのしきい値の機能が無い**ため HPC の結果を読む判定を自作した（第 3 波で 3 回続けてカバレッジの仕組みに手を入れた）。第 1 章の正解率は 0.7368 でほかの 13 言語版と一致した。

* **Update**: [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表を 13 言語分に更新（B23 の時点で止まっていた 4 言語ぶんの記載を、Java 以降 9 言語を含む現状に合わせた）。

* **Creation**: Haskell 版の全 15 章（[トップ](/article/getting-start-ml/haskell/index.md)・第 1〜15 章）と [ADR 014](/adr/014-haskell-ml-libraries.md) を新規作成し、シリーズ索引・nav に登録。[執筆計画](/article/getting-start-ml/outline.md) に Haskell 版執筆計画・確認した事実・B70〜B74 の完了を反映し、ADR 014 を承認済み（stable）にした。**機械学習のライブラリが無いので、突き合わせられるのは線形代数の層（hmatrix）だけ**で、決定木・前処理・ロジスティック回帰・ランダムフォレスト・評価指標・交差検証・ラッソ・K-means はすべて自作が最終実装になった（**第 11 章は「突き合わせの節が存在しない」シリーズ唯一の章**）。失敗は `Either`、欠損は `Maybe`、取りうる値は直和型で表し `-Wall -Werror` で網羅漏れを止めた。**この版でいちばん大きい発見は「C ライブラリの確認は 3 段ある」**こと——ビルドが通ること・呼び出しが通ること・環境を変えたあとに作り直されることは別物で、ステップ 1 で 1 つ目しか確かめずに hmatrix の採用を決めていた（実際には `openblas` が ILP64 で、ビルドは通るのに 2×2 の連立方程式すら解けなかった）。環境定義を `blas`・`lapack`（LP64）に差し替え、`LIBRARY_PATH` がパッケージハッシュに含まれないため cabal の store も作り直した。`Int` が Java と同じく折り返すので、自作の線形合同法は PHP 版で必要だった乗算の分割が要らず、第 2〜14 章の数値がほかの言語版と一致した。**第 10 章の特徴量の重要度は `Data.Map` が鍵の順で畳むため Elixir 版と一致し、挿入順の Java 版・Clojure 版・PHP 版とは 1 ulp ずれた**（PHP 版が「Elixir 版だけずれた」と書いた箇所の裏返し）。テストは 375 件・式カバレッジ 93% で、**章が 15 個そろった時点で HPC の初期化配列が macOS のリンカの範囲を超えた**ため `--enable-executable-dynamic` を足した。Haskell 版が完了し、**シリーズは 14 言語になって第 3 波のすべての言語がそろった**。
* **Update**: [執筆ワークフロー](/article/getting-start-ml/workflow.md) の進捗表に Haskell 版を反映（14 言語）。

* **Update**: [多言語統合解説](/article/getting-start-ml/integration/index.md) の索引と全 5 章を 9 言語から 14 言語に拡張（B75）。第 3 波（Ruby・Clojure・Elixir・PHP・Haskell）の完成にあわせ、**乱数の節を全面的に書き直した**（9 言語 7 通り → 14 言語 8 通り。Elixir・PHP・Haskell が `java.util.Random` と同じ線形合同法を自作したので、JVM の 3 言語と合わせて **6 言語が第 3 章の 6 行すべて一致**する）。新設した節は「カバレッジの仕組みは言語ごとにばらばらだった」「環境に道具が無く手元の PATH のものが見えていた」「『ライブラリにある』と『そのまま比べられる』は別」「C ライブラリへの依存——確認すべきことが 3 段ある」「同じ計算を違う入れ物で渡すと精度が変わる」「ライブラリが無いことが安全な設計を強制した例」。**シリーズ全体にまたがる事実誤認も 1 件直した**——自作の乱数の並びが「Kotlin 版とも一致する」という記述が ADR 2 本と記事 16 ファイル・実装のコメント 5 か所に広がっていたが、Kotlin 版は `kotlin.random.Random`（XorWow）を使っており一致しない（Elixir 版は第 2 章と第 9・11 章で矛盾していた）。あわせて ADR 013・014 の「各章で確かめた結果」の行を章順に直し、シリーズ索引の統合解説の説明を 14 言語に更新した。**これで第 3 波のすべての Bolt（B50〜B75）が完了し、シリーズは 14 言語 × 15 章で完成した。**

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
