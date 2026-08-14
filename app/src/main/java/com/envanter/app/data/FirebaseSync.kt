package com.envanter.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
    private var homeListener: ListenerRegistration? = null

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

    /**
     * Google hesabıyla giriş.
     *
     * Anonim kullanıcı varsa önce onu yükseltmeyi (link) dener; böylece bulutta
     * o cihaza özel biriken veri Google hesabına taşınır. Ancak bu Google hesabı
     * daha önce (ör. önceki kurulumda) başka bir Firebase kullanıcısına bağlanmışsa
     * link "This credential is already associated with a different user account"
     * hatası verir. Bu durumda anonim hesabı bırakıp doğrudan mevcut Google
     * hesabına giriliyor; cihazdaki ürünler Room'da durduğu için [attach] hepsini
     * yeni hesaba yükler, yani veri kaybı olmaz — iki taraf birleşir.
     */
    fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser

        fun succeeded(user: com.google.firebase.auth.FirebaseUser?) {
            _userEmail.value = user?.email
            attach()
        }

        fun failed(e: Exception) {
            _status.value = "Google giriş hatası: ${friendlyAuthError(e)}"
            Log.w(TAG, "google sign-in failed", e)
        }

        fun signInDirectly() {
            auth.signInWithCredential(credential)
                .addOnSuccessListener { succeeded(it.user) }
                .addOnFailureListener { failed(it) }
        }

        if (current != null && current.isAnonymous) {
            current.linkWithCredential(credential)
                .addOnSuccessListener { succeeded(it.user) }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthUserCollisionException) {
                        Log.i(TAG, "link collision, signing in to the existing Google account")
                        signInDirectly()
                    } else failed(e)
                }
        } else signInDirectly()
    }

    /** Firebase'in İngilizce hata metinlerini anlaşılır Türkçeye çevirir. */
    private fun friendlyAuthError(e: Exception): String = when {
        e is FirebaseAuthUserCollisionException ->
            "Bu Google hesabı başka bir kayda bağlı. Tekrar dene; sorun sürerse çıkış yapıp yeniden gir."
        e.message?.contains("network", ignoreCase = true) == true ->
            "İnternet bağlantısı kurulamadı."
        e.message?.contains("ApiException: 10", ignoreCase = true) == true ->
            "Uygulama imzası Firebase'e kayıtlı değil (SHA-1 eksik)."
        else -> e.message ?: "Bilinmeyen hata"
    }

    fun signOut(context: Context) {
        listener?.remove()
        homeListener?.remove()
        FirebaseAuth.getInstance().signOut()
        _userEmail.value = null
        start(context)
    }

    private fun userDoc() =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(FirebaseAuth.getInstance().currentUser!!.uid)

    private fun collection() = userDoc().collection("items")

    private fun homesCollection() = userDoc().collection("homes")

    private fun attach() {
        _status.value = "Senkron aktif"
        // Önce yereldeki her şeyi gönder (ilk kurulumda birleşme sağlar).
        scope.launch {
            runCatching { Repository.allHomesIncludingDeleted().forEach { pushHome(it) } }
            runCatching { Repository.allIncludingDeleted().forEach { push(it) } }
        }
        attachHomes()
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
                        expiryAutoDays = (doc.getLong("expiryAutoDays") ?: 0L).toInt(),
                        note = doc.getString("note") ?: "",
                        openedDate = doc.getString("openedDate") ?: "",
                        openedDays = (doc.getLong("openedDays") ?: 0L).toInt(),
                        homeId = doc.getString("homeId") ?: "",
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false
                    )
                    runCatching { Repository.applyRemote(item) }
                }
            }
        }
    }

    /** Evler ayrı bir koleksiyonda; ürünler homeId ile bunlara bağlanır. */
    private fun attachHomes() {
        homeListener?.remove()
        homeListener = homesCollection().addSnapshotListener { snap, e ->
            if (e != null || snap == null || snap.metadata.hasPendingWrites()) return@addSnapshotListener
            scope.launch {
                for (doc in snap.documents) {
                    val home = Home(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        emoji = doc.getString("emoji") ?: "🏠",
                        sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false
                    )
                    runCatching { Repository.applyRemoteHome(home) }
                }
            }
        }
    }

    fun pushHome(home: Home) {
        if (!isConfigured || FirebaseAuth.getInstance().currentUser == null) return
        runCatching {
            homesCollection().document(home.id).set(
                mapOf(
                    "name" to home.name,
                    "emoji" to home.emoji,
                    "sortOrder" to home.sortOrder,
                    "updatedAt" to home.updatedAt,
                    "deleted" to home.deleted
                )
            )
        }.onFailure { Log.w(TAG, "home push failed", it) }
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
                    "expiryAutoDays" to item.expiryAutoDays,
                    "note" to item.note,
                    "openedDate" to item.openedDate,
                    "openedDays" to item.openedDays,
                    "homeId" to item.homeId,
                    "updatedAt" to item.updatedAt,
                    "deleted" to item.deleted
                )
            )
        }.onFailure { Log.w(TAG, "push failed", it) }
    }
}
