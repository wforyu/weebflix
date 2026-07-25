const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Get the _0x2b049a object definition
const objStart = fullJS.indexOf('_0x2b049a={', 23000);
const objEnd = fullJS.indexOf('};', objStart) + 2;
const objStr = fullJS.substring(objStart, objEnd);

// Find specific keys
const keys = ['_0x538f90', '_0x5d9316', '_0x595e04', '_0x4d6071', '_0x1ce11d', '_0x56281e', '_0x2e37b6', '_0x25e1c3', '_0x2147a8', '_0x3aef6c', '_0x1b1ff4', '_0x309cce'];
for (const key of keys) {
    const idx = objStr.indexOf(key + ':');
    if (idx !== -1) {
        const after = objStr.substring(idx + key.length + 1, idx + key.length + 30);
        console.log(key, ':', after.split(',')[0]);
    } else {
        console.log(key, ': NOT FOUND');
    }
}

// Also find the function parameters for loadServer
// Get from position 23801
console.log('\n=== loadServer function signature and first 2000 chars ===');
const funcText = fullJS.substring(23801, 24200);
console.log(funcText);
