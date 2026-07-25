// Minimal decoder - skip the IIFE anti-tampering, just run the string table + decoder
const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Extract _0x1c23 string table function
const funcStart = fullJS.indexOf('function _0x1c23()');
const funcEnd = fullJS.indexOf('return _0x1c23();}', funcStart) + 'return _0x1c23();}'.length;
const funcStr = fullJS.substring(funcStart, funcEnd);

// Extract _0x5451 decoder
const decStart = fullJS.indexOf('function _0x5451');
let braceCount = 0, decEnd = decStart;
for (let i = decStart; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { decEnd = i + 1; break; } }
}
const decStr = fullJS.substring(decStart, decEnd);

const code = funcStr + '\n' + decStr + '\n';

fs.writeFileSync('C:/Users/pro021/weebflix/min_decode.js', code + `
// Manually shift the array - the IIFE shuffles it until parseInt matches target
// We need to figure out the correct shift. Let's try all shifts.
const origArray = _0x1c23();
console.log('Array length:', origArray.length);
console.log('First 5 raw:', origArray.slice(0, 5));

// Try each shift and see which one gives readable output for known hex/key combos
for (let shift = 0; shift < 20; shift++) {
    // Reset array for each attempt
    _0x5451['hashCode'] = undefined;
    // We need to actually re-create the array each time
    // Actually, _0x1c23 returns a new array each time but _0x5451 uses the shuffled one
    // Let's just try _0x5451 with the current state
    try {
        const v = _0x5451(0x360, 'qc7b');
        if (v && typeof v === 'string' && v.length > 0) {
            console.log('Shift', shift, ': 0x360 qc7b =', JSON.stringify(v));
            const v2 = _0x5451(0x3a8, 'KQaa');
            console.log('Shift', shift, ': 0x3a8 KQaa =', JSON.stringify(v2));
            const v3 = _0x5451(0x2be, 'jcrA');
            console.log('Shift', shift, ': 0x2be jcrA =', JSON.stringify(v3));
            const v4 = _0x5451(0x280, '!0^*');
            console.log('Shift', shift, ': 0x280 !0^* =', JSON.stringify(v4));
            break;
        }
    } catch(e) {}
    // Try to shift - actually this won't work because the array is already shuffled
}
`);
console.log('Written min_decode.js');
