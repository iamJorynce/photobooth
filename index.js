const express = require("express");
const axios = require("axios");
const crypto = require("crypto");
const multer = require("multer");
const path = require("path");
const { randomUUID } = require("crypto");

const app = express();
const upload = multer({ dest: path.join(__dirname, "uploads") });

// Set these in Render's dashboard: Environment > Add Environment Variable
const PAYMONGO_SECRET_KEY = process.env.PAYMONGO_SECRET_KEY;
const PAYMONGO_PUBLIC_KEY = process.env.PAYMONGO_PUBLIC_KEY;
const PAYMONGO_WEBHOOK_SECRET = process.env.PAYMONGO_WEBHOOK_SECRET;
// Render gives you this automatically as your service's public URL,
// e.g. https://photobooth-backend.onrender.com -- set it manually too
// so the server can build absolute photo links.
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL;

const PAYMONGO_BASE = "https://api.paymongo.com/v1";
const DOWNLOAD_TTL_MS = 24 * 60 * 60 * 1000; // 24h

// In-memory stores. Simple on purpose -- good enough for a single kiosk.
// NOTE: Render's free tier restarts the service after ~15 min of no traffic,
// which clears this memory. Fine for pending sessions (customer is mid-payment,
// traffic is happening). If you later need photos to survive restarts/redeploys,
// swap the "uploads" folder + downloads map for a small free object-storage
// service (e.g. Cloudinary) instead of local disk.
const sessions = new Map(); // sessionId -> { status, amount, createdAt }
const downloads = new Map(); // sessionId -> { filename, createdAt }

function authHeader(key) {
  return "Basic " + Buffer.from(`${key}:`).toString("base64");
}

app.use(express.json({
  verify: (req, res, buf) => { req.rawBody = buf; } // needed for webhook signature check
}));

/**
 * 1) Android app calls this to start a session.
 */
app.post("/createPaymentSession", async (req, res) => {
  try {
    const amountCentavos = 5000; // PHP 50.00

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

    const methodRes = await axios.post(
      `${PAYMONGO_BASE}/payment_methods`,
      { data: { attributes: { type: "qrph" } } },
      { headers: { Authorization: authHeader(PAYMONGO_PUBLIC_KEY) } }
    );
    const paymentMethodId = methodRes.data.data.id;

    const attachRes = await axios.post(
      `${PAYMONGO_BASE}/payment_intents/${paymentIntent.id}/attach`,
      { data: { attributes: { payment_method: paymentMethodId, client_key: clientKey } } },
      { headers: { Authorization: authHeader(PAYMONGO_PUBLIC_KEY) } }
    );
       const qrImageBase64 = attachRes.data.data.attributes.next_action.code.image_url;
   console.log("FULL NEXT ACTION:", JSON.stringify(attachRes.data.data.attributes.next_action));

    sessions.set(paymentIntent.id, {
      status: "pending",
      amount: amountCentavos,
      createdAt: Date.now(),
    });

    res.status(200).json({ sessionId: paymentIntent.id, qrImageBase64 });
  } catch (err) {
    console.error(err.response?.data || err.message);
    res.status(500).json({ error: "Failed to create payment session" });
  }
});

/**
 * 2) Android app polls this every few seconds while showing the QR.
 *    Simpler than realtime listeners -- no extra service needed.
 */
app.get("/sessionStatus/:id", (req, res) => {
  const session = sessions.get(req.params.id);
  if (!session) return res.status(404).json({ status: "not_found" });
  res.status(200).json({ status: session.status });
});

/**
 * 3) PayMongo calls this on payment.paid / payment.failed / qrph.expired.
 *    Set this URL in the PayMongo dashboard webhook settings, e.g.
 *    https://your-app.onrender.com/paymongoWebhook
 */
app.post("/paymongoWebhook", (req, res) => {
  console.log("WEBHOOK HIT:", JSON.stringify(req.body));
  const signatureHeader = req.headers["paymongo-signature"];
  console.log("SIGNATURE HEADER:", signatureHeader);
  
  const isValid = verifyPaymongoSignature(req.rawBody, signatureHeader, PAYMONGO_WEBHOOK_SECRET);
  console.log("SIGNATURE VALID?:", isValid);
  
  if (!isValid) {
    return res.status(400).send("Invalid signature");
  }
  // ... (padayon ang naa na nga code)

  const event = req.body.data;
  const eventType = event.attributes.type;
  const paymentIntentId =
    event.attributes.data.attributes.payment_intent_id ||
    event.attributes.data.attributes.payment_intent?.id;

  const session = sessions.get(paymentIntentId);
  if (session) {
    if (eventType === "payment.paid") session.status = "paid";
    else if (eventType === "payment.failed") session.status = "failed";
    else if (eventType === "qrph.expired") session.status = "expired";
  }

  res.status(200).send("ok");
});

function verifyPaymongoSignature(rawBody, signatureHeader, secret) {
  if (!signatureHeader || !secret) return false;
  // TODO: confirm exact header parsing against current PayMongo webhook docs
  // (test vs live mode uses a different field) before going to production.
  const parts = Object.fromEntries(signatureHeader.split(",").map((p) => p.split("=")));
  const signedPayload = `${parts.t}.${rawBody}`;
  const expected = crypto.createHmac("sha256", secret).update(signedPayload).digest("hex");
  const provided = parts.li || parts.te;
  return expected === provided;
}

/**
 * 4) Android app uploads the final photo here right after printing.
 *    Returns the download URL directly -- no separate polling needed.
 */
app.post("/uploadPhoto/:sessionId", upload.single("photo"), (req, res) => {
  if (!req.file) return res.status(400).json({ error: "No photo uploaded" });

  const downloadId = randomUUID();
  downloads.set(downloadId, { filename: req.file.filename, createdAt: Date.now() });

  res.status(200).json({ downloadUrl: `${PUBLIC_BASE_URL}/photo/${downloadId}` });
});

/**
 * 5) Serves the actual photo -- this is the URL encoded in the download QR.
 */
app.get("/photo/:downloadId", (req, res) => {
  const entry = downloads.get(req.params.downloadId);
  if (!entry) return res.status(404).send("Not found or expired");
  if (Date.now() - entry.createdAt > DOWNLOAD_TTL_MS) {
    return res.status(410).send("Link expired");
  }
  res.sendFile(path.join(__dirname, "uploads", entry.filename));
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Photobooth backend listening on port ${PORT}`));
