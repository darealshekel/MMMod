import { readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve, relative, join } from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const versions = JSON.parse(readFileSync(join(root, 'versions.json'), 'utf8').replace(/^\uFEFF/, ''));
function files(dir) {
    return readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(join(dir, e.name)) : [join(dir, e.name)]);
}
const entries = new Map();
for (const version of versions) {
    const src = join(root, 'versions', version.id, 'src/main/java');
    for (const file of files(src).filter(p => p.endsWith('.java'))) {
        const path = relative(src, file).replaceAll('\\', '/');
        const text = readFileSync(file, 'utf8').replaceAll('\r\n', '\n');
        const hash = createHash('sha256').update(text).digest('hex');
        if (!entries.has(path)) entries.set(path, []);
        entries.get(path).push({ version: version.id, hash, text });
    }
}
const records = [...entries].map(([path, values]) => {
    const hashes = new Set(values.map(v => v.hash));
    const platform = values.some(v => /\b(?:net\.minecraft|net\.fabricmc|org\.lwjgl|com\.mumfrey|org\.spongepowered|com\.mojang|org\.joml|fi\.dy\.masa)\b/.test(v.text));
    return { path, versions: values.map(v => v.version), variants: hashes.size, platform };
});
mkdirSync(join(root, 'build'), { recursive: true });
writeFileSync(join(root, 'build/inventory.json'), JSON.stringify(records, null, 2));
console.log(JSON.stringify({ files: [...entries.values()].reduce((n,v) => n+v.length,0), logicalFiles: records.length, repeatedIdentical: records.filter(r=>r.versions.length>1&&r.variants===1).length, portableCandidates: records.filter(r=>!r.platform).map(r=>({path:r.path,versions:r.versions.length,variants:r.variants})) }, null, 2));
