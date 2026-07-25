// Standalone decoder: extract _0x1c23 array + _0x5451 function, then decode specific strings
const fs = require('fs');
const aJs = fs.readFileSync('C:\\Users\\pro021\\.local\\share\\opencode\\tool-output\\tool_f9414da790017KPZ1vlRldkOJh', 'utf8');

// Extract _0x1c23 function body (the array definition)
const funcStart = aJs.indexOf('function _0x1c23()');
if (funcStart === -1) { console.log('Cannot find _0x1c23 function'); process.exit(1); }

// Find the matching closing brace
let depth = 0, funcEnd = -1;
for (let i = aJs.indexOf('{', funcStart); i < aJs.length; i++) {
    if (aJs[i] === '{') depth++;
    if (aJs[i] === '}') depth--;
    if (depth === 0) { funcEnd = i + 1; break; }
}

// Extract the _0x5451 function 
const decoderStart = aJs.indexOf('function _0x5451(');
let decoderEnd = -1;
depth = 0;
for (let i = aJs.indexOf('{', decoderStart); i < aJs.length; i++) {
    if (aJs[i] === '{') depth++;
    if (aJs[i] === '}') depth--;
    if (depth === 0) { decoderEnd = i + 1; break; }
}

// Extract the IIFE that rotates _0x1c23: (function(_0x1c23,0xc3dd0){...})(_0x1c23,0xc3dd0)
const iifeStart = aJs.indexOf('(function(_0x323382,_0x5d7501)');
const iifeEnd = aJs.indexOf('(_0x1c23,0xc3dd0))', iifeStart) + '(_0x1c23,0xc3dd0)'.length + 1;

const decoderCode = aJs.substring(decoderStart, decoderEnd);
const funcCode = aJs.substring(funcStart, funcEnd);
const iifeCode = aJs.substring(iifeStart, iifeEnd);

// Build standalone script
const script = `
// Anti-debug stubs
var _0x1c23_array;
${funcCode}
_0x1c23_array = _0x1c23();

// The decoder function
${decoderCode}

// Rotation IIFE
${iifeCode}

// Now decode! Let's try a bunch of indices to find c_api_host and API endpoints
var results = {};
for (var idx = 0x1eb; idx <= 0x460; idx++) {
    try {
        var decoded = _0x5451(idx, '');
        if (decoded && typeof decoded === 'string' && decoded.length > 0) {
            // Look for interesting strings: URLs, API paths, function names
            if (decoded.match(/https?:\\/\\//i) || decoded.match(/\\.(php|json|api)/i) || 
                decoded.match(/loadEpisode|loadServer|loadVideo|get_link|admin|api|host|server/i) ||
                decoded.match(/\\/(wp-|api|video|stream|episode)/i) ||
                decoded.match(/\\?action=/i)) {
                results[idx] = decoded;
            }
        }
    } catch(e) {}
}

console.log('\\n=== DECODED INTERESTING STRINGS ===');
console.log(JSON.stringify(results, null, 2));
console.log('Total interesting strings:', Object.keys(results).length);

// Also decode the rotation args to find what 0xc3dd0 was trying to match
console.log('\\n=== ALL DECODED STRINGS (sample) ===');
var allStrings = {};
for (var idx = 0x1eb; idx <= 0x460; idx++) {
    try {
        var decoded = _0x5451(idx, '');
        if (decoded && typeof decoded === 'string') allStrings[idx] = decoded;
    } catch(e) {}
}
console.log(JSON.stringify(allStrings, null, 2));
`;

fs.writeFileSync('C:\\Users\\pro021\\weebflix\\standalone_decode.js', script);
console.log('Written standalone_decode.js');
