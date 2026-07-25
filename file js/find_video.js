const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Find loadVideoHYDRAX function
const hvIdx = fullJS.indexOf('function loadVideoHYDRAX');
if (hvIdx !== -1) {
    let depth = 0, started = false;
    for (let i = hvIdx; i < fullJS.length; i++) {
        if (fullJS[i] === '{') { depth++; started = true; }
        if (fullJS[i] === '}') { depth--; if (started && depth === 0) { console.log('loadVideoHYDRAX:'); console.log(fullJS.substring(hvIdx, i+1)); break; } }
    }
}

// Also find loadVideoP2P and loadVideoSB
const p2pIdx = fullJS.indexOf('function loadVideoP2P');
if (p2pIdx !== -1) {
    let depth = 0, started = false;
    for (let i = p2pIdx; i < fullJS.length; i++) {
        if (fullJS[i] === '{') { depth++; started = true; }
        if (fullJS[i] === '}') { depth--; if (started && depth === 0) { console.log('\nloadVideoP2P:'); console.log(fullJS.substring(p2pIdx, i+1).substring(0, 2000)); break; } }
    }
}

// Also find get_link function
const glIdx = fullJS.indexOf('function get_link');
if (glIdx !== -1) {
    let depth = 0, started = false;
    for (let i = glIdx; i < fullJS.length; i++) {
        if (fullJS[i] === '{') { depth++; started = true; }
        if (fullJS[i] === '}') { depth--; if (started && depth === 0) { console.log('\nget_link:'); console.log(fullJS.substring(glIdx, i+1).substring(0, 3000)); break; } }
    }
}
