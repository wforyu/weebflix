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

// Extract the IIFE shuffle  
const iifeEnd = fullJS.indexOf('function _0x5451');
const iife = fullJS.substring(0, iifeEnd);

const code = funcStr + '\n' + decStr + '\n' + iife + '\n';

fs.writeFileSync('C:/Users/pro021/weebflix/brute_decode.js', code + `
// The IIFE tries to match parseInt result to 0xc3dd0 (802256)
// It pushes/shifts the array. Let's try to brute-force the shift count.
const arr = _0x1c23();
const len = arr.length;

for (let shift = 0; shift < len; shift++) {
    // Create a fresh copy of the array each time
    const testArr = [...arr];
    // Apply shift
    for (let s = 0; s < shift; s++) {
        testArr.push(testArr.shift());
    }
    // Now we need to test with this shifted array
    // But _0x5451 uses a global array. We need to re-create _0x5451 with a different array.
    // Actually the easiest way is to replace _0x1c23 to return the shifted array
    
    // Let's try a different approach: just modify the global array state
    // We can't easily do that, so let's just enumerate
}

// Actually the simplest way: just try all shifts by manipulating the returned array
// _0x5451 reads from a global that was set by _0x1c23 and shuffled by IIFE
// The IIFE is already in our code, so it already ran the right shuffle
// The issue is that the IIFE needs _0x5451 to be defined first (circular dependency)

// Let me check if the IIFE already ran
try {
    const v = _0x5451(0x360, 'qc7b');
    console.log('After IIFE: 0x360 qc7b =', JSON.stringify(v));
    // If this gives garbage, the IIFE didn't work properly
    if (/[^\\x20-\\x7E]/.test(v) || v.length < 2) {
        console.log('Garbage - IIFE shuffle not applied');
        
        // Brute force: try each shift
        for (let shift = 0; shift < 50; shift++) {
            // Need to re-run with shifted array
            // Replace the internal array by re-defining
            eval(funcStr); // re-define _0x1c23
            const freshArr = _0x1c23();
            for (let s = 0; s < shift; s++) {
                freshArr.push(freshArr.shift());
            }
            // Hack: we need to make _0x5451 use this shifted array
            // The decoder function references the global variable set by the IIFE
            // Let's look at how _0x5451 accesses the array
        }
    }
} catch(e) {
    console.log('Error:', e.message);
}
`);
console.log('Written brute_decode.js');
