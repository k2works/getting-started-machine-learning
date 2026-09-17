'use strict';

import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import readline from 'readline';

/**
 * Vault - .env ファイルの暗号化・復号化ユーティリティ
 *
 * 暗号化アルゴリズム: AES-256-GCM
 * 鍵導出: PBKDF2 (SHA-512, 100,000 iterations)
 */

const ALGORITHM = 'aes-256-gcm';
const PBKDF2_ITERATIONS = 100000;
const SALT_LENGTH = 32;
const IV_LENGTH = 16;
const AUTH_TAG_LENGTH = 16;
const KEY_LENGTH = 32;

/**
 * パスワードから暗号化キーを導出
 * @param {string} password - パスワード
 * @param {Buffer} salt - ソルト
 * @returns {Buffer} - 導出されたキー
 */
function deriveKey(password, salt) {
    return crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha512');
}

/**
 * データを暗号化
 * @param {string} plaintext - 平文
 * @param {string} password - パスワード
 * @returns {Buffer} - 暗号化されたデータ (salt + iv + authTag + ciphertext)
 */
function encrypt(plaintext, password) {
    const salt = crypto.randomBytes(SALT_LENGTH);
    const key = deriveKey(password, salt);
    const iv = crypto.randomBytes(IV_LENGTH);

    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    const encrypted = Buffer.concat([
        cipher.update(plaintext, 'utf8'),
        cipher.final()
    ]);
    const authTag = cipher.getAuthTag();

    // フォーマット: salt (32) + iv (16) + authTag (16) + ciphertext
    return Buffer.concat([salt, iv, authTag, encrypted]);
}

/**
 * データを復号化
 * @param {Buffer} encryptedData - 暗号化されたデータ
 * @param {string} password - パスワード
 * @returns {string} - 復号化された平文
 */
function decrypt(encryptedData, password) {
    const salt = encryptedData.subarray(0, SALT_LENGTH);
    const iv = encryptedData.subarray(SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
    const authTag = encryptedData.subarray(SALT_LENGTH + IV_LENGTH, SALT_LENGTH + IV_LENGTH + AUTH_TAG_LENGTH);
    const ciphertext = encryptedData.subarray(SALT_LENGTH + IV_LENGTH + AUTH_TAG_LENGTH);

    const key = deriveKey(password, salt);

    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([
        decipher.update(ciphertext),
        decipher.final()
    ]);

    return decrypted.toString('utf8');
}

/**
 * パスワードをプロンプトで取得（シンプル版）
 * @param {string} prompt - プロンプトメッセージ
 * @returns {Promise<string>} - 入力されたパスワード
 */
function promptPassword(prompt) {
    return new Promise((resolve) => {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });

        rl.question(prompt, (answer) => {
            rl.close();
            resolve(answer);
        });
    });
}

/**
 * 環境変数からパスワードを取得、なければプロンプト
 * @returns {Promise<string>} - パスワード
 */
async function getPassword() {
    const envPassword = process.env.VAULT_PASSWORD;
    if (envPassword) {
        return envPassword;
    }
    return promptPassword('Vault password: ');
}

/**
 * 環境変数からパスワードを取得、なければプロンプト（確認付き）
 * @returns {Promise<string>} - パスワード
 */
async function getPasswordWithConfirm() {
    const envPassword = process.env.VAULT_PASSWORD;
    if (envPassword) {
        return envPassword;
    }

    const password = await promptPassword('New vault password: ');
    const confirm = await promptPassword('Confirm vault password: ');

    if (password !== confirm) {
        throw new Error('Passwords do not match');
    }

    return password;
}

/**
 * 確認プロンプト
 * @param {string} message - メッセージ
 * @returns {Promise<boolean>} - true: yes, false: no
 */
function confirm(message) {
    return new Promise((resolve) => {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });

        rl.question(message, (answer) => {
            rl.close();
            resolve(answer.toLowerCase() === 'y');
        });
    });
}

// Function to register the vault tasks
export default function (gulp) {
    const ENV_FILE = '.env';
    const ENCRYPTED_FILE = '.env.vault';

    // Encrypt .env file
    gulp.task('vault:encrypt', async () => {
        const envPath = path.join(process.cwd(), ENV_FILE);
        const encryptedPath = path.join(process.cwd(), ENCRYPTED_FILE);

        // Check if .env exists
        if (!fs.existsSync(envPath)) {
            throw new Error(`${ENV_FILE} not found`);
        }

        // Read .env content
        const content = fs.readFileSync(envPath, 'utf8');

        // Get password
        const password = await getPasswordWithConfirm();

        if (!password || password.length < 8) {
            throw new Error('Password must be at least 8 characters');
        }

        // Encrypt
        const encrypted = encrypt(content, password);

        // Write encrypted file
        fs.writeFileSync(encryptedPath, encrypted);

        console.log(`\nEncrypted ${ENV_FILE} -> ${ENCRYPTED_FILE}`);
        console.log(`You can now safely commit ${ENCRYPTED_FILE} to version control.`);
        console.log(`\nRemember to keep your password safe!`);
    });

    // Decrypt .env.vault file
    gulp.task('vault:decrypt', async () => {
        const envPath = path.join(process.cwd(), ENV_FILE);
        const encryptedPath = path.join(process.cwd(), ENCRYPTED_FILE);

        // Check if .env.vault exists
        if (!fs.existsSync(encryptedPath)) {
            throw new Error(`${ENCRYPTED_FILE} not found`);
        }

        // Check if .env already exists
        if (fs.existsSync(envPath)) {
            const shouldOverwrite = await confirm(`${ENV_FILE} already exists. Overwrite? (y/N): `);
            if (!shouldOverwrite) {
                console.log('Aborted.');
                return;
            }
        }

        // Read encrypted content
        const encryptedData = fs.readFileSync(encryptedPath);

        // Get password
        const password = await getPassword();

        // Decrypt
        try {
            const decrypted = decrypt(encryptedData, password);

            // Write .env file
            fs.writeFileSync(envPath, decrypted);

            console.log(`\nDecrypted ${ENCRYPTED_FILE} -> ${ENV_FILE}`);
        } catch (error) {
            if (error.message.includes('Unsupported state') || error.code === 'ERR_OSSL_BAD_DECRYPT') {
                throw new Error('Invalid password');
            }
            throw error;
        }
    });

    // View encrypted file content (without saving)
    gulp.task('vault:view', async () => {
        const encryptedPath = path.join(process.cwd(), ENCRYPTED_FILE);

        // Check if .env.vault exists
        if (!fs.existsSync(encryptedPath)) {
            throw new Error(`${ENCRYPTED_FILE} not found`);
        }

        // Read encrypted content
        const encryptedData = fs.readFileSync(encryptedPath);

        // Get password
        const password = await getPassword();

        // Decrypt
        try {
            const decrypted = decrypt(encryptedData, password);

            console.log(`\n--- ${ENCRYPTED_FILE} contents ---\n`);
            console.log(decrypted);
            console.log(`\n--- end ---\n`);
        } catch (error) {
            if (error.message.includes('Unsupported state') || error.code === 'ERR_OSSL_BAD_DECRYPT') {
                throw new Error('Invalid password');
            }
            throw error;
        }
    });

    // Re-encrypt with new password
    gulp.task('vault:rekey', async () => {
        const encryptedPath = path.join(process.cwd(), ENCRYPTED_FILE);

        // Check if .env.vault exists
        if (!fs.existsSync(encryptedPath)) {
            throw new Error(`${ENCRYPTED_FILE} not found`);
        }

        // Read encrypted content
        const encryptedData = fs.readFileSync(encryptedPath);

        // Get current password
        console.log('Enter current password:');
        const currentPassword = await promptPassword('Current vault password: ');

        // Decrypt with current password
        let decrypted;
        try {
            decrypted = decrypt(encryptedData, currentPassword);
        } catch (error) {
            if (error.message.includes('Unsupported state') || error.code === 'ERR_OSSL_BAD_DECRYPT') {
                throw new Error('Invalid password');
            }
            throw error;
        }

        // Get new password
        console.log('\nEnter new password:');
        const newPassword = await getPasswordWithConfirm();

        if (!newPassword || newPassword.length < 8) {
            throw new Error('Password must be at least 8 characters');
        }

        // Re-encrypt with new password
        const reEncrypted = encrypt(decrypted, newPassword);

        // Write encrypted file
        fs.writeFileSync(encryptedPath, reEncrypted);

        console.log(`\nRe-encrypted ${ENCRYPTED_FILE} with new password.`);
    });
}
