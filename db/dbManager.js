import admin from "firebase-admin"
import { haversineMeters } from "../utils/geo.js"
// (Firebase 2025a) Admin SDK bootstrap/init guidance

let app // holds the Firebase app so we can reuse/close it
let db  // cached Firestore instance to avoid re-creating it

function getFirestore() {
  if (db) return db // return cached db if already set

  // (Firebase 2025a) initialize once: admin.apps / initializeApp
  if (!admin.apps?.length) {
    app = admin.initializeApp({
      credential: admin.credential.applicationDefault(), // (Firebase 2025a)
      projectId: process.env.FIREBASE_PROJECT_ID,
      databaseURL: process.env.FIREBASE_RTDB_URL
    })
  } else {
    app = admin.app() // (Firebase 2025a)
  }

  db = admin.firestore() // (Firebase 2025a)
  return db
}

async function connectToDatabase() {
  return getFirestore() // (Firebase 2025a)
}

async function listLocations({ limit = 50, skip = 0 } = {}) {
  const db = getFirestore()

  // server-side page over recent locations (orderBy/limit/offset) (Firebase 2025b)
  const snap = await db
    .collection("locations")
    .orderBy("createdAt", "desc") // (Firebase 2025b)
    .offset(Number(skip))          // (Firebase 2025b)
    .limit(Number(limit))          // (Firebase 2025b)
    .get()

  // map docs to plain objects with id included
  return snap.docs.map((d) => ({ _id: d.id, ...d.data() }))
}

async function findLocationByName(name) {
  try {
    const db = getFirestore()

    // case-insensitive search via lowercase field (where + limit) (Firebase 2025b)
    const snap = await db
      .collection("locations")
      .where("name_lc", "==", String(name).toLowerCase()) // (Firebase 2025b)
      .limit(1)                                           // (Firebase 2025b)
      .get()

    if (snap.empty) return null

    const doc = snap.docs[0]
    return { _id: doc.id, ...doc.data() } // return first match
  } catch (error) {
    console.error("Error finding location:", error) // log for debugging
    throw new Error("Failed to search location in database") // safe error up
  }
}

/* -------------------------------------------
 * Mapbox helpers (address + amenity enrichment)
 * ----------------------------------------- */

// Reverse-geocode coordinates to address (Mapbox Geocoding) (Mapbox 2025)
async function reverseGeocodeMapbox(lat, lon) {
  if (!process.env.MAPBOX_TOKEN) return { address: null, feature: null } // skip if no token

  // (Mapbox 2025) /geocoding/v5/{lon},{lat}.json with types & limit
  const url =
    `https://api.mapbox.com/geocoding/v5/mapbox.places/${lon},${lat}.json` +
    `?access_token=${process.env.MAPBOX_TOKEN}` +
    `&limit=1&types=poi,address,place,locality,neighborhood`

  try {
    const res = await fetch(encodeURI(url)) // call Mapbox Geocoding API (Mapbox 2025)
    if (!res.ok) return { address: null, feature: null }
    const j = await res.json()
    const feature = j?.features?.[0] || null // pick top result if any
    const address = feature?.place_name || null // (Mapbox 2025)
    return { address, feature }
  } catch {
    return { address: null, feature: null }
  }
}

// Guess a simple amenity type from Mapbox feature categories/text (Mapbox 2025)
function classifyAmenityFromMapbox(feature) {
  const catStr = feature?.properties?.category || "" // (Mapbox 2025)
  const cats = catStr.toLowerCase().split(",").map(s => s.trim()).filter(Boolean)
  const text = (feature?.text || "").toLowerCase() // (Mapbox 2025)
  const maki = (feature?.properties?.maki || "").toLowerCase() // (Mapbox 2025)
  const has = (kw) => cats.includes(kw) || text.includes(kw) || maki === kw

  if (has("pub")) return "pub"
  if (has("brewery")) return "brewery"
  if (has("nightclub")) return "nightclub"
  if (has("restaurant")) return "restaurant"
  if (has("cafe") || has("coffee")) return "cafe"
  if (has("bar")) return "bar"

  return "bar" // default if nothing matches
}

async function saveLocation(locationData) {
  try {
    const db = getFirestore()

    // start with provided amenity
    let amenity =
      locationData?.amenity ||
      locationData?.properties?.amenity ||
      (locationData?.properties?.tags?.craft === "brewery" ? "brewery" : "bar")

    let address = locationData.address ?? null
    const lat = locationData.latitude ?? null
    const lon = locationData.longitude ?? null

    if (typeof lat === "number" && typeof lon === "number") {
      const addressMissing = !address || address.trim() === ""
      const amenityWeak = !amenity || amenity === "bar"

      if (addressMissing || amenityWeak) {
        const { address: mbAddr, feature } = await reverseGeocodeMapbox(lat, lon) // (Mapbox 2025)

        if (addressMissing && mbAddr) address = mbAddr
        if (amenityWeak && feature) {
          const inferred = classifyAmenityFromMapbox(feature) // (Mapbox 2025)
          if (inferred) amenity = inferred
        }
      }
    }

    const payload = {
      name: locationData.name,
      name_lc: String(locationData.name || "").toLowerCase(), // for case-insensitive search
      address: address ?? null,
      latitude: typeof lat === "number" ? lat : null,
      longitude: typeof lon === "number" ? lon : null,
      amenity,
      createdAt: admin.firestore.FieldValue.serverTimestamp(), // server time (Firebase 2025a)
    }

    // write to Firestore and read back the stored doc (Firebase 2025b)
    const ref = await db.collection("locations").add(payload) // (Firebase 2025b)
    const stored = await ref.get() // (Firebase 2025b)
    return { _id: ref.id, ...stored.data() } // include generated id
  } catch (error) {
    console.error("Error saving location:", error) // surface details in logs
    throw new Error("Failed to save location to database") // generic message to callers
  }
}

async function getLocationById(id) {
  try {
    const db = getFirestore()
    // read single doc by id (Firebase 2025b)
    const doc = await db.collection("locations").doc(String(id)).get() // (Firebase 2025b)
    if (!doc.exists) return null
    return { _id: doc.id, ...doc.data() }
  } catch (error) {
    console.error("Error getting location by ID:", error)
    throw new Error("Failed to get location by ID")
  }
}

/* -------------------------------------------
 * Close connection
 * ----------------------------------------- */
async function closeConnection() {
  if (app) {
    await app.delete() // (Firebase 2025a)
    app = null
    db = null
  }
}

async function listLocationsNear({
  latitude,
  longitude,
  radiusMeters = 2000,
  limit = 100,
}) {
  const db = getFirestore()

  // recent window (orderBy/limit) before client-side geo filter (Firebase 2025b)
  const snap = await db
    .collection("locations")
    .orderBy("createdAt", "desc") // (Firebase 2025b)
    .limit(Math.max(limit, 100))  // (Firebase 2025b)
    .get()

  const center = { lat: latitude, lon: longitude }

  const items = snap.docs
    .map((d) => ({ _id: d.id, ...d.data() }))
    .filter(
      (d) => typeof d.latitude === "number" && typeof d.longitude === "number"
    )
    .map((d) => {
      // compute great-circle distance (Veness 2019)
      const dist = haversineMeters(center, {
        lat: d.latitude,
        lon: d.longitude,
      })
      return { ...d, distance: dist }
    })
    .filter((d) => d.distance <= radiusMeters)
    .sort((a, b) => a.distance - b.distance)
    .slice(0, limit)

  return items // nearest items within the given radius
}

/* ----------------------------------------- */

export {
  connectToDatabase,
  listLocations,
  findLocationByName,
  saveLocation,
  getLocationById,
  closeConnection,
  listLocationsNear,
}

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
