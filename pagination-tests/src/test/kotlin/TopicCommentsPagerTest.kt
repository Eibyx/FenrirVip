package dev.ragnarok.fenrir.fragment.comments

import org.junit.Assert.*
import org.junit.Test

class TopicCommentsPagerTest {
    @Test fun repeatedCursorAndDuplicatesPreserveAllOtherIds() {
        val pager = TopicCommentsPager()
        assertEquals(listOf(90, 151), pager.fresh(listOf(10, 90, 90, 151), setOf(10)) { it })
        pager.initial("asc", 4, 9, false)
        assertEquals(4, pager.offset("asc")) // not two deduplicated rows
    }

    @Test fun overlappingFocusPagesCanAdvanceWithoutNewRows() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 2, 6, true)
        assertTrue(pager.fresh(listOf(10, 30), setOf(10, 30)) { it }.isEmpty())
        pager.received("asc", 2, 6)
        assertEquals(2, pager.offset("asc"))
        assertTrue(pager.hasMore("asc"))
        assertEquals(listOf(50, 80), pager.fresh(listOf(50, 80), setOf(10, 30)) { it })
    }

    @Test fun initialPageAndSecondPage() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 20, 45, false)
        assertEquals(20, pager.offset("asc"))
        assertTrue(pager.hasMore("asc"))
        assertFalse(pager.hasMore("desc"))
        pager.received("asc", 20, 45)
        assertEquals(40, pager.offset("asc"))
    }

    @Test fun threePagesAndRealEndInBothOrders() {
        for (sort in listOf("asc", "desc")) {
            val pager = TopicCommentsPager()
            pager.initial(sort, 20, 45, false)
            pager.received(sort, 20, 45)
            pager.received(sort, 5, 45)
            assertEquals(45, pager.offset(sort))
            assertFalse(pager.hasMore(sort))
        }
    }

    @Test fun serverIgnoringOffsetIsAnErrorNotFalseEndOfList() {
        val pager = TopicCommentsPager()
        pager.received("asc", 2, 4, listOf(10, 50))
        assertThrows(IllegalStateException::class.java) {
            pager.received("asc", 2, 4, listOf(10, 50))
        }
        assertEquals(2, pager.offset("asc"))
        assertTrue(pager.hasMore("asc"))
        pager.received("asc", 2, 4, listOf(80, 100))
        assertFalse(pager.hasMore("asc"))
    }

    @Test fun emptyTopic() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 0, 0, false)
        assertFalse(pager.hasMore("asc"))
        assertFalse(pager.hasMore("desc"))
    }

    @Test fun emptyBeforeEndIsRetryable() {
        val pager = TopicCommentsPager()
        pager.initial("desc", 20, 30, false)
        assertThrows(IllegalStateException::class.java) { pager.received("desc", 0, 30) }
        assertEquals(20, pager.offset("desc"))
        assertTrue(pager.hasMore("desc"))
        pager.received("desc", 10, 30)
        assertFalse(pager.hasMore("desc"))
    }

    @Test fun apiFailureDoesNotCommitOffset() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 20, 60, false)
        // Failed requests never call received (the production Flow propagates the exception).
        assertEquals(20, pager.offset("asc"))
        pager.received("asc", 20, 60)
        assertEquals(40, pager.offset("asc"))
    }

    @Test fun focusScansFromBothEndsWithoutCursor() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 20, 120, true)
        assertEquals(0, pager.offset("asc"))
        assertEquals(0, pager.offset("desc"))
        pager.received("asc", 20, 120)
        assertEquals(0, pager.offset("desc"))
    }

    @Test fun shortPageIsNotEndWhileTotalHasMore() {
        val pager = TopicCommentsPager()
        pager.initial("asc", 7, 55, false)
        assertTrue(pager.hasMore("asc"))
        assertEquals(7, pager.offset("asc"))
    }

    @Test fun resetClearsExhaustionAndOffsets() {
        val pager = TopicCommentsPager()
        pager.initial("desc", 1, 1, false)
        pager.reset()
        assertTrue(pager.hasMore("desc"))
        assertEquals(0, pager.offset("desc"))
    }

    @Test fun deletedNonconsecutiveIdsDoNotDefineOffset() {
        val ids = listOf(10, 90, 151, 999)
        val pager = TopicCommentsPager()
        pager.initial("asc", ids.size, 6, false)
        assertEquals(4, pager.offset("asc"))
        pager.received("asc", 2, 6)
        assertFalse(pager.hasMore("asc"))
    }
}
