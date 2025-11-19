// (Manico and Detlefsen 2015): secure config loading pattern via environment variables
import "dotenv/config"; // load env vars from .env

import express from "express"; // web server framework
import cors from "cors"; // enable CORS

// Routers
import locationsRouter from "./routes/locations.js";
import discoverRouter from "./routes/discover.js";
import friendsRouter from "./routes/friends.js";
import notificationsRouter from "./utils/notifications.js";
import checkinsRouter from "./routes/checkins.js";
import messagesRouter from "./routes/messages.js";
import pubgolfRouter from "./routes/pubgolf.js";

// -----------------------------------------------------------------------------------------
// Config
// -----------------------------------------------------------------------------------------
const PORT = process.env.PORT || 8080; // server port

// App
const app = express(); // create express app
app.use(cors()); // enable CORS
app.use(express.json()); // parse JSON bodies

// -----------------------------------------------------------------------------------------
// Health
// -----------------------------------------------------------------------------------------
// (Manico and Detlefsen 2015): simple health-check endpoint
app.get("/", (_req, res) => {
  res.json({ ok: true, name: "Health Check" }); // health endpoint
});

// -----------------------------------------------------------------------------------------
// Routes
// -----------------------------------------------------------------------------------------

// Locations (search + save + list + by-id)
app.use("/api/locations", locationsRouter);

// Discovery + routing
app.use("/api", discoverRouter);

// Friends
app.use("/api/friends", friendsRouter);

// Notifications
app.use("/api/notify", notificationsRouter);

// Check-ins
app.use("/api/checkins", checkinsRouter);

// Messages (chat)
app.use("/api/messages", messagesRouter);

// Pub Golf games
app.use("/api/pubgolf", pubgolfRouter);

/* =========================
   404
   ========================= */
app.use((req, res) => {
  res.status(404).json({ success: false, message: "Endpoint not found" }); // default 404
});

const server = app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);

  console.log(`POST http://localhost:${PORT}/api/locations`);
  console.log(`GET  http://localhost:${PORT}/api/locations`);
  console.log(`GET  http://localhost:${PORT}/api/locations/:id`);

  console.log(`POST http://localhost:${PORT}/api/discover-pubs`);
  console.log(`POST http://localhost:${PORT}/api/discover-route`);
  console.log(`POST http://localhost:${PORT}/api/generate-route`);

  console.log(`POST http://localhost:${PORT}/api/checkins`);
  console.log(`DELETE http://localhost:${PORT}/api/checkins/:id`);

  console.log(`GET  http://localhost:${PORT}/api/friends`);
  console.log(`POST http://localhost:${PORT}/api/friends/:friendId`);
  console.log(`DELETE http://localhost:${PORT}/api/friends/:friendId`);

  console.log(`POST http://localhost:${PORT}/api/notify/follow-request`);
  console.log(`POST http://localhost:${PORT}/api/notify/follow-accept`);

  console.log(`GET  http://localhost:${PORT}/api/messages`);
  console.log(`GET  http://localhost:${PORT}/api/messages/since`);
  console.log(`GET  http://localhost:${PORT}/api/messages/:chatId`);
  console.log(`POST http://localhost:${PORT}/api/messages`);

  console.log(`POST http://localhost:${PORT}/api/pubgolf/games`);
  console.log(`GET  http://localhost:${PORT}/api/pubgolf/games/:gameId`);
  console.log(`POST http://localhost:${PORT}/api/pubgolf/games/:gameId/invites`);
  console.log(`GET  http://localhost:${PORT}/api/pubgolf/invites`);
  console.log(`POST http://localhost:${PORT}/api/pubgolf/invites/:inviteId/respond`);
});


export default server;


/*
 * REFERENCES
 *
 * Android Developers. 2025. 
 * “Create and Manage Notification Channels”. 
 * <https://developer.android.com/develop/ui/views/notifications/channels> 
 * [accessed 24 September 2025].
 * 
 * Firebase. 2025a. 
 * “Add the Firebase Admin SDK to Your Server”.
 * <https://firebase.google.com/docs/admin/setup> 
 * [accessed 20 September 2025].
 * 
 * Firebase. 2025b. 
 * “Perform Simple and Compound Queries in Cloud Firestore | Firebase Documentation”. 
 * <https://firebase.google.com/docs/firestore/query-data/queries> 
 * [accessed 20 September 2025].
 * 
 * Firebase. 2025c. 
 * “Send a Message Using Firebase Admin SDK | Firebase Cloud Messaging”. 
 * <https://firebase.google.com/docs/cloud-messaging/send/admin-sdk> 
 * [accessed 23 September 2025].
 * 
 * GeeksforGeeks. 2022. 
 * “How to Get Currently Signed in User in Web and Firebase ?” 
 * <https://www.geeksforgeeks.org/firebase/how-to-get-currently-signed-in-user-in-web-and-firebase/> 
 * [accessed 25 September 2025].
 * 
 * Manico, Jim and August Detlefsen. 2015. Iron-Clad Java: Building Secure Web Applications. New York, N.Y. Mcgraw-Hill Education.
 * 
 * Mapbox. 2025. 
 * “Geocoding | API”. 
 * <https://docs.mapbox.com/api/search/geocoding/>
 *  [accessed 23 September 2025].
 * 
 * MDN. 2019.
 * “JSON.parse()”.
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/parse>
 * [accessed 5 October 2025].
 * 
 * MDN. 2024. 
 * “How to Use Promises - Learn Web Development | MDN”.
 * <https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Async_JS/Promises#async_and_await>
 * [accessed 19 September 2025].
 * 
 * MDN. 2025a.
 * “Math - JavaScript | MDN”.
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math>
 * [accessed 21 September 2025].
 * 
 * MDN. 2025b.
 * “Object.entries() - JavaScript | MDN”. 
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/entries> 
 * [accessed 18 September 2025].
 * 
 * MDN. 2025c. 
 * “RegExp.prototype.test() - JavaScript | MDN”. 
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/test> 
 * [accessed 19 September 2025].
 * 
 * Overpass. 2011. 
 * “Overpass API - OpenStreetMap Wiki”. 
 * <https://wiki.openstreetmap.org/wiki/Overpass_API>
 * [accessed 23 September 2025].
 * 
 * Overpass. 2022.
 * “Key:amenity - OpenStreetMap Wiki”.
 * <https://wiki.openstreetmap.org/wiki/Key:amenity>
 * [accessed 28 September 2025].
 * 
 * Veness, Chris. 2019. 
 * “Calculate Distance and Bearing between Two Latitude/Longitude Points Using Haversine Formula in JavaScript”.
 * <https://www.movable-type.co.uk/scripts/latlong.html>
 *  [accessed 19 September 2025].
 */
