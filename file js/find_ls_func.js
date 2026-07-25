const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Get the exact loadServer function body
const lsStart = fullJS.indexOf('function loadServer');
if (lsStart === -1) {
    // Try with different pattern
    const lsIdx = fullJS.indexOf('loadServer(');
    console.log('loadServer call at:', lsIdx);
    // Search backwards for 'function'
    let backIdx = lsIdx;
    while (backIdx > 0 && fullJS.substring(backIdx - 9, backIdx) !== 'function ') {
        backIdx--;
    }
    console.log('Function starts at:', backIdx - 9);
    // Now get the full function
    let depth = 0, funcStart = backIdx - 9;
    let started = false;
    for (let i = funcStart; i < fullJS.length; i++) {
        if (fullJS[i] === '{') { depth++; started = true; }
        if (fullJS[i] === '}') { depth--; if (started && depth === 0) { console.log('Function ends at:', i + 1); break; } }
    }
} else {
    console.log('loadServer function at:', lsStart);
}

// Let me get the raw code around the second c_api_host usage (loadServer URL)
// Position 26190 area
console.log('\n=== Full URL construction from 26000 ===');
console.log(fullJS.substring(25800, 26800));
