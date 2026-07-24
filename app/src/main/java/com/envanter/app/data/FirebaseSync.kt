package com.envanter.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Firestore iki yönlü senkron. google-services.json gerçek bir projeyle
 * değiştirilmediyse (placeholder), senkron sessizce devre dışı kalır.
 */
object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: ListenerRegistration? = null

    private val _status = MutableStateFlow("Başlatılmadı")
    val status: StateFlow<String> = _status

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    val isConfigured: Boolean
        get() = runCatching {
            FirebaseApp.getApps(Repository.appContext).isNotEmpty() &&
                FirebaseApp.getInstance().options.projectId != "envanter-placeholder"
        }.getOrDefault(false)

    /** google-services plugin bunu yalnızca Console'da Google sign-in etkinse üretir. */
    fun webClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    fun start(context: Context) {
        if (!isConfigured) {
            _status.value = "Firebase yapılandırılmadı (google-services.json ekleyin)"
            return
        }
        _userEmail.value = FirebaseAuth.getInstance().currentUser?.takeIf { !it.isAnonymous }?.email
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { attach() }
                .addOnFailureListener { e ->
                    _status.value = "Giriş hatası: ${e.message}"
                    Log.w(TAG, "anon sign-in failed", e)
                }
        } else attach()
    }

    /** Google hesabıyla giriş; anonim kullanıcı varsa verisini kaybetmeden yükseltir. */
    fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        val task = if (current != null && current.isAnonymous) {
            current.linkWithCredential(credential)
        } else {
            auth.signInWithCredential(credential)
        }
        task
            .addOnSuccessListener {
                _userEmail.value = it.user?.email
                attach()
            }
            .addOnFailureListener { e ->
                _status.value = "Google giriş hatası: ${e.message}"
                Log.w(TAG, "google sign-in failed", e)
            }
    }

    fun signOut(context: Context) {
        listener?.remove()
        FirebaseAuth.getInstance().signOut()
        _userEmail.value = null
        start(context)
    }

    private fun collection() =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(FirebaseAuth.getInstance().currentUser!!.uid)
            .collection("items")

    private fun attach() {
        _status.value = "Senkron aktif"
        // Önce yereldeki her şeyi gönder (ilk kurulumda birleşme sağlar).
        scope.launch {
            runCatching { Repository.allIncludingDeleted().forEach { push(it) } }
        }
        listener?.remove()
        listener = collection().addSnapshotListener { snap, e ->
            if (e != null) {
                _status.value = "Senkron hatası: ${e.message}"
                return@addSnapshotListener
            }
            if (snap == null || snap.metadata.hasPendingWrites()) return@addSnapshotListener
            scope.launch {
                for (doc in snap.documents) {
                    val item = FoodItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        category = doc.getString("category") ?: CategoryStore.DEFAULT_ID,
                        quantity = doc.getDouble("quantity") ?: 1.0,
                        unit = doc.getString("unit") ?: "adet",
                        expiryDate = doc.getString("expiryDate") ?: "",
                        note = doc.getString("note") ?: "",
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false
                    )
                    runCatching { Repository.applyRemote(item) }
                }
            }
        }
    }

    fun push(item: FoodItem) {
        if (!isConfigured || FirebaseAuth.getInstance().currentUser == null) return
        runCatching {
            collection().document(item.id).set(
                mapOf(
                    "name" to item.name,
                    "category" to item.category,
                    "quantity" to item.quantity,
                    "unit" to item.unit,
                    "expiryDate" to item.expiryDate,
                    "note" to item.note,
                    "updatedAt" to item.updatedAt,
                    "deleted" to item.deleted
                )
            )
        }.onFailure { Log.w(TAG, "push failed", it) }
    }
}
