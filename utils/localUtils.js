import { findLocationByName, saveLocation } from "../db/dbManager.js"; // DB helpers to read/write locations
import { haversineMeters } from "./geo.js"; // distance calculator in meters

/* =========================
   Local utilities
   ========================= */

export const checkins = new Map();

export function getCurrentUserId(req) {
    // Express normalizes header keys to lowercase
  const headerUid = req.headers["x-user-id"];

  if (headerUid) {
    return String(headerUid);
  }

  // Fallback for local testing when hitting the API manually
  return "demo-user";
}

// quick unique-ish id: prefix + time + random
export function genId(prefix = "id") {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

// clamp a number into [min,max], default to min if not finite
export function clamp(n, min, max) {
  const x = Number(n);
  if (!Number.isFinite(x)) return min;
  return Math.min(Math.max(x, min), max);
}

/* =========================
   Route ordering
   ========================= */

//always go to the closest next pub
export function greedyOrder(origin, pubs) {
  const remaining = pubs.map((p) => ({
    ...p,
    coordinates: {
      longitude: p.coordinates?.longitude ?? p.longitude,
      latitude:  p.coordinates?.latitude  ?? p.latitude,
    },
  }));

  const ordered = []; // output list in visit order
  let prev = {
    coordinates: {
      longitude: origin.coordinates.longitude,
      latitude:  origin.coordinates.latitude,
    },
  };
  let total = 0; // running total distance

  while (remaining.length) {
    let bestIdx = 0;
    let bestDist = Infinity;

    for (let i = 0; i < remaining.length; i++) {
      const cand = remaining[i];

      // compute great-circle distance in meters
      const d = haversineMeters(
        { lat: prev.coordinates.latitude, lon: prev.coordinates.longitude },
        { lat: cand.coordinates.latitude, lon: cand.coordinates.longitude }
      );

      if (d < bestDist) {
        bestDist = d;
        bestIdx = i;
      }
    }

    const next = remaining.splice(bestIdx, 1)[0]; // take nearest next stop
    ordered.push({ ...next, distanceFromPrevious: bestDist }); // annotate hop distance
    total += bestDist; // add to total path length
    prev = next; // move origin forward
  }

  return { ordered, totalDistanceMeters: total }; // final path + total meters
}

/* =========================
   Mapbox feature normalization
   ========================= */

export function sanitizeFeature(f) {
  const coords = f.center || f.geometry?.coordinates || [];
  const lon = coords[0];
  const lat = coords[1];
  return {
    id: f.id,
    text: f.text || "",
    place_name: f.place_name || "",
    geometry: { coordinates: [lon, lat] },
    coordinates: { longitude: lon, latitude: lat },
    place_type: Array.isArray(f.place_type) ? f.place_type[0] : null,
    properties: f.properties || {},
  };
}

/* =========================
   Bar-like detection
   ========================= */

// quick keyword set to detect bar-like places
const BAR_AMENITIES = new Set(["bar", "pub","wine bar",
                              "tiki bar","tavern", "cocktail", 
                              "lounge", "brewery","beer bar", 
                              "whisky", "biergarten","taproom", 
                              "nightclub", "grill", "bar & grill", 
                              "bar and grill","grill","karaoke"]);

// classify if a location looks like a bar/pub/brewery
function isBarLike(loc = {}) {
  const p = loc.properties || {};
  const tags = p.tags || {};

  const amenity = String(p.amenity || tags.amenity || "").toLowerCase();
  const craft = String(p.craft || tags.craft || "").toLowerCase();
  const category = String(p.category || tags.category || "").toLowerCase();
  const name = String(loc.name || "").toLowerCase();

  if (BAR_AMENITIES.has(amenity)) return true;
  if (craft === "brewery") return true;

  // negative patterns to avoid false positives
  const NEG = [
    /\bbar\s*-?\s*ber(s|shop)?\b/,
    /\bbarista\b/,
    /\bhair\s*bar\b/,
    /\bbrow\s*bar\b/,
  ];
  if (NEG.some((rx) => rx.test(name) || rx.test(category))) return false; // early reject

  //common bar categories
  const POS = [
    /\bpub\b/,
    /\btavern\b/,
    /\bnight\s*club\b|\bnightclub\b/,
    /\bbrew(?:ery|pub)\b/,
    /\bcocktail\s*bar\b/,
    /\bwine\s*bar\b/,
    /\bbeer\s*bar\b/,
    /\bwhisk(?:e)?y\s*bar\b/,
    /\bsports\s*bar\b/,
    /\btiki\s*bar\b/,
    /\bhookah\s*lounge\b|\bshisha\s*lounge\b/,
    /\b(?:bar)\b/,
  ];
  if (POS.some((rx) => rx.test(name) || rx.test(category))) return true; // likely a bar

  return false; // default: not bar-like
}

/* =========================
   Search & Save
   ========================= */

export async function searchLocation(locationData) {
  try {
    let name, address, coordinates, place_type, properties;

    if (locationData.place_name && locationData.text) {
      name = locationData.text;
      address = locationData.place_name;
      coordinates = locationData.geometry?.coordinates
        ? {
            longitude: locationData.geometry.coordinates[0],
            latitude:  locationData.geometry.coordinates[1],
          }
        : null;
      place_type = Array.isArray(locationData.place_type) ? locationData.place_type[0] : null;
      properties = locationData.properties || {};
    } else {
      name = locationData.name;
      address = locationData.address;
      coordinates =
        locationData.coordinates &&
        locationData.coordinates.longitude !== undefined &&
        locationData.coordinates.latitude  !== undefined
          ? {
              longitude: locationData.coordinates.longitude,
              latitude:  locationData.coordinates.latitude,
            }
          : null;
      place_type = locationData.place_type || null;
      properties = locationData.properties || {};
    }

    if (!coordinates) throw new Error("Coordinates are required");

    //DB save
    const normalized = {
      name,
      address,
      latitude:  coordinates.latitude,
      longitude: coordinates.longitude,
      place_type,
      properties,
    };

    // if not bar-like
    if (!isBarLike({ name, properties })) {
      return {
        success: true,
        message: "Location accepted but not stored (non-bar category)",
        data: normalized,
        source: "volatile",
        persisted: false,
      };
    }

    // try to find existing by lowercase name key
    const existingLocation = await findLocationByName(name);
    if (existingLocation) {
      return {
        success: true,
        message: "Location found",
        data: existingLocation,
        source: "database",
        persisted: true,
      };
    }

    // save new location to DB and return stored copy
    const savedLocation = await saveLocation(normalized);
    return {
      success: true,
      message: "Location saved to database",
      data: savedLocation,
      source: "saved",
      persisted: true,
    };
  } catch (error) {
    console.error("Error in searchLocation:", error); // log the error for debugging
    throw error;
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

