# Development signing

CI uses the standard Android debug signing generated inside its ephemeral runner. No private signing key is stored in the repository or uploaded as an artifact.

Independent CI runs may use different certificates. If an update reports a signature conflict, export records from the installed application first, uninstall it, install the new APK, and import the backup. A stable release identity should be configured using a private signing secret before distributing long-lived builds.
