const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Find ALL calls to loadServer (not the function definition)
const calls = [...fullJS.matchAll(/loadServer\(([^)]+)\)/g)];
console.log('Found', calls.length, 'calls to loadServer:');
for (const call of calls) {
    console.log('\n--- Call ---');
    console.log('Args:', call[1]);
    // Show context
    const start = Math.max(0, call.index - 100);
    const end = Math.min(fullJS.length, call.index + call[0].length + 50);
    console.log('Context:', fullJS.substring(start, end));
}

// Also find the loadEpisode function to see how it calls loadServer
const leStart = fullJS.indexOf('function loadEpisode');
if (leStart !== -1) {
    let depth = 0, leEnd = leStart;
    let started = false;
    for (let i = leStart; i < fullJS.length; i++) {
        if (fullJS[i] === '{') { depth++; started = true; }
        if (fullJS[i] === '}') { depth--; if (started && depth === 0) { leEnd = i + 1; break; } }
    }
    console.log('\n\n=== loadEpisode function ===');
    console.log(fullJS.substring(leStart, Math.min(leEnd, leStart + 3000)));
}
