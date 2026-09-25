const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();
const bucket = admin.storage().bucket();

// Set these with:
//   firebase functions:config:set paymongo.secret_key="sk_test_xxx" paymongo.public_key="pk_test_xxx" paymongo.webhook_secret="whsec_xxx"
const PAYMONGO_SECRET_KEY = functions.config().paymongo?.secret_key;
const PAYMONGO_PUBLIC_KEY = functions.config().paymongo?.public_key;
const PAYMONGO_WEBHOOK_SECRET = functions.config().paymongo?.webhook_secret;

const PAYMONGO_BASE = "https://api.paymongo.com/v1";
const SESSION_TTL_MS = 30 * 60 * 1000; // matches PayMongo's 30 min QR expiry
const DOWNLOAD_TTL_MS = 24 * 60 * 60 * 1000; // 24h download link expiry

function authHeader(key) {
  return "Basic " + Buffer.from(`${key}:`).toString("base64");
}

/**
 * 1) Android app calls this to start a session.
 *    Creates a PayMongo Payment Intent -> Payment Method (qrph) -> attaches them,
 *    stores a "sessions/{sessionId}" doc in Firestore, and returns the QR image
 *    (base64 png) for the app to display.
 */
exports.createPaymentSession = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method not allowed");

  try {
    const amountCentavos = 5000; // PHP 50.00 -- adjust or read from req.body

    // Step 1: create the Payment Intent
    const intentRes = await axios.post(
      `${PAYMONGO_BASE}/payment_intents`,
      {
        data: {
          attributes: {
            amount: amountCentavos,
            currency: "PHP",
            payment_method_allowed: ["qrph"],
            description: "Photobooth session",
          },
        },
      },
      { headers: { Authorization: authHeader(PAYMONGO_SECRET_KEY) } }
    );
    const paymentIntent = intentRes.data.data;
    const clientKey = paymentIntent.attributes.client_key;

    // Step 2: create the qrph Payment Method
    const methodRes = await axios.post(
      `${PAYMONGO_BASE}/payment_methods`,
      { data: { attributes: { type: "qrph" } } },
      { headers: { Authorization: authHeader(PAYMONGO_PUBLIC_KEY) } }
    );
    const paymentMethodId = methodRes.data.data.id;

    // Step 3: attach -> triggers next_action.code.image_url (the QR)
    const attachRes = await axios.post(
      `${PAYMONGO_BASE}/payment_intents/${paymentIntent.id}/attach`,
      {
        data: {
          attributes: { payment_method: paymentMethodId, client_key: clientKey },
        },
      },
      { headers: { Authorization: authHeader(PAYMONGO_PUBLIC_KEY) } }
    );
    const qrImageBase64 = attachRes.data.data.attributes.next_action.code.image_url;

    // Store the session so the webhook and the app both have a shared place to check status
    const sessionRef = db.collection("sessions").doc(paymentIntent.id);
    await sessionRef.set({
      status: "pending", // pending -> paid | failed | expired
      amount: amountCentavos,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      expiresAt: Date.now() + SESSION_TTL_MS,
    });

    res.status(200).json({
      sessionId: paymentIntent.id,
      qrImageBase64, // Android: decode and show directly, or feed into an ImageView
    });
  } catch (err) {
    console.error(err.response?.data || err.message);
    res.status(500).json({ error: "Failed to create payment session" });
  }
});

/**
 * 2) PayMongo calls this when payment.paid / payment.failed / qrph.expired fires.
 *    Configure this URL in your PayMongo dashboard webhook settings.
 *    The Android app should listen to the matching Firestore doc
 *    (sessions/{sessionId}) in realtime instead of polling this endpoint directly.
 */
exports.paymongoWebhook = functions.https.onRequest(async (req, res) => {
  // Verify the webhook signature so random requests can't fake a "paid" event.
  const signatureHeader = req.headers["paymongo-signature"];
  if (!verifyPaymongoSignature(req.rawBody, signatureHeader, PAYMONGO_WEBHOOK_SECRET)) {
    return res.status(400).send("Invalid signature");
  }

  const event = req.body.data;
  const eventType = event.attributes.type;
  const paymentIntentId =
    event.attributes.data.attributes.payment_intent_id ||
    event.attributes.data.attributes.payment_intent?.id;

  if (!paymentIntentId) return res.status(400).send("Missing payment intent id");

  const sessionRef = db.collection("sessions").doc(paymentIntentId);

  if (eventType === "payment.paid") {
    await sessionRef.set({ status: "paid" }, { merge: true });
  } else if (eventType === "payment.failed") {
    await sessionRef.set({ status: "failed" }, { merge: true });
  } else if (eventType === "qrph.expired") {
    await sessionRef.set({ status: "expired" }, { merge: true });
  }

  res.status(200).send("ok");
});

function verifyPaymongoSignature(rawBody, signatureHeader, secret) {
  if (!signatureHeader || !secret) return false;
  // PayMongo signature header format: "t=timestamp,te=test_signature,li=live_signature"
  // TODO: confirm exact header parsing against your PayMongo dashboard's webhook docs
  // (test vs live mode uses a different field) before going to production.
  const parts = Object.fromEntries(
    signatureHeader.split(",").map((p) => p.split("="))
  );
  const signedPayload = `${parts.t}.${rawBody}`;
  const expected = crypto.createHmac("sha256", secret).update(signedPayload).digest("hex");
  const provided = parts.li || parts.te;
  return expected === provided;
}

/**
 * 3) Storage trigger: fires automatically when the Android app uploads the
 *    final photo to Storage at path "photos/{sessionId}.jpg".
 *    Generates a long-lived signed URL and stores it in "downloads/{sessionId}"
 *    so the app can read it back and render the download QR.
 */
exports.onPhotoUploaded = functions.storage.object().onFinalize(async (object) => {
  if (!object.name.startsWith("photos/")) return null;

  const sessionId = object.name.replace("photos/", "").replace(/\.[a-zA-Z]+$/, "");
  const file = bucket.file(object.name);

  const [url] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + DOWNLOAD_TTL_MS,
  });

  await db.collection("downloads").doc(sessionId).set({
    url,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt: Date.now() + DOWNLOAD_TTL_MS,
  });

  return null;
});
