const fs = require('fs');
const fullJS = fs.readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f93e36d39001EuUEsI6vOvTlzc', 'utf8');

// Get context around the loadServer URL construction (position 18834)
const ctx1 = fullJS.substring(18500, 20000);
// Also around the other c_api_host usages
const ctx2 = fullJS.substring(25500, 27000);
const ctx3 = fullJS.substring(41000, 42500);

console.log("=== Around loadServer URL (18834) ===");
console.log(ctx1);
console.log("\n\n=== Around 26190 ===");
console.log(ctx2);
