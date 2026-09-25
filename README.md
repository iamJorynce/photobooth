# Photobooth backend (Render.com - walay credit card)

Plain Node/Express server, walay Firebase. Naay 5 ka endpoints:

1. `POST /createPaymentSession` - naghimo ug PayMongo dynamic QR (Payment Intent ->
   Payment Method -> attach), gibalik ang QR image (base64).
2. `GET /sessionStatus/:id` - gi-poll sa app matag pipila ka segundo samtang naghulat sa
   bayad. Mobalik "pending" -> "paid" pagkabayad.
3. `POST /paymongoWebhook` - gitawag sa PayMongo (dili sa app) pag-bayad, mo-update sa
   session status.
4. `POST /uploadPhoto/:sessionId` - i-upload ang final photo diri, dayon direkta na
   iyang ibalik ang download URL (walay kinahanglan laing polling).
5. `GET /photo/:downloadId` - mao ni ang URL nga naka-QR sa Download screen - dinhi
   ma-download sa customer ang photo.

## Setup (Render, walay card)

1. I-push ni nga folder sa usa ka GitHub repo (private ra pwede).
2. Adto sa render.com, sign up gamit GitHub account (walay card kinahanglan para sa free
   tier).
3. "New +" > "Web Service" > i-connect ang imong repo.
4. Settings:
   - Build command: `npm install`
   - Start command: `npm start`
   - Instance type: Free
5. Idugang ang Environment Variables (Render dashboard > Environment):
   - `PAYMONGO_SECRET_KEY` (gikan sa PayMongo dashboard, sk_test_... una)
   - `PAYMONGO_PUBLIC_KEY` (pk_test_...)
   - `PAYMONGO_WEBHOOK_SECRET` (makuha pagkahuman nimo ma-register ang webhook, step 7)
   - `PUBLIC_BASE_URL` = ang URL nga ihatag ni Render sa imong service pagkahuman ma-deploy
     (e.g. `https://photobooth-backend.onrender.com`) - i-update ni ug balik human
     ma-deploy usa ka higayon aron makuha nimo ang tinuod nga URL.
6. Deploy (automatic pag-connect nimo sa repo, o pindot ang "Manual Deploy").
7. I-copy ang deployed URL + `/paymongoWebhook` (e.g.
   `https://photobooth-backend.onrender.com/paymongoWebhook`), i-register sa PayMongo
   dashboard > Developers > Webhooks, subscribe sa `payment.paid`, `payment.failed`,
   `qrph.expired`. Makuha nimo diri ang webhook secret nga ibutang sa step 5.

## Importante nga hibaloan

- **Free tier sa Render "matulog"** kung walay traffic sulod ~15 minutos, ug mo-restart
  pag-abot ug bag-ong request (naay ~30s-1min delay sa first request pagkahuman
  matulog). Dawaton lang na sa simple/low-traffic vendo, pero kung problema na ni sa
  imoha (customer maghulat), pwede mo-upgrade later sa paid tier ($7/mo) para dili
  matulog.
- **In-memory ra ang storage** (sessions/downloads) - mawala kung mag-restart ang
  server. Okay ra ni para sa mubo nga payment session (30 min lifespan) ug photos nga
  24h ra ang expiry, pero kung importante nimo nga dili gyud mawala ang photos bisan
  mag-restart, pwede ta mag-switch sa gamay nga free storage service (e.g. Cloudinary)
  sunod.
- Gamit una ang **test/sandbox PayMongo keys** hangtod na-verify ang tibuok flow.

## Sunod

I-update ang `KioskViewModel.kt` sa Android app para mo-poll sa `/sessionStatus/:id`
imbes maghulat sa Firestore, ug mo-upload direkta sa `/uploadPhoto/:sessionId` - gibuhat
na nako ni, tan-awa ang updated file.
