const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// The key insight: _0x1c23() caches its array. The IIFE shuffles it.
// The full file structure:
// 1. var _0x8730fc=_0x5451; (line 1)
// 2. IIFE shuffle (_0x1c23, 0xc3dd0) 
// 3. _0x341411 function
// 4. Anti-debug IIFE (calls _0x5895d5 which is defined later but function is hoisted? No - it's a var function)
// ...
// Later: function _0x5895d5(...) and function _0x5451(...)

// The problem is that _0x5895d5 is referenced in the anti-debug IIFE but defined LATER
// In the original file, function declarations ARE hoisted, but let me check if _0x5895d5 is a function declaration or expression

// Search for _0x5895d5 definition
const f5895d5Match = fullJS.match(/function _0x5895d5\b/);
console.log('_0x5895d5 is a function declaration:', !!f5895d5Match);

const f5451Match = fullJS.match(/function _0x5451\b/);
console.log('_0x5451 is a function declaration:', !!f5451Match);

// Since both are function declarations, they ARE hoisted to the top of their scope
// So the IIFE CAN call _0x5451 even though the declaration appears later in the file
// The issue was that in my previous extraction, I was extracting them wrong

// The correct approach: the ENTIRE file should work as-is in Node.js
// The only thing that fails is browser-specific APIs (document, window, $ etc.)
// But we don't need those - we just need _0x5451 to decode strings

// So let me just eval the parts we need: _0x1c23, _0x5451, and the IIFE
// Everything else can be stubbed

// Create a fake environment
const fakeEnv = `
var _0x5895d5 = function(a) { return a; }; // stub - we don't need the real anti-debug
var window = { location: { href: '' }, navigator: { userAgent: '' } };
var document = { createElement: function() { return {}; }, cookie: '' };
var location = { href: 'https://xdrakor33.nicewap.sbs/detail/the-husband-2026-v2e8/', pathname: '/detail/the-husband-2026-v2e8/', split: function(d) { return this.href.split(d); } };
var navigator = { userAgent: 'Mozilla/5.0' };
var console = { log: function(){} };
var $ = function() { return { ready: function(){}, click: function(){} }; };
var is_mob = '0';
var is_uc = '0';
var c_api_host = 'https://api.nonton.bid/c_api';
var api_host = 'https://api.nonton.bid/api';
var file_host = 'https://d.load.my.id';
var c = 'bfb1';
var t = '1784892473&ver=373iq';
var GLOBAL_MOVIE_ID = 'yLpA1nCVmw';
`;

// Get everything up to and including the IIFE and the _0x5451 function
// But we need to skip the anti-debug parts that use browser APIs

// Actually, let's just get _0x1c23 and _0x5451 and the IIFE in order
// _0x1c23 is defined early in the file (right after the IIFE or before?)
// Let me check where _0x1c23 appears
const c1c23Dec = fullJS.indexOf('var _0x1c23=');
const c1c23Func = fullJS.indexOf('function _0x1c23');
console.log('_0x1c23 var at:', c1c23Dec, 'function at:', c1c23Func);

// Extract from the very beginning through the IIFE and up to just before the anti-debug stuff
// Actually let's just grab everything up to the _0x5451 function and the IIFE

// Step 1: Get _0x1c23 function
let p1End = fullJS.indexOf('function _0x1c23()');
if (p1End === -1) p1End = fullJS.indexOf('var _0x1c23=');
let braceCount = 0, p1Start = p1End;

// Find the full _0x1c23 function  
for (let i = p1Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { p1End = i + 1; break; } }
}
const c1c23FuncStr = fullJS.substring(p1Start, p1End);
console.log('_0x1c23 function length:', c1c23FuncStr.length);

// Step 2: Get _0x5451 function
const d5451Start = fullJS.indexOf('function _0x5451');
braceCount = 0;
let d5451End = d5451Start;
for (let i = d5451Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { d5451End = i + 1; break; } }
}
const d5451FuncStr = fullJS.substring(d5451Start, d5451End);
console.log('_0x5451 function length:', d5451FuncStr.length);

// Step 3: Get the IIFE shuffle
const iifeStart = 0; // IIFE is at the very beginning
const iifeEnd = d5451Start; // ends where _0x5451 starts? No, let me check
// Actually the IIFE is: (function(_0x323382, _0x5d7501){...})(_0x1c23, 0xc3dd0);
// It should be right after var _0x8730fc=_0x5451;
const iifeMatch = fullJS.match(/\(function\(_0x323382.*?\}\)\(_0x1c23, 0xc3dd0\)/);
const iifeStr = iifeMatch ? iifeMatch[0] : '';
console.log('IIFE length:', iifeStr.length);

// Build the decoder
const code = fakeEnv + '\n' + c1c23FuncStr + '\n' + d5451FuncStr + '\nvar _0x8730fc = _0x5451;\n' + iifeStr + ';\n';

fs.writeFileSync('C:/Users/pro021/weebflix/decode_final.js', code + `
console.log('=== Decoding URL segments ===');

// Try to decode all the hex/key pairs found in the code
const tests = [
  // initEpisodeList desktop path
  [0x360, 'qc7b'],
  [0x3a8, 'KQaa'],
  // Path separators and parameters  
  [0x2be, 'jcrA'],
  [0x3c9, 'Km@Q'],
  [0x346, 'qc7b'],
  [0x3a4, '7R(7'],
  [0x2de, '!428'],
  [0x30a, 'Km@Q'],
  [0x26e, 'Y6vP'],
  [0x280, '!0^*'],
  [0x2b2, '$YyS'],
  [0x372, 'QAs('],
  [0x403, 'zFew'],
  [0x3b2, 'ODwK'],
  [0x2b8, 'ODwK'],
  [0x2af, 'Km@Q'],
  [0x3e4, 'Km@Q'],
  [0x300, '(KPj'],
  [0x21d, 'pZF3'],
  [0x286, 'Z#\$e'],
  [0x213, 'fssI'],
  [0x3af, 'fssI'],
  [0x3c2, 'S]mN'],
  [0x3ca, 'm[vS'],
  [0x36c, '(KPj'],
  [0x3f1, 'p3eK'],
  [0x44a, 'zFew'],
  [0x1f6, 'zFew'],
  [0x24d, 'Y6vP'],
  [0x3b2, 'ODwK'],
  [0x27c, '!0^*'],
  [0x293, 'Km@Q'],
  [0x301, 'Km@Q'],
  [0x3e7, 'Y6vP'],
  [0x2f8, '(KPj'],
  [0x23d, 'fssI'],
  [0x212, 'pZF3'],
  [0x407, 'rMFz'],
  [0x3b9, 'wo#F'],
  [0x223, 'bo@R'],
  [0x3b5, 'PSvH'],
  [0x314, 'LyA0'],
  [0x2a5, 'm[vS'],
  [0x38b, 'B]I&'],
  [0x33a, 'wo#F'],
  [0x3ab, 'PSvH'],
  [0x2c7, 'LyA0'],
  [0x390, 'xJrX'],
  [0x388, 'B]I&'],
  [0x251, '34IJ'],
  [0x2bd, '#RHr'],
  [0x3c0, 'vlq*'],
  [0x2e4, 'pZF3'],
  [0x2d7, 'wJGT'],
  [0x431, 'fssI'],
  [0x415, 'zy))'],
  [0x32b, 'pZF3'],
  [0x1f0, 'ymj)'],
  [0x20d, 'zFew'],
  [0x3c4, 'bo@R'],
  [0x41f, 'KQaa'],
];

for (const [hex, key] of tests) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string') {
      console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {}
}

// Also try the specific combos from the URL construction
console.log('\\n=== loadServer URL pattern ===');
const serverPaths = [
  [0x372, 'QAs('],
  [0x403, 'zFew'],
  [0x3b2, 'ODwK'],
  [0x3c9, 'Km@Q'],
  [0x440, 'Km@Q'],
  [0x35c, 'Km@Q'],
  [0x355, 'm[vS'],
  [0x2d6, 'Km@Q'],
  [0x466, 'Km@Q'],
  [0x3cd, 'm[vS'],
];
for (const [hex, key] of serverPaths) {
  try {
    const v = _0x5451(hex, key);
    if (v && typeof v === 'string') {
      console.log('0x' + hex.toString(16) + ' ' + key + ':', JSON.stringify(v));
    }
  } catch(e) {}
}
`);
console.log('Written decode_final.js');
