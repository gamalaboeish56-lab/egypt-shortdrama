package egypt.shortdrama.top

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import egypt.shortdrama.top.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** تسجيل الدخول: Google + موبايل (SMS) + إيميل/باسورد — كله Firebase Auth */
class AuthActivity : AppCompatActivity() {
    private lateinit var b: ActivityAuthBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }

        b.googleBtn.setOnClickListener { googleSignIn() }
        b.phoneBtn.setOnClickListener { if (verificationId == null) sendCode() else verifyCode() }
        b.loginBtn.setOnClickListener { emailAuth(register = false) }
        b.registerBtn.setOnClickListener { emailAuth(register = true) }
        b.forgotBtn.setOnClickListener { resetPassword() }
        b.skipBtn.setOnClickListener { finish() }
    }

    private fun busy(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        listOf(b.googleBtn, b.phoneBtn, b.loginBtn, b.registerBtn).forEach { it.isEnabled = !on }
    }

    private fun fail(e: Throwable?) {
        busy(false)
        Log.w(App.TAG, "auth failed", e)
        Toast.makeText(this, "حصلت مشكلة: ${e?.localizedMessage ?: "حاول تاني"}", Toast.LENGTH_LONG).show()
    }

    private fun done(user: FirebaseUser?) {
        if (user == null) return fail(null)
        Repo.ensureUserDoc(user) { busy(false); setResult(RESULT_OK); finish() }
    }

    // ---------- Google ----------
    private fun googleSignIn() {
        busy(true)
        val webClientId = getString(R.string.default_web_client_id) // بيتولد تلقائياً من google-services.json
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        lifecycleScope.launch {
            try {
                val result = CredentialManager.create(this@AuthActivity).getCredential(this@AuthActivity, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Repo.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .addOnSuccessListener { done(it.user) }
                        .addOnFailureListener { fail(it) }
                } else fail(null)
            } catch (e: Exception) { fail(e) }
        }
    }

    // ---------- Phone ----------
    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '+' }
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.startsWith("0") -> "+20" + digits.drop(1)
            else -> "+20$digits"
        }
    }

    private fun sendCode() {
        val phone = normalizePhone(b.phone.text?.toString().orEmpty())
        if (phone.length < 12) { b.phoneLayout.error = "رقم غير صحيح"; return }
        b.phoneLayout.error = null
        busy(true)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) = signInPhone(cred)
            override fun onVerificationFailed(e: FirebaseException) = fail(e)
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                busy(false)
                verificationId = id
                b.codeLayout.visibility = View.VISIBLE
                b.phoneBtn.text = "تأكيد الكود"
                Toast.makeText(this@AuthActivity, "بعتنالك SMS فيه الكود", Toast.LENGTH_SHORT).show()
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(Repo.auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
        )
    }

    private fun verifyCode() {
        val code = b.code.text?.toString()?.trim().orEmpty()
        val id = verificationId ?: return
        if (code.length < 6) { b.codeLayout.error = "الكود 6 أرقام"; return }
        busy(true)
        signInPhone(PhoneAuthProvider.getCredential(id, code))
    }

    private fun signInPhone(cred: PhoneAuthCredential) {
        Repo.auth.signInWithCredential(cred)
            .addOnSuccessListener { done(it.user) }
            .addOnFailureListener { fail(it) }
    }

    // ---------- Email ----------
    private fun emailAuth(register: Boolean) {
        val email = b.email.text?.toString()?.trim().orEmpty()
        val pass = b.password.text?.toString().orEmpty()
        val name = b.name.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || pass.length < 6) {
            Toast.makeText(this, "اكتب الإيميل وكلمة سر 6 حروف على الأقل", Toast.LENGTH_SHORT).show(); return
        }
        busy(true)
        if (register) {
            Repo.auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { res ->
                    val user = res.user
                    if (user != null && name.isNotEmpty()) {
                        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                            .addOnCompleteListener { done(user) }
                    } else done(user)
                }
                .addOnFailureListener { fail(it) }
        } else {
            Repo.auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { done(it.user) }
                .addOnFailureListener { fail(it) }
        }
    }

    private fun resetPassword() {
        val email = b.email.text?.toString()?.trim().orEmpty()
        if (email.isEmpty()) { Toast.makeText(this, "اكتب الإيميل الأول", Toast.LENGTH_SHORT).show(); return }
        Repo.auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { Toast.makeText(this, "بعتنالك لينك على الإيميل", Toast.LENGTH_LONG).show() }
            .addOnFailureListener { fail(it) }
    }
}
