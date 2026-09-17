'use strict';

import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

// ============================================
// 設定
// ============================================

/**
 * リリース対象のパッケージディレクトリ一覧を返す。
 * モノレポの場合は apps/packages 配下を追加する。
 * @param {string} rootDir - プロジェクトルート
 * @returns {string[]} パッケージディレクトリの絶対パス配列
 */
function discoverPackageDirs(rootDir) {
  const dirs = [rootDir];
  const candidates = ['apps', 'packages'];

  for (const candidate of candidates) {
    const dir = path.join(rootDir, candidate);
    if (!fs.existsSync(dir)) continue;
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const pkgJson = path.join(dir, entry.name, 'package.json');
      if (fs.existsSync(pkgJson)) {
        dirs.push(path.join(dir, entry.name));
      }
    }
  }

  return dirs;
}

// ============================================
// ヘルパー関数
// ============================================

/**
 * root の package.json からバージョンを読み取る
 * @param {string} rootDir - プロジェクトルート
 * @returns {string} 現在のバージョン (例: "0.1.0")
 */
function getCurrentVersion(rootDir) {
  const pkg = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'));
  return pkg.version;
}

/**
 * ドライラン用の簡易 semver 計算
 * @param {string} version - 現在のバージョン (例: "0.1.0")
 * @param {string} type - バンプの種類 ("patch" | "minor" | "major")
 * @returns {string} 新しいバージョン
 */
function semverBump(version, type) {
  const [major, minor, patch] = version.split('.').map(Number);
  switch (type) {
    case 'major':
      return `${major + 1}.0.0`;
    case 'minor':
      return `${major}.${minor + 1}.0`;
    case 'patch':
      return `${major}.${minor}.${patch + 1}`;
    default:
      throw new Error(`Unknown bump type: ${type}`);
  }
}

/**
 * 対象パッケージのバージョンを同期更新
 * @param {string} rootDir - プロジェクトルート
 * @param {string} type - バンプの種類 ("patch" | "minor" | "major")
 * @returns {string} 新しいバージョン
 */
function bumpVersions(rootDir, type) {
  const dirs = discoverPackageDirs(rootDir);

  for (const dir of dirs) {
    execSync(`npm version ${type} --no-git-tag-version`, {
      cwd: dir,
      stdio: 'pipe',
    });
  }

  return getCurrentVersion(rootDir);
}

/**
 * 直近タグから HEAD までの git log を Conventional Commits で分類し Markdown を生成
 * @param {string} rootDir - プロジェクトルート
 * @param {string} version - リリースバージョン
 * @returns {string} CHANGELOG エントリ（Markdown）
 */
function generateChangelog(rootDir, version) {
  let lastTag;
  try {
    lastTag = execSync('git describe --tags --abbrev=0', {
      cwd: rootDir,
      stdio: 'pipe',
      encoding: 'utf8',
    }).trim();
  } catch {
    lastTag = null;
  }

  const range = lastTag ? `${lastTag}..HEAD` : 'HEAD';
  let logOutput;
  try {
    logOutput = execSync(`git log ${range} --pretty=format:"%h|||%s"`, {
      cwd: rootDir,
      stdio: 'pipe',
      encoding: 'utf8',
    }).trim();
  } catch {
    logOutput = '';
  }

  if (!logOutput) {
    return buildChangelogEntry(version, {});
  }

  const categories = {
    feat: { title: 'Features', commits: [] },
    fix: { title: 'Bug Fixes', commits: [] },
    docs: { title: 'Documentation', commits: [] },
    refactor: { title: 'Refactoring', commits: [] },
    test: { title: 'Tests', commits: [] },
    chore: { title: 'Chores', commits: [] },
    perf: { title: 'Performance', commits: [] },
    ci: { title: 'CI', commits: [] },
    style: { title: 'Styles', commits: [] },
    build: { title: 'Build', commits: [] },
    other: { title: 'Other', commits: [] },
  };

  for (const line of logOutput.split('\n')) {
    if (!line.trim()) continue;
    const [hash, ...rest] = line.split('|||');
    const subject = rest.join('|||');
    const match = subject.match(/^(\w+)(?:\(.+?\))?:\s*(.+)$/);
    if (match) {
      const type = match[1].toLowerCase();
      const message = match[2];
      if (categories[type]) {
        categories[type].commits.push({ hash: hash.trim(), message });
      } else {
        categories.other.commits.push({ hash: hash.trim(), message: subject });
      }
    } else {
      categories.other.commits.push({ hash: hash.trim(), message: subject });
    }
  }

  return buildChangelogEntry(version, categories);
}

/**
 * CHANGELOG エントリを Markdown で構築
 * @param {string} version
 * @param {Object} categories
 * @returns {string}
 */
function buildChangelogEntry(version, categories) {
  const date = new Date().toISOString().slice(0, 10);
  const lines = [`## [${version}] - ${date}`, ''];

  let hasContent = false;
  for (const [, cat] of Object.entries(categories)) {
    if (cat.commits && cat.commits.length > 0) {
      hasContent = true;
      lines.push(`### ${cat.title}`, '');
      for (const c of cat.commits) {
        lines.push(`- ${c.message} (${c.hash})`);
      }
      lines.push('');
    }
  }

  if (!hasContent) {
    lines.push('- No notable changes', '');
  }

  return lines.join('\n');
}

/**
 * CHANGELOG.md の先頭にエントリを追加（なければ新規作成）
 * @param {string} rootDir - プロジェクトルート
 * @param {string} entry - 追加する CHANGELOG エントリ
 */
function updateChangelog(rootDir, entry) {
  const changelogPath = path.join(rootDir, 'CHANGELOG.md');
  const header = '# Changelog\n\n';

  if (fs.existsSync(changelogPath)) {
    const existing = fs.readFileSync(changelogPath, 'utf8');
    const withoutHeader = existing.replace(/^# Changelog\s*\n*/, '');
    fs.writeFileSync(changelogPath, header + entry + '\n' + withoutHeader);
  } else {
    fs.writeFileSync(changelogPath, header + entry);
  }
}

/**
 * 変更ファイルをステージング → commit → tag
 * @param {string} rootDir - プロジェクトルート
 * @param {string} version - リリースバージョン
 */
function createGitCommitAndTag(rootDir, version) {
  const filesToStage = collectFilesToStage(rootDir);

  for (const file of filesToStage) {
    execSync(`git add "${file}"`, { cwd: rootDir, stdio: 'pipe' });
  }

  execSync(`git commit -m "release: v${version}"`, {
    cwd: rootDir,
    stdio: 'pipe',
    env: { ...process.env, USER_APPROVED_COMMIT: '1' },
  });

  execSync(`git tag -a "v${version}" -m "v${version}"`, {
    cwd: rootDir,
    stdio: 'pipe',
  });
}

/**
 * ステージング対象のファイルパスを収集
 * @param {string} rootDir - プロジェクトルート
 * @returns {string[]} 相対パスの配列
 */
function collectFilesToStage(rootDir) {
  const files = ['package.json', 'CHANGELOG.md'];
  const dirs = discoverPackageDirs(rootDir);

  for (const dir of dirs) {
    if (dir === rootDir) continue;
    const relative = path.relative(rootDir, dir);
    files.push(path.join(relative, 'package.json'));
  }

  return files;
}

/**
 * リリース結果のサマリーを表示
 * @param {string} oldVersion - 旧バージョン
 * @param {string} newVersion - 新バージョン
 */
function printSummary(oldVersion, newVersion) {
  console.log('');
  console.log('=== Release Summary ===');
  console.log(`  Version: ${oldVersion} -> ${newVersion}`);
  console.log(`  Tag:     v${newVersion}`);
  console.log(`  Commit:  release: v${newVersion}`);
  console.log('');
  console.log('Next steps:');
  console.log('  git push && git push --tags');
  console.log('');
}

// ============================================
// コマンド実行ヘルパー
// ============================================

/**
 * シェルコマンドを実行し、失敗時に done コールバックへエラーを渡す
 * @param {string} command - 実行するコマンド
 * @param {string} rootDir - 実行ディレクトリ
 * @param {Function} done - コールバック
 */
function runCommandInDir(command, rootDir, done) {
  try {
    execSync(command, { cwd: rootDir, stdio: 'inherit' });
    done();
  } catch (error) {
    done(error);
  }
}

// ============================================
// タスク登録
// ============================================

/**
 * 指定されたタイプのリリースタスクを生成
 * @param {string} rootDir - プロジェクトルート
 * @param {string} type - "patch" | "minor" | "major"
 * @returns {Function} gulp タスク関数
 */
function createReleaseTask(rootDir, type) {
  const task = (done) => {
    try {
      const oldVersion = getCurrentVersion(rootDir);
      console.log(`[1/4] バージョンを更新 (${type})...`);
      const newVersion = bumpVersions(rootDir, type);

      console.log('[2/4] CHANGELOG を生成...');
      const entry = generateChangelog(rootDir, newVersion);
      updateChangelog(rootDir, entry);

      console.log('[3/4] git commit + tag...');
      createGitCommitAndTag(rootDir, newVersion);

      console.log('[4/4] サマリー表示');
      printSummary(oldVersion, newVersion);

      done();
    } catch (error) {
      done(error);
    }
  };
  task.displayName = `release:${type}:execute`;
  return task;
}

/**
 * リリースタスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 * @param {{ rootDir?: string }} [options] - オプション
 * @param {string} [options.rootDir] - プロジェクトルート（省略時は gulpfile の 2 階層上）
 */
export default function (gulp, options = {}) {
  const rootDir = options.rootDir || path.resolve(process.cwd());

  // ──────────────────────────────────────────────
  // preflight: 品質ゲート
  // ──────────────────────────────────────────────

  gulp.task('release:preflight:clean', (done) => {
    console.log('=== Release Preflight ===');
    console.log('[1/5] Working tree がクリーンか確認...');
    try {
      const status = execSync('git status --porcelain', {
        cwd: rootDir,
        stdio: 'pipe',
        encoding: 'utf8',
      }).trim();
      if (status) {
        done(new Error('Working tree is not clean. Commit or stash your changes first.\n' + status));
        return;
      }
      console.log('  -> Clean');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('release:preflight:lint', (done) => {
    console.log('[2/5] Lint...');
    runCommandInDir('npm run lint', rootDir, done);
  });

  gulp.task('release:preflight:test', (done) => {
    console.log('[3/5] Test...');
    runCommandInDir('npm run test', rootDir, done);
  });

  gulp.task('release:preflight:build', (done) => {
    console.log('[4/5] Build...');
    runCommandInDir('npm run build', rootDir, done);
  });

  gulp.task('release:preflight:e2e', (done) => {
    console.log('[5/5] E2E Test...');
    runCommandInDir('npm run test:e2e', rootDir, done);
  });

  gulp.task(
    'release:preflight',
    gulp.series(
      'release:preflight:clean',
      'release:preflight:lint',
      'release:preflight:test',
      'release:preflight:build',
      'release:preflight:e2e'
    )
  );

  // ──────────────────────────────────────────────
  // release:patch / minor / major
  // ──────────────────────────────────────────────

  gulp.task('release:patch', gulp.series('release:preflight', createReleaseTask(rootDir, 'patch')));
  gulp.task('release:minor', gulp.series('release:preflight', createReleaseTask(rootDir, 'minor')));
  gulp.task('release:major', gulp.series('release:preflight', createReleaseTask(rootDir, 'major')));

  // ──────────────────────────────────────────────
  // release:dry-run
  // ──────────────────────────────────────────────

  gulp.task('release:dry-run', (done) => {
    try {
      const currentVersion = getCurrentVersion(rootDir);
      console.log('=== Release Dry Run ===');
      console.log('');
      console.log(`Current version: ${currentVersion}`);
      console.log('');
      console.log('Version preview:');
      console.log(`  patch: ${currentVersion} -> ${semverBump(currentVersion, 'patch')}`);
      console.log(`  minor: ${currentVersion} -> ${semverBump(currentVersion, 'minor')}`);
      console.log(`  major: ${currentVersion} -> ${semverBump(currentVersion, 'major')}`);
      console.log('');
      console.log('CHANGELOG preview:');
      console.log('---');
      const entry = generateChangelog(rootDir, semverBump(currentVersion, 'patch'));
      console.log(entry);
      console.log('---');
      console.log('');
      console.log('To release, run:');
      console.log('  npm run release:patch   # patch release');
      console.log('  npm run release:minor   # minor release');
      console.log('  npm run release:major   # major release');
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // release:deploy:* (release + deploy)
  // ──────────────────────────────────────────────

  gulp.task('release:deploy:patch', gulp.series('release:patch', 'deploy:prd'));
  gulp.task('release:deploy:minor', gulp.series('release:minor', 'deploy:prd'));
  gulp.task('release:deploy:major', gulp.series('release:major', 'deploy:prd'));
}
