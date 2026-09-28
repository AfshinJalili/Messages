package org.fossify.messages

import android.provider.Telephony
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.helpers.BLOCK_REASON_KEYWORD
import org.fossify.messages.helpers.SearchRepository
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.CONVERSATION_SEARCH_RESULT_ID
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.fossify.messages.models.RecycleBinMessage
import org.fossify.messages.models.SearchMatch
import org.fossify.messages.models.SearchResult
import org.fossify.messages.models.SpamMessage
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxUiState
import org.fossify.messages.ui.search.SearchContent
import org.fossify.messages.ui.search.SearchFilter
import org.fossify.messages.ui.search.SearchInput
import org.fossify.messages.ui.search.SearchUiState
import org.fossify.messages.ui.search.searchExcerpt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Search checks use only synthetic fixtures and a test-only Compose activity. */
@RunWith(AndroidJUnit4::class)
class SearchChecks {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun result(
        id: Long,
        title: String,
        snippet: String,
        threadId: Long = id,
    ) = SearchResult(id, title, snippet, "fixture date", threadId, "")

    private fun fixtureMessage(
        id: Long,
        threadId: Long,
        body: String,
        attachment: MessageAttachment? = null,
        isMms: Boolean = false,
    ) = Message(
        id = id,
        body = body,
        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
        status = -1,
        participants = ArrayList(),
        date = 1,
        read = true,
        threadId = threadId,
        isMMS = isMms,
        attachment = attachment,
        senderPhoneNumber = "fixture-address",
        senderName = "Search fixture",
        senderPhotoUri = "",
        subscriptionId = -1,
    )

    private fun fixtureConversation(threadId: Long, title: String) = Conversation(
        threadId = threadId,
        snippet = "synthetic preview",
        date = 1,
        read = true,
        title = title,
        photoUri = "",
        isGroupConversation = false,
        phoneNumber = "fixture-address-$threadId",
        messageCount = 1,
    )

    private fun fixtureAttachment(id: Long, filename: String, mimetype: String) = MessageAttachment(
        id = id,
        text = "",
        attachments = arrayListOf(Attachment(null, id, "content://fixture/$id", mimetype, 10, 10, filename)),
    )

    /** Fixtures stay in this database; the app singleton and real message cache are never replaced. */
    private fun withInMemorySearchDatabase(block: (MessagesDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(targetContext, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryMatchesWildcardsLiterallyAndExcludesRecycledAndSpamRows() = withInMemorySearchDatabase { database ->
        val query = "wild%card_under"
        val literalBody = "synthetic body with $query token"
        val liveThread = 8_101L
        val decoyThread = 8_102L
        val recycledThread = 8_103L
        val spamThread = 8_104L
        val messages = database.MessagesDao()
        val conversations = database.ConversationsDao()

        messages.insertOrUpdate(fixtureMessage(81_011, liveThread, literalBody))
        conversations.insertOrUpdate(fixtureConversation(liveThread, "Fixture $query"))

        messages.insertOrUpdate(fixtureMessage(81_021, decoyThread, "synthetic wildXcard_under token"))
        conversations.insertOrUpdate(fixtureConversation(decoyThread, "Fixture wildXcard_under"))

        val matchingMmsId = 81_081L
        val irrelevantMmsId = 81_082L
        messages.insertOrUpdate(fixtureMessage(matchingMmsId, 8_108, literalBody, isMms = true))
        messages.insertOrUpdate(fixtureMessage(irrelevantMmsId, 8_109, "synthetic wildXcard_under token", isMms = true))

        val candidates = messages.searchCandidates("%wild\\%card\\_under%", "%wild\\%card\\_under%").map { it.id }.toSet()
        check(matchingMmsId in candidates && irrelevantMmsId !in candidates) {
            "Candidate search must include matching MMS rows and exclude irrelevant MMS rows"
        }

        messages.insertOrUpdate(fixtureMessage(81_031, recycledThread, literalBody))
        messages.insertRecycleBinEntry(RecycleBinMessage(81_031, deletedTS = 1))
        conversations.insertOrUpdate(fixtureConversation(recycledThread, "Fixture $query recycled"))

        messages.insertOrUpdate(fixtureMessage(81_041, spamThread, literalBody))
        messages.insertSpamMarker(SpamMessage(81_041, spamThread, BLOCK_REASON_KEYWORD))
        conversations.insertOrUpdate(fixtureConversation(spamThread, "Fixture $query spam"))

        val photoName = "photo-$query.jpg"
        messages.insertOrUpdate(fixtureMessage(81_051, 8_105, "synthetic caption", fixtureAttachment(81_051, photoName, "image/jpeg")))
        messages.insertOrUpdate(
            fixtureMessage(81_061, 8_106, "synthetic caption", fixtureAttachment(81_061, "photo-wildXcard_under.jpg", "image/jpeg"))
        )
        messages.insertOrUpdate(
            fixtureMessage(81_071, 8_107, "synthetic caption", fixtureAttachment(81_071, "clip-$query.mp4", "video/mp4"))
        )

        val matches = runBlocking { SearchRepository(targetContext, database.MessagesDao(), database.ConversationsDao()).search(query) }
        val people = matches.filter { it.person }
        val messageMatches = matches.filterNot { it.person }
        check(people.map { it.result.threadId } == listOf(liveThread)) {
            "People search must honor literal LIKE wildcards and ignore recycled or spam-only threads"
        }
        check(messageMatches.map { it.result.messageId }.toSet() == setOf(81_011L, matchingMmsId, 81_051L, 81_071L)) {
            "Message search must match literal wildcards and omit decoy, recycled, spam, and irrelevant MMS rows"
        }
        val photo = messageMatches.single { it.result.messageId == 81_051L }
        check(photo.media && photo.photo && photo.result.snippet == photoName) {
            "Attachment filename results must retain their media and photo metadata"
        }
        val video = messageMatches.single { it.result.messageId == 81_071L }
        check(video.media && !video.photo) { "Non-photo attachments must remain media without becoming photos" }

        val mediaEntry = runBlocking { SearchRepository(targetContext, database.MessagesDao(), database.ConversationsDao()).search("") }.filterNot { it.person }
        check(mediaEntry.map { it.result.messageId }.toSet() == setOf(81_051L, 81_061L)) {
            "Blank Photos search must return image attachments and exclude nonimages"
        }
    }

    @Test
    fun attachmentSearchMatchesJsonEncodedSpecialCharactersInFilenames() = withInMemorySearchDatabase { database ->
        val fixtures = listOf(
            "\"" to (82_001L to "fixture quoted \" filename.pdf"),
            "\\" to (82_002L to "fixture back\\slash filename.pdf"),
            "<&>" to (82_003L to "fixture <&> filename.pdf"),
        )
        fixtures.forEach { (_, fixture) ->
            val (id, filename) = fixture
            database.MessagesDao().insertOrUpdate(
                fixtureMessage(id, id, "unmatched message body", fixtureAttachment(id, filename, "application/pdf"))
            )
        }
        val repository = SearchRepository(targetContext, database.MessagesDao(), database.ConversationsDao())

        fixtures.forEach { (query, fixture) ->
            val (id, filename) = fixture
            val match = runBlocking { repository.search(query) }.single { !it.person }
            check(match.result.messageId == id && match.result.snippet == filename) {
                "Attachment search must match the decoded filename for query ${query.toCharArray().contentToString()}"
            }
        }
    }

    @Test
    fun filtersSeparatePeopleMessagesMediaAndEntryPhotos() {
        val person = SearchMatch(result(CONVERSATION_SEARCH_RESULT_ID, "Fixture person", "fixture-address", threadId = 9_001))
        val textMessage = SearchMatch(result(9_002, "Fixture text", "synthetic body"))
        val photo = SearchMatch(result(9_003, "Fixture photo", "photo name"), media = true, photo = true)
        val video = SearchMatch(result(9_004, "Fixture video", "video name"), media = true)
        val matches = listOf(person, textMessage, photo, video)

        check(SearchUiState(query = "fixture", filter = SearchFilter.PEOPLE, matches = matches).visibleMatches == listOf(person))
        check(
            SearchUiState(query = "fixture", filter = SearchFilter.MESSAGES, matches = matches).visibleMatches ==
                listOf(textMessage, photo, video)
        )
        check(SearchUiState(query = "fixture", filter = SearchFilter.MEDIA, matches = matches).visibleMatches == listOf(photo, video))
        check(SearchUiState(filter = SearchFilter.MEDIA, matches = matches).visibleMatches == listOf(photo)) {
            "The entry media filter is photo-only"
        }
    }

    @Test
    fun longExcerptKeepsContextBeforeTheLiteralMatch() {
        val snippet = "before ".repeat(12) + "Needle" + " after".repeat(12)
        val matchIndex = snippet.indexOf("Needle")
        val expected = "…" + snippet.substring(matchIndex - 40)

        check(searchExcerpt(snippet, " needle ") == expected)
        check(searchExcerpt("needle begins here", "needle") == "needle begins here")
    }

    @Test
    fun historyShowsRecentQueriesAndReportsTheSelectedQuery() {
        var selectedQuery: String? = null
        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchContent(
                    state = SearchUiState(recent = listOf("synthetic recent query")),
                    onQuery = { selectedQuery = it },
                    onFilter = {},
                    onOpen = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.recent_searches)).assertExists()
        compose.onNodeWithText("synthetic recent query").performClick()
        check(selectedQuery == "synthetic recent query")
    }

    @Test
    fun emptyResultsShowTheQueryAndNoMatchesState() {
        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchContent(
                    state = SearchUiState(query = "synthetic absent term"),
                    onQuery = {},
                    onFilter = {},
                    onOpen = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.search_no_matches)).assertExists()
        compose.onNodeWithText("synthetic absent term", substring = true).assertExists()
    }

    @Test
    fun peopleAndPhotoFilterChipsInvokeCallbacksAndFilterResults() {
        val person = SearchMatch(result(CONVERSATION_SEARCH_RESULT_ID, "Fixture person", "fixture-address", threadId = 9_101))
        val textMessage = SearchMatch(result(9_102, "Fixture text", "synthetic body"))
        val photo = SearchMatch(result(9_103, "Fixture photo", "photo name"), media = true, photo = true)
        val video = SearchMatch(result(9_104, "Fixture video", "video name"), media = true)
        val matches = listOf(person, textMessage, photo, video)
        var state by mutableStateOf(SearchUiState(matches = matches))
        val filters = mutableListOf<SearchFilter>()

        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchContent(
                    state = state,
                    onQuery = {},
                    onFilter = { filter ->
                        filters += filter
                        state = state.copy(filter = filter)
                    },
                    onOpen = {},
                    onRetry = {},
                )
            }
        }

        compose.onNode(hasText(text(SearchFilter.PEOPLE.label)) and hasClickAction()).performClick()
        compose.onNodeWithText("Fixture person").assertExists()
        compose.onNodeWithText("Fixture photo").assertDoesNotExist()
        check(filters.lastOrNull() == SearchFilter.PEOPLE)

        compose.onNode(hasText(text(R.string.search_photos)) and hasClickAction()).performClick()
        compose.onNodeWithText("Fixture photo").assertExists()
        compose.onNodeWithText("Fixture video").assertDoesNotExist()
        compose.onNodeWithText("Fixture person").assertDoesNotExist()
        check(filters.lastOrNull() == SearchFilter.MEDIA)
    }

    @Test
    fun resultRowsHighlightLongExcerptsAndOpenTheSelectedResult() {
        val query = "needle"
        val snippet = "context ".repeat(10) + "NEEDLE" + " after".repeat(10)
        val searchResult = result(9_201, "Fixture contact", snippet, threadId = 9_202)
        var opened: SearchResult? = null

        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchContent(
                    state = SearchUiState(query = query, matches = listOf(SearchMatch(searchResult))),
                    onQuery = {},
                    onFilter = {},
                    onOpen = { opened = it },
                    onRetry = {},
                )
            }
        }

        val excerpt = searchExcerpt(snippet, query)
        val textNode = compose.onNodeWithText(excerpt, useUnmergedTree = true).fetchSemanticsNode()
        val annotated = textNode.config[SemanticsProperties.Text].single()
        val highlightStart = excerpt.indexOf("NEEDLE", ignoreCase = true)
        check(
            annotated.spanStyles.any { range ->
                range.start <= highlightStart && range.end >= highlightStart + query.length &&
                    range.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
            }
        ) { "The matching text must be highlighted in the long excerpt" }

        compose.onNodeWithText("Fixture contact").performClick()
        check(opened == searchResult)
    }

    @Test
    fun existingResultsStayVisibleWhileLoadingAndAfterRefreshFails() {
        val match = SearchMatch(result(9_301, "Fixture retained contact", "needle remains visible"))
        var state by mutableStateOf(SearchUiState(query = "needle", matches = listOf(match)))
        var retryCount = 0

        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchContent(
                    state = state,
                    onQuery = {},
                    onFilter = {},
                    onOpen = {},
                    onRetry = { retryCount++ },
                )
            }
        }

        state = state.copy(loading = true)
        compose.waitForIdle()
        compose.onNodeWithText("Fixture retained contact").assertExists()

        state = state.copy(loading = false, failed = true)
        compose.waitForIdle()
        compose.onNodeWithText("Fixture retained contact").assertExists()
        compose.onNodeWithText(text(R.string.search_error)).assertExists()
        compose.onNodeWithText(text(R.string.search_retry)).performClick()
        check(retryCount == 1)
    }

    @Test
    fun scrollingSearchDoesNotCollapseTheInboxHeader() {
        val recent = (1..40).map { "synthetic recent query $it" }
        var search by mutableStateOf(SearchUiState())

        compose.setContent {
            OpenLineTheme(dark = false) {
                InboxScreen(
                    state = InboxUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpen = {},
                    onToggleSelection = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onAction = {},
                    onSwipe = { _, _ -> },
                    onFilter = {},
                    onSearch = { search = SearchUiState(open = true, recent = recent) },
                    onNewMessage = {},
                    onLibrary = {},
                    onSettings = {},
                    search = search,
                    onSearchBack = { search = search.copy(open = false) },
                )
            }
        }

        val title = compose.onNodeWithText(text(R.string.messages)).getUnclippedBoundsInRoot().top
        compose.onNodeWithText(text(R.string.inbox_search_hint)).performClick()
        compose.onNodeWithText(recent.first()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.search_back)).performClick()
        compose.waitForIdle()

        val titleAfterSearch = compose.onNodeWithText(text(R.string.messages)).getUnclippedBoundsInRoot().top
        check(kotlin.math.abs((titleAfterSearch - title).value) < 1f) {
            "Scrolling search results must not change the inbox header's collapsed state"
        }
    }

    @Test
    fun clearButtonReportsAnEmptyQuery() {
        var query by mutableStateOf("synthetic clear query")
        var reportedQuery: String? = null
        compose.setContent {
            OpenLineTheme(dark = false) {
                SearchInput(
                    query = query,
                    onQuery = {
                        query = it
                        reportedQuery = it
                    },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.search_clear)).performClick()
        compose.waitForIdle()
        check(query.isEmpty() && reportedQuery == "")
    }
}
