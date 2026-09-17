'use strict';

import path from 'path';
import fs from 'fs';
import { execSync } from 'child_process';
import { openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** 上流リポジトリ（reference_agent パッケージ: enrich / visualize） */
const OKF_REPO_URL = 'https://github.com/GoogleCloudPlatform/open-knowledge-format';

/** ローカルの検証・アップグレードスクリプト（migrating-okf スキルに同梱） */
const OKF_CHECK_SCRIPT = path.join('.claude', 'skills', 'migrating-okf', 'scripts', 'okf_check.py');

/** 既定の OKF 仕様バージョン */
const OKF_DEFAULT_VERSION = '0.2';

/**
 * バンドルルート（既定 docs/）
 * @returns {string}
 */
function bundleDir() {
  return process.env.OKF_BUNDLE || 'docs';
}

/**
 * 上流ツールのクローン先（tmp/ は .gitignore 対象）
 * @returns {string}
 */
function toolDir() {
  return process.env.OKF_TOOL_DIR || path.join('tmp', 'open-knowledge-format');
}

/**
 * visualize の出力先（既定 tmp/okf/viz.html）
 * @returns {string}
 */
function vizOut() {
  return process.env.OKF_VIZ_OUT || path.join('tmp', 'okf', 'viz.html');
}

/**
 * システムの Python コマンド
 * @returns {string}
 */
function systemPython() {
  return process.env.OKF_PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
}

/**
 * 上流ツール用 venv の Python 実行ファイル
 * @returns {string}
 */
function venvPython() {
  const venv = path.join(toolDir(), '.venv');
  return process.platform === 'win32'
    ? path.join(venv, 'Scripts', 'python.exe')
    : path.join(venv, 'bin', 'python');
}

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドを実行し標準出力をそのまま流す
 * @param {string} command - 実行するコマンド
 * @param {Object} [opts] - execSync オプション
 */
function run(command, opts = {}) {
  console.log(`  $ ${command}`);
  execSync(command, { stdio: 'inherit', env: { ...process.env, PYTHONUTF8: '1' }, ...opts });
}

/**
 * 引数を安全にクォートする
 * @param {string} s - 引数
 * @returns {string}
 */
function q(s) {
  return `"${String(s).replace(/"/g, '\\"')}"`;
}

/**
 * 上流ツールがセットアップ済みか確認し、未セットアップならエラーで終了する
 */
function requireTool() {
  if (!fs.existsSync(venvPython())) {
    throw new Error(`上流ツールが未セットアップです。先に \`gulp okf:setup\` を実行してください（${toolDir()}）`);
  }
}

/**
 * 生成された可視化 HTML の埋め込み JSON を安全化する
 * 上流の generator は json.dumps の結果を <script> にそのまま埋め込むため、
 * 本文に `</script>` を含むコンセプトがあるとスクリプトが途中で終了し、
 * 残りの JSON が生表示（\uXXXX の文字化け）になる。JSON 内の `</` を `<\/` に置換して防ぐ
 * @param {string} htmlPath - 可視化 HTML のパス
 */
function hardenEmbeddedJson(htmlPath) {
  const lines = fs.readFileSync(htmlPath, 'utf8').split('\n');
  let fixed = 0;
  const out = lines.map((line) => {
    if (!line.startsWith('window.BUNDLE = ') && !line.startsWith('window.BUNDLE_NAME = ')) return line;
    const safe = line.replace(/<\//g, '<\\/');
    if (safe !== line) fixed += 1;
    return safe;
  });
  if (fixed > 0) {
    fs.writeFileSync(htmlPath, out.join('\n'), 'utf8');
    console.log(`  埋め込み JSON の "</" をエスケープしました（${fixed} 行）`);
  }
}

/**
 * バンドルルートの存在を確認する
 * @returns {string} バンドルルート
 */
function requireBundle() {
  const dir = bundleDir();
  if (!fs.existsSync(dir)) {
    throw new Error(`バンドルが見つかりません: ${dir}（OKF_BUNDLE で変更可）`);
  }
  return dir;
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  /**
   * バンドルの適合性検証（ガイド §11）
   * ERROR があれば非ゼロ終了する
   */
  gulp.task('okf:check', (done) => {
    const dir = requireBundle();
    console.log(`=== OKF 適合性検証: ${dir} ===`);
    run(`${systemPython()} ${q(OKF_CHECK_SCRIPT)} --check ${q(dir)}`);
    done();
  });

  /**
   * バンドルを指定バージョンの仕様に追従させる（既定 0.2）
   * OKF_VERSION で対象バージョン、OKF_BY で generated.by の既定値、OKF_DRY_RUN=1 で試行のみ
   */
  gulp.task('okf:upgrade', (done) => {
    const dir = requireBundle();
    const version = process.env.OKF_VERSION || OKF_DEFAULT_VERSION;
    const by = process.env.OKF_BY ? ` --by ${q(process.env.OKF_BY)}` : '';
    const dry = process.env.OKF_DRY_RUN === '1' ? ' --dry-run' : '';
    console.log(`=== OKF アップグレード: ${dir} → v${version}${dry ? '（dry-run）' : ''} ===`);
    run(`${systemPython()} ${q(OKF_CHECK_SCRIPT)} --upgrade ${version} ${q(dir)}${by}${dry}`);
    console.log('');
    console.log('次のステップ: log.md に **Upgrade** エントリを追記し、`gulp okf:check` で確認してください。');
    done();
  });

  /**
   * 上流ツール（reference_agent）をクローンし venv にインストールする
   * 2 回目以降は git pull と再インストールを行う
   */
  gulp.task('okf:setup', (done) => {
    const dir = toolDir();
    console.log('=== OKF 上流ツール セットアップ開始 ===');
    if (fs.existsSync(path.join(dir, '.git'))) {
      console.log('[1/3] 既存クローンを更新...');
      run('git pull --ff-only', { cwd: dir });
    } else {
      console.log(`[1/3] ${OKF_REPO_URL} をクローン...`);
      fs.mkdirSync(path.dirname(dir), { recursive: true });
      run(`git clone --depth 1 ${OKF_REPO_URL} ${q(dir)}`);
    }
    console.log('[2/3] venv を作成...');
    if (!fs.existsSync(venvPython())) {
      run(`${systemPython()} -m venv .venv`, { cwd: dir });
    }
    console.log('[3/3] reference_agent をインストール...');
    run(`${q(venvPython())} -m pip install --quiet --upgrade pip`);
    run(`${q(venvPython())} -m pip install --quiet -e ${q(dir)}`);
    console.log('');
    console.log('=== OKF 上流ツール セットアップ完了 ===');
    console.log(`  ツール: ${dir}`);
    console.log('  次のステップ: gulp okf:viz でバンドルをグラフ表示');
    done();
  });

  /**
   * バンドルを自己完結 HTML（force-directed graph）に可視化する
   * OKF_VIZ_OUT で出力先、OKF_VIZ_NAME でヘッダー表示名を指定
   */
  gulp.task('okf:viz', (done) => {
    requireTool();
    const dir = requireBundle();
    const out = vizOut();
    const name = process.env.OKF_VIZ_NAME ? ` --name ${q(process.env.OKF_VIZ_NAME)}` : '';
    fs.mkdirSync(path.dirname(out), { recursive: true });
    console.log(`=== OKF 可視化: ${dir} → ${out} ===`);
    run(`${q(venvPython())} -m reference_agent visualize --bundle ${q(dir)} --out ${q(out)}${name}`);
    hardenEmbeddedJson(out);
    done();
  });

  /**
   * 生成済みの可視化 HTML をブラウザで開く
   */
  gulp.task('okf:viz:open', (done) => {
    const out = vizOut();
    if (!fs.existsSync(out)) {
      throw new Error(`可視化 HTML がありません: ${out}。先に \`gulp okf:viz\` を実行してください`);
    }
    openUrl(path.resolve(out));
    done();
  });

  /**
   * 参照エージェントで BigQuery データセットからバンドルを生成する
   * 必須: OKF_DATASET（<project>.<dataset>）。任意: OKF_SEED_FILE、OKF_ENRICH_OUT、OKF_NO_WEB=1
   * 認証: gcloud ADC と GEMINI_API_KEY（または Vertex AI 設定）が必要
   */
  gulp.task('okf:enrich', (done) => {
    requireTool();
    const dataset = process.env.OKF_DATASET;
    if (!dataset) {
      throw new Error('OKF_DATASET が未設定です（例: OKF_DATASET=my-project.sales）');
    }
    const out = process.env.OKF_ENRICH_OUT || path.join('tmp', 'okf', 'bundles', dataset.split('.').pop());
    // シードファイルがあれば Web パスを実行、無ければ BigQuery メタデータのみ
    const web = process.env.OKF_SEED_FILE ? ` --web-seed-file ${q(process.env.OKF_SEED_FILE)}` : ' --no-web';
    fs.mkdirSync(out, { recursive: true });
    console.log(`=== OKF バンドル生成: ${dataset} → ${out} ===`);
    run(`${q(venvPython())} -m reference_agent enrich --source bq --dataset ${q(dataset)} --out ${q(out)}${web}`);
    console.log('');
    console.log(`次のステップ: OKF_BUNDLE=${out} gulp okf:check で検証`);
    done();
  });

  /**
   * ヘルプ
   */
  gulp.task('okf:help', (done) => {
    console.log(`
OKF（Open Knowledge Format）タスク

  gulp okf:check          バンドルの適合性を検証（ERROR で非ゼロ終了）
  gulp okf:upgrade        仕様バージョンへ追従（既定 v${OKF_DEFAULT_VERSION}。OKF_DRY_RUN=1 で試行）
  gulp okf:setup          上流ツール reference_agent をクローン・インストール
  gulp okf:viz            バンドルをグラフ HTML に可視化
  gulp okf:viz:open       可視化 HTML をブラウザで開く
  gulp okf:enrich         BigQuery データセットからバンドルを生成（要 GCP 認証）
  gulp okf:help           このヘルプ

環境変数（.env）
  OKF_BUNDLE      バンドルルート（既定 docs）
  OKF_VERSION     okf:upgrade の対象バージョン（既定 ${OKF_DEFAULT_VERSION}）
  OKF_BY          okf:upgrade で generated.by を推定できない場合の既定アクター
  OKF_TOOL_DIR    上流ツールのクローン先（既定 tmp/open-knowledge-format）
  OKF_PYTHON      Python コマンド（既定 ${systemPython()}）
  OKF_VIZ_OUT     可視化 HTML の出力先（既定 tmp/okf/viz.html）
  OKF_VIZ_NAME    可視化ヘッダーの表示名
  OKF_DATASET     okf:enrich の BigQuery データセット（<project>.<dataset>）
  OKF_SEED_FILE   okf:enrich の Web シード URL ファイル（未指定なら --no-web）
  OKF_ENRICH_OUT  okf:enrich の出力先（既定 tmp/okf/bundles/<dataset>）

仕様: docs/reference/OKF導入ガイド_V0.2.md / 上流: ${OKF_REPO_URL}
`);
    done();
  });
}
