import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const repository=resolve(fileURLToPath(new URL('../..',import.meta.url)));
const testRoot=join(repository,'build/tool-tests');
mkdirSync(testRoot,{recursive:true});
function fixture() {
    const root=mkdtempSync(join(testRoot,'extraction-'));
    mkdirSync(join(root,'tools'));
    copyFileSync(join(repository,'tools/extract-shared.mjs'),join(root,'tools/extract-shared.mjs'));
    writeFileSync(join(root,'versions.json'),JSON.stringify([{id:'a'},{id:'b'}]));
    for(const id of ['a','b']) {
        const dir=join(root,'versions',id,'src/main/java/com/mmm/tracker');
        mkdirSync(dir,{recursive:true});
        writeFileSync(join(root,'versions',id,'build.gradle'),"plugins { id 'java' }\n");
        for(const name of ['First','Second']) writeFileSync(join(dir,name+'.java'),`package com.mmm.tracker;\r\npublic final class ${name} {}\r\n`);
    }
    return root;
}
const run=root=>spawnSync(process.execPath,[join(root,'tools/extract-shared.mjs'),'--apply'],{cwd:root,encoding:'utf8'});

test('a destination collision is rejected before any file is moved',()=>{
    const root=fixture();
    const collision=join(root,'core/src/main/java/com/mmm/tracker/Second.java');
    mkdirSync(resolve(collision,'..'),{recursive:true});
    writeFileSync(collision,'existing contents');
    const result=run(root);
    assert.notEqual(result.status,0);
    assert.match(result.stderr,/Would overwrite/);
    assert.equal(readFileSync(collision,'utf8'),'existing contents');
    assert.equal(existsSync(join(root,'core/src/main/java/com/mmm/tracker/First.java')),false);
    for(const id of ['a','b']) {
        assert.equal(existsSync(join(root,'versions',id,'source-set.json')),false);
        assert.match(readFileSync(join(root,'versions',id,'src/main/java/com/mmm/tracker/First.java'),'utf8'),/\r\n/);
        assert.equal(readFileSync(join(root,'versions',id,'build.gradle'),'utf8'),"plugins { id 'java' }\n");
    }
});

test('successful extraction records byte-exact recovery copies and rejects a destructive rerun',()=>{
    const root=fixture();
    const result=run(root);
    assert.equal(result.status,0,result.stderr);
    const recovery=join(root,'build',readdirSync(join(root,'build')).find(n=>n.startsWith('extraction-recovery-')));
    assert.ok(existsSync(join(recovery,'plan.json')));
    for(const id of ['a','b']) {
        const manifest=JSON.parse(readFileSync(join(root,'versions',id,'source-set.json'),'utf8'));
        assert.equal(manifest.main['com/mmm/tracker/First.java'],'core/src/main/java/com/mmm/tracker/First.java');
        assert.equal(existsSync(join(root,'versions',id,'src/main/java/com/mmm/tracker/First.java')),false);
        assert.match(readFileSync(join(recovery,'versions',id,'src/main/java/com/mmm/tracker/First.java'),'utf8'),/\r\n/);
    }
    const second=run(root);
    assert.notEqual(second.status,0);
    assert.match(second.stderr,/Extraction already applied/);
    assert.equal(readFileSync(join(root,'core/src/main/java/com/mmm/tracker/First.java'),'utf8'),'package com.mmm.tracker;\npublic final class First {}\n');
});
