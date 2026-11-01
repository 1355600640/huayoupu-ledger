package net.ankio.ledger.capture

import android.content.ComponentName
import android.content.Context

/** System settings and permission checks must address the manifest entry, not its base class. */
internal fun captureServiceComponent(context: Context)=
    ComponentName(context,com.google.android.accessibility.selecttospeak.SelectToSpeakService::class.java)
