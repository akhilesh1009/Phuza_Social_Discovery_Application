import express from "express";
import admin from "../db/firebaseAdmin.js";
import { getCurrentUserId } from "../utils/localUtils.js";
import {
  createFirestoreNotification,
  sendPushToUser,
  getUserProfile,
} from "../utils/notify.js";


const router = express.Router();
const db = admin.firestore();

const GAMES_COL = "games";
const INVITES_COL = "gameInvites";

/* -------------------------------------------
 * Hole drinks + hazards helpers
 * ----------------------------------------- */

// More realistic / fun drink catalogue for pub golf holes
// More realistic / fun drink catalogue for pub golf holes
const DRINK_LIBRARY = [
  { id: "castle_lager_pint", name: "Castle Lager (500ml draft)", par: 5 },
  { id: "black_label_pint", name: "Carling Black Label (500ml draft)", par: 5 },
  { id: "windhoek_lager_pint", name: "Windhoek Lager (500ml draft)", par: 5 },
  { id: "castle_light_bottle", name: "Castle Light (330ml bottle)", par: 3 },
  { id: "hansa_bottle", name: "Hansa Pilsener (330ml)", par: 3 },
  { id: "heineken_bottle", name: "Heineken (330ml)", par: 3 },
  { id: "amstel_bottle", name: "Amstel Lager (330ml)", par: 3 },
  { id: "budweiser_bottle", name: "Budweiser (330ml)", par: 3 },
  { id: "local_craft_lager", name: "Local craft lager (440ml)", par: 3 },
  { id: "ipa_pint", name: "Craft IPA (500ml draft)", par: 5 },

  { id: "cider_pint", name: "Apple cider (500ml)", par: 4 },
  { id: "savanna_dry", name: "Savanna Dry (330ml)", par: 3 },
  { id: "savanna_light", name: "Savanna Light (330ml)", par: 3 },
  { id: "hunters_dry", name: "Hunters Dry (330ml)", par: 3 },
  { id: "hunters_gold", name: "Hunters Gold (330ml)", par: 3 },
  { id: "brutal_fruit", name: "Brutal Fruit (275ml)", par: 3 },
  { id: "smirnoff_ice", name: "Smirnoff Ice (275ml)", par: 3 },
  { id: "vawter_seltzer", name: "Vawter Hard Seltzer (330ml)", par: 3 },
  { id: "guinness_pint", name: "Guinness (500ml draft)", par: 5 },
  { id: "light_beer_bottle", name: "Light beer (330ml bottle)", par: 3 },

  { id: "radler", name: "Shandy / Radler (330ml)", par: 2 },
  { id: "hard_seltzer", name: "Hard seltzer (330ml)", par: 3 },

  // Shots & shooters
  { id: "stroh_shot", name: "Stroh rum shot", par: 2 },
  { id: "everclear_shot", name: "Everclear shot", par: 2 },
  { id: "vodka_shot", name: "Vodka shot", par: 2 },
  { id: "tequila_shot", name: "Tequila shot", par: 2 },
  { id: "jager_shot", name: "Jägermeister shot", par: 2 },
  { id: "sambuca_shot", name: "Sambuca shot", par: 2 },
  { id: "rum_shot", name: "Dark rum shot", par: 2 },
  { id: "whisky_single", name: "Single whisky neat", par: 2 },
  { id: "klippies_shot", name: "Klipdrift brandy shot", par: 2 },
  { id: "amarula_shot", name: "Amarula shot", par: 2 },
  { id: "springbokkie", name: "Springbokkie shooter", par: 2 },
  { id: "b52_shot", name: "B52 shooter", par: 2 },
  { id: "kamikaze_shot", name: "Kamikaze shooter", par: 2 },
  { id: "skittle_bomb", name: "Skittle Bomb", par: 2 },
  { id: "russian_bear_shot", name: "Russian Bear Vodka shot", par: 2 },

  // Wine
  { id: "house_white_glass", name: "House white wine (150ml)", par: 3 },
  { id: "house_red_glass", name: "House red wine (150ml)", par: 3 },
  { id: "sauvignon_blanc_glass", name: "Sauvignon Blanc (150ml)", par: 3 },
  { id: "chardonnay_glass", name: "Chardonnay (150ml)", par: 3 },
  { id: "pinotage_glass", name: "Pinotage (150ml)", par: 3 },
  { id: "merlot_glass", name: "Merlot (150ml)", par: 3 },
  { id: "rose_glass", name: "Rosé wine (150ml)", par: 3 },
  { id: "sparking_wine_glass", name: "Sparkling wine (150ml)", par: 3 },

  // Simple mixed / highballs
  { id: "gn_tonic_single", name: "Single gin & tonic", par: 4 },
  { id: "gn_tonic_double", name: "Double gin & tonic", par: 5 },
  { id: "rum_coke_single", name: "Rum & coke", par: 4 },
  { id: "rum_coke_double", name: "Double rum & coke", par: 4 },
  { id: "brandy_coke_single", name: "Brandy & coke", par: 4 },
  { id: "double_brandy_coke", name: "Double brandy & coke", par: 5 },
  { id: "vodka_soda_single", name: "Vodka soda", par: 4 },
  { id: "vodka_cranberry", name: "Vodka cranberry", par: 4 },
  { id: "whisky_coke_single", name: "Whisky & coke", par: 4 },
  { id: "whisky_coke_double", name: "Double whisky & coke", par: 5 },

  // Classic cocktails
  { id: "aperol_spritz", name: "Aperol Spritz", par: 4 },
  { id: "mojito", name: "Mojito", par: 4 },
  { id: "negroni", name: "Negroni", par: 4 },
  { id: "old_fashioned", name: "Old fashioned", par: 4 },
  { id: "martini_cocktail", name: "Martini (classic)", par: 4 },
  { id: "cosmopolitan", name: "Cosmopolitan", par: 4 },
  { id: "whisky_sour", name: "Whisky sour", par: 4 },
  { id: "gin_fizz", name: "Gin fizz", par: 4 },
  { id: "tom_collins", name: "Tom Collins", par: 4 },
  { id: "sex_on_the_beach", name: "Sex on the Beach", par: 4 },
  { id: "pina_colada", name: "Piña Colada", par: 4 },
  { id: "daiquiri", name: "Daiquiri", par: 4 },
  { id: "blue_lagoon", name: "Blue Lagoon", par: 4 },
  { id: "caipirinha", name: "Caipirinha", par: 4 },
  { id: "irish_coffee", name: "Irish coffee", par: 4 },
  { id: "hot_toddy", name: "Hot toddy", par: 4 },

  // Big/strong cocktails
  { id: "long_island", name: "Long Island iced tea", par: 5 },
  { id: "long_beach", name: "Long Beach iced tea", par: 5 },
  { id: "zombie", name: "Zombie", par: 5 },
  { id: "mai_tai", name: "Mai Tai", par: 5 },
  { id: "adios_mother", name: "Adios Mother****er", par: 5 },
  { id: "pornstar_martini", name: "Pornstar Martini", par: 5 },
  { id: "espresso_martini", name: "Espresso Martini", par: 5 },
  { id: "rum_punch", name: "Rum punch", par: 5 },

  // Energy mixes
  { id: "jagerbomb", name: "Jägerbomb", par: 4 },
  { id: "vodka_redbull", name: "Vodka Red Bull", par: 4 },
  { id: "tequila_redbull", name: "Tequila & Red Bull", par: 4 },

  // Creamy / liqueur
  { id: "amarula_rocks", name: "Amarula on the rocks", par: 3 },
  { id: "irish_crusher", name: "Irish Crusher shooter", par: 2 },

  // Premium whiskies etc.
  { id: "whisky_double", name: "Double whisky neat", par: 3 },
  { id: "johnnie_black_single", name: "Johnnie Walker Black (single)", par: 3 },
  { id: "johnnie_black_double", name: "Johnnie Walker Black (double)", par: 5 },
  { id: "jack_daniels_single", name: "Jack Daniel's (single)", par: 3 },
  { id: "jack_daniels_double", name: "Jack Daniel's (double)", par: 5 },

  // Non-alc & softs (for easier holes / mercy holes)
  { id: "castle_free", name: "Castle Free 0.0%", par: 3 },
  { id: "soft_drink_330", name: "Soft drink (330ml)", par: 2 },
  { id: "energy_drink_can", name: "Energy drink (250ml)", par: 2 },

  // Waters (par 1 for “free” / recovery holes)
  { id: "water_500", name: "Still water (500ml)", par: 1 },
  { id: "water_sparkling_500", name: "Sparkling water (500ml)", par: 1 },

  // Extra beer / craft entries
  { id: "shandy_half_pint", name: "Half-pint shandy", par: 2 },
  { id: "local_craft_ipa_can", name: "Local craft IPA (330ml can)", par: 3 },
  { id: "copper_lake_lager", name: "Copper Lake Lager (330ml)", par: 3 },
  { id: "devils_peak_lager", name: "Devil's Peak Lager (340ml)", par: 3 }
];

// deterministic “shuffle” so the same game seed is stable
function pickHoleDrinks(holePar, holeIndex) {

  const matchingDrinks = DRINK_LIBRARY.filter(d => d.par === holePar);
  
  // const base = [...DRINK_LIBRARY];

if (matchingDrinks.length === 0) {
  if (DRINK_LIBRARY.length === 0) return [];

  const fallback = [...DRINK_LIBRARY];
  const shift = holeIndex % fallback.length;
  const rotated = fallback.slice(shift).concat(fallback.slice(0, shift));
  return rotated.slice(0, 3);
}

  // rotate array by index to vary per hole
  // const shift = holeIndex % base.length;
  // const rotated = base.slice(shift).concat(base.slice(0, shift));
  const shift = holeIndex % matchingDrinks.length;
  const rotated = matchingDrinks.slice(shift).concat(matchingDrinks.slice(0, shift));
  return rotated.slice(0, 3); // first 3
}

// Random par for each hole
function randomParForHole(holeIndex) {
  const r = Math.random();
  if (r < 0.2) {
    return 2;   // 20%
  } 
  if (r < 0.5) {
    return 3;   // 30%
  }
  if (r < 0.8) {
    return 4;   // 30%
  }
  return 5;     // 20%
}

// assign water + bunker hazards + drinks + par to each hole
function enrichHolesWithDrinksAndHazards(holesBase) {
  const waterHoles = new Set([3, 7]); // water hazard holes
  const bunkerHoles = new Set([5, 9]); // bunker hazard holes

  return holesBase.map((h, idx) => {
    const holeNumber = idx + 1;

    const par = randomParForHole(idx);    
    const drinks = pickHoleDrinks(par, idx);
    
    // const avgPar =
    //   drinks.reduce((sum, d) => sum + (d.par || 3), 0) / drinks.length;
    // const par = Math.max(1, Math.round(avgPar)); // fallback to >=1

    return {
      ...h,
      par,
      drinks, // [{ id, name, par }]
      waterHazard: 
        // waterHoles.has(holeNumber),
        waterHoles.has(holeNumber) && holeNumber <= holesBase.length,
      bunkerHazard:
        // bunkerHoles.has(holeNumber),
        bunkerHoles.has(holeNumber) && holeNumber <= holesBase.length,
    };
  });
}

// Recalculate strokes/scoreToPar for every player
function recomputeScores(game) {
  const holes = game.holes || [];
  const totalPar = holes.reduce((sum, h) => sum + (h.par || 3), 0);

  const players = (game.players || []).map((p) => {
    const strokesArr = Array.isArray(p.strokes)
      ? [...p.strokes]
      : Array(holes.length).fill(null);

    // Ensure array length matches hole count
    if (strokesArr.length < holes.length) {
      strokesArr.push(...Array(holes.length - strokesArr.length).fill(null));
    } else if (strokesArr.length > holes.length) {
      strokesArr.splice(holes.length);
    }

    const totalStrokes = strokesArr
      .filter((v) => typeof v === "number")
      .reduce((sum, v) => sum + v, 0);

    const scoreToPar =
      typeof totalStrokes === "number" ? totalStrokes - totalPar : null;

    return {
      ...p,
      strokes: strokesArr,
      totalStrokes: isNaN(totalStrokes) ? null : totalStrokes,
      scoreToPar:
        scoreToPar === null || isNaN(scoreToPar) ? null : scoreToPar,
    };
  });

  return { ...game, players };
}

/**
 * POST /api/pubgolf/games
 * Create a pub golf game from an origin + pubs (output of /api/discover-pubs).
 *
 * Body:
 * {
 *   title?: string,
 *   origin: { name, address, coordinates: { latitude, longitude } },
 *   pubs: [ { name, address, coordinates: { latitude, longitude }, ... } ]
 * }
 */
router.post("/games", async (req, res) => {
  try {
    const hostUid = getCurrentUserId(req);

    const { title, origin, pubs } = req.body || {};

    if (!origin?.coordinates?.latitude || !origin?.coordinates?.longitude) {
      return res.status(400).json({
        success: false,
        message:
          "origin with coordinates {latitude, longitude} is required",
      });
    }

    if (!Array.isArray(pubs) || pubs.length === 0) {
      return res.status(400).json({
        success: false,
        message: "A non-empty array of pubs is required to create a game",
      });
    }

    let hostName = null;
    try {
      const { firstName, displayName } = await getUserProfile(hostUid);
      hostName = displayName || firstName || null;
    } catch (e) {
      console.error("[pubgolf] failed to fetch host profile:", e);
    }

    // Clean pubs:
    //  - skip empty / missing addresses
    //  - exclude the current location (by coords, name, or address)
    const oc = origin.coordinates || {};
    const originName = (origin.name || "").trim().toLowerCase();
    const originAddress = (origin.address || "").trim().toLowerCase();

    const cleanedPubs = pubs.filter((p) => {
      const name = (p.name || "").trim();
      const address = (p.address || "").trim();
      const coords = p.coordinates || {};

      // Must have a non-empty address
      if (!address) return false;

      // Check if obviously "current location" style
      const nameLooksLikeCurrent = /current location|your position/i.test(name);
      const addrLooksLikeCurrent = /current location|your position/i.test(
        address
      );
      if (nameLooksLikeCurrent || addrLooksLikeCurrent) return false;

      // Compare with origin name/address
      const sameName =
        originName &&
        name.toLowerCase() === originName;
      const sameAddress =
        originAddress &&
        address.toLowerCase() === originAddress;

      // Compare coordinates (small tolerance)
      const sameLat =
        typeof oc.latitude === "number" &&
        typeof coords.latitude === "number" &&
        Math.abs(oc.latitude - coords.latitude) < 1e-5;
      const sameLng =
        typeof oc.longitude === "number" &&
        typeof coords.longitude === "number" &&
        Math.abs(oc.longitude - coords.longitude) < 1e-5;
      const sameCoords = sameLat && sameLng;

      if (sameName || sameAddress || sameCoords) {
        return false;
      }

      return true;
    });

    if (!cleanedPubs.length) {
      return res.status(400).json({
        success: false,
        message: "No valid pubs found (after excluding current location and empty addresses).",
      });
    }

    // base 9 holes from first 9 cleaned pubs
    const baseHoles = cleanedPubs.slice(0, 9).map((p, idx) => ({
      holeNumber: idx + 1,
      name: p.name,
      address: p.address,
      coordinates: p.coordinates,
    }));

    // add drinks + hazards + par
    const holes = enrichHolesWithDrinksAndHazards(baseHoles);

    const createdAt = admin.firestore.FieldValue.serverTimestamp();
    const joinedAt = admin.firestore.Timestamp.now();

    const payload = {
      title: title || "Pub Golf game",
      hostUid,
      hostName,
      origin,
      holes,
      createdAt,
      status: "pending",
      players: [
        {
          uid: hostUid,
          role: "host",
          joinedAt,
          name: hostName,
        },
      ],
    };

    const ref = await db.collection(GAMES_COL).add(payload);
    const snap = await ref.get();

    return res.status(201).json({
      success: true,
      message: "Pub golf game created",
      data: { id: ref.id, ...snap.data() },
    });
  } catch (error) {
    console.error("POST /api/pubgolf/games error:", error);
    return res.status(500).json({
      success: false,
      message: "Failed to create pub golf game",
    });
  }
});


/**
 * GET /api/pubgolf/games/:gameId
 * Fetch a single game document.
 */
router.get("/games/:gameId", async (req, res) => {
  try {
    const { gameId } = req.params;
    const doc = await db.collection(GAMES_COL).doc(String(gameId)).get();
    if (!doc.exists) {
      return res
        .status(404)
        .json({ success: false, message: "Game not found" });
    }
    return res.json({
      success: true,
      data: { id: doc.id, ...doc.data() },
    });
  } catch (error) {
    console.error("GET /api/pubgolf/games/:gameId error:", error);
    return res.status(500).json({
      success: false,
      message: "Failed to fetch game",
    });
  }
});

/* ============================================================
   GAME LIFECYCLE + SCORES
   ============================================================ */

/**
 * POST /api/pubgolf/games/:gameId/start
 */
router.post("/games/:gameId/start", async (req, res) => {
  try {
    const uid = getCurrentUserId(req);
    const { gameId } = req.params;

    const ref = db.collection(GAMES_COL).doc(String(gameId));

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw new Error("Game not found");

      const game = snap.data();
      if (game.hostUid !== uid) {
        throw new Error("Only the host can start this game");
      }

      if (game.status === "finished") {
        throw new Error("Game is already finished");
      }

      const holes = game.holes || [];
      const players = (game.players || []).map((p) => ({
        ...p,
        strokes: Array(holes.length).fill(null),
        totalStrokes: null,
        scoreToPar: null,
      }));

      const updated = {
        ...game,
        status: "active",
        players,
      };

      tx.set(ref, updated, { merge: true });

      return { id: snap.id, ...updated };
    });

    return res.json({
      success: true,
      message: "Game started",
      data: result,
    });
  } catch (error) {
    console.error("POST /api/pubgolf/games/:gameId/start error:", error);
    const msg =
      error.message === "Game not found" ||
        error.message === "Only the host can start this game" ||
        error.message === "Game is already finished"
        ? error.message
        : "Failed to start game";
    const code =
      msg === error.message && msg !== "Failed to start game" ? 400 : 500;

    return res.status(code).json({ success: false, message: msg });
  }
});

/**
 * POST /api/pubgolf/games/:gameId/score
 */
router.post("/games/:gameId/score", async (req, res) => {
  try {
    const hostUid = getCurrentUserId(req);
    const { gameId } = req.params;
    const { playerUid, holeNumber, strokes } = req.body || {};

    if (!playerUid || typeof holeNumber !== "number" || typeof strokes !== "number") {
      return res.status(400).json({
        success: false,
        message: "playerUid, holeNumber and strokes are required",
      });
    }

    if (holeNumber < 1 || holeNumber > 18) {
      return res.status(400).json({
        success: false,
        message: "holeNumber must be between 1 and 18",
      });
    }

    const ref = db.collection(GAMES_COL).doc(String(gameId));

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw new Error("Game not found");

      const game = snap.data();
      if (game.hostUid !== hostUid) {
        throw new Error("Only the host can edit scores");
      }

      if (game.status === "finished") {
        throw new Error("Game is already finished");
      }

      const holes = game.holes || [];
      const idx = holeNumber - 1;
      if (!holes[idx]) {
        throw new Error("Invalid holeNumber for this game");
      }

      const players = game.players || [];
      const pIndex = players.findIndex((p) => p.uid === playerUid);
      if (pIndex === -1) throw new Error("Player not found in game");

      const player = players[pIndex];
      const strokesArr = Array.isArray(player.strokes)
        ? [...player.strokes]
        : Array(holes.length).fill(null);

      if (strokesArr.length < holes.length) {
        strokesArr.push(...Array(holes.length - strokesArr.length).fill(null));
      }

      strokesArr[idx] = strokes;

      const updatedPlayers = [...players];
      updatedPlayers[pIndex] = {
        ...player,
        strokes: strokesArr,
      };

      let updated = { ...game, players: updatedPlayers };
      updated = recomputeScores(updated);

      tx.set(ref, updated, { merge: true });

      return { id: snap.id, ...updated };
    });

    return res.json({
      success: true,
      message: "Score updated",
      data: result,
    });
  } catch (error) {
    console.error("POST /api/pubgolf/games/:gameId/score error:", error);
    const msg =
      error.message === "Game not found" ||
        error.message === "Only the host can edit scores" ||
        error.message === "Game is already finished" ||
        error.message === "Invalid holeNumber for this game" ||
        error.message === "Player not found in game"
        ? error.message
        : "Failed to update score";
    const code =
      msg === error.message && msg !== "Failed to update score" ? 400 : 500;

    return res.status(code).json({ success: false, message: msg });
  }
});

/* ============================================================
   INVITES
   ============================================================ */

router.post("/games/:gameId/invites", async (req, res) => {
  try {
    const fromUid = getCurrentUserId(req);
    const { gameId } = req.params;
    const { toUserIds } = req.body || {};

    if (!Array.isArray(toUserIds) || toUserIds.length === 0) {
      return res.status(400).json({
        success: false,
        message: "toUserIds must be a non-empty array",
      });
    }

    const gameRef = db.collection(GAMES_COL).doc(String(gameId));
    const gameSnap = await gameRef.get();

    if (!gameSnap.exists) {
      return res
        .status(404)
        .json({ success: false, message: "Game not found" });
    }

    const game = gameSnap.data();
    const gameTitle = game?.title || "Pub Golf⛳";

    const now = admin.firestore.FieldValue.serverTimestamp();
    const batch = db.batch();
    const createdIds = [];

    toUserIds.forEach((toUid) => {
      const ref = db.collection(INVITES_COL).doc();
      createdIds.push({ inviteId: ref.id, toUid: String(toUid) });
      batch.set(ref, {
        gameId,
        fromUid,
        toUid: String(toUid),
        status: "pending", // pending | accepted | declined
        createdAt: now,
        respondedAt: null,
      });
    });

    await batch.commit();

    // ---- NOTIFICATIONS (Firestore + FCM) ----
    try {
      const { displayName, username } = await getUserProfile(fromUid);
      const hostName = displayName || username || "A friend";

      await Promise.all(
        createdIds.map(async ({ inviteId, toUid }) => {
          const message = `${hostName} invited you to join “${gameTitle}”`;

          // 1) Firestore notification document
          await createFirestoreNotification(toUid, {
            type: "pubgolf_invite",
            fromUid,
            fromName: hostName,
            fromUsername: username,
            message,
          });

          // 2) Push notification to all devices
          await sendPushToUser(toUid, {
            title: "Pub Golf invite",
            body: message,
            data: {
              type: "pubgolf_invite",
              gameId: String(gameId),
              inviteId: String(inviteId),
              fromUid: String(fromUid),
            },
          });
        })
      );
    } catch (notifyErr) {
      console.error("[pubgolf] invite notify failed:", notifyErr);

    }

    return res.status(201).json({
      success: true,
      message: `Created ${createdIds.length} invite(s)`,
      data: {
        inviteIds: createdIds.map((x) => x.inviteId),
      },
    });
  } catch (error) {
    console.error("POST /api/pubgolf/games/:gameId/invites error:", error);
    return res.status(500).json({
      success: false,
      message: "Failed to create game invites",
    });
  }
});


router.get("/invites", async (req, res) => {
  try {
    const uid = getCurrentUserId(req);
    const { status } = req.query;

    let q = db.collection(INVITES_COL).where("toUid", "==", uid);
    if (status && typeof status === "string") {
      q = q.where("status", "==", status);
    }
    q = q.orderBy("createdAt", "desc").limit(50);

    const snap = await q.get();
    const items = snap.docs.map((d) => ({ id: d.id, ...d.data() }));

    return res.json({
      success: true,
      data: items,
    });
  } catch (error) {
    console.error("GET /api/pubgolf/invites error:", error);
    return res.status(500).json({
      success: false,
      message: "Failed to list invites",
    });
  }
});

router.post("/invites/:inviteId/respond", async (req, res) => {
  try {
    const uid = getCurrentUserId(req);
    const { inviteId } = req.params;
    const { action } = req.body || {};

    if (action !== "accept" && action !== "decline") {
      return res.status(400).json({
        success: false,
        message: 'action must be "accept" or "decline"',
      });
    }

    let playerName = null;
    if (action === "accept") {
      try {
        const { firstName, displayName } = await getUserProfile(uid);
        playerName = displayName || firstName || null;
      } catch (e) {
        console.error("[pubgolf] failed to fetch player profile:", e);
      }
    }

    const inviteRef = db.collection(INVITES_COL).doc(String(inviteId));

    const result = await db.runTransaction(async (tx) => {
      const inviteSnap = await tx.get(inviteRef);
      if (!inviteSnap.exists) {
        throw new Error("Invite not found");
      }

      const invite = inviteSnap.data();

      if (invite.toUid !== uid) {
        throw new Error("Not authorized to respond to this invite");
      }

      if (invite.status !== "pending") {
        // already handled
        return { invite: { id: inviteSnap.id, ...invite } };
      }

      const respondedAt = admin.firestore.FieldValue.serverTimestamp();
      const joinedAt = admin.firestore.Timestamp.now();

      tx.update(inviteRef, {
        status: action === "accept" ? "accepted" : "declined",
        respondedAt,
      });

      if (action === "accept") {
        const gameRef = db.collection(GAMES_COL).doc(invite.gameId);

        const playerPayload = {
          uid,
          role: "player",
          joinedAt,
        };
        if (playerName) {
          playerPayload.name = playerName;
        }

        tx.update(gameRef, {
          players: admin.firestore.FieldValue.arrayUnion(playerPayload),
        });
      }

      return {
        invite: {
          id: inviteSnap.id,
          ...invite,
          status: action === "accept" ? "accepted" : "declined",
        },
      };
    });

    return res.json({
      success: true,
      message: `Invite ${action}ed`,
      data: result,
    });
  } catch (error) {
    console.error("POST /api/pubgolf/invites/:inviteId/respond error:", error);
    const msg =
      error.message === "Invite not found" ||
        error.message === "Not authorized to respond to this invite" ||
        error.message === "Game has already started"
        ? error.message
        : "Failed to respond to invite";
    const code =
      msg === "Invite not found" ||
        msg === "Not authorized to respond to this invite" ||
        msg === "Game has already started"
        ? 400
        : 500;
    return res.status(code).json({
      success: false,
      message: msg,
    });
  }
});

router.post("/games/:gameId/finish", async (req, res) => {
  try {
    const uid = getCurrentUserId(req);
    const { gameId } = req.params;

    const ref = db.collection(GAMES_COL).doc(String(gameId));

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw new Error("Game not found");

      const game = snap.data();

      if (game.hostUid !== uid) {
        throw new Error("Only the host can finish this game");
      }

      if (game.status === "finished") {
        throw new Error("Game is already finished");
      }

      const updated = {
        ...game,
        status: "finished",
        finishedAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      tx.set(ref, updated, { merge: true });

      return { id: snap.id, ...updated };
    });

    return res.json({
      success: true,
      message: "Game finished",
      data: result,
    });
  } catch (error) {
    console.error("POST /api/pubgolf/games/:gameId/finish error:", error);
    const msg =
      error.message === "Game not found" ||
        error.message === "Only the host can finish this game" ||
        error.message === "Game is already finished"
        ? error.message
        : "Failed to finish game";
    const code =
      msg === error.message && msg !== "Failed to finish game" ? 400 : 500;

    return res.status(code).json({ success: false, message: msg });
  }
});

export default router;
