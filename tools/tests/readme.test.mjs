import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const read = path => readFileSync(resolve(root, path), 'utf8').replace(/^\uFEFF/, '');
const release = read('release.properties').match(/^mod_version=(.+)$/m)[1].trim();
const versions = JSON.parse(read('versions.json'));
const readme = read('README.md');

test('main README lists the current release artifact for every supported wrapper', () => {
    assert.ok(readme.includes(`Current release: [${release}]`));
    const links = [...readme.matchAll(/https:\/\/github\.com\/darealshekel\/MMMod\/releases\/download\/([^/)]+)\/([^\s)]+)/g)];
    assert.equal(links.length, versions.length);
    for (const version of versions) {
        const legacy = version.id.endsWith('liteloader');
        const artifact = `mmm-${release}+${version.minecraft}${legacy ? '-liteloader.litemod' : '.jar'}`;
        assert.ok(links.some(([, tag, file]) => tag === `v${release}` && decodeURIComponent(file) === artifact), artifact);
        const wrapper = read(`versions/${version.id}/README.md`);
        assert.ok(wrapper.includes(artifact), `${version.id} README artifact`);
        assert.ok(wrapper.includes(`**${release}`), `${version.id} README version`);
    }
});

test('README relative links resolve inside the maintained source tree', () => {
    for (const path of ['README.md', ...versions.map(v => `versions/${v.id}/README.md`)]) {
        for (const [, target] of read(path).matchAll(/\]\(([^)]+)\)/g)) {
            if (/^(?:https?:|#)/.test(target)) continue;
            const file = decodeURIComponent(target.split('#')[0]);
            assert.ok(existsSync(resolve(root, dirname(path), file)), `${path}: ${target}`);
        }
    }
});

test('player documentation separates sync permission from linking', () => {
    assert.ok(readme.includes('canary sync access'));
    assert.ok(readme.includes('Linking an account does **not** automatically grant permission'));
    assert.ok(readme.includes('refactor/shared-core'));
});

test('legacy smoke launcher resolves the shared release instead of a stale jar', () => {
    const script = read('versions/1.12.2-liteloader/tools/smoke-client.ps1');
    assert.ok(script.includes('../../release.properties'));
    assert.ok(script.includes('mmm-$release+1.12.2-liteloader-development.jar'));
    assert.doesNotMatch(script, /mmm-\d+\.\d+\.\d+\+1\.12\.2/);
});
