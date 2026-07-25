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

// Get the FULL _0x2b049a object
const objStart = fullJS.indexOf('_0x2b049a={', 23000);
const objEnd = fullJS.indexOf('};', objStart) + 2;
const objStr = fullJS.substring(objStart, objEnd);
console.log('Object definition length:', objStr.length);

// Extract all numeric keys from the object
const keyMatches = [...objStr.matchAll(/_0x([0-9a-f]+):(0x[0-9a-f]+|'[^']*')/g)];
const allPairs = [];
for (const m of keyMatches) {
    const propName = '_0x' + m[1];
    const val = m[2];
    allPairs.push([propName, val]);
}
console.log('Found', allPairs.length, 'keys in _0x2b049a');

// Also find the _0x533965 object (used in initEpisodeList)
const obj2Start = fullJS.indexOf('_0x533965={', 10000);
let obj2End = -1;
if (obj2Start !== -1) {
    obj2End = fullJS.indexOf('};', obj2Start) + 2;
    console.log('_0x533965 found at', obj2Start, 'to', obj2End);
}

// Now decode all keys from _0x2b049a
// First, find the next key in the pair (which comes immediately after each numeric value)
const pairPattern = /_0x([0-9a-f]+):(0x[0-9a-f]+),_0x([0-9a-f]+):'([^']+)'/g;
let match;
const decodePairs = [];
while ((match = pairPattern.exec(objStr)) !== null) {
    decodePairs.push({
        hex: parseInt(match[2], 16),
        key: match[4],
        propName1: '_0x' + match[1],
        propName2: '_0x' + match[3],
    });
}
console.log('Found', decodePairs.length, 'decode pairs');

fs.writeFileSync('C:/Users/pro021/weebflix/decode_all.js', code + `
console.log('=== ALL _0x2b049a decode pairs ===');
for (const [hex, key] of ${JSON.stringify(decodePairs.map(p => [p.hex, p.key]))}) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string') {
      console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {}
}
`);
console.log('Written decode_all.js');
