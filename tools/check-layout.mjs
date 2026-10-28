import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const read=path=>JSON.parse(readFileSync(join(root,path),'utf8').replace(/^\uFEFF/,''));
const versions=read('versions.json');
const used=new Set();
let selections=0;
for(const v of versions) {
    const manifest=read(`versions/${v.id}/source-set.json`);
    for(const kind of ['main','test','resources']) {
        const classes=new Set();
        for(const [logical,source] of Object.entries(manifest[kind]??{})) {
            const resolved=resolve(root,source);
            if(relative(root,resolved).startsWith('..') || !existsSync(resolved)) throw Error(`Invalid source ${source}`);
            if(!source.endsWith('/'+logical)) throw Error(`Path mismatch ${source} -> ${logical}`);
            if(classes.has(logical)) throw Error(`Duplicate compiler input ${v.id}: ${logical}`);
            if(source.startsWith('versions/')&&!source.startsWith(`versions/${v.id}/`)) throw Error(`Wrapper depends on another wrapper: ${source}`);
            classes.add(logical);used.add(source);selections++;
        }
    }
    for(const kind of ['main','test']) {
        const base=join(root,`versions/${v.id}/src/${kind}/java`);
        for(const file of walk(base).filter(f=>f.endsWith('.java'))) {
            const logical=relative(base,file).replaceAll('\\','/');
            if(manifest[kind][logical]!==relative(root,file).replaceAll('\\','/')) throw Error(`Unregistered or shadowing wrapper source: ${file}`);
        }
    }
}
function walk(dir) {return existsSync(dir)?readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(join(dir,e.name)):[join(dir,e.name)]):[];}
for(const area of ['core/src','core/compat','adapters/shared','shared-tests']) for(const file of walk(join(root,area))) {
    const path=relative(root,file).replaceAll('\\','/');
    if((path.endsWith('.java')||path.includes('/resources/'))&&!used.has(path)) throw Error(`Orphan shared file: ${path}`);
    if(area.startsWith('core')&&file.endsWith('.java')&&/\b(?:net\.minecraft|net\.fabricmc|org\.lwjgl|com\.mumfrey|org\.spongepowered|com\.mojang|org\.joml|fi\.dy\.masa)\b/.test(readFileSync(file,'utf8'))) throw Error(`Game API in core: ${path}`);
}
console.log(`PASS: ${versions.length} wrappers, ${selections} registered source/resource inputs, no missing or orphaned shared sources.`);
