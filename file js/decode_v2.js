// Properly decode _0x5451 by extracting ALL needed code from a.js
const fs = require('fs');
const aJs = fs.readFileSync('C:\\Users\\pro021\\.local\\share\\opencode\\tool-output\\tool_f9414da790017KPZ1vlRldkOJh', 'utf8');

// Extract: _0x1c23 function, _0x5451 function, rotation IIFE
const funcStart = aJs.indexOf('function _0x1c23()');
let depth = 0, funcEnd = -1;
for (let i = aJs.indexOf('{', funcStart); i < aJs.length; i++) {
    if (aJs[i] === '{') depth++;
    if (aJs[i] === '}') depth--;
    if (depth === 0) { funcEnd = i + 1; break; }
}

const decoderStart = aJs.indexOf('function _0x5451(');
depth = 0;
let decoderEnd = -1;
for (let i = aJs.indexOf('{', decoderStart); i < aJs.length; i++) {
    if (aJs[i] === '{') depth++;
    if (aJs[i] === '}') depth--;
    if (depth === 0) { decoderEnd = i + 1; break; }
}

// The rotation IIFE starts with the first (function pattern
const iifeStart = aJs.indexOf('(function(_0x323382,_0x5d7501)');
const iifeEndMarker = '(_0x1c23,0xc3dd0))';
const iifeEnd = aJs.indexOf(iifeEndMarker, iifeStart) + iifeEndMarker.length;

const funcCode = aJs.substring(funcStart, funcEnd);
const decoderCode = aJs.substring(decoderStart, decoderEnd);
const iifeCode = aJs.substring(iifeStart, iifeEnd);

// Write a script that just initializes the decoder and tests it
const script = `
// Run the decoder initialization
var _0x5451;
${funcCode}

${decoderCode}

// Run the rotation IIFE
${iifeCode}

// Now test: decode the strings used in initEpisodeList / loadEpisode
// From the decoded output, we know these patterns:
// c_api_host + (is_mob==0x0 ? _0x5451(0x372,'QAs(') : _0x5451(0x403,'zFew'))
// $[_0x5451(0x270,'pZF3')]  => $.ajax
// _0x5451(0x2da,'qc7b')     => '&movie_id='
// _0x5451(0x33d,'Km@Q')     => '&uc='
// _0x5451(0x381,'K[dK')      => '&id='
// _0x5451(0x202,'8&2i')      => '&cat='
// _0x5451(0x266,'wJGT')      => '&tag='
// _0x5451(0x42c,'#RHr')      => '&c='
// _0x5451(0x20c,'zy))')      => '&t='
// _0x5451(0x246,'XzeV')      => 'GET'
// _0x5451(0x310,'wfKC')      => 'json'

// Also decode loadServer, loadVideoLoc patterns
var testIndices = [
    [0x372, 'QAs('], [0x403, 'zFew'], [0x270, 'pZF3'], [0x2da, 'qc7b'],
    [0x33d, 'Km@Q'], [0x381, 'K[dK'], [0x202, '8&2i'], [0x266, 'wJGT'],
    [0x42c, '#RHr'], [0x20c, 'zy))'], [0x246, 'XzeV'], [0x310, 'wfKC'],
    // loadServer patterns
    [0x444, 'LSg8'], [0x28a, 'jcrA'],
    // loadVideoLoc patterns
    [0x1eb, 'QCw+'], [0x362, 'p3eK'],
    // More indices from the code
    [0x317, 'I41E'], [0x2c5, 'oL(9'], [0x287, 'Wo#F'], [0x23a, 'v3Lx'],
    [0x255, 'g!yD'], [0x3a4, 'Y6vP'],
    // get_link patterns
    [0x3a7, '6!Bh'], [0x3a6, 'v3Lx'],
    // Common strings
    [0x27e, '6!Bh'], [0x32f, 'fssI'], [0x369, 'wfKC'],
    // loadVideoHYDRAX patterns  
    [0x3be, 'p3eK'], [0x451, 'qc7b'],
];

console.log('=== DECODED API STRINGS ===');
for (var [idx, key] of testIndices) {
    try {
        var decoded = _0x5451(idx, key);
        console.log('_0x5451(' + '0x' + idx.toString(16) + ', \\'' + key + '\\') = \\'' + decoded + '\\'');
    } catch(e) {
        console.log('_0x5451(' + '0x' + idx.toString(16) + ', \\'' + key + '\\') ERROR: ' + e.message);
    }
}

// Also decode a wider range to find any URLs
console.log('\\n=== SCANNING FOR URLs AND API PATHS ===');
for (var idx = 0x1eb; idx <= 0x460; idx++) {
    try {
        // Try common second args
        for (var key of ['', 'Km@Q', 'fssI', 'wfKC', 'jcrA', 'Z#$e', 'pZF3', 'Y6vP', '8&2i', 'xJrX', 'LSg8', 'ODwK', 'QCw+', 'WOtdUCotFW', 'v3Lx', 'g!yD', 'W7VdQ0ddSY52WO7dKqZdJc4', '(KPj', 'zFew', 'QAs(']) {
            var decoded = _0x5451(idx, key);
            if (decoded && typeof decoded === 'string' && (
                decoded.match(/^https?:\\/\\//) ||
                decoded.match(/\\.php/) ||
                decoded.match(/\\/(wp-|api|video|stream|episode|server|load|get)/i) ||
                decoded.match(/\\?action=/i) ||
                decoded.match(/^\\/[^\\s]/) ||
                decoded.match(/admin/i) ||
                decoded.match(/c_api/i)
            )) {
                console.log('FOUND: _0x5451(0x' + idx.toString(16) + ', \\'' + key + '\\') = \\'' + decoded + '\\'');
            }
        }
    } catch(e) {}
}

// Also try decoding with no second arg (some lookups only need index)
console.log('\\n=== DECODE ALL WITH EMPTY KEY ===');
var allDecoded = {};
for (var idx = 0x1eb; idx <= 0x460; idx++) {
    try {
        var decoded = _0x5451(idx, '');
        if (decoded && typeof decoded === 'string' && decoded.length > 1 && !decoded.match(/[\\x00-\\x08]/)) {
            allDecoded['0x' + idx.toString(16)] = decoded;
        }
    } catch(e) {}
}
// Print only short, readable strings
for (var [k, v] of Object.entries(allDecoded)) {
    if (v.length < 100 && v.match(/^[a-zA-Z0-9_\\-\\/\\s\\.=&?:'"+%!@#{}\\[\\]<>|;,~`^*()]+$/)) {
        console.log(k + ' = ' + JSON.stringify(v));
    }
}
`;

fs.writeFileSync('C:\\Users\\pro021\\weebflix\\decode_v2.js', script);
console.log('Written decode_v2.js');
