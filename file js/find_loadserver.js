const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Find where _0x2b049a is defined (used in the second URL construction around 26190)
const ctx = fullJS.substring(24500, 27000);
// Find the object literal
const objMatch = ctx.match(/_0x2b049a=\{([^}]+)\}/);
if (objMatch) {
    console.log('_0x2b049a object:');
    console.log(objMatch[0].substring(0, 2000));
} else {
    // Try broader search
    const idx = fullJS.lastIndexOf('_0x2b049a={', 26000);
    if (idx !== -1) {
        console.log('Found at', idx);
        console.log(fullJS.substring(idx, idx + 500));
    } else {
        console.log('Not found directly. Searching broader...');
        const idx2 = fullJS.indexOf('_0x2b049a=', 24000);
        console.log('_0x2b049a= at:', idx2);
        if (idx2 !== -1) {
            console.log(fullJS.substring(idx2, idx2 + 500));
        }
    }
}

// Also look for the loadServer function specifically  
const lsIdx = fullJS.indexOf('loadServer');
console.log('\nloadServer at:', lsIdx);
if (lsIdx !== -1) {
    // Search backwards for function definition
    const nearIdx = fullJS.lastIndexOf('function', lsIdx);
    console.log('Nearby function at:', nearIdx);
    console.log(fullJS.substring(Math.max(0, lsIdx - 200), lsIdx + 200));
}
