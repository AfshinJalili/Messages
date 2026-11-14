package com.afsh.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.afsh.messages.R

/** Which vector set draws [AfshIcon]s. */
enum class IconSet { Lucide, MaterialSymbols }

/** Lucide until the redesign switches it; one provider at the theme root changes every icon. */
val LocalIconSet = staticCompositionLocalOf { IconSet.Lucide }

/**
 * Every interface icon by meaning, with its Lucide drawable and the Material Symbols (Outlined, 400) one that replaces
 * it. Call sites ask for a meaning, never a raw vector. Arrows and chevrons mirror under RTL in both sets.
 * `tests/check_material_symbols.py` keeps the two sets paired; `tools/export_material_symbols.py` writes the symbols.
 */
enum class AfshIcon(@DrawableRes private val lucide: Int, @DrawableRes private val symbol: Int) {
    Archive(R.drawable.ic_lucide_archive, R.drawable.ic_msym_archive),
    ArrowBack(R.drawable.ic_lucide_arrow_left, R.drawable.ic_msym_arrow_back),
    ArrowForward(R.drawable.ic_lucide_arrow_right, R.drawable.ic_msym_arrow_forward),
    Block(R.drawable.ic_lucide_ban, R.drawable.ic_msym_block),
    Notifications(R.drawable.ic_lucide_bell, R.drawable.ic_msym_notifications),
    NotificationsOff(R.drawable.ic_lucide_bell_off, R.drawable.ic_msym_notifications_off),
    Calendar(R.drawable.ic_lucide_calendar, R.drawable.ic_msym_calendar_month),
    SimCard(R.drawable.ic_lucide_card_sim, R.drawable.ic_msym_sim_card),
    Check(R.drawable.ic_lucide_check, R.drawable.ic_msym_check),
    CheckAll(R.drawable.ic_lucide_check_check, R.drawable.ic_msym_done_all),
    ChevronForward(R.drawable.ic_lucide_chevron_right, R.drawable.ic_msym_chevron_right),
    ChevronUp(R.drawable.ic_lucide_chevron_up, R.drawable.ic_msym_keyboard_arrow_up),
    Verified(R.drawable.ic_lucide_circle_check, R.drawable.ic_msym_check_circle),
    Help(R.drawable.ic_lucide_circle_help, R.drawable.ic_msym_help),
    Schedule(R.drawable.ic_lucide_clock, R.drawable.ic_msym_schedule),
    CloudOff(R.drawable.ic_lucide_cloud_off, R.drawable.ic_msym_cloud_off),
    Contact(R.drawable.ic_lucide_contact, R.drawable.ic_msym_contacts),
    Copy(R.drawable.ic_lucide_copy, R.drawable.ic_msym_content_copy),
    Return(R.drawable.ic_lucide_corner_down_left, R.drawable.ic_msym_keyboard_return),
    Backup(R.drawable.ic_lucide_database_backup, R.drawable.ic_msym_backup),
    Download(R.drawable.ic_lucide_download, R.drawable.ic_msym_download),
    More(R.drawable.ic_lucide_ellipsis_vertical, R.drawable.ic_msym_more_vert),
    OpenExternal(R.drawable.ic_lucide_external_link, R.drawable.ic_msym_open_in_new),
    Visible(R.drawable.ic_lucide_eye, R.drawable.ic_msym_visibility),
    Hidden(R.drawable.ic_lucide_eye_off, R.drawable.ic_msym_visibility_off),
    Document(R.drawable.ic_lucide_file_text, R.drawable.ic_msym_description),
    Forward(R.drawable.ic_lucide_forward, R.drawable.ic_msym_forward),
    Stop(R.drawable.ic_lucide_hand, R.drawable.ic_msym_pan_tool),
    Number(R.drawable.ic_lucide_hash, R.drawable.ic_msym_tag),
    Image(R.drawable.ic_lucide_image, R.drawable.ic_msym_image),
    Inbox(R.drawable.ic_lucide_inbox, R.drawable.ic_msym_inbox),
    Info(R.drawable.ic_lucide_info, R.drawable.ic_msym_info),
    Language(R.drawable.ic_lucide_languages, R.drawable.ic_msym_translate),
    Layers(R.drawable.ic_lucide_layers, R.drawable.ic_msym_layers),
    Checklist(R.drawable.ic_lucide_list_checks, R.drawable.ic_msym_checklist),
    Lock(R.drawable.ic_lucide_lock_keyhole, R.drawable.ic_msym_lock),
    Mail(R.drawable.ic_lucide_mail, R.drawable.ic_msym_mail),
    MailOpen(R.drawable.ic_lucide_mail_open, R.drawable.ic_msym_drafts),
    Chat(R.drawable.ic_lucide_message_circle, R.drawable.ic_msym_chat_bubble),
    Message(R.drawable.ic_lucide_message_square_text, R.drawable.ic_msym_chat),
    Conversations(R.drawable.ic_lucide_messages_square, R.drawable.ic_msym_forum),
    Attach(R.drawable.ic_lucide_paperclip, R.drawable.ic_msym_attach_file),
    Edit(R.drawable.ic_lucide_pencil, R.drawable.ic_msym_edit),
    Call(R.drawable.ic_lucide_phone, R.drawable.ic_msym_call),
    Pin(R.drawable.ic_lucide_pin, R.drawable.ic_msym_keep),
    Unpin(R.drawable.ic_lucide_pin_off, R.drawable.ic_msym_keep_off),
    Add(R.drawable.ic_lucide_plus, R.drawable.ic_msym_add),
    Refresh(R.drawable.ic_lucide_refresh_cw, R.drawable.ic_msym_refresh),
    Restore(R.drawable.ic_lucide_rotate_ccw, R.drawable.ic_msym_rotate_left),
    Search(R.drawable.ic_lucide_search, R.drawable.ic_msym_search),
    Send(R.drawable.ic_lucide_send, R.drawable.ic_msym_send),
    Settings(R.drawable.ic_lucide_settings, R.drawable.ic_msym_settings),
    Share(R.drawable.ic_lucide_share_2, R.drawable.ic_msym_share),
    SpamShield(R.drawable.ic_lucide_shield_ban, R.drawable.ic_msym_gpp_bad),
    Star(R.drawable.ic_lucide_star, R.drawable.ic_msym_star),
    StarFilled(R.drawable.ic_lucide_star_filled, R.drawable.ic_msym_star_filled),
    TextCursor(R.drawable.ic_lucide_text_cursor, R.drawable.ic_msym_text_fields),
    Delete(R.drawable.ic_lucide_trash_2, R.drawable.ic_msym_delete),
    Warning(R.drawable.ic_lucide_triangle_alert, R.drawable.ic_msym_warning),
    TextSize(R.drawable.ic_lucide_type, R.drawable.ic_msym_title),
    Upload(R.drawable.ic_lucide_upload, R.drawable.ic_msym_upload),
    Person(R.drawable.ic_lucide_user, R.drawable.ic_msym_person),
    PersonAdd(R.drawable.ic_lucide_user_plus, R.drawable.ic_msym_person_add),
    People(R.drawable.ic_lucide_users, R.drawable.ic_msym_group),
    Volume(R.drawable.ic_lucide_volume_2, R.drawable.ic_msym_volume_up),
    Close(R.drawable.ic_lucide_x, R.drawable.ic_msym_close);

    @DrawableRes
    fun resource(set: IconSet): Int = if (set == IconSet.Lucide) lucide else symbol

    @Composable
    fun painter(): Painter = painterResource(resource(LocalIconSet.current))
}
