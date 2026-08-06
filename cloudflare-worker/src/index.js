// In-memory cache for Google OAuth2 access token
let cachedToken = null;
// In-memory cache for Google Public Keys (JWKS)
let cachedJwks = null;
/**
 * Base64 URL encoding helper
 */
function base64UrlEncode(strOrBuffer) {
    const bytes = typeof strOrBuffer === "string"
        ? new TextEncoder().encode(strOrBuffer)
        : new Uint8Array(strOrBuffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary)
        .replace(/=/g, "")
        .replace(/\+/g, "-")
        .replace(/\//g, "_");
}
/**
 * Base64 URL decoding helper
 */
function base64UrlDecode(str) {
    let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
    while (base64.length % 4) {
        base64 += "=";
    }
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}
/**
 * Convert a PEM private key into an CryptoKey using Web Crypto API
 */
async function importPrivateKey(pemKey) {
    const cleanPem = pemKey
        .replace(/\\n/g, "\n")
        .replace(/-----BEGIN PRIVATE KEY-----/, "")
        .replace(/-----END PRIVATE KEY-----/, "")
        .replace(/\s+/g, "");
    const binaryDer = base64UrlDecode(cleanPem);
    return await crypto.subtle.importKey("pkcs8", binaryDer, {
        name: "RSASSA-PKCS1-v1_5",
        hash: "SHA-256",
    }, false, ["sign"]);
}
/**
 * Generates a signed JWT and exchanges it for a Google OAuth2 Access Token
 */
async function getGoogleAccessToken(env) {
    const nowSeconds = Math.floor(Date.now() / 1000);
    // Return cached token if valid for at least 5 more minutes
    if (cachedToken && cachedToken.expiresAt > nowSeconds + 300) {
        return cachedToken.accessToken;
    }
    const clientEmail = env.FIREBASE_CLIENT_EMAIL;
    const privateKeyPem = env.FIREBASE_PRIVATE_KEY;
    if (!clientEmail || !privateKeyPem) {
        throw new Error("FIREBASE_CLIENT_EMAIL or FIREBASE_PRIVATE_KEY secret is missing");
    }
    const header = { alg: "RS256", typ: "JWT" };
    const claimSet = {
        iss: clientEmail,
        scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        exp: nowSeconds + 3600,
        iat: nowSeconds,
    };
    const encodedHeader = base64UrlEncode(JSON.stringify(header));
    const encodedClaimSet = base64UrlEncode(JSON.stringify(claimSet));
    const unsignedJwt = `${encodedHeader}.${encodedClaimSet}`;
    const cryptoKey = await importPrivateKey(privateKeyPem);
    const signatureBuffer = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, new TextEncoder().encode(unsignedJwt));
    const signedJwt = `${unsignedJwt}.${base64UrlEncode(signatureBuffer)}`;
    // Request OAuth2 token from Google
    const response = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
            grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion: signedJwt,
        }),
    });
    if (!response.ok) {
        const errText = await response.text();
        throw new Error(`Failed to exchange JWT for OAuth token: ${response.status} ${errText}`);
    }
    const tokenData = (await response.json());
    cachedToken = {
        accessToken: tokenData.access_token,
        expiresAt: nowSeconds + tokenData.expires_in,
    };
    return cachedToken.accessToken;
}
/**
 * Fetch and cache Google Public JWKS keys for verifying Firebase Auth ID tokens
 */
async function getGoogleJwksKeys() {
    const now = Date.now();
    if (cachedJwks && cachedJwks.expiresAt > now) {
        return cachedJwks.keys;
    }
    const res = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    if (!res.ok) {
        throw new Error("Failed to fetch Google JWKS");
    }
    const data = (await res.json());
    cachedJwks = {
        keys: data.keys,
        expiresAt: now + 3600 * 1000, // cache for 1 hour
    };
    return cachedJwks.keys;
}
/**
 * Verifies a Firebase Auth ID Token (JWT) sent by the Android App
 */
async function verifyFirebaseIdToken(authHeader, projectId) {
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
        throw new Error("Missing or invalid Authorization header");
    }
    const idToken = authHeader.split("Bearer ")[1].trim();
    const parts = idToken.split(".");
    if (parts.length !== 3) {
        throw new Error("Invalid JWT token format");
    }
    const header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
    const nowSeconds = Math.floor(Date.now() / 1000);
    // Validate Token Claims
    if (payload.aud !== projectId) {
        throw new Error(`Invalid audience: expected ${projectId}, got ${payload.aud}`);
    }
    if (payload.iss !== `https://securetoken.google.com/${projectId}`) {
        throw new Error(`Invalid issuer: ${payload.iss}`);
    }
    if (payload.exp < nowSeconds) {
        throw new Error("ID Token has expired");
    }
    if (!payload.sub || typeof payload.sub !== "string") {
        throw new Error("Invalid subject/UID in ID token");
    }
    // Find matching key by kid
    const jwks = await getGoogleJwksKeys();
    const matchingKey = jwks.find((k) => k.kid === header.kid);
    if (!matchingKey) {
        throw new Error(`No matching public key found for kid: ${header.kid}`);
    }
    // Import RSA JWK Public Key
    const publicKey = await crypto.subtle.importKey("jwk", matchingKey, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const signedData = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signature = base64UrlDecode(parts[2]);
    const isValid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, signature, signedData);
    if (!isValid) {
        throw new Error("ID Token signature verification failed");
    }
    return payload.sub; // return user UID
}
/**
 * Converts Firestore REST fields into a clean JS object
 */
function parseFirestoreFields(fields) {
    const result = {};
    if (!fields)
        return result;
    for (const key of Object.keys(fields)) {
        const valObj = fields[key];
        if ("stringValue" in valObj)
            result[key] = valObj.stringValue;
        else if ("booleanValue" in valObj)
            result[key] = valObj.booleanValue;
        else if ("integerValue" in valObj)
            result[key] = parseInt(valObj.integerValue, 10);
        else if ("doubleValue" in valObj)
            result[key] = parseFloat(valObj.doubleValue);
        else if ("timestampValue" in valObj)
            result[key] = valObj.timestampValue;
        else if ("nullValue" in valObj)
            result[key] = null;
        else if ("mapValue" in valObj)
            result[key] = parseFirestoreFields(valObj.mapValue.fields);
    }
    return result;
}
/**
 * Helper to fetch a Firestore document using REST API
 */
async function getFirestoreDocument(projectId, path, accessToken) {
    const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${path}`;
    const res = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (res.status === 404)
        return null;
    if (!res.ok) {
        const err = await res.text();
        throw new Error(`Firestore GET ${path} failed (${res.status}): ${err}`);
    }
    const json = (await res.json());
    return parseFirestoreFields(json.fields);
}
/**
 * Helper to send FCM Push Notification via FCM HTTP v1 API
 */
async function sendFcmMessage(projectId, token, title, body, dataPayload, channelId, accessToken) {
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
    const bodyPayload = {
        message: {
            token: token,
            notification: {
                title: title,
                body: body,
            },
            data: dataPayload,
            android: {
                priority: "HIGH",
                notification: {
                    sound: "default",
                    channelId: channelId,
                },
            },
        },
    };
    const res = await fetch(url, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json",
        },
        body: JSON.stringify(bodyPayload),
    });
    if (!res.ok) {
        const errText = await res.text();
        throw new Error(`FCM send failed (${res.status}): ${errText}`);
    }
    return await res.json();
}
/**
 * Cloudflare Worker Export
 */
export default {
    async fetch(request, env) {
        // CORS headers
        const corsHeaders = {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, Authorization",
        };
        if (request.method === "OPTIONS") {
            return new Response(null, { headers: corsHeaders });
        }
        const url = new URL(request.url);
        // Health check endpoint
        if (url.pathname === "/" || url.pathname === "/health") {
            return new Response(JSON.stringify({ status: "online", project: env.PROJECT_ID }), {
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            });
        }
        try {
            // 1. Authenticate Requesting User via Firebase ID Token
            const authHeader = request.headers.get("Authorization");
            const callerUid = await verifyFirebaseIdToken(authHeader, env.PROJECT_ID);
            // 2. Get Google OAuth2 Access Token for Firestore & FCM APIs
            const accessToken = await getGoogleAccessToken(env);
            // --- Endpoint 1: POST /notify/message ---
            if (url.pathname === "/notify/message" && request.method === "POST") {
                const body = (await request.json());
                const { chatId, messageId } = body;
                if (!chatId || !messageId) {
                    return new Response(JSON.stringify({ error: "Missing chatId or messageId" }), {
                        status: 400,
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                // Fetch Message Document from Firestore
                const messageData = await getFirestoreDocument(env.PROJECT_ID, `chats/${chatId}/messages/${messageId}`, accessToken);
                if (!messageData) {
                    return new Response(JSON.stringify({ error: "Message document not found" }), {
                        status: 444,
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                const senderId = messageData.senderId;
                const receiverId = messageData.receiverId;
                // Security check: callerUid must match senderId
                if (callerUid !== senderId) {
                    return new Response(JSON.stringify({ error: "Forbidden: Caller is not the message sender" }), {
                        status: 403,
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                if (!receiverId || receiverId === senderId) {
                    return new Response(JSON.stringify({ status: "skipped", reason: "self_message_or_invalid_receiver" }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                // Fetch Receiver and Sender user documents
                const [receiverData, senderData] = await Promise.all([
                    getFirestoreDocument(env.PROJECT_ID, `users/${receiverId}`, accessToken),
                    getFirestoreDocument(env.PROJECT_ID, `users/${senderId}`, accessToken),
                ]);
                if (!receiverData) {
                    return new Response(JSON.stringify({ status: "skipped", reason: "receiver_not_found" }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                const fcmToken = receiverData.fcmToken;
                if (!fcmToken || typeof fcmToken !== "string" || fcmToken.trim() === "") {
                    return new Response(JSON.stringify({ status: "skipped", reason: "no_fcm_token" }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                if (receiverData.notificationsEnabled === false) {
                    return new Response(JSON.stringify({ status: "skipped", reason: "notifications_disabled" }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                // Active chat foreground suppression check
                if (receiverData.activeChatId === chatId) {
                    return new Response(JSON.stringify({ status: "skipped", reason: "chat_active_in_foreground" }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                const senderName = (senderData && senderData.name) ? senderData.name : "GupShup Contact";
                let notifBody = messageData.text || "";
                if (messageData.type === "image" || messageData.imageUrl) {
                    notifBody = "📷 Photo";
                }
                if (!notifBody.trim()) {
                    notifBody = "Sent a message";
                }
                const dataPayload = {
                    type: "message",
                    chatId: chatId,
                    senderId: senderId,
                    title: senderName,
                    body: notifBody,
                };
                const fcmRes = await sendFcmMessage(env.PROJECT_ID, fcmToken, senderName, notifBody, dataPayload, "messages", accessToken);
                return new Response(JSON.stringify({ success: true, result: fcmRes }), {
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                });
            }
            // --- Endpoint 2: POST /notify/friend-request ---
            if (url.pathname === "/notify/friend-request" && request.method === "POST") {
                const body = (await request.json());
                const { requestId } = body;
                if (!requestId) {
                    return new Response(JSON.stringify({ error: "Missing requestId" }), {
                        status: 400,
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                const reqData = await getFirestoreDocument(env.PROJECT_ID, `friend_requests/${requestId}`, accessToken);
                if (!reqData) {
                    return new Response(JSON.stringify({ error: "Friend request doc not found" }), {
                        status: 404,
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                const status = reqData.status;
                const fromUid = reqData.fromUid;
                const toUid = reqData.toUid;
                if (status === "pending") {
                    if (callerUid !== fromUid) {
                        return new Response(JSON.stringify({ error: "Forbidden: Caller is not the request sender" }), {
                            status: 403,
                            headers: { ...corsHeaders, "Content-Type": "application/json" },
                        });
                    }
                    const [toUser, fromUser] = await Promise.all([
                        getFirestoreDocument(env.PROJECT_ID, `users/${toUid}`, accessToken),
                        getFirestoreDocument(env.PROJECT_ID, `users/${fromUid}`, accessToken),
                    ]);
                    if (!toUser || !toUser.fcmToken || toUser.notificationsEnabled === false) {
                        return new Response(JSON.stringify({ status: "skipped", reason: "target_user_unavailable_or_disabled" }), {
                            headers: { ...corsHeaders, "Content-Type": "application/json" },
                        });
                    }
                    const senderName = (fromUser && fromUser.name) ? fromUser.name : "Someone";
                    const title = "Friend Request";
                    const notifBody = `${senderName} sent you a friend request`;
                    const fcmRes = await sendFcmMessage(env.PROJECT_ID, toUser.fcmToken, title, notifBody, { type: "friend_request", fromUid, title, body: notifBody }, "friend_requests", accessToken);
                    return new Response(JSON.stringify({ success: true, result: fcmRes }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                else if (status === "accepted") {
                    if (callerUid !== toUid) {
                        return new Response(JSON.stringify({ error: "Forbidden: Caller is not the request acceptor" }), {
                            status: 403,
                            headers: { ...corsHeaders, "Content-Type": "application/json" },
                        });
                    }
                    const [fromUser, toUser] = await Promise.all([
                        getFirestoreDocument(env.PROJECT_ID, `users/${fromUid}`, accessToken),
                        getFirestoreDocument(env.PROJECT_ID, `users/${toUid}`, accessToken),
                    ]);
                    if (!fromUser || !fromUser.fcmToken || fromUser.notificationsEnabled === false) {
                        return new Response(JSON.stringify({ status: "skipped", reason: "target_user_unavailable_or_disabled" }), {
                            headers: { ...corsHeaders, "Content-Type": "application/json" },
                        });
                    }
                    const acceptingName = (toUser && toUser.name) ? toUser.name : "Someone";
                    const title = "Friend Request Accepted";
                    const notifBody = `${acceptingName} accepted your friend request`;
                    const fcmRes = await sendFcmMessage(env.PROJECT_ID, fromUser.fcmToken, title, notifBody, { type: "friend_request_accepted", toUid, title, body: notifBody }, "friend_requests", accessToken);
                    return new Response(JSON.stringify({ success: true, result: fcmRes }), {
                        headers: { ...corsHeaders, "Content-Type": "application/json" },
                    });
                }
                return new Response(JSON.stringify({ status: "ignored", reason: `unsupported_status_${status}` }), {
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                });
            }
            return new Response(JSON.stringify({ error: "Not Found" }), {
                status: 404,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            });
        }
        catch (err) {
            console.error("[Worker Error]", err);
            return new Response(JSON.stringify({ error: err.message || "Internal Worker Error" }), {
                status: err.message?.includes("Authorization") || err.message?.includes("ID Token") ? 401 : 500,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            });
        }
    },
};
