# Advertising And Consent Release Notes

Now Do This uses Google User Messaging Platform (UMP) to refresh consent information
at every launch and show a consent form when required. Google Mobile Ads initializes
only when UMP reports that ads may be requested. If consent information cannot be
refreshed, the last UMP status remains authoritative; the planner stays usable and no
banner is shown unless `canRequestAds()` is true.

The banner is a global app-shell surface and contains no task, category, reminder,
history, or backup values. A privacy-options action appears in the Tasks overflow menu
when UMP reports that an entry point is required.

The debug-only store-media capture rule suppresses consent and ad startup before its
Activities launch so generated Play Store screenshots remain deterministic and contain
no live advertising. This seam is guarded by `BuildConfig.DEBUG` and is not used by
normal app launches.

This branch deliberately uses Google's sample identifiers from
`app/src/main/res/values/ads_key_ids.xml`. Before production:

1. Register Now Do This in AdMob and replace both sample identifiers.
2. Configure and publish the required GDPR message in AdMob Privacy & messaging.
3. Publish a privacy policy that discloses Google UMP and Mobile Ads processing.
4. Complete Google Play Data Safety and Ads declarations from the final SDK behavior.
5. Verify consent grant, denial, revocation, privacy options, offline launch, and ad
   loading on a release candidate without interacting with live ads.
6. Publish and verify the applicable `app-ads.txt` seller record.

The release must remain blocked while sample identifiers are present.
