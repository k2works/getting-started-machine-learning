'use strict';

import path from 'path';
import fs from 'fs';
import readline from 'readline';
import { execSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

/**
 * プロジェクト定義を取得
 * sonarqube.config.json から読み込み、なければデフォルトを返す
 *
 * sonarqube.config.json のフォーマット:
 * {
 *   "projects": [
 *     { "name": "backend", "label": "Backend", "projectKey": "my-backend", "scanType": "sonar-scanner", "srcDir": "apps/backend" },
 *     { "name": "frontend", "label": "Frontend", "projectKey": "my-frontend", "scanType": "sonar-scanner", "srcDir": "apps/frontend" }
 *   ]
 * }
 *
 * scanType: "sonar-scanner" (npx sonarqube-scanner) | "sbt" (sbt sonarScan) | "maven" (mvn sonar:sonar) | "gradle" (gradle sonar)
 */
function loadProjects() {
  const configPath = path.join(process.cwd(), 'sonarqube.config.json');
  if (fs.existsSync(configPath)) {
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    return config.projects || [];
  }
  // デフォルト: 単一プロジェクト（sonar-scanner）
  return [
    {
      name: 'app',
      label: 'Application',
      projectKey: process.env.SONAR_PROJECT_KEY || 'my-project',
      scanType: 'sonar-scanner',
      srcDir: '.',
    },
  ];
}

/** SonarQube ポート */
function sonarPort() {
  return process.env.LOCAL_SONAR_PORT || '9000';
}

/** DB パスワード */
function sonarDbPassword() {
  return process.env.LOCAL_SONAR_DB_PASSWORD || 'sonarqube_password';
}

/** SonarQube ホスト URL */
function sonarHostUrl() {
  return process.env.SONAR_HOST_URL || `http://localhost:${sonarPort()}`;
}

/** docker-compose.yml のディレクトリ */
function composeDir() {
  return path.join(process.cwd(), 'ops', 'docker', 'sonarqube-local');
}

// ============================================
// ヘルパー関数
// ============================================

/**
 * ローカルコマンドを実行
 * @param {string} command - 実行するコマンド
 * @param {Object} [opts] - オプション
 * @param {boolean} [opts.ignoreError] - エラーを無視するか
 * @param {string} [opts.cwd] - 作業ディレクトリ
 */
function localExec(command, opts = {}) {
  try {
    execSync(command, {
      stdio: 'inherit',
      shell: true,
      env: cleanDockerEnv(),
      ...(opts.cwd ? { cwd: opts.cwd } : {}),
    });
  } catch (error) {
    if (!opts.ignoreError) {
      throw error;
    }
  }
}

/**
 * docker compose コマンドを構築・実行
 * @param {string} subcommand - docker compose サブコマンド
 * @param {Object} [opts] - オプション
 */
function dockerCompose(subcommand, opts = {}) {
  const dir = composeDir();
  localExec(`docker compose -f "${dir}/docker-compose.yml" ${subcommand}`, opts);
}

/**
 * 対話的な確認プロンプト
 * @param {string} question - 質問文
 * @returns {Promise<boolean>}
 */
function confirm(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim().toLowerCase() === 'y');
    });
  });
}

/**
 * docker-compose.yml の内容を生成
 * @returns {string} YAML 文字列
 */
function generateSonarComposeContent() {
  const dbPassword = sonarDbPassword();
  const port = sonarPort();

  return `services:
  sonarqube:
    image: sonarqube:community
    container_name: sonarqube
    restart: unless-stopped
    depends_on:
      sonarqube-db:
        condition: service_healthy
    ports:
      - "${port}:9000"
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://sonarqube-db:5432/sonarqube
      SONAR_JDBC_USERNAME: sonarqube
      SONAR_JDBC_PASSWORD: ${dbPassword}
      SONAR_CE_JAVAOPTS: "-Xmx512m -Xms512m"
      SONAR_WEB_JAVAOPTS: "-Xmx256m -Xms256m"
      SONAR_SEARCH_JAVAOPTS: "-Xmx512m -Xms512m"
    volumes:
      - sonarqube_data:/opt/sonarqube/data
      - sonarqube_logs:/opt/sonarqube/logs
      - sonarqube_extensions:/opt/sonarqube/extensions
    networks:
      - sonarqube-net
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9000/api/system/status || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 300s

  sonarqube-db:
    image: postgres:16-alpine
    container_name: sonarqube-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: sonarqube
      POSTGRES_USER: sonarqube
      POSTGRES_PASSWORD: ${dbPassword}
    volumes:
      - sonarqube_postgresql:/var/lib/postgresql/data
    networks:
      - sonarqube-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U sonarqube"]
      interval: 10s
      timeout: 5s
      retries: 5

networks:
  sonarqube-net:
    driver: bridge

volumes:
  sonarqube_data:
  sonarqube_logs:
  sonarqube_extensions:
  sonarqube_postgresql:
`;
}

/**
 * 指定ミリ秒だけ同期的にスリープ
 * @param {number} ms - 待機ミリ秒
 */
function sleepSync(ms) {
  execSync(`node -e "setTimeout(()=>{},${ms})"`, { stdio: 'ignore' });
}

/**
 * ヘルスチェック待機（SonarQube コンテナ）
 */
function waitForSonarHealthy() {
  console.log('ヘルスチェック待機中（最大 5 分）...');
  const maxRetries = 30;
  for (let i = 1; i <= maxRetries; i++) {
    let status = 'not found';
    try {
      status = execSync(
        'docker inspect --format="{{.State.Health.Status}}" sonarqube',
        { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] },
      ).trim().replace(/"/g, '');
    } catch {
      // コンテナが見つからない場合
    }
    if (status === 'healthy') {
      console.log('  sonarqube: healthy');
      return;
    }
    if (i === maxRetries) {
      console.log(`  sonarqube: timeout (status=${status})`);
      return;
    }
    sleepSync(10000);
  }
}

/**
 * SonarQube プロジェクトキーを取得
 * 環境変数 SONAR_PROJECT_KEY で指定可能
 * @returns {string} プロジェクトキー
 */
function sonarProjectKey() {
  const projects = loadProjects();
  return process.env.SONAR_PROJECT_KEY || (projects.length > 0 ? projects[0].projectKey : 'my-project');
}

/**
 * SonarQube API を呼び出す
 * @param {string} apiPath - API パス（例: /api/qualitygates/project_status）
 * @param {Object} [params] - クエリパラメータ
 * @returns {Object} レスポンス JSON
 */
function sonarApi(apiPath, params = {}) {
  const token = requireSonarToken();
  const hostUrl = sonarHostUrl();
  const qs = new URLSearchParams(params).toString();
  const url = `${hostUrl}${apiPath}${qs ? '?' + qs : ''}`;
  const auth = Buffer.from(`${token}:`).toString('base64');
  const result = execSync(
    `curl -sf -H "Authorization: Basic ${auth}" "${url}"`,
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], shell: true, env: cleanDockerEnv() },
  );
  return JSON.parse(result);
}

/**
 * SonarQube トークンの検証
 * @returns {string} トークン
 */
function requireSonarToken() {
  const token = process.env.SONAR_TOKEN;
  if (!token) {
    throw new Error('SONAR_TOKEN を .env に設定してください');
  }
  return token;
}

/**
 * スキャンタイプに応じたスキャンコマンドを実行
 * @param {Object} project - プロジェクト定義
 * @param {string} token - SonarQube トークン
 * @param {string} hostUrl - SonarQube ホスト URL
 */
function runScan(project, token, hostUrl) {
  const cwd = path.join(process.cwd(), project.srcDir);

  switch (project.scanType) {
    case 'sbt':
      execSync(
        `sbt -Dsonar.host.url=${hostUrl} -Dsonar.token=${token} sonarScan`,
        { stdio: 'inherit', cwd, shell: true, env: cleanDockerEnv() },
      );
      break;

    case 'maven':
      execSync(
        `mvn sonar:sonar ` +
        `-Dsonar.projectKey=${project.projectKey} ` +
        `-Dsonar.projectName="${project.label}" ` +
        `-Dsonar.host.url=${hostUrl} ` +
        `-Dsonar.token=${token}`,
        { stdio: 'inherit', cwd, shell: true, env: cleanDockerEnv() },
      );
      break;

    case 'gradle':
      execSync(
        `gradle sonar ` +
        `-Dsonar.projectKey=${project.projectKey} ` +
        `-Dsonar.projectName="${project.label}" ` +
        `-Dsonar.host.url=${hostUrl} ` +
        `-Dsonar.token=${token}`,
        { stdio: 'inherit', cwd, shell: true, env: cleanDockerEnv() },
      );
      break;

    case 'sonar-scanner':
    default:
      execSync(
        `npx sonarqube-scanner ` +
        `-Dsonar.projectKey=${project.projectKey} ` +
        `-Dsonar.projectName="${project.label}" ` +
        `-Dsonar.sources=src ` +
        `-Dsonar.tests=src ` +
        `-Dsonar.test.inclusions="**/*.test.ts,**/*.test.tsx,**/*.spec.ts,**/*.spec.tsx" ` +
        `-Dsonar.host.url=${hostUrl} ` +
        `-Dsonar.token=${token}`,
        { stdio: 'inherit', cwd, env: cleanDockerEnv() },
      );
      break;
  }
}

// ============================================
// Gulp タスク登録
// ============================================

/**
 * SonarQube ローカルタスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  // ──────────────────────────────────────────────
  // 初回セットアップ
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:setup', (done) => {
    try {
      const dir = composeDir();

      console.log('=== SonarQube ローカル セットアップ開始 ===');

      // 1. Docker 動作確認
      console.log('[1/4] Docker の動作を確認...');
      const devNull = process.platform === 'win32' ? 'NUL' : '/dev/null';
      localExec(`docker info > ${devNull} 2>&1`);
      console.log('  Docker: OK');

      // 2. ディレクトリ作成
      console.log('[2/4] ディレクトリを作成...');
      fs.mkdirSync(dir, { recursive: true });

      // 3. docker-compose.yml 配置
      console.log('[3/4] docker-compose.yml を配置...');
      const composePath = path.join(dir, 'docker-compose.yml');
      fs.writeFileSync(composePath, generateSonarComposeContent(), 'utf8');

      // 4. コンテナ起動 & ヘルスチェック待機
      console.log('[4/4] コンテナを起動...');
      dockerCompose('up -d');
      waitForSonarHealthy();

      // 完了メッセージ
      const hostUrl = sonarHostUrl();
      console.log('');
      console.log('=== SonarQube ローカル セットアップ完了 ===');
      console.log(`  URL: ${hostUrl}`);
      console.log('  初期ログイン: admin / admin');
      console.log('');
      console.log('次のステップ:');
      console.log('  1. ブラウザで上記 URL にアクセス');
      console.log('  2. admin パスワードを変更');
      console.log('  3. 分析トークンを生成');
      console.log('  4. .env に SONAR_TOKEN=<トークン> を追加');
      console.log('  5. .env に SONAR_HOST_URL=http://localhost:9000 を追加');
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // コンテナ操作
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:open', (done) => {
    try {
      const hostUrl = sonarHostUrl();
      let command;
      if (process.platform === 'win32') {
        command = `start "" "${hostUrl}"`;
      } else if (process.platform === 'linux') {
        command = `xdg-open "${hostUrl}"`;
      } else {
        command = `open "${hostUrl}"`;
      }
      console.log(`=== SonarQube ダッシュボードを開く: ${hostUrl} ===`);
      localExec(command);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:start', (done) => {
    try {
      console.log('=== SonarQube ローカル 起動 ===');
      dockerCompose('up -d');
      console.log('=== SonarQube ローカル 起動完了 ===');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:stop', (done) => {
    try {
      console.log('=== SonarQube ローカル 停止 ===');
      dockerCompose('down');
      console.log('=== SonarQube ローカル 停止完了 ===');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:restart', (done) => {
    try {
      console.log('=== SonarQube ローカル 再起動 ===');
      dockerCompose('restart');
      console.log('=== SonarQube ローカル 再起動完了 ===');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:status', (done) => {
    try {
      console.log('=== SonarQube ローカル コンテナ状態 ===');
      dockerCompose('ps');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:logs', (done) => {
    try {
      dockerCompose('logs --tail=50');
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // 環境完全削除
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:clean', async (done) => {
    try {
      console.log('');
      console.log('SonarQube ローカル環境を完全削除します。');
      console.log('  Docker ボリューム（データ）も削除されます。');
      console.log('');
      const ok = await confirm('実行しますか？ (y/N): ');
      if (!ok) {
        console.log('キャンセルしました。');
        done();
        return;
      }

      console.log('=== SonarQube ローカル クリーン開始 ===');

      console.log('[1/1] Docker コンテナ・ボリュームを停止・削除...');
      dockerCompose('down --rmi all -v', { ignoreError: true });

      console.log('');
      console.log('=== SonarQube ローカル クリーン完了 ===');
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // スキャン（個別プロジェクト）
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:scan', (done) => {
    try {
      const token = requireSonarToken();
      const hostUrl = sonarHostUrl();
      const projects = loadProjects();

      if (projects.length === 0) {
        console.log('スキャン対象のプロジェクトが定義されていません。');
        console.log('sonarqube.config.json を作成するか、環境変数 SONAR_PROJECT_KEY を設定してください。');
        done();
        return;
      }

      for (const project of projects) {
        console.log(`=== ${project.label} SonarQube スキャン（ローカル） ===`);
        runScan(project, token, hostUrl);
        console.log(`=== ${project.label} スキャン完了 ===`);
        console.log(`  結果: ${hostUrl}/dashboard?id=${project.projectKey}`);
      }

      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // Quality Gate / イシュー確認
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:gate', (done) => {
    try {
      const projectKey = sonarProjectKey();
      console.log('=== Quality Gate ステータス確認 ===');

      const data = sonarApi('/api/qualitygates/project_status', { projectKey });
      const status = data.projectStatus?.status || 'UNKNOWN';
      const conditions = data.projectStatus?.conditions || [];

      const icon = status === 'OK' ? 'PASS' : 'FAIL';
      console.log(`  Quality Gate: ${icon} (${status})`);

      if (conditions.length > 0) {
        console.log('');
        console.log('  条件:');
        for (const c of conditions) {
          const condIcon = c.status === 'OK' ? 'o' : 'x';
          const actual = c.actualValue ?? '-';
          const threshold = c.errorThreshold ?? '-';
          console.log(`    [${condIcon}] ${c.metricKey}: ${actual} (閾値: ${threshold})`);
        }
      }

      console.log('');
      if (status !== 'OK') {
        console.log('  Quality Gate を通過していません。sonar-local:issues で詳細を確認してください。');
      }
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('sonar-local:issues', (done) => {
    try {
      const projectKey = sonarProjectKey();
      const hostUrl = sonarHostUrl();
      console.log('=== SonarQube プロジェクトサマリー ===');

      // メトリクスを取得
      const metricsData = sonarApi('/api/measures/component', {
        component: projectKey,
        metricKeys: [
          'bugs', 'vulnerabilities', 'code_smells', 'security_hotspots',
          'coverage', 'duplicated_lines_density', 'duplicated_blocks',
          'ncloc', 'reliability_issues', 'maintainability_issues', 'security_issues',
        ].join(','),
      });

      const measures = metricsData.component?.measures || [];
      const metric = (key) => {
        const m = measures.find((x) => x.metric === key);
        return m?.value ?? '-';
      };

      console.log('');
      console.log('── メトリクス ──');
      console.log(`  コード行数        : ${metric('ncloc')}`);
      console.log(`  カバレッジ        : ${metric('coverage')}%`);
      console.log(`  重複率            : ${metric('duplicated_lines_density')}%`);
      console.log(`  重複ブロック      : ${metric('duplicated_blocks')}`);
      console.log(`  Bug               : ${metric('bugs')}`);
      console.log(`  Vulnerability     : ${metric('vulnerabilities')}`);
      console.log(`  Code Smell        : ${metric('code_smells')}`);
      console.log(`  Security Hotspot  : ${metric('security_hotspots')}`);
      console.log('');

      // 重複コードの詳細を取得
      const dupDensity = parseFloat(metric('duplicated_lines_density'));
      if (dupDensity > 0) {
        console.log('── 重複コード ──');
        try {
          const dupData = sonarApi('/api/duplications/show', {
            key: projectKey,
          });
          const duplications = dupData.duplications || [];
          const files = dupData.files || {};
          if (duplications.length > 0) {
            for (const dup of duplications) {
              const blocks = dup.blocks || [];
              const blockDescs = blocks.map((b) => {
                const file = files[b._ref]
                  ? files[b._ref].name
                  : b._ref;
                return `  ${file}:${b.from}-${b.from + b.size - 1} (${b.size} 行)`;
              });
              console.log('  重複:');
              for (const desc of blockDescs) {
                console.log(`    ${desc}`);
              }
            }
          } else {
            console.log(`  重複率 ${dupDensity}% — 詳細はダッシュボードを参照`);
          }
        } catch {
          console.log(`  重複率 ${dupDensity}% — 詳細はダッシュボードを参照`);
        }
        console.log('');
      }

      // 未解決のイシューを取得（最大 500 件）
      const data = sonarApi('/api/issues/search', {
        componentKeys: projectKey,
        resolved: 'false',
        ps: '500',
      });

      const total = data.total || 0;
      const issues = data.issues || [];

      if (total === 0) {
        console.log('── イシュー ──');
        console.log('  未解決のイシューはありません。');
      } else {
        console.log(`── イシュー (${total} 件) ──`);

        // 種別ごとに集計
        const byType = {};
        for (const issue of issues) {
          const type = issue.type || 'UNKNOWN';
          if (!byType[type]) byType[type] = [];
          byType[type].push(issue);
        }

        const typeLabels = {
          BUG: 'Bug',
          VULNERABILITY: 'Vulnerability',
          CODE_SMELL: 'Code Smell',
          SECURITY_HOTSPOT: 'Security Hotspot',
        };

        for (const [type, items] of Object.entries(byType)) {
          const label = typeLabels[type] || type;
          console.log(`  ${label} (${items.length}):`);
          for (const issue of items) {
            const component = (issue.component || '').replace(`${projectKey}:`, '');
            const line = issue.line ? `:${issue.line}` : '';
            const severity = issue.severity || '';
            console.log(`    [${severity}] ${component}${line}`);
            console.log(`      ${issue.message}`);
          }
        }
      }

      console.log('');
      console.log(`詳細: ${hostUrl}/dashboard?id=${projectKey}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // スキャン → Quality Gate 確認の一連フロー
  gulp.task('sonar-local:check', gulp.series('sonar-local:scan', 'sonar-local:gate'));

  // ──────────────────────────────────────────────
  // ヘルプ
  // ──────────────────────────────────────────────

  gulp.task('sonar-local:help', (done) => {
    const projects = loadProjects();
    const projectList = projects.map((p) => `    - ${p.name}: ${p.label} (${p.scanType})`).join('\n');

    console.log(`
SonarQube ローカル タスク一覧
================================

セットアップ:
  sonar-local:setup          初回セットアップ（ローカルに SonarQube を構築）

コンテナ操作:
  sonar-local:open           ダッシュボードをブラウザで開く
  sonar-local:start          コンテナ起動
  sonar-local:stop           コンテナ停止
  sonar-local:restart        コンテナ再起動
  sonar-local:status         コンテナ状態確認
  sonar-local:logs           ログ表示（直近 50 行）

スキャン:
  sonar-local:scan           全プロジェクトのスキャン実行

分析:
  sonar-local:gate           Quality Gate ステータス確認
  sonar-local:issues         メトリクス・イシュー・重複コード詳細表示
  sonar-local:check          スキャン → Quality Gate 確認の一連フロー

管理:
  sonar-local:clean          環境完全削除（確認プロンプト付き）
  sonar-local:help           このヘルプを表示

登録プロジェクト:
${projectList}

設定ファイル:
  sonarqube.config.json      プロジェクト定義（プロジェクトルートに配置）

環境変数（.env に設定）:
  LOCAL_SONAR_PORT           SonarQube ポート（デフォルト: 9000）
  LOCAL_SONAR_DB_PASSWORD    DB パスワード（デフォルト: sonarqube_password）
  SONAR_HOST_URL             SonarQube URL（デフォルト: http://localhost:9000）
  SONAR_TOKEN                分析トークン（スキャン時に必須）
  SONAR_PROJECT_KEY          Quality Gate / Issues 確認対象のプロジェクトキー

典型的なフロー:
  1. sonarqube.config.json を作成（任意）
  2. npx gulp sonar-local:setup        # ローカルに SonarQube を構築
  3. ブラウザで初期設定・トークン生成
  4. .env に SONAR_TOKEN を追加
  5. npx gulp sonar-local:scan         # 全プロジェクトをスキャン
`);
    done();
  });
}
