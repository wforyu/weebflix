const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// The file starts with: var _0x8730fc=_0x5451;(function(...)(_0x1c23, 0xc3dd0));
// Let me extract the IIFE by finding the balanced parens
const iifeStart = fullJS.indexOf('(function(_0x323382');
if (iifeStart === -1) { console.log('IIFE not found!'); process.exit(1); }

let depth = 0;
let iifeEnd = -1;
for (let i = iifeStart; i < fullJS.length; i++) {
    if (fullJS[i] === '(') depth++;
    if (fullJS[i] === ')') { depth--; if (depth === 0) { iifeEnd = i + 1; break; } }
}
const iife = fullJS.substring(iifeStart, iifeEnd);
console.log('IIFE found at', iifeStart, 'to', iifeEnd, 'length:', iife.length);
console.log('IIFE start:', iife.substring(0, 100));
console.log('IIFE end:', iife.substring(iife.length - 100));

// Also verify it ends with , 0xc3dd0)
console.log('Ends with 0xc3dd0):', iife.endsWith(', 0xc3dd0)'));
