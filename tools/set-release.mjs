import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const versions=JSON.parse(readFileSync(join(root,'versions.json'),'utf8').replace(/^\uFEFF/,''));
const release=readFileSync(join(root,'release.properties'),'utf8').match(/^mod_version=(.+)$/m)[1].trim();
if(!/^\d+(?:\.\d+){2,}$/.test(release)) throw Error('Invalid release version');
for(const v of versions) {
    const dir=join(root,'versions',v.id);
    const build=join(dir,'build.gradle');
    const content=readFileSync(build,'utf8');
    writeFileSync(build,content.replace(/^version = (?:project.mod_version|'[^']+')\r?$/m,"apply from: '../../gradle/release-version.gradle'"));
    const props=join(dir,'gradle.properties');
    try { writeFileSync(props,readFileSync(props,'utf8').replace(/^mod_version=.*\r?\n/m,'')); } catch(e) {if(e.code!=='ENOENT')throw e;}
    const suffix=v.id.endsWith('liteloader')?'-liteloader':'';
    const readme=join(dir,'README.md');
    const artifactPattern=new RegExp(`mmm-\\d+(?:\\.\\d+)+\\+${v.minecraft.replaceAll('.','\\.')}${suffix}(?=[.\\s\x60])`,'g');
    writeFileSync(readme,readFileSync(readme,'utf8').replace(artifactPattern,`mmm-${release}+${v.minecraft}${suffix}`));
    const changelog=join(dir,'CHANGELOG.md');
    let old='';try{old=readFileSync(changelog,'utf8');}catch(e){if(e.code!=='ENOENT')throw e;}
    const heading=`## ${release} - Shared core\n\n- Build shared MMM code from one repository while preserving this version's existing features.\n- Keep Minecraft/loader integrations in version-specific adapters.\n- No new sync rules or required extra mod installation.\n\n`;
    if(!old.includes(`## ${release} - `)) writeFileSync(changelog,old.startsWith('# Changelog')?old.replace(/^# Changelog\r?\n\r?\n/,`# Changelog\n\n${heading}`):`# Changelog\n\n${heading}${old}`);
}
