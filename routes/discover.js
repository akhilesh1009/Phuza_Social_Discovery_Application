// routes/discover.js
import express from "express";

import {
  clamp,
  greedyOrder,
  searchLocation,
} from "../utils/localUtils.js"; // helpers (Veness 2019 for Haversine)
import { generateOptimalRoute } from "../utils/routeGenerator.js"; // route builder
import { discoverNearbyPubs } from "../overpass/overpassManager.js"; // OSM/Overpass search (Overpass 2011)

// Defaults for discover & route
const DEFAULT_PUBS = 10; // default number of pubs to fetch
const DEFAULT_RADIUS_METERS = 10_000; // default search radius

const router = express.Router();

/**
 * POST /api/discover-pubs
 * Find pubs near an origin and return a greedy-ordered list.
 */
// (Manico and Detlefsen 2015): validate inputs; handle external API failures cleanly
router.post("/discover-pubs", async (req, res) => {
  try {
    const { origin, numberOfPubs, radiusMeters } = req.body || {}; // inputs

    if (!origin?.coordinates?.latitude || !origin?.coordinates?.longitude) {
      return res.status(400).json({
        success: false,
        message:
          "origin with coordinates {latitude, longitude} is required",
      }); // validate origin
    }

    const count = clamp(numberOfPubs ?? DEFAULT_PUBS, 1, 100); // clamp count
    const radius = clamp(
      radiusMeters ?? DEFAULT_RADIUS_METERS,
      50,
      100_000,
    ); // clamp radius

    const pubs = await discoverNearbyPubs(
      {
        longitude: origin.coordinates.longitude,
        latitude: origin.coordinates.latitude,
      }, // origin coords
      count,
      radius,
    ); // Overpass discovery (Overpass 2011)

    if (!pubs.length) {
      return res
        .status(404)
        .json({ success: false, message: "No pubs found near the origin" }); // no results
    }

    // Save origin and pubs (Firebase 2025b inside searchLocation)
    try {
      await searchLocation(origin); // save origin
    } catch {}
    for (const p of pubs) {
      try {
        await searchLocation(p); // save pub
      } catch {}
    }

    // Order pubs greedily from the origin (nearest-neighbor using Haversine) (Veness 2019)
    const { ordered, totalDistanceMeters } = greedyOrder(origin, pubs);

    return res.status(200).json({
      success: true,
      message: `Saved new pubs (if any) and returned ${ordered.length} pubs from database`, // summary
      data: {
        origin,
        pubs: ordered.map((p) => ({
          name: p.name,
          address: p.address,
          coordinates: p.coordinates,
          amenity: p.amenity,
          distanceFromPreviousKm: Number(
            (p.distanceFromPrevious / 1000).toFixed(3),
          ), // meters→km
        })),
        meta: {
          orderedCount: ordered.length,
          totalDistanceKm: Number((totalDistanceMeters / 1000).toFixed(2)), // total distance
          searchRadius: radius,
          source: "database",
        },
      },
    });
  } catch (error) {
    console.error("POST /api/discover-pubs error:", error); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error discovering pubs" }); // fail
  }
});

/**
 * POST /api/discover-route
 * Discover pubs via Overpass then build an optimal route.
 */
// (Manico and Detlefsen 2015): robust validation + logging
router.post("/discover-route", async (req, res) => {
  try {
    const { startingPoint, numberOfPubs, radiusMeters } = req.body || {}; // inputs

    if (!startingPoint || !startingPoint.coordinates) {
      return res.status(400).json({
        success: false,
        message: "Starting point with coordinates is required",
      }); // validate
    }

    const pubCount = clamp(numberOfPubs ?? DEFAULT_PUBS, 1, 100); // clamp count
    const radius = clamp(
      radiusMeters ?? DEFAULT_RADIUS_METERS,
      50,
      100_000,
    ); // clamp radius

    const discoveredPubs = await discoverNearbyPubs(
      startingPoint.coordinates,
      pubCount,
      radius,
    ); // query Overpass (Overpass 2011)

    console.log(`[discover-route] pubs_found=${discoveredPubs.length}`); // debug log

    if (!discoveredPubs.length) {
      return res.status(404).json({
        success: false,
        message: "No pubs found near the starting location",
      }); // no pubs
    }

    // Save starting point + pubs (best-effort) (Firebase 2025b inside searchLocation)
    try {
      await searchLocation(startingPoint); // save start
    } catch (e) {
      console.error("Error saving starting point:", e); // log save error
    }
    for (let i = 0; i < discoveredPubs.length; i++) {
      try {
        await searchLocation(discoveredPubs[i]); // save each pub
      } catch (e) {
        console.error(`Error saving pub ${i + 1}:`, e); // log failure
      }
    }

    // Normalize coordinates for route generator
    const transformedPubs = discoveredPubs.map((pub) => ({
      ...pub,
      coordinates: {
        latitude: pub.latitude || pub.coordinates?.latitude,
        longitude: pub.longitude || pub.coordinates?.longitude,
      },
    })); // ensure lat/lon shape

    const transformedStartingPoint = {
      ...startingPoint,
      coordinates: {
        latitude: startingPoint.coordinates?.latitude || startingPoint.latitude,
        longitude:
          startingPoint.coordinates?.longitude || startingPoint.longitude,
      },
    }; // ensure lat/lon shape

    const routeResult = generateOptimalRoute(
      transformedStartingPoint,
      transformedPubs,
    ); // build route (Veness 2019)

    return res.status(200).json({
      success: true,
      message: `Discovered ${discoveredPubs.length} pubs and generated optimal route`, // summary
      data: {
        ...routeResult,
        discoveredPubs: discoveredPubs.length,
        searchRadius: radius,
      },
    });
  } catch (error) {
    console.error("Auto discovery error:", error); // log error
    if (error.message?.includes?.("OverPass API error")) {
      return res.status(500).json({
        success: false,
        message: "Error accessing OverPass API.",
      }); // Overpass issue (Overpass 2011)
    }
    return res.status(500).json({
      success: false,
      message: "Error discovering pubs and generating route",
    }); // generic error
  }
});

/* POST /api/generate-route */
router.post("/generate-route", async (req, res) => {
  try {
    const { startingPoint, pubs } = req.body || {}; // inputs

    if (!startingPoint || !startingPoint.coordinates) {
      return res.status(400).json({
        success: false,
        message: "Starting point with coordinates is required",
      }); // validate start
    }
    if (!Array.isArray(pubs) || pubs.length === 0) {
      return res.status(400).json({
        success: false,
        message: "A non-empty array of pubs is required",
      }); // validate pubs
    }

    // Save starting point & pubs (best-effort) (Firebase 2025b inside searchLocation)
    try {
      await searchLocation(startingPoint); // save start
    } catch (e) {
      console.error("Error saving starting point:", e); // log save error
    }
    for (let i = 0; i < pubs.length; i++) {
      try {
        await searchLocation(pubs[i]); // save pub
      } catch (e) {
        console.error(`Error saving pub ${i + 1}:`, e); // log failure
      }
    }

    // normalize coordinates for route generator
    const transformedPubs = pubs.map((pub) => ({
      ...pub,
      coordinates: {
        latitude: pub.latitude || pub.coordinates?.latitude,
        longitude: pub.longitude || pub.coordinates?.longitude,
      },
    })); // ensure shape

    const transformedStartingPoint = {
      ...startingPoint,
      coordinates: {
        latitude: startingPoint.coordinates?.latitude || startingPoint.latitude,
        longitude:
          startingPoint.coordinates?.longitude || startingPoint.longitude,
      },
    }; // ensure shape

    // Generate route (nearest-neighbor using Haversine) (Veness 2019)
    const routeResult = generateOptimalRoute(
      transformedStartingPoint,
      transformedPubs,
    );

    return res.status(200).json({
      success: true,
      message: `Generated custom route with ${pubs.length} pubs`, // summary
      data: routeResult,
    });
  } catch (error) {
    console.error("Generate route error:", error); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error generating custom route" }); // fail
  }
});

export default router;
