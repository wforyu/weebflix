// Copy lines 1-4 from decode_v2.js (the decoder setup)
const fs = require('fs');
const src = fs.readFileSync('C:/Users/pro021/weebflix/decode_v2.js', 'utf8');
// Get just lines 1-4 (functions + IIFE)
const lines = src.split('\n');
const setup = lines.slice(0, 4).join('\n');

fs.writeFileSync('C:/Users/pro021/weebflix/decode_video.js', setup + `

console.log('=== loadVideoHYDRAX URL params ===');
// From object _0x3b5055, property names map to values used as hex in _0x5451()
// _0x4a4eb6:0x2b8, _0x48ddbf:'QAs('  => _0x5451(0x2b8, 'QAs(')
console.log('path:', JSON.stringify(_0x5451(0x2b8, 'QAs(')));
// _0x3853d7:'S]mN' => _0x5451(0x2af, 'S]mN') [0x2af is direct hex]
console.log('&is_uc:', JSON.stringify(_0x5451(0x2af, 'S]mN')));
// _0x3b48cd:'VpRf' => _0x5451(0x24d, 'VpRf')
console.log('&id:', JSON.stringify(_0x5451(0x24d, 'VpRf')));
// _0x6e03d2:'vlq*' => _0x5451(_0x3b5055._0x527932, 'vlq*')
// _0x527932:0x407 => _0x5451(0x407, 'vlq*')
console.log('&qua:', JSON.stringify(_0x5451(0x407, 'vlq*')));
// _0x4ca149:'Km@Q' => _0x5451(_0x3b5055._0x5a1017, 'Km@Q')
// _0x5a1017:0x29c => _0x5451(0x29c, 'Km@Q')
console.log('&res:', JSON.stringify(_0x5451(0x29c, 'Km@Q')));
// _0x5041da:'zFew' => _0x5451(_0x3b5055._0x26c229, 'zFew')
// _0x26c229:0x21b => _0x5451(0x21b, 'zFew')
console.log('&server_id:', JSON.stringify(_0x5451(0x21b, 'zFew')));
// _0x11e795:'(KPj' => _0x5451(_0x3b5055._0x17ae48, '(KPj')
// _0x17ae48:0x321 => _0x5451(0x321, '(KPj')
console.log('&cat:', JSON.stringify(_0x5451(0x321, '(KPj')));
// _0x1f0190:'wJGT' => _0x5451(0x324, 'wJGT') [direct hex]
console.log('&tag:', JSON.stringify(_0x5451(0x324, 'wJGT')));
// _0xbfe9af:'Y6vP' => _0x5451(0x1ec, 'Y6vP') [direct hex]
console.log('&c:', JSON.stringify(_0x5451(0x1ec, 'Y6vP')));
// _0x404152:'I41E' => _0x5451(_0x3b5055._0x5df94d, 'I41E')
// _0x5df94d:0x3a3 => _0x5451(0x3a3, 'I41E')
console.log('&t:', JSON.stringify(_0x5451(0x3a3, 'I41E')));

console.log('\\n=== get_link URL params ===');
// From object _0x7d7a6b:
// _0x14063b:'v3Lx' => _0x5451(0x440, 'v3Lx')
console.log('path:', JSON.stringify(_0x5451(0x440, 'v3Lx')));
// _0x3cd62f:0x436, _0x431a90:'34IJ' => _0x5451(0x436, '34IJ')
console.log('param1:', JSON.stringify(_0x5451(0x436, '34IJ')));
// _0x44079a:'zy))' => _0x5451(0x275, 'zy))')
console.log('param2:', JSON.stringify(_0x5451(0x275, 'zy))')));
// _0x35a238:0x3c5, _0x4a3f88:'p3eK' => _0x5451(0x3c5, 'p3eK')
console.log('param3:', JSON.stringify(_0x5451(0x3c5, 'p3eK')));
// _0x591b88:0x37b, _0xcb603f:'KQaa' => _0x5451(0x37b, 'KQaa')
console.log('param4:', JSON.stringify(_0x5451(0x37b, 'KQaa')));
// _0x38e523:0x33c, _0x33ca08:'EWbD' => _0x5451(0x33c, 'EWbD')
console.log('param5:', JSON.stringify(_0x5451(0x33c, 'EWbD')));

// loadVideoLoc: find its function too
// Let me also check loadVideoSB
console.log('\\n=== Additional decodes ===');
console.log('0x2b8 ODwK:', JSON.stringify(_0x5451(0x2b8, 'ODwK')));
console.log('0x2b8 QAs(:', JSON.stringify(_0x5451(0x2b8, 'QAs(')));
console.log('0x214 wJGT:', JSON.stringify(_0x5451(0x214, 'wJGT')));
console.log('0x205 wJGT:', JSON.stringify(_0x5451(0x205, 'wJGT')));
console.log('0x28f B]I&:', JSON.stringify(_0x5451(0x28f, 'B]I&')));
console.log('0x33e LyA0:', JSON.stringify(_0x5451(0x33e, 'LyA0')));
console.log('0x3b2 ODwK:', JSON.stringify(_0x5451(0x3b2, 'ODwK')));
`);
console.log('Written decode_video.js');
