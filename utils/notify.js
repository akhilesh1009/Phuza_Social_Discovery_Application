import admin from "../db/firebaseAdmin.js"; // Admin SDK already initialized (Firebase 2025a)

/** Return Firestore database instance from Admin SDK */
function fsdb() {
  return admin.firestore(); // get Firestore service (Firebase 2025a)
}

/**
 * Create a new notification document for a user under:
 * Notifications/{userId}/items/{autoId}
 */
// (Firebase 2025c)
export async function createFirestoreNotification(
  userId,
  { type, fromUid, message, fromName, fromUsername }
) {
  const uid = String(userId || "").trim(); // ensure valid user id
  if (!uid) throw new Error("createFirestoreNotification: userId required");

  // create reference to the user’s notifications subcollection (Firebase 2025b)
  const ref = fsdb()
    .collection("Notifications")
    .doc(uid)
    .collection("items")
    .doc(); // auto-generated doc id (Firebase 2025b)

  // payload includes message info + timestamp + read status
  const payload = {
    type: String(type || "notification"),
    fromUid: String(fromUid || ""),
    message: String(message || ""),
    ...(fromName ? { fromName: String(fromName) } : {}),
    ...(fromUsername ? { fromUsername: String(fromUsername) } : {}),
    createdAt: admin.firestore.FieldValue.serverTimestamp(), // server timestamp (Firebase 2025a)
    read: false,
  };

  await ref.set(payload); // write to Firestore (Firebase 2025b)
  return { id: ref.id, ...payload }; // return new doc info
}

/** Get all FCM device tokens for a given user */
// (Firebase 2025c)
export async function getUserDeviceTokens(userId) {
  const uid = String(userId || "").trim();
  if (!uid) return [];
  const col = fsdb().collection("users").doc(uid).collection("fcmTokens"); // Firestore path (Firebase 2025b)
  const docs = await col.listDocuments(); // list document refs (Firebase 2025b)
  return docs.map((d) => d.id); // each doc id is a token
}

/**
 * Send push notifications to all user tokens via FCM.
 * Removes invalid tokens automatically.
 */
// (Firebase 2025c)
export async function sendPushToUser(userId, { title, body, data = {} }) {
  const uid = String(userId || "").trim();
  if (!uid) return { sent: 0, pruned: 0 };

  const tokensCol = fsdb().collection("users").doc(uid).collection("fcmTokens");
  const tokens = await getUserDeviceTokens(uid);
  console.log("[sendPushToUser] uid", uid, "tokens", tokens);
  if (!tokens.length) {
    console.log("[sendPushToUser] no tokens for user", uid);
    return { sent: 0, pruned: 0 };
  }

  const message = {
    tokens,
    notification: {
      title: title ?? "Notification",
      body: body ?? "",
    },
    data: Object.fromEntries(
      Object.entries({
        title: title ?? "Notification",
        body: body ?? "",
        ...data,
      }).map(([k, v]) => [k, v == null ? "" : String(v)])
    ),
    android: {
      priority: "high",
      ttl: 60 * 60 * 1000,
      notification: {
        sound: "default",
        channelId: "phuza-general",
      },
    },
  };

  const res = await admin.messaging().sendEachForMulticast(message);
  console.log("[sendPushToUser] result", {
    successCount: res.successCount,
    failureCount: res.failureCount,
  });

  const toDelete = [];
  res.responses.forEach((r, i) => {
    if (!r.success) {
      console.error(
        "[sendPushToUser] error for token",
        tokens[i],
        r.error?.code,
        r.error?.message
      );
      toDelete.push(tokens[i]);
    }
  });

  await Promise.all(
    toDelete.map((t) => tokensCol.doc(t).delete().catch(() => {}))
  );

  return { sent: res.successCount, pruned: toDelete.length };
}

/** Get a user’s display name and username from Firebase Auth */
export async function getUserProfile(uid) {
  try {
    const rec = await admin.auth().getUser(String(uid)); // fetch user record (Firebase 2025a)
    const displayName =
      rec.displayName ||
      rec.customClaims?.displayName ||
      ""; // may not exist

    // pick username from custom claim, email, or phone
    const username =
      rec.customClaims?.username ||
      (rec.email ? rec.email.split("@")[0] : "") ||
      (rec.phoneNumber || "") ||
      String(uid);

    return {
      displayName: displayName || username || String(uid),
      username: username || String(uid),
    };
  } catch {
    // fallback in case user not found
    return { displayName: String(uid), username: String(uid) };
  }
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
