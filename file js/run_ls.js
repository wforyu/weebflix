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

// Decode ALL the _0x2b049a object values
const obj2b049a = {
    _0x54f3d7: [0x438, 'QAs('],
    _0x5d09cd: [0x418, 'Km@Q'],
    _0x2278a0: [null, 'Km@Q'], // key already given
    _0x28dcbf: [0x235, 'I41E'],
    _0x470909: [null, 'I41E'],
    _0x4eee1f: [null, '8&2i'],
    _0x2a1d6a: [0x1f3, 'PSvH'],
    _0x380bef: [null, 'PSvH'],
    _0x59fcb2: [0x269, 'Y6vP'],
    _0x109f2a: [null, 'Y6vP'],
    _0x24fd9f: [null, 'rMFz'],
    _0x18c3f1: [0x31e, '34IJ'],
    _0x3db5b3: [null, '34IJ'],
    _0xa332e: [0x22b, 'Z#$e'],
    _0x3c608a: [0x3f2, 'ODwK'],
    _0x1a571f: [null, 'ODwK'],
    _0x296dff: [0x2a2, '6!Bh'],
    _0x438dc1: [null, '6!Bh'],
    _0x27df80: [0x350, '%o@X'],
    _0x2dd604: [null, '%o@X'],
    _0x15a1c6: [0x444, 'LSg8'],
    _0x151960: [null, 'LSg8'],
    _0x56dfdb: [0x28a, 'jcrA'],
    _0x58afa7: [null, 'jcrA'],
    _0xedf2c9: [0x432, '(KPj'],
    _0x300f11: [null, '(KPj'],
    _0x12343f: [0x36d, 'VpRf'],
    _0x454eaa: [null, 'VpRf'],
    _0x4ef8ce: [0x42d, 'L...'],
    _0x25a33d: [null, 'L...'],
};

// Actually the object keys map hex values and second keys
// _0x15a1c6: 0x444, _0x151960: 'LSg8' means _0x5451(0x444, 'LSg8')

// Let me just decode the important ones for URL construction
fs.writeFileSync('C:/Users/pro021/weebflix/decode_ls.js', code + `
// Decode loadServer URL parts
// From _0x2b049a object: _0x15a1c6: 0x444, _0x151960: 'LSg8'
console.log('loadServer desktop path:', JSON.stringify(_0x5451(0x444, 'LSg8')));
console.log('loadServer mobile path:', JSON.stringify(_0x5451(0x28a, 'jcrA')));

// Parameter separators
console.log('?is_mob=', JSON.stringify(_0x5451(0x36d, 'VpRf')));
console.log('&is_uc=', JSON.stringify(_0x5451(0x42d, 'L...')));

// But let me just decode ALL keys from the object
const pairs = [
    [0x438, 'QAs('], [0x418, 'Km@Q'], [0x235, 'I41E'],
    [0x1f3, 'PSvH'], [0x269, 'Y6vP'],
    [0x31e, '34IJ'], [0x22b, 'Z#\$e'],
    [0x3f2, 'ODwK'], [0x2a2, '6!Bh'],
    [0x350, '%o@X'], [0x444, 'LSg8'],
    [0x28a, 'jcrA'], [0x432, '(KPj'],
    [0x36d, 'VpRf'], [0x42d, 'wJGT'],
    [0x350, 'm[vS'], [0x3e4, 'Km@Q'],
    [0x44a, 'fssI'], [0x202, '8&2i'],
    [0x409, 'ODwK'], [0x293, 'fOtR'],
    [0x3e7, '#RHr'],
    // loadVideoSB params
    [0x3a1, 'QAs('], [0x309, 'm[vS'],
    // Additional from the area around 41698 and 47005
    [0x466, 'Km@Q'], [0x4a4, 'Km@Q'],
    [0x2d6, 'Km@Q'],
    // From context around 51017
    [0x245, 'Km@Q'],
    // From 55361
    [0x4b5, 'Km@Q'],
    // From 58342
    [0x440, 'Km@Q'],
];

console.log('\\n=== All parameter-related strings ===');
for (const [hex, key] of pairs) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string' && /^[\x20-\x7e]+$/.test(v) && v.length > 0 && v.length < 100) {
      console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {}
}

// Now try the loadServer endpoint
console.log('\\n=== Trying loadServer URLs ===');
const base = 'https://api.nonton.bid/c_api';
const lsPath = _0x5451(0x444, 'LSg8');
const mobPath = _0x5451(0x28a, 'jcrA');
console.log('Desktop:', base + lsPath);
console.log('Mobile:', base + mobPath);

// The loadServer URL pattern: 
// c_api_host + path + '?is_mob=' + is_mob + '&is_uc=' + is_uc + '&movie_id=' + movie_id + '&server=' + server + '&lang=' + lang + '&tag=' + tag + '&c=' + c + '&t=' + t
console.log('\\nFull loadServer URL:');
const url = base + lsPath + '?is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&server=hs&lang=ind&tag=hs&c=bfb1&t=1784892473%26ver%3D373iq';
console.log(url);
`);
console.log('Written decode_ls.js');
