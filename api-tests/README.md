# api-tests (Rest Assured)

End-to-end API tests that call the **running** system through the gateway, like the React app does.

## Run
1. Start the stack: `docker compose up -d` and wait until the gateway answers on http://localhost:2323.
2. Set the admin you put in `.env` (BOOTSTRAP_ADMIN_*):

   Linux/macOS: `export ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='your-password'`
   PowerShell:  `$env:ADMIN_EMAIL="you@example.com"; $env:ADMIN_PASSWORD="your-password"`
3. From `ecommerce-microservice/`: `mvn -f api-tests/pom.xml verify`

`API_BASE_URL` points the same tests at another environment (DEV/STAGING). Without ADMIN_* the signed-in tests are
skipped, and the "no token => 401" tests still run.

## What is covered
| Class | Checks |
|---|---|
| AuthApiTest | login ok / wrong password / validation, /me with no, forged and valid token |
| ProductApiTest | public browsing, limit, empty search, 404, writes need login, validation 400 |
| CartApiTest | every cart call is 401 without a token |
| CouponApiTest | create, listed vs private visibility, duplicate, validation, cleanup |
