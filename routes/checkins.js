// routes/checkins.js
import express from "express";

import {
  checkins,
  getCurrentUserId,
  genId,
} from "../utils/localUtils.js"; // helpers, in-memory store

const router = express.Router();

/* =========================
   Check-ins (in-memory)
   ========================= */
router.post("/", async (req, res) => {
  try {
    const userId = getCurrentUserId(req); // derive user id
    const {
      locationId,
      message = "",
      visibility = "friends",
      ttlMinutes = 120,
    } = req.body || {}; // inputs

    if (!locationId || typeof locationId !== "string") {
      return res
        .status(400)
        .json({ success: false, message: "locationId is required" }); // validate id
    }
    if (!["private", "friends", "public"].includes(visibility)) {
      return res.status(400).json({
        success: false,
        message: "visibility must be private|friends|public",
      }); // validate visibility
    }
    const ttl = Number(ttlMinutes); // TTL minutes (MDN 2025a Math)
    if (!Number.isFinite(ttl) || ttl <= 0 || ttl > 24 * 60) {
      return res.status(400).json({
        success: false,
        message: "ttlMinutes must be between 1 and 1440",
      }); // clamp TTL
    }

    const now = new Date(); // now timestamp
    const expiresAt = new Date(now.getTime() + ttl * 60 * 1000); // expire time
    const checkinId = genId("chk"); // new check-in id

    const payload = {
      checkinId,
      userId,
      locationId,
      message,
      visibility,
      createdAt: now.toISOString(),
      expiresAt: expiresAt.toISOString(),
      active: true,
    }; // check-in record

    checkins.set(checkinId, payload); // store in memory
    return res.status(201).json({ success: true, data: payload }); // created
  } catch (e) {
    console.error("POST /api/checkins error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error creating check-in" }); // fail
  }
});

router.delete("/:id", (req, res) => {
  try {
    const userId = getCurrentUserId(req); // current user
    const { id } = req.params; // check-in id
    const exists = checkins.get(id); // lookup
    if (!exists)
      return res
        .status(404)
        .json({ success: false, message: "Check-in not found" }); // missing
    if (exists.userId !== userId) {
      return res
        .status(401)
        .json({ success: false, message: "Not your check-in" }); // access deny
    }
    checkins.delete(id); // remove
    return res.json({ success: true, message: "Checked out" }); // success
  } catch (e) {
    console.error("DELETE /api/checkins/:id error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error checking out" }); // fail
  }
});

export default router;
