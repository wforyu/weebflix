const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

const c1c23Start = fullJS.indexOf('function _0x1c23()');
let braceCount = 0, c1c23End = c1c23Start;
for (let i = c1c23Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { c1c23End = i + 1; break; } }
}
const c1c23Func = fullJS.substring(c1c23Start, c1c23End);

const d5451Start = fullJS.indexOf('function _0x5451');
braceCount = 0;
let d5451End = d5451Start;
for (let i = d5451Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { d5451End = i + 1; break; } }
}
const d5451Func = fullJS.substring(d5451Start, d5451End);
const iifeStart = 21;
const iifeEndIdx = fullJS.indexOf(',0xc3dd0))', iifeStart) + ',0xc3dd0))'.length;
const iife = fullJS.substring(iifeStart, iifeEndIdx);
const code = c1c23Func + '\n' + d5451Func + '\nvar _0x8730fc=_0x5451;\n' + iife + ';\n';

// Now decode ALL remaining hex/key pairs used in the URL constructions
// I'll extract them from the raw JS around the loadServer area
const urlArea = fullJS.substring(18500, 30000);
// Find all _0xNNN(0xNNN,'NNNN') patterns
const matches = [...urlArea.matchAll(/_0x\w+\((0x[0-9a-f]+),\s*'([^']+)'\)/g)];
const pairs = new Set(matches.map(m => `${m[1]},'${m[2]}'`));

const tests = [...pairs].map(p => {
    const [hex, key] = p.split(/,'/);
    return [parseInt(hex, 16), key.replace(/'$/, '')];
});

fs.writeFileSync('C:/Users/pro021/weebflix/decode_url.js', code + `
console.log('Decoding URL-related hex/key pairs:');
console.log('');
for (const [hex, key] of ${JSON.stringify(tests)}) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string') {
      const printable = /^[\x20-\x7e]+$/.test(v);
      if (printable && v.length > 0) {
        console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
      }
    }
  } catch(e) {}
}

// Also decode known specific keys
console.log('');
console.log('=== Specific keys for loadServer URL ===');
for (const [hex, key] of [
  // From the second URL construction  
  [0x26f, 'Y6vP'], [0x227, '8&2i'], [0x3dc, 'p3eK'],
  [0x2be, 'jcrA'], [0x3c9, 'Km@Q'], [0x346, 'qc7b'],
  [0x293, 'fOtR'], [0x3e7, '#RHr'],
  // Additional params
  [0x409, 'Km@Q'], [0x202, '8&2i'],
  [0x3e4, 'Km@Q'],
  // Server-specific
  [0x403, 'zFew'], [0x372, 'QAs('],
]) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string' && /^[\x20-\x7e]+$/.test(v)) {
      console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {}
}

// Now let's construct the full URLs
console.log('');
console.log('=== CONSTRUCTED URLs ===');
const c_api = 'https://api.nonton.bid/c_api';
const api = 'https://api.nonton.bid/api';
const file_h = 'https://d.load.my.id';

// initEpisodeList URL (desktop)
console.log('initEpisodeList:', c_api + '/episode.php?is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&c=bfb1&t=1784892473%26ver%3D373iq');

// Now decode loadServer URL  
console.log('loadServer base: (need to decode path)');
`);
console.log('Written decode_url.js');
