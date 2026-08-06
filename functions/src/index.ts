import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Trigger 1: New Message Notification
 * Fires when a new message document is created in chats/{chatId}/messages/{messageId}
 */
export const onNewMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const chatId = event.params.chatId;
    const messageId = event.params.messageId;
    const messageData = event.data?.data();

    if (!messageData) {
      console.log(`[onNewMessage] No data for messageId: ${messageId}`);
      return;
    }

    const senderId = messageData.senderId as string | undefined;
    let receiverId = messageData.receiverId as string | undefined;

    if (!senderId) {
      console.log(`[onNewMessage] Missing senderId for messageId: ${messageId}`);
      return;
    }

    // Fallback: If receiverId is not directly in message, infer from chatId or chat doc
    if (!receiverId) {
      const chatDoc = await db.collection("chats").doc(chatId).get();
      if (chatDoc.exists) {
        const participants = chatDoc.data()?.participants as string[] | undefined;
        if (participants && Array.isArray(participants)) {
          receiverId = participants.find((id) => id !== senderId);
        }
      }
    }

    // Guard: Invalid receiver or self-message
    if (!receiverId || receiverId === senderId) {
      console.log(`[onNewMessage] Self-message or missing receiverId. Sender: ${senderId}, Receiver: ${receiverId}`);
      return;
    }

    // Fetch sender and receiver user profiles in parallel
    const [senderSnap, receiverSnap] = await Promise.all([
      db.collection("users").doc(senderId).get(),
      db.collection("users").doc(receiverId).get(),
    ]);

    if (!receiverSnap.exists) {
      console.log(`[onNewMessage] Receiver doc not found: ${receiverId}`);
      return;
    }

    const receiverData = receiverSnap.data() || {};
    const senderData = senderSnap.data() || {};

    const fcmToken = receiverData.fcmToken as string | undefined;

    // Guard: No FCM token registered for receiver
    if (!fcmToken || typeof fcmToken !== "string" || fcmToken.trim() === "") {
      console.log(`[onNewMessage] Receiver ${receiverId} has no FCM token.`);
      return;
    }

    // Guard: Receiver turned off notifications in Settings
    if (receiverData.notificationsEnabled === false) {
      console.log(`[onNewMessage] Receiver ${receiverId} disabled notifications.`);
      return;
    }

    // Guard: Receiver currently has this active chat open in foreground
    if (receiverData.activeChatId === chatId) {
      console.log(`[onNewMessage] Receiver ${receiverId} is actively viewing chat ${chatId}. Suppressing notification.`);
      return;
    }

    const senderName = senderData.name || "GupShup Contact";

    // Format notification body based on message type
    let notificationBody = messageData.text || "";
    if (messageData.type === "image" || messageData.imageUrl) {
      notificationBody = "📷 Photo";
    }
    if (!notificationBody.trim()) {
      notificationBody = "Sent a message";
    }

    const payload = {
      token: fcmToken,
      notification: {
        title: senderName,
        body: notificationBody,
      },
      data: {
        type: "message",
        chatId: chatId,
        senderId: senderId,
        title: senderName,
        body: notificationBody,
      },
      android: {
        priority: "high" as const,
        notification: {
          sound: "default",
          channelId: "messages",
        },
      },
    };

    try {
      const response = await messaging.send(payload);
      console.log(`[onNewMessage] Successfully sent FCM message to ${receiverId}. Message ID: ${response}`);
    } catch (error) {
      console.error(`[onNewMessage] Error sending FCM message to ${receiverId}:`, error);
    }
  }
);

/**
 * Trigger 2a: New Friend Request Notification
 * Fires when a new document is created in friend_requests/{requestId} with status == "pending"
 */
export const onFriendRequestCreated = onDocumentCreated(
  "friend_requests/{requestId}",
  async (event) => {
    const requestData = event.data?.data();
    if (!requestData) return;

    if (requestData.status !== "pending") return;

    const fromUid = requestData.fromUid as string | undefined;
    const toUid = requestData.toUid as string | undefined;

    if (!fromUid || !toUid || fromUid === toUid) return;

    const [fromSnap, toSnap] = await Promise.all([
      db.collection("users").doc(fromUid).get(),
      db.collection("users").doc(toUid).get(),
    ]);

    if (!toSnap.exists) return;

    const toData = toSnap.data() || {};
    const fromData = fromSnap.data() || {};

    const fcmToken = toData.fcmToken as string | undefined;
    if (!fcmToken || typeof fcmToken !== "string" || fcmToken.trim() === "") return;

    if (toData.notificationsEnabled === false) return;

    const senderName = fromData.name || "Someone";
    const title = "Friend Request";
    const body = `${senderName} sent you a friend request`;

    const payload = {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
      data: {
        type: "friend_request",
        fromUid: fromUid,
        title: title,
        body: body,
      },
      android: {
        priority: "normal" as const,
        notification: {
          sound: "default",
          channelId: "friend_requests",
        },
      },
    };

    try {
      const response = await messaging.send(payload);
      console.log(`[onFriendRequestCreated] Sent FCM request notification to ${toUid}: ${response}`);
    } catch (error) {
      console.error(`[onFriendRequestCreated] Error sending FCM notification to ${toUid}:`, error);
    }
  }
);

/**
 * Trigger 2b: Friend Request Accepted Notification
 * Fires when a document in friend_requests/{requestId} is updated to status == "accepted"
 */
export const onFriendRequestAccepted = onDocumentUpdated(
  "friend_requests/{requestId}",
  async (event) => {
    const beforeData = event.data?.before?.data();
    const afterData = event.data?.after?.data();

    if (!beforeData || !afterData) return;

    // Trigger only on status transition: pending -> accepted
    if (beforeData.status !== "pending" || afterData.status !== "accepted") {
      return;
    }

    const fromUid = afterData.fromUid as string | undefined; // original requester
    const toUid = afterData.toUid as string | undefined;     // user who accepted

    if (!fromUid || !toUid) return;

    const [fromSnap, toSnap] = await Promise.all([
      db.collection("users").doc(fromUid).get(),
      db.collection("users").doc(toUid).get(),
    ]);

    if (!fromSnap.exists) return;

    const fromData = fromSnap.data() || {};
    const toData = toSnap.data() || {};

    const fcmToken = fromData.fcmToken as string | undefined;
    if (!fcmToken || typeof fcmToken !== "string" || fcmToken.trim() === "") return;

    if (fromData.notificationsEnabled === false) return;

    const acceptingName = toData.name || "Someone";
    const title = "Friend Request Accepted";
    const body = `${acceptingName} accepted your friend request`;

    const payload = {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
      data: {
        type: "friend_request_accepted",
        toUid: toUid,
        title: title,
        body: body,
      },
      android: {
        priority: "normal" as const,
        notification: {
          sound: "default",
          channelId: "friend_requests",
        },
      },
    };

    try {
      const response = await messaging.send(payload);
      console.log(`[onFriendRequestAccepted] Sent FCM acceptance notification to ${fromUid}: ${response}`);
    } catch (error) {
      console.error(`[onFriendRequestAccepted] Error sending FCM notification to ${fromUid}:`, error);
    }
  }
);
