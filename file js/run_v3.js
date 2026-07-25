const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

const c1c23Start = fullJS.indexOf('function _0x1c23()');
let braceCount = 0, c1c23End = c1c23Start;
for (let i = c1c23Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { c1c23End = i + 1; break; } }
}
const d5451Start = fullJS.indexOf('function _0x5451');
braceCount = 0;
let d5451End = d5451Start;
for (let i = d5451Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { d5451End = i + 1; break; } }
}

const iifeStart = 21;
const iifeEndIdx = fullJS.indexOf(',0xc3dd0))', iifeStart) + ',0xc3dd0))'.length;
const iife = fullJS.substring(iifeStart, iifeEndIdx);

const code = fullJS.substring(c1c23Start, c1c23End) + '\n' +
    fullJS.substring(d5451Start, d5451End) + '\n' +
    iife + ';\n' +
    `var _0x8730fc = _0x5451;\n`;

fs.writeFileSync('C:/Users/pro021/weebflix/decode_v3.js', code + `
console.log("=== loadVideoHYDRAX URL params ===");
console.log('path (0x4a4eb6 QAs():', JSON.stringify(_0x5451(0x4a4eb6, 'QAs(')));
console.log('param1 (0x2af S]mN):', JSON.stringify(_0x5451(0x2af, 'S]mN')));
console.log('param2 (0x24d VpRf):', JSON.stringify(_0x5451(0x24d, 'VpRf')));
console.log('param3 (0x20c p3eK):', JSON.stringify(_0x5451(0x20c, 'p3eK')));
console.log('param4 (0x407 vlq*):', JSON.stringify(_0x5451(0x407, 'vlq*')));
console.log('param5 (0x29c Km@Q):', JSON.stringify(_0x5451(0x29c, 'Km@Q')));
console.log('param6 (0x21b zFew):', JSON.stringify(_0x5451(0x21b, 'zFew')));
console.log('param7 (0x321 (KPj):', JSON.stringify(_0x5451(0x321, '(KPj')));
console.log('param8 (0x324 wJGT):', JSON.stringify(_0x5451(0x324, 'wJGT')));
console.log('&c= (0x1ec Y6vP):', JSON.stringify(_0x5451(0x1ec, 'Y6vP')));
console.log('&t= (0x3a3 I41E):', JSON.stringify(_0x5451(0x3a3, 'I41E')));

console.log("\\n=== get_link URL params ===");
console.log('path (0x440 v3Lx):', JSON.stringify(_0x5451(0x440, 'v3Lx')));
console.log('param1 (0x436 34IJ):', JSON.stringify(_0x5451(0x436, '34IJ')));
console.log('param2 (0x275 zy)):):', JSON.stringify(_0x5451(0x275, 'zy))')));
console.log('param3 (0x3c5 p3eK):', JSON.stringify(_0x5451(0x3c5, 'p3eK')));
console.log('param4 (0x37b KQaa):', JSON.stringify(_0x5451(0x37b, 'KQaa')));
console.log('&c= (0x33c EWbD):', JSON.stringify(_0x5451(0x33c, 'EWbD')));

// Also decode loadVideoLoc to find its URL pattern
`);
console.log('Written decode_v3.js');
