'use strict';

import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

/**
 * docker compose コマンドを実行
 * @param {string} args - docker compose に渡す引数
 */
function dockerCompose(args) {
  execSync(`docker compose ${args}`, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Docker が利用可能か確認し、不可なら警告メッセージを表示して false を返す
 * @returns {boolean} Docker が利用可能なら true
 */
function requireDocker() {
  if (isDockerAvailable()) {
    return true;
  }
  console.warn('Warning: Docker is not running. Skipping this task.');
  console.warn('Please start Docker Desktop and try again.');
  return false;
}

/**
 * MkDocs タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('mkdocs:serve', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Starting MkDocs server...');
      dockerCompose('up -d mkdocs');
      console.log('\nDocumentation is available at http://localhost:8000');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('mkdocs:build', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Building MkDocs documentation...');
      const siteDir = path.join(process.cwd(), 'site');
      if (fs.existsSync(siteDir)) {
        fs.rmSync(siteDir, { recursive: true, force: true });
      }
      dockerCompose('run --rm mkdocs mkdocs build');
      console.log('\nBuild completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('mkdocs:stop', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Stopping MkDocs server...');
      dockerCompose('down');
      console.log('Stopped.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('mkdocs:open', (done) => {
    try {
      openUrl('http://localhost:8000');
      done();
    } catch (error) {
      done(error);
    }
  });
}
