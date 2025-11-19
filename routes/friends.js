// routes/friends.js
import express from "express";

import {
  getAllUsers,
  getFriends,
  addFriend,
  removeFriend,
} from "../utils/friendsUtils.js"; // friends via RTDB (Firebase 2025a)
import { getCurrentUserId } from "../utils/localUtils.js"; // derive current user

const router = express.Router();

// -----------------------------------------------------------------------------------------
// Friends
// -----------------------------------------------------------------------------------------
router.get("/", async (_req, res) => {
  try {
    const users = await getAllUsers(); // load users from RTDB (Firebase 2025a)
    return res.json({
      success: true,
      data: users,
      meta: { count: users.length, source: "rtdb", path: "/users" }, // meta info
    });
  } catch (e) {
    console.error("GET /api/friends error:", e); // log error
    return res.status(500).json({
      success: false,
      message: e.message || "Error listing users",
    }); // fail
  }
});

router.post("/:friendId", async (req, res) => {
  try {
    const userId = getCurrentUserId(req); // current user
    const { friendId } = req.params; // friend id to add
    if (!friendId || friendId === userId) {
      return res
        .status(400)
        .json({ success: false, message: "Invalid friendId" }); // validate
    }
    await addFriend(userId, friendId); // write to RTDB (Firebase 2025a)
    return res.status(201).json({
      success: true,
      data: { userId, friendId },
      meta: { source: "rtdb" },
    }); // created
  } catch (e) {
    console.error("POST /api/friends/:friendId error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error adding friend" }); // fail
  }
});

router.delete("/:friendId", async (req, res) => {
  try {
    const userId = getCurrentUserId(req); // current user
    const { friendId } = req.params; // friend id to remove

    const current = await getFriends(userId); // read current list (Firebase 2025a)
    if (!current.includes(friendId)) {
      return res
        .status(404)
        .json({ success: false, message: "Friend not found" }); // not found
    }

    await removeFriend(userId, friendId); // delete link (Firebase 2025a)
    return res.json({
      success: true,
      message: "Friend removed",
      meta: { source: "rtdb" },
    }); // ok
  } catch (e) {
    console.error("DELETE /api/friends/:friendId error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Error removing friend" }); // fail
  }
});

// -----------------------------------------------------------------------------------------
// Get friends of logged-in user
// -----------------------------------------------------------------------------------------
router.get("/me", async (req, res) => {
  try {
    const userId = getCurrentUserId(req); // current user ID

    if (!userId) {
      return res
        .status(401)
        .json({ success: false, message: "Not authenticated" }); // missing login/auth
    }

    const friends = await getFriends(userId); // read friends from RTDB (Firebase 2025a)

    return res.json({
      success: true,
      data: friends, // array of userIds
      meta: { count: friends.length, source: "rtdb", path: `/friends/${userId}` },
    }); // ok
  } catch (e) {
    console.error("GET /api/friends/me error:", e);
    return res.status(500).json({
      success: false,
      message: e.message || "Error fetching your friends list",
    }); // fail
  }
});

// -----------------------------------------------------------------------------------------
// Get detailed friend objects for logged-in user
// GET /api/friends/me/details
// -----------------------------------------------------------------------------------------
router.get("/me/details", async (req, res) => {
  try {
    const userId = getCurrentUserId(req); // current user

    if (!userId) {
      return res
        .status(401)
        .json({ success: false, message: "Not authenticated" });
    }

    const friendIds = await getFriends(userId); // ["uid1", "uid2", ...]
    if (!friendIds || friendIds.length === 0) {
      return res.json({
        success: true,
        data: [],
        meta: { count: 0, source: "rtdb", path: `/friends/${userId}` },
      });
    }

    const allUsers = await getAllUsers(); // list of user objs (e.g. { uid, displayName, username, photoUrl, ... })

    // Match by uid (or id depending on what you store)
    const friendsDetailed = allUsers.filter((u) =>
      friendIds.includes(u.uid || u.id)
    );

    return res.json({
      success: true,
      data: friendsDetailed,
      meta: {
        count: friendsDetailed.length,
        source: "rtdb",
        path: `/friends/${userId}`,
      },
    });
  } catch (e) {
    console.error("GET /api/friends/me/details error:", e);
    return res.status(500).json({
      success: false,
      message: e.message || "Error fetching friend details",
    });
  }
});


export default router;
