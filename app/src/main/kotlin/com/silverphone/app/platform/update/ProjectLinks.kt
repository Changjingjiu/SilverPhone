package com.silverphone.app.platform.update

/**
 * Every address of this project, written down once.
 *
 * The About screen, the update check and the releases fallback all read these, so a
 * moved repository is one edit rather than three that can disagree with each other.
 */
object ProjectLinks {

    const val REPOSITORY = "https://github.com/Changjingjiu/SilverPhone"

    /** The same address without the scheme: this is what a person reads on screen. */
    const val REPOSITORY_DISPLAY = "github.com/Changjingjiu/SilverPhone"

    const val RELEASES = "$REPOSITORY/releases"

    const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Changjingjiu/SilverPhone/releases/latest"
}
