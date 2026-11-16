package app.hyperstream

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLock)
    }
}

/**
 * App lock: every time the app comes back to the screen it is covered until the user unlocks it
 * with their fingerprint or the phone's own PIN/pattern. Phones with no screen lock set are not locked.
 */
object AppLock : Application.ActivityLifecycleCallbacks {
    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private var started = 0
    private var locked = true
    private var prompting = false // the PIN screen can take the app off screen; that must not relock it

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        // Keep the app's contents out of the recent-apps screen (screenshots still work).
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        if (activity is FragmentActivity) guard(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        if (started == 0 && !prompting) locked = true
    }

    private fun guard(activity: FragmentActivity) {
        val cover = activity.findViewById(R.id.lock_cover) ?: activity.layoutInflater.inflate(R.layout.lock_cover, null).also {
            activity.addContentView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            it.findViewById<View>(R.id.unlock).setOnClickListener { prompt(activity) }
        }
        if (locked && BiometricManager.from(activity).canAuthenticate(AUTH) != BiometricManager.BIOMETRIC_SUCCESS) locked = false
        cover.visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) cover.post { prompt(activity) }
    }

    private fun prompt(activity: FragmentActivity) {
        if (prompting || !locked) return
        prompting = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked = false
                activity.findViewById<View>(R.id.lock_cover)?.visibility = View.GONE
            }

            // Cancelled or failed: the cover stays, and its Unlock button tries again.
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                prompting = false
            }
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock HyperStream").setAllowedAuthenticators(AUTH).build()
        )
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
