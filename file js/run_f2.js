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

fs.writeFileSync('C:/Users/pro021/weebflix/decode_final2.js', code + `
// loadServer URL construction:
// c_api_host + /server.php + ?is_mob= + is_mob + &is_uc= + is_uc + [key1] + _0x4fdac4 + [key2] + _0x514c9b + [key3] + _0x21b1b3 + [key4] + _0x23e92b + &c= + c + &t= + t

// The 4 URL param keys (from _0x2b049a object)
// key1: _0x538f90=0x3cf, _0x4eee1f='8&2i'
console.log('key1 (0x3cf 8&2i):', JSON.stringify(_0x5451(0x3cf, '8&2i')));
// key2: _0x5d9316=0x41a, _0x2147a8='!0^*'
console.log('key2 (0x41a !0^*):', JSON.stringify(_0x5451(0x41a, '!0^*')));
// key3: _0x595e04=0x336, _0x2e37b6='zFew'
console.log('key3 (0x336 zFew):', JSON.stringify(_0x5451(0x336, 'zFew')));
// key4: _0x4d6071=0x2bb, _0x1ce11d='LyA0'
console.log('key4 (0x2bb LyA0):', JSON.stringify(_0x5451(0x2bb, 'LyA0')));

// Also: &c= and &t=
console.log('&c= (0x293 fOtR):', JSON.stringify(_0x5451(0x293, 'fOtR')));
console.log('&t= (0x3e7 zy)):', JSON.stringify(_0x5451(0x3e7, 'zy))')));

// The 5 loadServer params are:
// _0x4fdac4 = cat (hs), _0x514c9b = tag (ind), _0x21b1b3 = server_xid (f1), _0x387d8d = ???, _0x23e92b = ???
// From the call: loadServer(movie_id, server, lang, ep_prefix+ep_id, server_xid)
// Wait no - the params are: cat, tag, server_xid, ep, server_xid_2?

// Actually, looking at the 4th call context more carefully:
// 'episodeId':_0x1e3d78, 'targetCat':_0x58d586, 'server':_0xc4c5dc, 'server_xid':_0x495433
// And: loadServer(_0x1e3d78, _0x58d586, _0x323393, _0xc4c5dc, _0x495433)
// So: loadServer(episodeId, targetCat, lang??, server, server_xid)

// Wait - let me re-read the function params: loadServer(cat, tag, server_xid, ???, ???)
// And the URL has: &cat= + cat + &tag= + tag + &server_xid= + server_xid + [key4] + _0x23e92b + &c= + c + &t= + t

// So there's a 4th URL param after server_xid. Let me find it.
// Looking at the raw code: 
// _0x19ba7d + ?is_mob= + is_mob + &is_uc= + is_uc + &cat= + _0x4fdac4 + &tag= + _0x514c9b + &server_xid= + _0x21b1b3 + [???] + _0x23e92b + &c= + c + &t= + t

// Actually let me look at the EXACT loadServer code to get the 4th param key.
// From the object _0x2b049a, the 4th param key should be decoded with a pair from the object
// The raw code is: _0x32c3ed(_0x2b049a._0x595e04,_0x2b049a._0x2e37b6) which is 0x336,zFew
// But we already identified that as key3
// The NEXT key after that is _0x4d6071,_0x1ce11d = 0x2bb,LyA0 = &server_xid=
// Hmm wait, let me recount...

// Actually looking at the code structure:
// + [key] + _0x4fdac4  (1st value + separator)  -> &cat= + cat_value
// + [key] + _0x514c9b  (2nd value + separator)  -> &tag= + tag_value
// + [key] + _0x21b1b3  (3rd value + separator)  -> &server_xid= + server_xid_value
// + [key] + _0x23e92b  (4th value + separator)  -> ??? + ???_value

// Let me decode what key4 resolves to for the 4th URL param
// Actually looking at the _0x2b049a object more carefully, there might be a 4th key
// Let me check: the pattern is _0x2b049a._0xNNNN, _0x2b049a._0xMMMM for each pair

// From the raw URL code:
// + _0x32c3ed(_0x2b049a._0x5d9316,_0x2b049a._0x2147a8) + _0x514c9b
// + _0x32c3ed(_0x2b049a._0x595e04,_0x2b049a._0x2e37b6) + _0x21b1b3
// + _0x32c3ed(_0x2b049a._0x4d6071,_0x2b049a._0x1ce11d) + _0x23e92b
// + _0x32c3ed(0x293,'fOtR') + c
// + _0x32c3ed(0x3e7,_0x2b049a._0x25e1c3) + t

// So the structure is:
// key2 (0x41a) + _0x514c9b
// key3 (0x336) + _0x21b1b3  
// key4 (0x2bb) + _0x23e92b
// &c= + c
// &t= + t

// And the first key is:
// key1 (0x3cf) + _0x4fdac4

console.log('');
console.log('=== loadServer URL (reconstructed) ===');
const base = 'https://api.nonton.bid/c_api';
console.log(base + '/server.php' + 
  '?is_mob=0' + 
  '&is_uc=0' +
  _0x5451(0x3cf, '8&2i') + 'hs' +
  _0x5451(0x41a, '!0^*') + 'ind' +
  _0x5451(0x336, 'zFew') + 'f1' +
  _0x5451(0x2bb, 'LyA0') + 'xyMtUFbMQifT' +
  '&c=bfb1' +
  '&t=1784892473%26ver%3D373iq');

// Also try without the 4th param
console.log('');
console.log(base + '/server.php' + 
  '?is_mob=0' + 
  '&is_uc=0' +
  _0x5451(0x3cf, '8&2i') + 'hs' +
  _0x5451(0x41a, '!0^*') + 'ind' +
  _0x5451(0x336, 'zFew') + 'f1' +
  '&c=bfb1' +
  '&t=1784892473%26ver%3D373iq');
`);
console.log('Written decode_final2.js');
