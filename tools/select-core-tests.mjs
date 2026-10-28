import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const ownership=JSON.parse(readFileSync(join(root,'migration/ownership.json'),'utf8'));
const sources=new Map();
for(const entry of ownership.filter(e=>e.kind==='main')) {
    const name=entry.logical.replaceAll('/','.').replace(/\.java$/,'');
    if(!sources.has(name) || entry.target.startsWith('core/src/')) sources.set(name,entry);
}
const content=e=>readFileSync(join(root,e.target),'utf8');
const allowedImports=(text,test=false)=>[...text.matchAll(/\bimport\s+(?:static\s+)?([\w.*]+)\s*;/g)]
    .every(m=>/^(java\.|javax\.|com\.google\.gson\.|com\.mmm\.)/.test(m[1]) || test&&m[1].startsWith('org.junit.'));
function dependencies(text) {
    const deps=new Set();
    const pkg=text.match(/\bpackage\s+([\w.]+);/)?.[1];
    const code=text.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:\\.|[^"\\])*"/g,' ');
    for(const match of code.matchAll(/\bcom\.mmm\.[\w.*]+/g)) {
        let name=match[0];
        if(name.endsWith('.*')) {
            const prefix=name.slice(0,-1);
            for(const key of sources.keys()) if(key.startsWith(prefix)&&!key.slice(prefix.length).includes('.')) deps.add(key);
        } else {
            while(name.startsWith('com.mmm.')) {
                if(sources.has(name)) {deps.add(name);break;}
                name=name.slice(0,name.lastIndexOf('.'));
            }
        }
    }
    for(const key of sources.keys()) if(key.startsWith(pkg+'.') && !key.slice(pkg.length+1).includes('.')) {
        const simple=key.slice(pkg.length+1);
        if(new RegExp(`\\b${simple}\\b`).test(code)) deps.add(key);
    }
    return deps;
}
const selected=new Set([...sources].filter(([,e])=>e.target.startsWith('core/src/')).map(([name])=>name));
let changed=true;
while(changed) {
    changed=false;
    for(const name of selected) {
        const text=content(sources.get(name));
        if([...dependencies(text)].some(d=>!selected.has(d)) || !allowedImports(text)) {
            selected.delete(name);changed=true;
        }
    }
}
const testCandidates=new Map();
for(const e of ownership.filter(e=>e.kind==='test')) if(!testCandidates.has(e.logical)) testCandidates.set(e.logical,e);
const tests=[...testCandidates.values()].filter(e=>{
    const text=content(e);
    return allowedImports(text,true)
        && !/\b(?:net\.minecraft|net\.fabricmc|src\/main|reference-1\.21)\b/.test(text)
        && [...dependencies(text)].every(d=>selected.has(d));
});
const result={main:[...selected].sort().map(n=>sources.get(n).target),test:tests.map(e=>e.target).sort()};
writeFileSync(join(root,'core/standalone-sources.json'),JSON.stringify(result,null,2)+'\n');
console.log(`Standalone core: ${result.main.length} classes, ${result.test.length} test classes (compiler/test validation required).`);
