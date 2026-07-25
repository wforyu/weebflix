const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Extract _0x1c23 string table function
const funcStart = fullJS.indexOf('function _0x1c23()');
const funcEnd = fullJS.indexOf('return _0x1c23();}', funcStart) + 'return _0x1c23();}'.length;
const funcStr = fullJS.substring(funcStart, funcEnd);

// We need to also get _0x5895d5 function that's referenced before its definition
// Let me find it
const f5895d5Start = fullJS.indexOf('function _0x5895d5');
let braceCount = 0, f5895d5End = f5895d5Start;
for (let i = f5895d5Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { f5895d5End = i + 1; break; } }
}
const f5895d5Str = fullJS.substring(f5895d5Start, f5895d5End);

// Extract _0x5451 decoder
const decStart = fullJS.indexOf('function _0x5451');
braceCount = 0;
let decEnd = decStart;
for (let i = decStart; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { decEnd = i + 1; break; } }
}
const decStr = fullJS.substring(decStart, decEnd);

// Extract the IIFE shuffle (everything from start to just before function _0x5451)
const iifeEnd = fullJS.indexOf('function _0x5451');
const iife = fullJS.substring(0, iifeEnd);

const code = funcStr + '\n' + f5895d5Str + '\n' + decStr + '\n' + iife + '\n';

fs.writeFileSync('C:/Users/pro021/weebflix/full_decode2.js', code + `
// Now decode all the URL segment strings
console.log('=== initEpisodeList URL segments ===');
// Desktop path
console.log('0x360 qc7b:', JSON.stringify(_0x5451(0x360,'qc7b')));
// All the separator/path chars
for (const [hex, key] of [
  [0x2be,'jcrA'],[0x3c9,'Km@Q'],[0x346,'qc7b'],[0x3a4,'7R(7'],
  [0x2de,'!428'],[0x30a,'Km@Q'],[0x26e,'Y6vP'],[0x280,'!0^*'],
  [0x2b2,'$YyS'],[0x360,'qc7b'],[0x3a8,'KQaa'],[0x372,'QAs('],
  [0x328,'jcrA'],[0x3c9,'Km@Q'],[0x3c4,'bo@R'],[0x2b8,'ODwK'],
  [0x300,'(KPj'],[0x21d,'pZF3']
]) {
  try {
    const v = _0x5451(hex, key);
    console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
  } catch(e) {}
}

// Also decode the _0x5895d5 function's strings
console.log('\\n=== _0x5895d5 decoded strings ===');
try {
  console.log('_0x5895d5(0x286, Z#\$e):', JSON.stringify(_0x5895d5(0x286,'Z#\$e')));
} catch(e) { console.log('error:', e.message); }
`);
console.log('Written full_decode2.js');
