const fs = require('fs');
const path = 'C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc';
const content = fs.readFileSync(path, 'utf8');

// Extract the string table array
const arrStart = content.lastIndexOf('var _0x5bacab=[');
const arrEnd = content.indexOf('];', arrStart) + 2;
const arrStr = content.substring(arrStart, arrEnd);

// Find the _0x1c23 function
const funcStart = content.indexOf('function _0x1c23()');
const funcEnd = content.indexOf('return _0x1c23();}', funcStart) + 'return _0x1c23();}'.length;
const funcStr = content.substring(funcStart, funcEnd);

// Find the decoder function  
const decStart = content.indexOf('function _0x5451');
// Find the closing brace of the function
let braceCount = 0;
let decEnd = decStart;
for (let i = decStart; i < content.length; i++) {
    if (content[i] === '{') braceCount++;
    if (content[i] === '}') {
        braceCount--;
        if (braceCount === 0) {
            decEnd = i + 1;
            break;
        }
    }
}
const decStr = content.substring(decStart, decEnd);

const fullCode = arrStr + '\n' + funcStr + '\n' + decStr;

fs.writeFileSync('C:/Users/pro021/weebflix/decoder_only.js', fullCode);
console.log('Decoder extracted, length:', fullCode.length);
