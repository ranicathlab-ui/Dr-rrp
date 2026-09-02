# DR RRP backend — deploying to Render (free, no billing)

This replaces the Cloud Functions version (`functions/`, kept only for reference — not deployable
without Firebase's Blaze plan). Same logic, running as a plain Node process instead.

## 1. Get a Firebase service account key (free, no billing)

1. https://console.firebase.google.com/project/dr-rrp-app-9f517/settings/serviceaccounts/adminsdk
2. **Generate new private key** — downloads a JSON file. Keep it secret; it's full Admin SDK
   access to your Firebase project (Firestore, Auth, FCM) — treat it like a password, never commit
   it to a public repo.

## 2. Push this project to a Git host Render can see (GitHub, GitLab, or Bitbucket)

Render deploys from a connected repo. If this project isn't on GitHub yet, create a repo there and
push it — ask Claude to do the `git init`/commit/push steps if you'd like help with that part.

## 3. Create the Render Web Service

1. https://render.com → sign up (no card required for the free tier)
2. **New → Web Service** → connect the repo from step 2
3. Settings:
   - **Root Directory:** `server`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Instance Type:** Free
4. **Environment** tab → add:
   - `FIREBASE_SERVICE_ACCOUNT_JSON` — paste the *entire contents* of the JSON file from step 1
   - `MISSED_ENTRY_CRON_SECRET` — any random string you make up (used to protect the daily
     missed-entry sweep endpoint below — it's not a per-user credential, just a shared secret so
     random internet traffic can't trigger it)
5. **Create Web Service** — first deploy takes a few minutes. Once live, note the URL — something
   like `https://dr-rrp-backend.onrender.com`.

## 4. Point the Android app at it

Update `RetrofitSyncApiService.BASE_URL` and `InviteApiProvider.BASE_URL` (in `app/src/main/java/com/postpci/drrrp/data/sync/`) to that Render URL, then rebuild. (Ask Claude to do this once you have the URL — it's a one-line change in each file.)

## 5. Set up the daily missed-entry check (free, no billing)

Render's free tier has no built-in cron scheduler, so this uses an external one to hit the
sweep endpoint once a day:

1. https://cron-job.org → free account, no card required
2. Create a cron job:
   - **URL:** `https://<your-render-url>/internal/check-missed-entries`
   - **Method:** POST
   - **Header:** `X-Cron-Secret: <the same value you set as MISSED_ENTRY_CRON_SECRET>`
   - **Schedule:** once daily, whatever time makes sense (e.g. 18:00 IST)

This ping also happens to be useful for step 6 below — it wakes the server up if it's asleep.

## Known trade-off: cold starts

Render's free tier spins the server down after ~15 minutes with no traffic, and takes roughly
30–50 seconds to wake up on the next request. The app's sync calls will just be slow (not
broken) the first time after a period of inactivity — nothing to fix, just worth knowing about
if a request seems to hang briefly.
