// routes/locations.js
import express from "express";

import { listLocations, getLocationById } from "../db/dbManager.js"; // DB reads (Firebase 2025b)
import {
  clamp,
  sanitizeFeature,
  searchLocation,
} from "../utils/localUtils.js"; // helpers and logic (Veness 2019 for distance inside helpers)

// Config (Mapbox)
const MAPBOX_TOKEN = process.env.MAPBOX_TOKEN; // Mapbox API token (Mapbox 2025)
const MAPBOX_GEOCODE_BASE =
  "https://api.mapbox.com/geocoding/v5/mapbox.places"; // geocode base URL (Mapbox 2025)

const router = express.Router();

/**
 * POST /api/locations (search or save)
 */
// (Manico and Detlefsen 2015): validate inputs and handle errors defensively
router.post("/", async (req, res) => {
  try {
    const body = req.body || {}; // request body

    // SEARCH MODE (Mapbox Geocoding)
    if (typeof body.query === "string" && body.query.trim()) {
      if (!MAPBOX_TOKEN) {
        return res
          .status(500)
          .json({
            success: false,
            message: "MAPBOX_TOKEN missing on server",
          }); // missing token (Mapbox 2025)
      }
      const q = body.query.trim(); // search text
      const limit = clamp(body.limit ?? 8, 1, 10); // clamp results
      const lon = body.lon ?? body.longitude; // proximity lon
      const lat = body.lat ?? body.latitude; // proximity lat
      const haveProx =
        Number.isFinite(Number(lon)) && Number.isFinite(Number(lat)); // check prox
      const country = (body.country || "ZA").toString(); // ISO country filter (Mapbox 2025)
      const language = (body.language || "en").toString(); // response language (Mapbox 2025)

      const url = new URL(
        `${MAPBOX_GEOCODE_BASE}/${encodeURIComponent(q)}.json`,
      ); // build URL (Mapbox 2025)
      url.searchParams.set("access_token", MAPBOX_TOKEN); // token (Mapbox 2025)
      url.searchParams.set("limit", String(limit)); // limit (Mapbox 2025)
      url.searchParams.set("types", "poi,place"); // feature types (Mapbox 2025)
      url.searchParams.set(
        "categories",
        "bar,pub,tavern,brewery,nightclub,beer,wine,cocktail",
      ); // categories (Mapbox 2025)
      url.searchParams.set("country", country); // country (Mapbox 2025)
      url.searchParams.set("language", language); // language (Mapbox 2025)
      if (haveProx) url.searchParams.set("proximity", `${lon},${lat}`); // proximity bias (Mapbox 2025)

      const r = await fetch(url.toString()); // call Mapbox (MDN 2024 async/await)
      if (!r.ok) {
        const text = await r.text(); // read error text (MDN 2024)
        return res.status(502).json({
          success: false,
          message: `Mapbox error: ${r.status}`,
          error: text,
        }); // proxy error
      }
      const json = await r.json(); // parse JSON (MDN 2024)
      const features = Array.isArray(json.features)
        ? json.features.map(sanitizeFeature) // normalize features (Mapbox 2025)
        : [];
      return res.json({
        success: true,
        mode: "search",
        data: features,
        meta: { count: features.length }, // count meta
      });
    }

    // SAVE/LOOKUP MODE (persist location)
    const locationData = body; // incoming place
    let name, address; // required fields

    if (locationData.place_name && locationData.text) {
      name = locationData.text; // Mapbox-style name (Mapbox 2025)
      address = locationData.place_name; // Mapbox-style address (Mapbox 2025)
    } else {
      name = locationData.name; // custom name
      address = locationData.address; // custom address
    }

    if (!name || !String(name).trim()) {
      return res.status(400).json({
        success: false,
        message: "Location name is required and cannot be empty",
      }); // validate name
    }
    if (!address || !String(address).trim()) {
      return res.status(400).json({
        success: false,
        message: "Location address is required and cannot be empty",
      }); // validate address
    }

    const result = await searchLocation(locationData); // upsert/search (Firebase 2025b inside implementation)
    return res.status(result.success ? 200 : 404).json(result); // respond with result
  } catch (error) {
    console.error("Server error (/api/locations):", error); // log server error
    if (
      error.message?.includes?.("MONGODB_URI") ||
      error.message?.includes?.("DB_NAME")
    ) {
      return res.status(500).json({
        success: false,
        message:
          "Database configuration error. Please check environment variables.", // db env issue
      });
    }
    if (error.message?.includes?.("Database connection failed")) {
      return res
        .status(500)
        .json({ success: false, message: "Unable to connect to database" }); // connection issue
    }
    if (error.message?.includes?.("Coordinates are required")) {
      return res
        .status(400)
        .json({ success: false, message: "Coordinates are required" }); // missing coords
    }
    return res
      .status(500)
      .json({ success: false, message: "Internal server error" }); // generic 500
  }
});

// (Manico and Detlefsen 2015): paginated read with input clamping
router.get("/", async (req, res) => {
  try {
    const limit = Math.min(Number(req.query.limit ?? 100), 200); // clamp limit
    const skip = Number(req.query.skip ?? 0); // offset
    const rows = await listLocations({ limit, skip }); // fetch list (Firebase 2025b inside)
    res.json({
      success: true,
      data: rows,
      meta: { count: rows.length, limit, skip },
    }); // respond
  } catch (err) {
    console.error("GET /api/locations error:", err); // log error
    res
      .status(500)
      .json({ success: false, message: "Error loading locations" }); // failure
  }
});

// (Manico and Detlefsen 2015): id-based fetch with clear 404 vs 500
router.get("/:id", async (req, res) => {
  try {
    const doc = await getLocationById(req.params.id); // get by id (Firebase 2025b inside)
    if (!doc)
      return res
        .status(404)
        .json({ success: false, message: "Location not found" }); // 404
    return res.json({ success: true, data: doc }); // ok
  } catch (e) {
    console.error("GET /api/locations/:id error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error loading location" }); // fail
  }
});

export default router;
