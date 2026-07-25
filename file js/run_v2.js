const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Extract _0x1c23 function (string table) 
const c1c23Start = fullJS.indexOf('function _0x1c23()');
let braceCount = 0, c1c23End = c1c23Start;
for (let i = c1c23Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { c1c23End = i + 1; break; } }
}
const c1c23Func = fullJS.substring(c1c23Start, c1c23End);

// Extract _0x5451 function (decoder)
const d5451Start = fullJS.indexOf('function _0x5451');
braceCount = 0;
let d5451End = d5451Start;
for (let i = d5451Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { d5451End = i + 1; break; } }
}
const d5451Func = fullJS.substring(d5451Start, d5451End);

// Extract IIFE shuffle (starts at position 21, ends with 0xc3dd0))
const iifeStart = 21; // after "var _0x8730fc=_0x5451;"
const iifeEndIdx = fullJS.indexOf(',0xc3dd0))', iifeStart) + ',0xc3dd0))'.length;
const iife = fullJS.substring(iifeStart, iifeEndIdx);
console.log('IIFE ends at:', iifeEndIdx);
console.log('Last 60 chars:', fullJS.substring(iifeEndIdx - 60, iifeEndIdx));

// Build: _0x1c23 + _0x5451 + IIFE
const code = c1c23Func + '\n' + d5451Func + '\nvar _0x8730fc=_0x5451;\n' + iife + ';\n';

fs.writeFileSync('C:/Users/pro021/weebflix/decode_v2.js', code + `
console.log('Decoder loaded successfully');
console.log('Array first 3:', _0x1c23().slice(0, 3));

// Decode all hex/key pairs
const tests = [
  [0x360, 'qc7b'], [0x3a8, 'KQaa'], [0x2be, 'jcrA'], [0x3c9, 'Km@Q'],
  [0x346, 'qc7b'], [0x3a4, '7R(7'], [0x2de, '!428'], [0x30a, 'Km@Q'],
  [0x26e, 'Y6vP'], [0x280, '!0^*'], [0x2b2, '$YyS'], [0x372, 'QAs('],
  [0x403, 'zFew'], [0x3b2, 'ODwK'], [0x2b8, 'ODwK'], [0x2af, 'Km@Q'],
  [0x3e4, 'Km@Q'], [0x300, '(KPj'], [0x21d, 'pZF3'], [0x286, 'Z#\$e'],
  [0x213, 'fssI'], [0x3af, 'fssI'], [0x3c2, 'S]mN'], [0x3ca, 'm[vS'],
  [0x36c, '(KPj'], [0x3f1, 'p3eK'], [0x44a, 'zFew'], [0x1f6, 'zFew'],
  [0x24d, 'Y6vP'], [0x27c, '!0^*'], [0x293, 'Km@Q'], [0x301, 'Km@Q'],
  [0x3e7, 'Y6vP'], [0x2f8, '(KPj'], [0x23d, 'fssI'], [0x212, 'pZF3'],
  [0x407, 'rMFz'], [0x3b9, 'wo#F'], [0x223, 'bo@R'], [0x3b5, 'PSvH'],
  [0x314, 'LyA0'], [0x2a5, 'm[vS'], [0x38b, 'B]I&'], [0x33a, 'wo#F'],
  [0x3ab, 'PSvH'], [0x2c7, 'LyA0'], [0x390, 'xJrX'], [0x388, 'B]I&'],
  [0x251, '34IJ'], [0x2bd, '#RHr'], [0x3c0, 'vlq*'], [0x2e4, 'pZF3'],
  [0x2d7, 'wJGT'], [0x431, 'fssI'], [0x415, 'zy))'], [0x32b, 'pZF3'],
  [0x1f0, 'ymj)'], [0x20d, 'zFew'], [0x3c4, 'bo@R'], [0x41f, 'KQaa'],
  [0x35c, 'Km@Q'], [0x355, 'm[vS'], [0x2d6, 'Km@Q'], [0x466, 'Km@Q'],
  [0x3cd, 'm[vS'], [0x440, 'Km@Q'], [0x4a4, 'Km@Q'], [0x487, 'Km@Q'],
  [0x1ee, '8&2i'], [0x250, 'B]I&'], [0x323, 'wfKC'], [0x3fc, 'Km@Q'],
  [0x20a, '(KPj'], [0x3cd, 'OI)]'], [0x3bb, 'vlq*'], [0x367, 'LyA0'],
  [0x2b3, 'fOtR'],
];
for (const [hex, key] of tests) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string' && v.length > 0) {
      const printable = /^[\x20-\x7e]+$/.test(v);
      console.log((printable ? '[OK] ' : '[??] ') + '0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {
    console.log('[ERR] 0x' + hex.toString(16) + ' ' + key + ':', e.message);
  }
}
`);
console.log('Written decode_v2.js');
