// routes/messages.js
import { Router } from "express";
import { body, param, query, validationResult } from "express-validator";

import {
  sendChatMessage,
  getChatMessagesChrono,
  chatIdFor,
  firestore,
  decryptBody
} from "../utils/chat.js";

const router = Router();

/* ---------------------------
   Helper: Return validation errors
---------------------------- */
function handleValidation(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({
      success: false,
      message: "Invalid request",
      errors: errors.array()
    });
  }
  return null;
}

/* =====================================================
   GET /api/messages/since
   Query: since (number), uid (string)
   Purpose: get all messages for a uid since timestamp
   No auth / no role checks
===================================================== */
router.get(
  "/since",
  query("since").isNumeric(),
  query("uid").isString().notEmpty(),

  async (req, res) => {
    const err = handleValidation(req, res);
    if (err) return err;

    try {
      const uid = String(req.query.uid);
      const since = Number(req.query.since);

      // Fetch chats where user participates (NO role checks – purely by UID match)
      const chatsSnapshot = await firestore()
        .collection("Chats")
        .where("participants", "array-contains", uid)
        .get();

      const allMessages = [];

      for (const chatDoc of chatsSnapshot.docs) {
        const chatId = chatDoc.id;

        const messagesSnapshot = await firestore()
          .collection("Chats")
          .doc(chatId)
          .collection("Messages")
          .where("createdAt", ">", since)
          .orderBy("createdAt", "asc")
          .limit(200)
          .get();

        for (const doc of messagesSnapshot.docs) {
          const m = doc.data();
          let body = m.body;

          if (m.bodyEnc && !body) {
            try {
              body = decryptBody(m.bodyEnc);
            } catch {
              body = "";
            }
          }

          allMessages.push({
            id: doc.id,
            chatId,
            fromUid: m.fromUid,
            toUid: m.toUid,
            body,
            createdAt: m.createdAt,
            updatedAt: m.updatedAt,
            status: m.status
          });
        }
      }

      allMessages.sort((a, b) => a.createdAt - b.createdAt);

      return res.json(allMessages);
    } catch (e) {
      console.error("GET /messages/since error:", e);
      return res.status(500).json({ success: false, message: "Error fetching messages" });
    }
  }
);

/* =====================================================
   GET /api/messages/:chatId
   Query: uid, limit?, after?
   No participant checks (any user can read)
===================================================== */
router.get(
  "/:chatId",
  param("chatId").isString().notEmpty(),
  query("uid").isString().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 500 }),
  query("after").optional().isInt(),

  async (req, res) => {
    const err = handleValidation(req, res);
    if (err) return err;

    try {
      const { chatId } = req.params;
      const limit = req.query.limit ? Number(req.query.limit) : 100;
      const after =
        req.query.after != null ? Number(req.query.after) : undefined;

      // No isParticipant() check – fully open access
      const { messages, nextAfter } = await getChatMessagesChrono(chatId, {
        limit,
        after
      });

      return res.json({
        success: true,
        chatId,
        messages,
        nextAfter,
        meta: { limit, order: "asc" }
      });
    } catch (e) {
      console.error("GET /messages/:chatId error:", e);
      return res.status(500).json({
        success: false,
        message: "Error retrieving chat messages"
      });
    }
  }
);

/* =====================================================
   GET /api/messages
   Query: peerId, uid, limit?
   (Legacy endpoint)
===================================================== */
router.get(
  "/",
  query("peerId").isString().notEmpty(),
  query("uid").isString().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 500 }),

  async (req, res) => {
    const err = handleValidation(req, res);
    if (err) return err;

    try {
      const uid = String(req.query.uid);
      const peerId = String(req.query.peerId);
      const limit = req.query.limit ? Number(req.query.limit) : 100;

      const chatId = chatIdFor(uid, peerId);

      // Fetch messages (No role checks)
      const snapshot = await firestore()
        .collection("Chats")
        .doc(chatId)
        .collection("Messages")
        .orderBy("createdAt", "asc")
        .limit(limit)
        .get();

      const messages = snapshot.docs.map((doc) => {
        const m = doc.data();
        let body = m.body;

        if (m.bodyEnc && !body) {
          try {
            body = decryptBody(m.bodyEnc);
          } catch {
            body = "";
          }
        }

        return {
          id: doc.id,
          fromUid: m.fromUid,
          toUid: m.toUid,
          body,
          createdAt: m.createdAt,
          updatedAt: m.updatedAt,
          status: m.status
        };
      });

      return res.json(messages);
    } catch (e) {
      console.error("GET /messages error:", e);
      return res.status(500).json({ success: false, message: "Failed to load messages" });
    }
  }
);

/* =====================================================
   POST /api/messages
   Body: { fromUid, toUid, body }
   Anyone can send to anyone
===================================================== */
router.post(
  "/",
  body("fromUid").isString().notEmpty(),
  body("toUid").isString().notEmpty(),
  body("body").isString().notEmpty(),

  async (req, res) => {
    const err = handleValidation(req, res);
    if (err) return err;

    try {
      const { fromUid, toUid, body: text } = req.body;

      const saved = await sendChatMessage({
        fromUid,
        toUid,
        body: text
      });

      return res.status(201).json({
        id: saved.id,
        chatId: saved.chatId,
        fromUid: saved.fromUid,
        toUid: saved.toUid,
        body: saved.body,
        createdAt: saved.createdAt,
        updatedAt: saved.updatedAt,
        status: saved.status
      });
    } catch (e) {
      console.error("POST /messages error:", e);
      return res.status(500).json({
        success: false,
        message: "Failed to send message"
      });
    }
  }
);

export default router;
