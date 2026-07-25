const response = {"ptype":"p2p","server_xid":"f1","first_ep_id":"xyMtUFbMQifT","episode_lists":"EPISODE<\/span> 1<\/a>2<\/a>3<\/a>4<\/a>5<\/a>6<\/a><\/p>","active_cat":"hs","active_tag":"ind"};

// The episode_lists is HTML. Let's see the full unescaped HTML
console.log("episode_lists HTML:");
console.log(response.episode_lists);

console.log("\n\nServer xid:", response.server_xid);
console.log("First ep id:", response.first_ep_id);
console.log("Active cat:", response.active_cat);
console.log("Active tag:", response.active_tag);
console.log("Ptype:", response.ptype);

// The episode IDs are embedded in the HTML - "1</a>" means each episode is an <a> tag
// We need the full href/id for each episode
// Let's also check if we can get the next episodes or the actual server list

// Try to load video for episode 1
console.log("\n\n=== Next step: get server for episode xyMtUFbMQifT ===");
console.log("The loadServer function takes: movie_id, server, lang, ep_id, ...");
