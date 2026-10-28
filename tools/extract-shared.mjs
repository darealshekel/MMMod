import { readdirSync, readFileSync, mkdirSync, writeFileSync, unlinkSync, existsSync } from 'node:fs';
import { resolve, relative, join, dirname } from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const versions = JSON.parse(readFileSync(join(root, 'versions.json'), 'utf8').replace(/^\uFEFF/, ''));
const apply = process.argv.includes('--apply');
const platform = /\b(?:net\.minecraft|net\.fabricmc|org\.lwjgl|com\.mumfrey|org\.spongepowered|com\.mojang|org\.joml|fi\.dy\.masa)\b/;
const hash = text => createHash('sha256').update(text).digest('hex');
const files = dir => readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(join(dir,e.name)) : [join(dir,e.name)]);
const mappings = Object.fromEntries(versions.map(v => [v.id, { main: {}, test: {} }]));
const baseline = {};
const writes = new Map();
const removals = [];
const report = [];
if (versions.some(v => existsSync(join(root,'versions',v.id,'source-set.json')))) throw new Error('Extraction already applied. Never rerun over edited source.');
for (const kind of ['main', 'test']) {
    const groups = new Map();
    for (const v of versions) {
        const dir = join(root,'versions',v.id,`src/${kind}/java`);
        if (!existsSync(dir)) continue;
        for (const file of files(dir).filter(f=>f.endsWith('.java'))) {
            const logical = relative(dir,file).replaceAll('\\','/');
            const content = readFileSync(file,'utf8').replaceAll('\r\n','\n');
            if (!groups.has(logical)) groups.set(logical,new Map());
            const variants = groups.get(logical);
            const key = hash(content);
            if (!variants.has(key)) variants.set(key,{content,users:[],paths:[]});
            variants.get(key).users.push(v.id);
            variants.get(key).paths.push(file);
            baseline[`${v.id}/${kind}/${logical}`] = key;
        }
    }
    for (const [logical, variants] of groups) {
        const ordered = [...variants.values()].sort((a,b)=>b.users.length-a.users.length || a.users[0].localeCompare(b.users[0]));
        for (const [index,v] of ordered.entries()) {
            const portable = kind==='main' && !platform.test(v.content) && !logical.startsWith('com/mmm/legacy/') && !logical.startsWith('com/mmm/liteloader/');
            const share = v.users.length>1 || portable;
            const owner = portable ? 'core' : kind==='test' ? 'shared-tests' : 'adapters/shared';
            const variant = index===0 ? '' : `/compat/${v.users[0]}`;
            const target = share ? `${owner}${variant}/src/${kind}/java/${logical}` : `versions/${v.users[0]}/src/${kind}/java/${logical}`;
            for (const user of v.users) mappings[user][kind][logical]=target;
            if (share) {
                writes.set(target,v.content);
                removals.push(...v.paths);
            }
            report.push({kind,logical,target,users:v.users,portable});
        }
    }
}
console.log(JSON.stringify({originalFiles:Object.keys(baseline).length,uniqueImplementations:report.length,sharedImplementations:report.filter(r=>r.users.length>1).length,coreImplementations:report.filter(r=>r.portable).length,removedCopies:removals.length-writes.size},null,2));
if (apply) {
    // Preflight the entire plan before any destination write or original removal.
    for (const target of writes.keys()) if (existsSync(join(root,target))) throw new Error(`Would overwrite ${target}`);
    for (const file of removals) {
        if (!resolve(file).startsWith(join(root,'versions')+'/') && !resolve(file).startsWith(join(root,'versions')+'\\')) throw new Error(`Unsafe removal: ${file}`);
        if (!existsSync(file)) throw new Error(`Missing original: ${file}`);
    }
    // Retain byte-exact originals and a recovery plan before changing the imported tree.
    const recovery = join(root,'build',`extraction-recovery-${Date.now()}`);
    for (const file of [...removals,...versions.map(v=>join(root,'versions',v.id,'build.gradle'))]) {
        const backup=join(recovery,relative(root,file));
        mkdirSync(dirname(backup),{recursive:true});
        writeFileSync(backup,readFileSync(file));
    }
    writeFileSync(join(recovery,'plan.json'),JSON.stringify({created:[...writes.keys()],mappings,baseline,report},null,2));
    console.log(`Recovery snapshot: ${recovery}`);
    for (const [target,content] of writes) {
        const file = join(root,target);
        if (existsSync(file)) throw new Error(`Would overwrite ${file}`);
        mkdirSync(dirname(file),{recursive:true});
        writeFileSync(file,content);
    }
    for (const v of versions) {
        const dir = join(root,'versions',v.id);
        writeFileSync(join(dir,'source-set.json'),JSON.stringify(mappings[v.id],null,2)+'\n');
        const build = join(dir,'build.gradle');
        writeFileSync(build,readFileSync(build,'utf8')+"\napply from: '../../gradle/shared-sources.gradle'\n");
    }
    // All removed files are verified imports inside this new workspace, never user worktrees.
    for (const file of removals) {
        unlinkSync(file);
    }
    mkdirSync(join(root,'migration'),{recursive:true});
    writeFileSync(join(root,'migration/source-baseline.json'),JSON.stringify(baseline,null,2)+'\n');
    writeFileSync(join(root,'migration/ownership.json'),JSON.stringify(report,null,2)+'\n');
}
