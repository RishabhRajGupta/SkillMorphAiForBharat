
package com.example.skillmorph

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The base Application class for the app.
 * The @HiltAndroidApp annotation triggers Hilt's code generation.
 * The Google Services plugin now handles Firebase initialization automatically.
 */
@HiltAndroidApp
class SkillMorphApp : Application()
