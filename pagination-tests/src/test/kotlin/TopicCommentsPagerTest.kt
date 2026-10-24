package dev.ragnarok.fenrir.fragment.comments

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException

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

    @Test fun apiFailureDoesNotCommitOffset() = runBlocking {
        val pager = TopicCommentsPager()
        pager.initial("asc", 2, 4, false)
        val offsets = mutableListOf<Int>()
        try {
            pager.load("asc", setOf(10, 20), { it: Int -> it }) { offset ->
                offsets.add(offset)
                throw IOException("VK temporary failure")
            }
            fail("Must propagate the API error")
        } catch (_: IOException) { }
        val result = pager.load("asc", setOf(10, 20), { it: Int -> it }) { offset ->
            offsets.add(offset)
            TopicCommentsPager.Page(listOf(50, 90), 4)
        }
        assertEquals(listOf(2, 2), offsets)
        assertEquals(listOf(50, 90), result.items)
        assertFalse(pager.hasMore("asc"))
    }

    @Test fun mockVkLoadsMultiplePagesInBothDirections() = runBlocking {
        for (sort in listOf("asc", "desc")) {
            val source = if (sort == "asc") listOf(3, 10, 30, 80, 900) else listOf(900, 80, 30, 10, 3)
            val pager = TopicCommentsPager()
            val loaded = mutableSetOf<Int>()
            val offsets = mutableListOf<Int>()
            while (pager.hasMore(sort)) {
                val page = pager.load(sort, loaded, { it: Int -> it }) { offset ->
                    offsets.add(offset)
                    TopicCommentsPager.Page(source.drop(offset).take(2), source.size)
                }
                loaded.addAll(page.items)
            }
            assertEquals(source.toSet(), loaded)
            assertEquals(listOf(0, 2, 4), offsets)
        }
    }

    @Test fun focusedOlderLoadSkipsNewerPagesWithoutShowingWrongSide() = runBlocking {
        val pager = TopicCommentsPager()
        pager.initial("asc", 2, 6, true)
        val offsets = mutableListOf<Int>()
        val page = pager.load("desc", setOf(70, 80), { it: Int -> it }, { it < 70 }) { offset ->
            offsets.add(offset)
            TopicCommentsPager.Page(listOf(100, 90, 80, 70, 60, 40).drop(offset).take(2), 6)
        }
        assertEquals(listOf(0, 2, 4), offsets)
        assertEquals(listOf(60, 40), page.items)
    }

    @Test fun duplicateOnlyPagesAreConsumedInOneLoad() = runBlocking {
        val pager = TopicCommentsPager()
        val offsets = mutableListOf<Int>()
        val page = pager.load("asc", setOf(10, 20), { it: Int -> it }) { offset ->
            offsets.add(offset)
            TopicCommentsPager.Page(if (offset == 0) listOf(10, 20) else listOf(30, 40), 4)
        }
        assertEquals(listOf(0, 2), offsets)
        assertEquals(listOf(30, 40), page.items)
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
