package com.gtarayici.browser.ui.feedback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ekranda gösterilecek metin: kaynak kimliği (çevrilebilir) veya hazır metin. */
sealed interface UiText {
    data class Res(val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            // Bağımsız değişken olarak verilen UiText'ler de çözülür (ör. "<başlık>. <ne yapılmalı>").
            context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
        }
        is Plain -> text
    }

    companion object {
        fun res(id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** Mesajın türü: simgeyi ve rengi belirler. */
enum class MessageKind { Success, Info, Warning, Error, Hint }

class MessageAction(val label: UiText, val onClick: () -> Unit)

/**
 * Uygulama genelinde tek bir mesaj. [key] aynı olan mesajlar kuyrukta birleştirilir.
 * [long] uzun gösterim (hata, eylemli mesaj) içindir.
 */
class Message(
    val text: UiText,
    val kind: MessageKind = MessageKind.Info,
    val action: MessageAction? = null,
    val long: Boolean = action != null || kind == MessageKind.Error,
    val key: Any = text,
) {
    /** Her mesajın benzersiz sırası: aynı metin ikinci kez gösterildiğinde de zamanlayıcı yeniden başlar. */
    internal var id: Long = 0
}

/**
 * Tek mesaj kanalı. Ekrandan bağımsızdır (servisler ve yöneticiler de kullanır); aynı anda yalnızca kuyruğun ilk
 * mesajı gösterilir, gösterilen veya bekleyen bir mesajla aynı anahtara sahip yeni mesaj eklenmez (tekrar birleşir).
 */
class Messenger {
    private val queue = MutableStateFlow<List<Message>>(emptyList())
    private var nextId = 1L

    /** Mesaj kuyruğu; ekranda yalnızca ilk eleman gösterilir. */
    val current: StateFlow<List<Message>> = queue.asStateFlow()

    fun show(message: Message) {
        queue.update { list ->
            if (list.any { it.key == message.key }) list
            else list + message.also { it.id = nextId++ }
        }
    }

    fun success(text: UiText, action: MessageAction? = null) = show(Message(text, MessageKind.Success, action))
    fun info(text: UiText, action: MessageAction? = null) = show(Message(text, MessageKind.Info, action))
    fun warning(text: UiText, action: MessageAction? = null) = show(Message(text, MessageKind.Warning, action))
    fun error(text: UiText, action: MessageAction? = null) = show(Message(text, MessageKind.Error, action))

    /**
     * Geri alınabilir bir silme sonrası "Geri al" mesajı. Aynı [key] ile gelen yeni silme öncekinin yerini alır
     * (yalnızca son silme geri alınabilir; mesajlar kuyrukta birikmez).
     */
    fun undoable(text: UiText, key: Any, onUndo: () -> Unit) {
        dismissKey(key)
        show(Message(text, MessageKind.Info, MessageAction(UiText.res(com.gtarayici.browser.R.string.action_undo), onUndo), key = key))
    }

    /** Gösterilen mesajı kapatır (süre doldu, eylem seçildi veya kaydırıldı); sıradaki gelir. */
    fun dismiss(message: Message) {
        queue.update { list -> list.filterNot { it === message } }
    }

    /** Belirli bir anahtardaki mesajı (ör. artık geçerli olmayan "Geri al") kaldırır. */
    fun dismissKey(key: Any) {
        queue.update { list -> list.filterNot { it.key == key } }
    }

    companion object {
        const val SHORT_MS = 4_000L
        const val LONG_MS = 8_000L
    }
}
