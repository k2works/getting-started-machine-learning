'use strict';

import path from 'path';
import { execSync } from 'child_process';

// ============================================
// 設定
// ============================================

/** サンプル実装（記事の各言語版） */
const APPS = [
  {
    name: 'python',
    nix: 'python',
    dir: path.join('apps', 'python'),
    tools: [{ cmd: 'uv', version: 'uv --version' }],
    setup: 'uv sync',
    check: 'uv run pytest -q',
  },
  {
    name: 'node',
    nix: 'node',
    dir: path.join('apps', 'node'),
    tools: [{ cmd: 'node', version: 'node --version' }, { cmd: 'npm', version: 'npm --version' }],
    setup: 'npm ci',
    check: 'npm run check',
  },
  {
    name: 'kotlin',
    nix: 'kotlin',
    dir: path.join('apps', 'kotlin'),
    tools: [{ cmd: 'java', version: 'java -version' }],
    setup: `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} testClasses`,
    check: `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} check`,
  },
  {
    name: 'java',
    nix: 'java',
    dir: path.join('apps', 'java'),
    tools: [{ cmd: 'java', version: 'java -version' }],
    setup: `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} testClasses`,
    check: `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} check`,
  },
  {
    name: 'go',
    nix: 'go',
    dir: path.join('apps', 'go'),
    tools: [{ cmd: 'go', version: 'go version' }],
    setup: 'go mod download',
    // CI（.github/workflows/go-ci.yml）と同じ順に、整形・vet・lint・テストを検査する
    check: 'test -z "$(gofmt -l .)" || (gofmt -l . && exit 1) && go vet ./... && golangci-lint run && go test ./... -cover',
  },
  {
    name: 'rust',
    nix: 'rust',
    dir: path.join('apps', 'rust'),
    tools: [{ cmd: 'cargo', version: 'cargo --version' }],
    setup: 'cargo fetch',
    // CI（.github/workflows/rust-ci.yml）と同じ順に、整形・lint・テスト・カバレッジを検査する
    check:
      'cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test && cargo llvm-cov --summary-only',
  },
  {
    name: 'clojure',
    nix: 'clojure',
    dir: path.join('apps', 'clojure'),
    // clj-kondo と cljfmt は Nix の環境にしかないので、そろっていなければ Nix の中で実行する
    tools: [{ cmd: 'clojure', version: 'clojure --version' }, { cmd: 'clj-kondo', version: 'clj-kondo --version' }],
    setup: 'clojure -P -M:test',
    // CI（.github/workflows/clojure-ci.yml）と同じ順に、整形・静的解析・テスト・カバレッジを検査する
    check:
      'cljfmt check src test && clj-kondo --lint src test --fail-level warning && clojure -M:test && clojure -M:coverage',
  },
  {
    name: 'elixir',
    nix: 'elixir',
    dir: path.join('apps', 'elixir'),
    tools: [{ cmd: 'mix', version: 'mix --version' }],
    setup: 'mix deps.get',
    // CI（.github/workflows/elixir-ci.yml）と同じ順に、整形・警告・静的解析・テスト・カバレッジを検査する
    check:
      'mix format --check-formatted && mix compile --warnings-as-errors && mix credo --strict && mix test --cover',
  },
  {
    name: 'ruby',
    nix: 'ruby',
    dir: path.join('apps', 'ruby'),
    // macOS に付属する Ruby 2.6 では動かないので、3.3 未満なら Nix の環境で実行する
    tools: [{ cmd: 'ruby', version: `ruby -e 'exit(Gem::Version.new(RUBY_VERSION) >= Gem::Version.new("3.3"))'` }],
    setup: 'bundle install',
    // CI（.github/workflows/ruby-ci.yml）と同じく、RuboCop とテスト（SimpleCov つき）を検査する
    check: 'bundle exec rake check',
  },
  {
    name: 'scala',
    nix: 'scala',
    dir: path.join('apps', 'scala'),
    tools: [{ cmd: 'sbt', version: 'sbt --script-version' }],
    setup: 'sbt -batch --no-colors update',
    // CI（.github/workflows/scala-ci.yml）と同じ順に、整形・コンパイル・テストを検査する
    check: "sbt -batch --no-colors 'scalafmtCheckAll; test'",
  },
  {
    name: 'csharp',
    nix: 'dotnet',
    dir: path.join('apps', 'csharp'),
    tools: [{ cmd: 'dotnet', version: 'dotnet --version' }],
    setup: 'dotnet restore --locked-mode && dotnet build --no-restore',
    // CI（.github/workflows/csharp-ci.yml）と同じ順に、整形・アナライザー・テストを検査する
    check: 'dotnet format MachineLearning.sln --verify-no-changes --no-restore && dotnet build --no-restore && dotnet test --no-restore --no-build',
  },
  {
    name: 'fsharp',
    nix: 'dotnet',
    dir: path.join('apps', 'fsharp'),
    // global.json の SDK バージョンで解決できるかをアプリのディレクトリで確かめる
    tools: [{ cmd: 'dotnet', version: 'dotnet --version' }],
    setup: 'dotnet tool restore && dotnet restore --locked-mode && dotnet build --no-restore',
    // CI（.github/workflows/fsharp-ci.yml）と同じ順に、整形・静的解析・Notebook の出力・テストを検査する
    check: 'dotnet fantomas --check . && dotnet fsharplint lint MachineLearning.sln && dotnet fsharplint lint tools/notebooks.fsx && dotnet fsi tools/notebooks.fsx verify && dotnet test',
  },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドを実行する
 * @param {string} cmd - 実行するコマンド
 * @param {string} cwd - 作業ディレクトリ
 */
function run(cmd, cwd) {
  console.log(`\n[${cwd}] ${cmd}`);
  execSync(cmd, { cwd, stdio: 'inherit' });
}

/**
 * 前提ツールが揃っているか確認し、不足しているツール名を返す
 * @param {typeof APPS[number]} app - 対象アプリ
 * @returns {string[]}
 */
function missingTools(app) {
  return app.tools
    .filter(({ version }) => {
      try {
        execSync(version, { cwd: app.dir, stdio: 'ignore' });
        return false;
      } catch {
        return true;
      }
    })
    .map(({ cmd }) => cmd);
}

/**
 * Nix が使えるか確認する
 * @returns {boolean}
 */
function hasNix() {
  try {
    execSync('nix --version', { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

/**
 * コマンドを Nix の開発環境（nix develop .#<env>）の中で実行する
 * @param {typeof APPS[number]} app - 対象アプリ
 * @param {string} cmd - 実行するコマンド
 */
function runInNix(app, cmd) {
  const inner = `cd '${app.dir}' && ${cmd}`;
  run(`nix develop .#${app.nix} --command bash -c "${inner}"`, '.');
}

/**
 * アプリごとのタスクを作る
 * @param {typeof APPS[number]} app - 対象アプリ
 * @param {'setup' | 'check'} kind - タスク種別
 * @returns {(done: (err?: Error) => void) => void}
 */
function appTask(app, kind) {
  return (done) => {
    const missing = missingTools(app);
    const useNix = missing.length > 0;
    if (useNix && !hasNix()) {
      done(new Error(`${app.name}: 前提ツールが見つからないか、バージョンが合いません: ${missing.join(', ')}（インストールするか、Nix を導入してください）`));
      return;
    }
    if (useNix) {
      console.log(`${app.name}: ローカルの ${missing.join(', ')} が使えないため、nix develop .#${app.nix} の中で実行します。`);
    }
    try {
      if (useNix) {
        runInNix(app, app[kind]);
      } else {
        run(app[kind], app.dir);
      }
    } catch (err) {
      done(new Error(`${app.name}: ${app[kind]} に失敗しました。ツールのバージョンが合わない場合は nix develop .#${app.nix} で実行してください。\n${err.message}`));
      return;
    }
    done();
  };
}

// ============================================
// Gulp タスク
// ============================================

/**
 * サンプル実装の環境セットアップタスクを gulp に登録する（data:* タスクの登録後に呼ぶ）
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  for (const app of APPS) {
    gulp.task(`apps:setup:${app.name}`, appTask(app, 'setup'));
    gulp.task(`apps:check:${app.name}`, appTask(app, 'check'));
  }

  gulp.task('apps:setup', gulp.series(
    'data:setup:ifmissing',
    ...APPS.map((app) => `apps:setup:${app.name}`),
  ));

  gulp.task('apps:check', gulp.series(
    'data:check',
    ...APPS.map((app) => `apps:check:${app.name}`),
  ));

  gulp.task('apps:help', (done) => {
    const names = APPS.map((app) => app.name).join('|');
    console.log(`
サンプル実装（apps/）の環境セットアップ

  gulp apps:setup            学習データを配置し（未配置時のみ）、全アプリの依存関係をインストールする
  gulp apps:setup:<name>     指定アプリの依存関係をインストールする（${names}）
  gulp apps:check            学習データを確認し、全アプリのテスト・静的解析を実行する
  gulp apps:check:<name>     指定アプリのテスト・静的解析を実行する（${names}）
  gulp apps:help             このヘルプを表示する

${APPS.map((app) => `  ${app.name.padEnd(8)} setup: ${app.setup}\n           check: ${app.check}`).join('\n')}

前提ツール: uv / Node.js（22・24 系）/ JDK 21 以上 / .NET SDK 10.0.101 以上
ローカルのツールが見つからないかバージョンが合わない場合は、nix develop .#<python|node|kotlin|java|dotnet> の対応する環境 の中で自動的に実行します。
`);
    done();
  });
}
