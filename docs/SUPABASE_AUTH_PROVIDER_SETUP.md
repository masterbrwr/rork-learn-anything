# Supabase authentication provider setup

This app uses Supabase Auth for email/password, Google OAuth, and SMS one-time codes.

## Project

The Supabase project URL currently visible in the app's OAuth error is:

`https://rqyitdceca.supabase.co`

Confirm that this is the intended production project before changing settings.

## Enable Google sign-in

1. Open the Supabase Dashboard for the project.
2. Go to **Authentication → Sign In / Providers → Google**.
3. Enable Google.
4. In Google Cloud Console, create/configure an OAuth client for a web application.
5. Add the Supabase callback URL shown in the Supabase Google provider setup page to the Google OAuth client's **Authorized redirect URIs**. It should be:
   `https://rqyitdceca.supabase.co/auth/v1/callback`
6. Copy the Google OAuth **Client ID** and **Client Secret** into the Supabase Google provider settings. Save.
7. In **Authentication → URL Configuration**, add this exact redirect URL to the allowed redirect URLs:
   `learnanything://auth/callback`
8. Test on a real Android device. The browser should return to the app and the app should exchange the PKCE code for a Supabase session.

Never put the Google Client Secret in the Android app, Git repository, APK, or a public CI variable. It belongs only in the Supabase provider configuration.

## Enable phone sign-in

1. Go to **Authentication → Sign In / Providers → Phone** and enable phone authentication.
2. Configure an SMS provider supported by Supabase Auth and enter its credentials in the Supabase Dashboard.
3. Set an appropriate SMS message template and provider rate limits.
4. Set OTP expiry and abuse protections appropriate for the app.
5. Test delivery and verification using a real phone number. SMS delivery generally has a per-message cost.

Do not put SMS provider credentials in the Android app or Git repository.

## Current app behavior

The Android app already implements the Google OAuth PKCE browser round-trip and phone OTP request/verification calls. The app cannot enable either provider by itself: provider enablement and provider credentials are project-level Supabase settings.

If Supabase returns `unsupported provider: provider is not enabled`, the request has reached Supabase successfully, but Google is still disabled or missing valid provider configuration. Rebuilding the APK alone cannot fix that dashboard setting.

## Release checklist still required

- Verify Google and SMS sign-in end-to-end on physical devices.
- Apply and verify all backend database migrations and Edge Functions.
- Replace the current PayPal subscription path with Google Play Billing for Android digital subscriptions and Apple In-App Purchase for iOS, including server-side purchase verification.
- Complete privacy policy, account deletion/data deletion, security review, production signing, store listing, and store testing.
