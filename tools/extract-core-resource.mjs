import { readFileSync, writeFileSync, mkdirSync, unlinkSync, existsSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const versions=JSON.parse(readFileSync(join(root,'versions.json'),'utf8').replace(/^\uFEFF/,''));
const logical='assets/mmm/chat-censor-terms.txt';
const target=`core/src/main/resources/${logical}`;
const baseline=JSON.parse(readFileSync(join(root,'migration/source-baseline.json'),'utf8'));
const files=versions.map(v=>({v,file:join(root,'versions',v.id,'src/main/resources',logical)})).filter(x=>existsSync(x.file));
const expected=readFileSync(files[0].file);
for(const {file} of files) if(!expected.equals(readFileSync(file))) throw Error(`Different terms resource: ${file}`);
if(existsSync(join(root,target))) throw Error('Resource already extracted');
mkdirSync(dirname(join(root,target)),{recursive:true});
writeFileSync(join(root,target),expected);
for(const {v,file} of files) {
    const manifestPath=join(root,'versions',v.id,'source-set.json');
    const manifest=JSON.parse(readFileSync(manifestPath,'utf8'));
    manifest.resources={[logical]:target};
    baseline[`${v.id}/resources/${logical}`]=createHash('sha256').update(expected.toString().replaceAll('\r\n','\n')).digest('hex');
    writeFileSync(manifestPath,JSON.stringify(manifest,null,2)+'\n');
    unlinkSync(file);
}
writeFileSync(join(root,'migration/source-baseline.json'),JSON.stringify(baseline,null,2)+'\n');
console.log(`Shared identical censor dictionary across ${files.length} wrappers.`);
