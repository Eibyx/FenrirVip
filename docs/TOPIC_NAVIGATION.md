# Direct topic navigation

Fenrir requests 20 raw board comments per portion (`TopicCommentsPager.PAGE_SIZE`).
Page numbers are one-based: `(page - 1) * 20`. Offsets are zero-based in the
selected API sort order, not comment IDs or deduplicated row counts. Descending
offset zero means newest comments. The dialog never downloads preceding pages.

The official VKCOM/vk-api-schema board.getComments schema specifies count default
20, maximum 100, asc/desc chronological ordering, offset for a subset, and an
optional start_comment_id. Direct navigation passes offset/count/sort and omits
start_comment_id. response.count supplies the total, not the number of returned
items. Web UI pagination settings/order have not been verified in a live VK
session: a web URL offset is not claimed to identify the same comments unless
its ordering matches the API ordering. No undocumented remote offset maximum
is assumed; the Kotlin API accepts a nonnegative Int and API errors remain visible.
Source: https://github.com/VKCOM/vk-api-schema/blob/master/board/methods.json

Successful jumps replace the displayed window and initialize independent raw
cursors for both directions. The opposite cursor is total - startingOffset;
the forward cursor is startingOffset + rawCount. Existing boundary checks and
ID deduplication remain active. Errors/out-of-range destinations preserve the
previous window. Changing order resets the window and pending generation.
The subtitle describes the starting page/offset of the jumped window; expanding
it with LOAD MORE does not pretend to track the viewport's scroll position.

Tests use mock requests and coroutine cancellation, not a live VK session.
The authentication presenter is unchanged from the starting PR #3 commit.
Neither SID login nor live VK navigation can be claimed verified without a real
account/session. CI builds and validates the APK for the exact pushed commit,
including native libraries, package identity and development signature.
