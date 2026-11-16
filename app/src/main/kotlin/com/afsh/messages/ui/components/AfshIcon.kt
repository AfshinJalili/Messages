package com.afsh.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import org.fossify.commons.R

/**
 * Every interface icon by meaning, drawn from Material Symbols (Rounded, weight 400, grade 0, optical size 24). Call
 * sites ask for a meaning, never a raw vector. Arrows and chevrons mirror under RTL. Icons with a filled variant (pin,
 * star, bottom nav destinations) use FILL 1 for selected/active states through [painter]; Unpin uses keep_off and Unstar uses StarFilled. Drawables live in commons.
 * `tests/check_material_symbols.py` keeps every entry backed by a shipped symbol; `tools/export_material_symbols.py`
 * writes the symbols.
 */
enum class AfshIcon(@DrawableRes val resource: Int, @DrawableRes val filledResource: Int = 0) {
    Camera(R.drawable.ic_msym_photo_camera),
    Video(R.drawable.ic_msym_video_library),
    RecordVideo(R.drawable.ic_msym_videocam),
    Microphone(R.drawable.ic_msym_mic),
    ScheduleSend(R.drawable.ic_msym_schedule_send),
    SendArrow(R.drawable.ic_msym_arrow_outward),
    ArrowDown(R.drawable.ic_msym_arrow_downward),
    Archive(R.drawable.ic_msym_archive),
    ArrowBack(R.drawable.ic_msym_arrow_back),
    ArrowForward(R.drawable.ic_msym_arrow_forward),
    Block(R.drawable.ic_msym_block),
    Notifications(R.drawable.ic_msym_notifications),
    NotificationsOff(R.drawable.ic_msym_notifications_off),
    Calendar(R.drawable.ic_msym_calendar_month),
    SimCard(R.drawable.ic_msym_sim_card),
    Check(R.drawable.ic_msym_check),
    CheckAll(R.drawable.ic_msym_done_all),
    ChevronForward(R.drawable.ic_msym_chevron_right),
    ChevronBack(R.drawable.ic_msym_chevron_left),
    ChevronUp(R.drawable.ic_msym_keyboard_arrow_up),
    Verified(R.drawable.ic_msym_check_circle),
    Help(R.drawable.ic_msym_help),
    Schedule(R.drawable.ic_msym_schedule),
    CloudOff(R.drawable.ic_msym_cloud_off),
    Contact(R.drawable.ic_msym_contacts),
    Copy(R.drawable.ic_msym_content_copy),
    Return(R.drawable.ic_msym_keyboard_return),
    Backup(R.drawable.ic_msym_backup),
    Download(R.drawable.ic_msym_download),
    More(R.drawable.ic_msym_more_vert),
    OpenExternal(R.drawable.ic_msym_open_in_new),
    Visible(R.drawable.ic_msym_visibility),
    Hidden(R.drawable.ic_msym_visibility_off),
    Document(R.drawable.ic_msym_description),
    Forward(R.drawable.ic_msym_forward),
    Stop(R.drawable.ic_msym_pan_tool),
    Number(R.drawable.ic_msym_tag),
    Image(R.drawable.ic_msym_image),
    Inbox(R.drawable.ic_msym_inbox),
    Info(R.drawable.ic_msym_info),
    Language(R.drawable.ic_msym_translate),
    Layers(R.drawable.ic_msym_layers, R.drawable.ic_msym_layers_fill),
    Checklist(R.drawable.ic_msym_checklist),
    Lock(R.drawable.ic_msym_lock),
    Mail(R.drawable.ic_msym_mail),
    MailOpen(R.drawable.ic_msym_drafts),
    Chat(R.drawable.ic_msym_chat_bubble),
    Message(R.drawable.ic_msym_chat),
    Conversations(R.drawable.ic_msym_forum, R.drawable.ic_msym_forum_fill),
    Attach(R.drawable.ic_msym_attach_file),
    Edit(R.drawable.ic_msym_edit),
    Call(R.drawable.ic_msym_call),
    Pin(R.drawable.ic_msym_keep, R.drawable.ic_msym_keep_fill),
    Unpin(R.drawable.ic_msym_keep_off),
    Add(R.drawable.ic_msym_add),
    Refresh(R.drawable.ic_msym_refresh),
    Restore(R.drawable.ic_msym_rotate_left),
    Search(R.drawable.ic_msym_search),
    Send(R.drawable.ic_msym_send),
    Settings(R.drawable.ic_msym_settings, R.drawable.ic_msym_settings_fill),
    Share(R.drawable.ic_msym_share),
    SpamShield(R.drawable.ic_msym_gpp_bad),
    Star(R.drawable.ic_msym_star, R.drawable.ic_msym_star_fill),
    StarFilled(R.drawable.ic_msym_star_fill),
    TextCursor(R.drawable.ic_msym_text_fields),
    Delete(R.drawable.ic_msym_delete),
    Warning(R.drawable.ic_msym_warning),
    TextSize(R.drawable.ic_msym_title),
    Upload(R.drawable.ic_msym_upload),
    Person(R.drawable.ic_msym_person),
    PersonAdd(R.drawable.ic_msym_person_add),
    People(R.drawable.ic_msym_group),
    Volume(R.drawable.ic_msym_volume_up),
    Close(R.drawable.ic_msym_close),
    Play(R.drawable.ic_msym_play_circle),
    AudioFile(R.drawable.ic_msym_audio_file),
    Pdf(R.drawable.ic_msym_picture_as_pdf),
    ZipFile(R.drawable.ic_msym_folder_zip),
    DragHandle(R.drawable.ic_msym_drag_handle),
    RemoveCircle(R.drawable.ic_msym_do_not_disturb_on),
    Donate(R.drawable.ic_msym_volunteer_activism);

    /**
     * The icon painter. Pass `filled = true` to request the filled variant (FILL 1) for selected/active states; the
     * default `false` returns the outline (FILL 0). Icons without a filled resource ignore the parameter.
     */
    @Composable
    fun painter(filled: Boolean = false): Painter =
        painterResource(if (filled && filledResource != 0) filledResource else resource)
}
