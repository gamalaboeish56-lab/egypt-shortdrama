package egypt.shortdrama.top

/** باقات الشحن حسب ملف المتطلبات. لازم تتطابق مع firestore.rules و admin/app.js */
data class Plan(
    val id: String,
    val name: String,
    val priceEgp: Int,
    val coins: Int,
    val bonus: Int,
    val badge: String,
) {
    val totalCoins get() = coins + bonus
}

object Plans {
    val all = listOf(
        Plan("basic", "المبتدئ", 50, 100, 0, "مبتدئ"),
        Plan("pro", "المحترف", 100, 250, 50, "محترف"),
        Plan("star", "النجم", 200, 600, 0, "نجم"),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    /** الموقع بيعرض الباقات بالاسم ("اشترك في المحترف") */
    fun byName(text: String?): Plan? {
        if (text.isNullOrBlank()) return null
        return all.firstOrNull { text.contains(it.name) }
    }
}
