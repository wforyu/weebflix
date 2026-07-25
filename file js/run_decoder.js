const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Extract the IIFE that shuffles the array (starts at beginning, ends before the first "function _0x5451")
const iifeEnd = fullJS.indexOf('function _0x5451');
const iife = fullJS.substring(0, iifeEnd);

// Extract the _0x1c23 function (string table)
const funcStart = fullJS.indexOf('function _0x1c23()');
const funcEnd = fullJS.indexOf('return _0x1c23();}', funcStart) + 'return _0x1c23();}'.length;
const funcStr = fullJS.substring(funcStart, funcEnd);

// Extract the decoder function
const decStart = fullJS.indexOf('function _0x5451');
let braceCount = 0;
let decEnd = decStart;
for (let i = decStart; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') {
        braceCount--;
        if (braceCount === 0) { decEnd = i + 1; break; }
    }
}
const decStr = fullJS.substring(decStart, decEnd);

const code = funcStr + '\n' + iife + '\n' + decStr + '\n';

fs.writeFileSync('C:/Users/pro021/weebflix/decoder_runner.js', code + `
// Decode initEpisodeList URL segments
console.log('=== initEpisodeList URL (desktop) ===');
console.log('0x360 qc7b:', JSON.stringify(_0x5451(0x360,'qc7b')));
console.log('0x3a8 KQaa:', JSON.stringify(_0x5451(0x3a8,'KQaa')));

// URL param separators
const params = [
  [0x2be,'jcrA'],[0x3c9,'Km@Q'],[0x346,'qc7b'],[0x3a4,'7R(7'],
  [0x2de,'!428'],[0x30a,'Km@Q'],[0x26e,'Y6vP'],[0x280,'!0^*'],
  [0x2b2,'$YyS']
];
for (const [hex, key] of params) {
  console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(_0x5451(hex, key)));
}

// loadEpisode URL segments
console.log('\\n=== loadEpisode URL ===');
console.log('0x372 QAs(:', JSON.stringify(_0x5451(0x372,'QAs(')));
console.log('0x403 zFew:', JSON.stringify(_0x5451(0x403,'zFew')));

// Also decode specific known strings to find the API path
console.log('\\n=== Specific decode attempts for API path ===');
for (const key of ['qc7b','Km@Q','!0^*','jcrA','Y6vP','zFew','p3eK','v3Lx','EWbD','MMF[','K[dK','ODwK','I41E','QAs(','VpRf','8&2i','wfKC','LyA0','vlq*','XzeV','Z#$e','PSvH','g!yD','bo@R','#RHr','m[vS','!428','KQaa','(KPj','rMFz','fOtR','pZF3','bmsq','34IJ','wJGT','LSg8','fssI','B]I&','emrmdu','pqoapqGDUOaybazqzf','Ymft','W6hdNqZdJaO','W6uepSku']) {
  for (const hex of [0x360, 0x3a8, 0x2b8, 0x440, 0x2be, 0x1f6, 0x44a, 0x2af, 0x3e4, 0x24d, 0x3b2, 0x27c, 0x293, 0x301, 0x3e7, 0x355, 0x2f8, 0x23d, 0x212, 0x355, 0x2b8, 0x407, 0x3b9, 0x223, 0x3b5, 0x314, 0x2a5, 0x38b]) {
    try {
      const val = _0x5451(hex, key);
      if (val && val.length > 0 && val.length < 80 && /^[\x20-\x7E]+$/.test(val)) {
        console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(val));
      }
    } catch(e) {}
  }
}
`);
console.log('Written decoder_runner.js');
