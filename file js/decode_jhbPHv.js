const fs = require('fs');
const html = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e24f08001877qbkweswYqtm', 'utf8');

// Extract the jhbPHv string
const match = html.match(/jhbPHv='([^']+)'/);
if (!match) { console.log('Not found'); process.exit(1); }
const blob = match[1];

let onaPo = '';
blob.split('.').forEach(function(part) {
    onaPo += String.fromCharCode(parseInt(atob(part).replace(/\D/g, ''), 10));
});

const decoded = decodeURIComponent(escape(onaPo));
fs.writeFileSync('C:/Users/pro021/weebflix/jhbPHv_decoded.js', decoded);
console.log('Length:', decoded.length);
console.log('First 2000 chars:');
console.log(decoded.substring(0, 2000));
