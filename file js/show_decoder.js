const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Extract _0x5451 decoder only
const decStart = fullJS.indexOf('function _0x5451');
let braceCount = 0, decEnd = decStart;
for (let i = decStart; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { decEnd = i + 1; break; } }
}
const decStr = fullJS.substring(decStart, decEnd);
console.log('Decoder function:');
console.log(decStr);
