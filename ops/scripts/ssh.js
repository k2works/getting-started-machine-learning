'use strict';

import { execSync, execFileSync } from 'child_process';

// ============================================
// SSH ヘルパー関数（外部からも利用可能）
// ============================================

/**
 * SSH 接続パラメータを取得
 * @param {string} [prefix='PRD'] 環境変数プレフィックス（'PRD' or 'STG' or 'DEV'）
 * @returns {{ host: string, user: string, port: string, keyFile: string | undefined }}
 */
export function getSSHConfig(prefix = 'PRD') {
  const host = process.env[`${prefix}_SSH_HOST`];
  const user = process.env[`${prefix}_SSH_USER`];
  const port = process.env[`${prefix}_SSH_PORT`] || '22';
  const keyFile = process.env[`${prefix}_SSH_KEY`];

  if (!host || !user) {
    throw new Error(`${prefix}_SSH_HOST と ${prefix}_SSH_USER を .env に設定してください`);
  }

  return { host, user, port, keyFile };
}

/**
 * SSH でリモートコマンドを実行
 * @param {string} command 実行するコマンド
 * @param {object} [options] オプション
 * @param {boolean} [options.ignoreError] エラーを無視するか
 * @param {boolean} [options.capture] 出力を文字列で返すか（true: pipe, false: inherit）
 * @param {string} [options.prefix] 環境変数プレフィックス（デフォルト: 'PRD'）
 * @returns {string} 標準出力
 */
export function sshExec(command, options = {}) {
  const { host, user, port, keyFile } = getSSHConfig(options.prefix || 'PRD');

  const sshCliArgs = ['-o', 'StrictHostKeyChecking=accept-new', '-p', port];
  if (keyFile) {
    sshCliArgs.push('-i', keyFile);
  }

  const wrappedCommand = `export PATH=/usr/local/bin:$PATH && ${command}`;
  sshCliArgs.push(`${user}@${host}`, wrappedCommand);

  console.log(`SSH: ${command}`);
  try {
    const stdio = options.capture ? 'pipe' : options.ignoreError ? 'pipe' : 'inherit';
    return execFileSync('ssh', sshCliArgs, { stdio, encoding: 'utf-8' }) || '';
  } catch (err) {
    if (options.ignoreError) {
      return '';
    }
    throw err;
  }
}

/**
 * SCP でローカルファイル/ディレクトリをリモートに転送
 * @param {string} localPath ローカルパス
 * @param {string} remotePath リモートパス
 * @param {object} [options] オプション
 * @param {boolean} [options.recursive] ディレクトリを再帰的に転送
 * @param {string} [options.prefix] 環境変数プレフィックス（デフォルト: 'PRD'）
 */
export function scpUpload(localPath, remotePath, options = {}) {
  const { host, user, port, keyFile } = getSSHConfig(options.prefix || 'PRD');

  const scpCliArgs = ['-O', '-o', 'StrictHostKeyChecking=accept-new', '-P', port];
  if (keyFile) {
    scpCliArgs.push('-i', keyFile);
  }
  if (options.recursive) {
    scpCliArgs.push('-r');
  }

  const normalizedPath = localPath.replace(/\\/g, '/');
  scpCliArgs.push(normalizedPath, `${user}@${host}:${remotePath}`);
  console.log(`SCP: ${localPath} → ${remotePath}`);
  execFileSync('scp', scpCliArgs, { stdio: 'inherit' });
}

/**
 * SCP でリモートファイル/ディレクトリをローカルにダウンロード
 * @param {string} remotePath リモートパス
 * @param {string} localPath ローカルパス
 * @param {object} [options] オプション
 * @param {boolean} [options.recursive] ディレクトリを再帰的に転送
 * @param {string} [options.prefix] 環境変数プレフィックス（デフォルト: 'PRD'）
 */
export function scpDownload(remotePath, localPath, options = {}) {
  const { host, user, port, keyFile } = getSSHConfig(options.prefix || 'PRD');

  const scpCliArgs = ['-O', '-o', 'StrictHostKeyChecking=accept-new', '-P', port];
  if (keyFile) {
    scpCliArgs.push('-i', keyFile);
  }
  if (options.recursive) {
    scpCliArgs.push('-r');
  }

  scpCliArgs.push(`${user}@${host}:${remotePath}`, localPath);
  console.log(`SCP: ${host}:${remotePath} → ${localPath}`);
  execFileSync('scp', scpCliArgs, { stdio: 'inherit' });
}

// ============================================
// Gulp タスク登録（最小限）
// ============================================

/**
 * SSH 運用タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 * @param {{ prefix?: string }} [options] - オプション
 * @param {string} [options.prefix='PRD'] - 環境変数プレフィックス
 */
export default function (gulp, options = {}) {
  const prefix = options.prefix || 'PRD';

  gulp.task('ssh:ping', (done) => {
    try {
      const { host } = getSSHConfig(prefix);
      console.log(`=== ${host} への SSH 接続テスト ===`);
      sshExec('echo "SSH 接続成功: $(hostname) / $(date)"', { prefix });
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('ssh:login', (done) => {
    try {
      const { host, user, port, keyFile } = getSSHConfig(prefix);
      console.log(`=== ${host} にログイン ===`);

      const sshArgs = ['-o StrictHostKeyChecking=accept-new', `-p ${port}`];
      if (keyFile) {
        sshArgs.push(`-i "${keyFile}"`);
      }

      execSync(`ssh ${sshArgs.join(' ')} ${user}@${host}`, { stdio: 'inherit' });
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('ssh:info', (done) => {
    try {
      const { host } = getSSHConfig(prefix);
      console.log(`=== ${host} サーバー情報 ===`);
      sshExec([
        'echo "--- ホスト名 ---" && hostname',
        'echo "--- OS ---" && uname -a',
        'echo "--- ディスク使用量 ---" && df -h / 2>/dev/null',
        'echo "--- メモリ ---" && free -h 2>/dev/null || echo "free コマンドなし"',
      ].join(' && '), { prefix });
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('ssh:help', (done) => {
    console.log(`
SSH 運用タスク一覧
==================

  ssh:ping    SSH 接続テスト
  ssh:login   SSH ログイン（対話シェル）
  ssh:info    サーバー情報表示

環境変数（.env に設定）:
  <PREFIX>_SSH_HOST   SSH ホスト（必須）
  <PREFIX>_SSH_USER   SSH ユーザー名（必須）
  <PREFIX>_SSH_PORT   SSH ポート（デフォルト: 22）
  <PREFIX>_SSH_KEY    SSH 鍵ファイルパス

  ※ PREFIX のデフォルトは PRD（例: PRD_SSH_HOST）

ヘルパー関数（import して利用）:
  getSSHConfig(prefix)                  接続パラメータ取得
  sshExec(command, options)             リモートコマンド実行
  scpUpload(localPath, remotePath, options)    ファイルアップロード
  scpDownload(remotePath, localPath, options)  ファイルダウンロード
`);
    done();
  });
}
