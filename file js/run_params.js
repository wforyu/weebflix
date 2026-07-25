const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');
const c1c23Start = fullJS.indexOf('function _0x1c23()');
let braceCount = 0, c1c23End = c1c23Start;
for (let i = c1c23Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { c1c23End = i + 1; break; } }
}
const c1c23Func = fullJS.substring(c1c23Start, c1c23End);
const d5451Start = fullJS.indexOf('function _0x5451');
braceCount = 0;
let d5451End = d5451Start;
for (let i = d5451Start; i < fullJS.length; i++) {
    if (fullJS[i] === '{') braceCount++;
    if (fullJS[i] === '}') { braceCount--; if (braceCount === 0) { d5451End = i + 1; break; } }
}
const d5451Func = fullJS.substring(d5451Start, d5451End);
const iifeStart = 21;
const iifeEndIdx = fullJS.indexOf(',0xc3dd0))', iifeStart) + ',0xc3dd0))'.length;
const iife = fullJS.substring(iifeStart, iifeEndIdx);
const code = c1c23Func + '\n' + d5451Func + '\nvar _0x8730fc=_0x5451;\n' + iife + ';\n';

fs.writeFileSync('C:/Users/pro021/weebflix/decode_params.js', code + `
// loadServer URL params
// _0x19ba7d + ?is_mob= + is_mob + &is_uc= + is_uc + [param1] + _0x4fdac4 + [param2] + _0x514c9b + [param3] + _0x21b1b3 + [param4] + _0x23e92b + &c= + c + &t= + t
// where param keys:
// ?is_mob=    _0x36d VpRf
// &is_uc=     _0x42d LyA0  (0x42d is the hex, key 'LyA0')  
// param1:     _0x538f90=0x3cf, key='8&2i'  => _0x5451(0x3cf, '8&2i')
// param2:     _0x5d9316=0x41a, key='!0^*'  => _0x5451(0x41a, '!0^*')
// param3:     _0x595e04=0x336, key='zFew'  => _0x5451(0x336, 'zFew')
// param4:     _0x4d6071=0x2bb, key='LyA0'  => _0x5451(0x2bb, 'LyA0')
// &c=         _0x293 fOtR
// &t=         0x3e7, key=zy))

console.log('=== loadServer URL parameters ===');
console.log('?is_mob=', JSON.stringify(_0x5451(0x36d, 'VpRf')));
console.log('&is_uc=', JSON.stringify(_0x5451(0x42d, 'LyA0')));
console.log('param1=', JSON.stringify(_0x5451(0x3cf, '8&2i')));
console.log('param2=', JSON.stringify(_0x5451(0x41a, '!0^*')));
console.log('param3=', JSON.stringify(_0x5451(0x336, 'zFew')));
console.log('param4=', JSON.stringify(_0x5451(0x2bb, 'LyA0')));
console.log('&c=', JSON.stringify(_0x5451(0x293, 'fOtR')));
console.log('&t=', JSON.stringify(_0x5451(0x3e7, 'zy))')));

// Also decode the loadServer function params:
// function loadServer(_0x4fdac4, _0x514c9b, _0x21b1b3, _0x387d8d, _0x23e92b)
// From the code context, these are: cat, tag, server_xid, ?, episode
// Let me check by looking at the onclick handlers
console.log('\\n=== Looking at how loadServer is called ===');
// From the HTML episode data: onclick="loadEpisode('yLpA1nCVmw','hs','ind')"
// And loadEpisode calls loadServer with (movie_id, server, lang, ep_id, server_xid)
// So the parameters passed to loadServer are: _0x4fdac4=movie_id, _0x514c9b=server, _0x21b1b3=lang, _0x387d8d=ep_id, _0x23e92b=server_xid

// Wait, let me re-examine. From the a.js:
// initEpisodeList(movie_id, server, lang)
// loadEpisode(movie_id, server, lang) -> calls loadServer
// loadServer(movie_id, server, lang, ep_id, server_xid) -> builds URL

// Actually looking at the URL params decoded above:
// param1 = &cat=   -> _0x4fdac4 = cat (server type, e.g. 'hs')
// param2 = &tag=   -> _0x514c9b = tag (language, e.g. 'ind')  
// param3 = &server_xid= -> _0x21b1b3 = server_xid
// param4 = ?       -> _0x23e92b = episode

// Hmm wait, let me check param4
console.log('\\nparam4 again (0x2bb LyA0):', JSON.stringify(_0x5451(0x2bb, 'LyA0')));
// That's &server_xid=, so there must be a 5th param
// Let me check what _0x387d8d is used for

// From the JS code around 26000:
// _0x19ba7d + ?is_mob= + is_mob + &is_uc= + is_uc + param1 + _0x4fdac4 + param2 + _0x514c9b + param3 + _0x21b1b3 + param4 + _0x23e92b + &c= + c + &t= + t
// So:
// ?is_mob=0
// &is_uc=0
// param1 (cat=) + _0x4fdac4 (value)
// param2 (tag=) + _0x514c9b (value)  
// param3 (server_xid=) + _0x21b1b3 (value)
// param4 + _0x23e92b (value)

// But we only found 4 params after &is_uc=. Let me check what param4 is
// The 4th param key would be decoded from the _0x2b049a object
// _0x2b049a._0x4d6071=0x2bb, _0x2b049a._0x1ce11d='LyA0' -> &server_xid=
// But that leaves _0x23e92b as the 5th variable
// Wait, let me recount. The URL is:
// base + ?is_mob= + val + &is_uc= + val + param1 + val + param2 + val + param3 + val + param4 + val + &c= + c + &t= + t

// Hmm, but there are only 4 variable params (5 values with &is_uc). Let me look at the raw code more carefully.
console.log('\\nDone');
`);
console.log('Written decode_params.js');
