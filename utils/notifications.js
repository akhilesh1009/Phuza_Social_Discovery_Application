// routes/notifications.js
import express from "express";

import {
  createFirestoreNotification,
  sendPushToUser,
  getUserProfile,
} from "./notify.js"; // notifications (Firebase 2025c; Android Developers 2025)

const router = express.Router();

/**
 * POST /api/notify/follow-request
 * body: { toUid, fromUid } -> store notification and push
 */
// (Manico and Detlefsen 2015): avoid leaking internal IDs in response; validate inputs
router.post("/follow-request", async (req, res) => {
  try {
    const { toUid, fromUid } = req.body || {}; // inputs
    if (!toUid || !fromUid) {
      return res
        .status(400)
        .json({ success: false, message: "toUid and fromUid are required" }); // validate
    }

    const { displayName, username } = await getUserProfile(fromUid); // fetch names (Firebase 2025a)
    const message = "sent you a follow request"; // body text

    await createFirestoreNotification(toUid, {
      type: "follow_request",
      fromUid,
      fromName: displayName,
      fromUsername: username,
      message,
    }); // write Firestore notification (Firebase 2025b)

    const push = await sendPushToUser(toUid, {
      title: "New Chommie Request",
      body: `${displayName} ${message}`,
      data: {
        type: "follow_request",
        fromUid: String(fromUid),
        fromName: String(displayName),
        fromUsername: String(username),
      },
    }); // push via FCM (Firebase 2025c; Android Developers 2025)

    return res.json({
      success: true,
      meta: { pushed: push.sent, pruned: push.pruned },
    }); // result
  } catch (e) {
    console.error("POST /api/notify/follow-request error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Failed to send notification" }); // fail
  }
});

/**
 * POST /api/notify/follow-accept
 * body: { toUid (requester), fromUid (accepter) }
 */
// (Manico and Detlefsen 2015): same validation + minimal exposure
router.post("/follow-accept", async (req, res) => {
  try {
    const { toUid, fromUid } = req.body || {}; // inputs
    if (!toUid || !fromUid) {
      return res
        .status(400)
        .json({ success: false, message: "toUid and fromUid are required" }); // validate
    }

    const { displayName, username } = await getUserProfile(fromUid); // names (Firebase 2025a)
    const message = "accepted your follow request"; // body text

    await createFirestoreNotification(toUid, {
      type: "follow_accept",
      fromUid,
      fromName: displayName,
      fromUsername: username,
      message,
    }); // write Firestore notification (Firebase 2025b)

    const push = await sendPushToUser(toUid, {
      title: "New Chommie!",
      body: `${displayName} ${message}`,
      data: {
        type: "follow_accept",
        fromUid: String(fromUid),
        fromName: String(displayName),
        fromUsername: String(username),
      },
    }); // push via FCM (Firebase 2025c; Android Developers 2025)

    return res.json({
      success: true,
      meta: { pushed: push.sent, pruned: push.pruned },
    });
  } catch (e) {
    console.error("POST /api/notify/follow-accept error:", e); // log error
    return res
      .status(500)
      .json({ success: false, message: "Failed to send notification" }); // fail
  }
});

export default router;
