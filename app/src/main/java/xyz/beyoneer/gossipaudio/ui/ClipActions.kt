package xyz.beyoneer.gossipaudio.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons

/** Shared row-based action sheet used by the library and the player. */
object ClipActions {

    fun show(activity: MainActivity, clip: Clip, onChanged: () -> Unit) {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val playingThis = PlaybackHub.state.clipId == clip.id && PlaybackHub.state.playing
        content.addView(
            row(activity, if (playingThis) Icons.Kind.PAUSE else Icons.Kind.PLAY, if (playingThis) "Pause" else "Play") {
                if (playingThis) {
                    PlaybackHub.pause()
                } else {
                    val file = activity.app.store.fileFor(clip)
                    if (!file.exists()) {
                        activity.toast("Audio file missing.")
                    } else {
                        PlaybackHub.playFile(file, clip.id, clip.title, activity.app.store.coverFor(clip))
                    }
                }
                onChanged()
            }
        )
        content.addView(row(activity, Icons.Kind.HEART, if (clip.liked) "Remove from favourites" else "Add to favourites") {
            activity.app.store.toggleLike(clip.id)
            onChanged()
            activity.toast(if (clip.liked) "Removed from favourites" else "Liked \u2764\uFE0F")
        })
        content.addView(row(activity, Icons.Kind.DOWNLOAD, "Download (WAV / MP3 / M4A)") {
            ExportFlow.open(activity, clip)
        })
        content.addView(row(activity, Icons.Kind.SHARE, "Share as MP3") {
            ExportFlow.quickShare(activity, clip)
        })
        content.addView(row(activity, Icons.Kind.EDIT, "Rename") {
            Overlays.prompt(activity, "Rename gossip", clip.title) { newName ->
                if (newName.isNotBlank()) {
                    activity.app.store.rename(clip.id, newName)
                    onChanged()
                    activity.toast("Renamed.")
                }
            }
        })
        content.addView(row(activity, Icons.Kind.TRASH, "Delete", danger = true) {
            Overlays.confirm(
                activity,
                "Delete this gossip?",
                "\"${clip.title}\" will be removed from this device. The download copies in Downloads stay where they are.",
                "Delete",
                danger = true
            ) {
                if (PlaybackHub.state.clipId == clip.id) PlaybackHub.stop()
                activity.app.store.delete(clip.id)
                onChanged()
                activity.toast("Deleted. The tea is gone \uD83E\uDDE8")
            }
        })
        Overlays.sheet(activity, clip.title, content)
    }

    private fun row(activity: MainActivity, icon: Icons.Kind, label: String, danger: Boolean = false, action: () -> Unit): View {
        val palette = activity.palette
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 14f), Ui.dp(activity, 10f), Ui.dp(activity, 14f))
            background = Ui.rounded(Ui.withAlpha(if (danger) palette.danger else palette.text, 0.045f), Ui.dpf(activity, 16f))
            isClickable = true
        }
        row.addView(
            IconView(activity, icon, if (danger) palette.danger else palette.text, 19f),
            LinearLayout.LayoutParams(Ui.dp(activity, 34f), Ui.dp(activity, 20f))
        )
        row.addView(
            Ui.text(activity, label, 14.5f, if (danger) palette.danger else palette.text, bold = true, font = "sans-serif-medium"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            IconView(activity, Icons.Kind.NEXT, palette.textDim, 14f),
            LinearLayout.LayoutParams(Ui.dp(activity, 16f), Ui.dp(activity, 16f))
        )
        Ui.pressable(row, 0.985f) {
            Overlays.dismissTop(activity)
            action()
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = Ui.dp(activity, 6f)
        row.layoutParams = params
        return row
    }
}
