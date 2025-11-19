import { haversineMeters } from "../utils/geo.js"; // great-circle distance helper (Veness 2019)

// format full address from OSM tags
function formatAddress(tags = {}) {
  return [
    tags["addr:housenumber"],
    tags["addr:street"],
    tags["addr:suburb"],
    tags["addr:city"],
    tags["addr:postcode"],
    tags["addr:country"],
  ].filter(Boolean).join(", ");
}

// normalize Overpass element into a standard object
function normOverpassElement(el) {
  const lat = el.lat ?? el.center?.lat;           // ways/relations use 'center' when requested (Overpass 2011)
  const lon = el.lon ?? el.center?.lon;           // via `out center` (Overpass 2011)
  if (typeof lon !== "number" || typeof lat !== "number") return null;
  const name = el.tags?.name || "Bar";
  const address = formatAddress(el.tags);
  const amenity = el.tags?.amenity || (el.tags?.craft === "brewery" ? "brewery" : "bar"); // amenity/craft tags (Overpass 2022)
  return {
    name,
    address,
    coordinates: { longitude: lon, latitude: lat },
    place_type: "establishment",
    properties: {
      source: "overpass",
      osm_id: `${el.type}/${el.id}`, // stable OSM id tuple (Overpass 2011)
      amenity,
      tags: el.tags || {},
    },
  };
}

// build Overpass QL query string
function buildOverpassQuery(lat, lon, radiusMeters, amenities, includeBrewery) {
  const amenityRegex = `^(${amenities.join("|")})$`; // filter by amenity list (Overpass 2011)
  return `
    [out:json][timeout:30];
    (
      node["amenity"~"${amenityRegex}"](around:${radiusMeters},${lat},${lon});
      way["amenity"~"${amenityRegex}"](around:${radiusMeters},${lat},${lon});
      relation["amenity"~"${amenityRegex}"](around:${radiusMeters},${lat},${lon});
      ${includeBrewery
        ? `
      node["craft"="brewery"](around:${radiusMeters},${lat},${lon});
      way["craft"="brewery"](around:${radiusMeters},${lat},${lon});
      relation["craft"="brewery"](around:${radiusMeters},${lat},${lon});`
        : ""}
    );
    out center tags 200;  /* return centroids + tags for non-node elements (Overpass 2011) */
  `.trim();
}

// send POST to Overpass API and return JSON
async function fetchOverpass(query, endpoint, timeoutMs = 25000) {
  const ctrl = new AbortController();                         // request abort support (MDN 2024)
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const resp = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `data=${encodeURIComponent(query)}`,              // URL-encode Overpass QL payload (MDN 2019)
      signal: ctrl.signal,
    });
    const text = await resp.text();
    if (!resp.ok) throw new Error(`HTTP ${resp.status} ${resp.statusText}: ${text.slice(0, 200)}`);
    try { return JSON.parse(text); } catch { return {}; }     // parse JSON safely (MDN 2019)
  } finally {
    clearTimeout(t);
  }
}

// public Overpass API endpoints to try
const OVERPASS_ENDPOINTS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
  "https://overpass.openstreetmap.fr/api/interpreter",
];

// find nearby pubs/bars using Overpass data
export async function discoverNearbyPubs(
  coordinates,
  numberOfPubs = 5, //In app is 10
  radiusMeters = 2000, //10000 metres
  options = {}
) {
  const { longitude, latitude } = coordinates;
  const {
    amenities = ["bar", "pub", "biergarten", "nightclub", "wine bar",
                              "tiki bar","tavern", "cocktail", 
                              "lounge", "brewery","beer bar", 
                              "whisky", "biergarten","taproom", 
                              "grill", "bar & grill", 
                              "bar and grill","grill","karaoke"], // common bar-like amenities (Overpass 2022)
    includeBrewery = true,
    timeoutMs = 25000,
  } = options;

  const q = buildOverpassQuery(latitude, longitude, Math.max(500, radiusMeters), amenities, includeBrewery);

  let data = null;
  let lastErr = null;
  for (const ep of OVERPASS_ENDPOINTS) {
    try {
      data = await fetchOverpass(q, ep, timeoutMs);
      console.log(`[overpass] endpoint ok: ${ep}`);
      break;
    } catch (e) {
      lastErr = e;
      console.log(`[overpass] endpoint failed: ${ep} -> ${e.message}`);
    }
  }
  if (!data) throw new Error(`Overpass failed: ${lastErr?.message || "no response"}`);

  const elements = Array.isArray(data?.elements) ? data.elements : []; // Overpass returns elements[] (Overpass 2011)
  console.log(`[overpass] raw elements: ${elements.length}`);

  const seen = new Set();
  const pubs = elements
    .map(normOverpassElement) // normalize OSM elements (Overpass 2011)
    .filter(Boolean)
    .map((p) => ({
      ...p,
      distance: haversineMeters( // great-circle distance in meters (Veness 2019)
        { lat: latitude, lon: longitude },
        { lat: p.coordinates.latitude, lon: p.coordinates.longitude }
      ),
    }))
    .filter((p) => (radiusMeters ? p.distance <= radiusMeters + 1 : true)) // extra radius guard
    .filter((p) => {
      const key = p.properties?.osm_id || `${p.name}|${p.address}`; // dedupe by id or name+address (Overpass 2011)
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => a.distance - b.distance) // nearest first
    .slice(0, numberOfPubs);

  console.log(`[discover] using Overpass results: ${pubs.length}`);
  return pubs;
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
