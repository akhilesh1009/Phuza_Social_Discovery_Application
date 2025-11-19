import admin from "../db/firebaseAdmin.js";

/** RTDB handle (singleton) */
export function rtdb() {
  return admin.database(); // returns the shared real-time database instance
}

// get all users from the "users" node in the RTDB
export async function getAllUsers() {
  const snap = await rtdb().ref("users").once("value"); // read all data once
  const val = snap.val() || {}; // default to empty object if null

  // convert the object into an array of {id, data}
  return Object.entries(val).map(([id, data]) => ({
    id,
    ...(data || {}),
  }));
}

// get users in pages using startAfter + limit for pagination
export async function getUsersPage({ limit = 50, startAfter } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 50, 200)); // clamp limit between 1–200

  let ref = rtdb().ref("users").orderByKey(); // order by user key (id)
  if (startAfter != null && startAfter !== "") {
    ref = ref.startAfter(String(startAfter)); // start after a given user id
  }
  ref = ref.limitToFirst(n); // limit to n users

  const snap = await ref.get(); // fetch data snapshot
  const obj = snap.val() || {}; // handle empty case

  // format data so each entry has uid + id + other info
  const users = Object.entries(obj).map(([uid, data]) =>
    (data && typeof data === "object")
      ? { uid, id: uid, ...data }
      : { uid, id: uid, value: data }
  );

  // token for the next page (last uid in current set)
  const nextPageToken = users.length ? users[users.length - 1].uid : null;

  return { users, nextPageToken }; // return both data + next page key
}

// get list of friend IDs for a specific user
export async function getFriends(userId) {
  const snap = await rtdb().ref(`users/${userId}/friends`).get(); // fetch friend list
  const obj = snap.val() || {}; // default to empty if none
  return Object.keys(obj); // return array of friend IDs
}

// add a friend by setting their id to true in the user's friends list
export async function addFriend(userId, friendId) {
  await rtdb().ref(`users/${userId}/friends/${friendId}`).set(true);
}

// remove a friend by deleting their id entry from the user's friends list
export async function removeFriend(userId, friendId) {
  await rtdb().ref(`users/${userId}/friends/${friendId}`).remove();
}

/*
 * REFERENCES

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

