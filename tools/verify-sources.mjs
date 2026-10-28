import { readFileSync, readdirSync } from 'node:fs';
import { resolve, join, relative } from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const read=p=>JSON.parse(readFileSync(join(root,p),'utf8').replace(/^\uFEFF/,''));
const versions=read('versions.json');
const baseline=read('migration/source-baseline.json');
let count=0;
const seen=new Set();
for(const v of versions) {
    const manifest=read(`versions/${v.id}/source-set.json`);
    for(const [kind,entries] of Object.entries(manifest)) for(const [logical,path] of Object.entries(entries)) {
        const full=resolve(root,path);
        if(relative(root,full).startsWith('..')) throw Error(`Escaped root: ${path}`);
        let text=readFileSync(full,'utf8').replaceAll('\r\n','\n');
        // These two tests resolve assembled sources now; their assertions are unchanged.
        if(kind==='test') text=text
            .replace('System.getProperty("mmm.version", properties.getProperty("mod_version", ""))', 'properties.getProperty("mod_version", "")')
            .replace('Path.of(System.getProperty("mmm.sources", "src/main/java"))\n                    .resolve("com/mmm/mixin/"', 'PROJECT_ROOT.resolve("src/main/java/com/mmm/mixin/"')
            .replace('Path.of(System.getProperty("mmm.sources", "src/main/java"), "com/mmm", file)', 'Path.of("src/main/java/com/mmm", file)');
        if(v.id==='1.12.2-liteloader' && logical==='com/mmm/Reference.java') text=text
            .replace('properties.getProperty("version", "unknown")','properties.getProperty("version", "1.0.20.2+1.12.2-liteloader")');
        const key=`${v.id}/${kind}/${logical}`;
        if(createHash('sha256').update(text).digest('hex')!==baseline[key]) throw Error(`Changed behavior/source: ${key}`);
        if(seen.has(key)) throw Error(`Duplicate ${key}`);
        seen.add(key);count++;
    }
}
if(count!==Object.keys(baseline).length) throw Error('Source missing from migration');
console.log(`PASS: ${count} source/resource inputs match released baselines, allowing only documented test-path/version metadata adjustments and line endings.`);
