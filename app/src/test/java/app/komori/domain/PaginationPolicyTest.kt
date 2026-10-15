package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class PaginationPolicyTest {
    @Test fun continuesWhenNewTitlesArrive() { assertTrue(PaginationPolicy.hasNext(true, 20, 40)) }
    @Test fun stopsOnRepeatedPage() { assertFalse(PaginationPolicy.hasNext(true, 20, 20)) }
    @Test fun stopsOnEmptyFirstPage() { assertFalse(PaginationPolicy.hasNext(true, 0, 0)) }
    @Test fun respectsSourceEnd() { assertFalse(PaginationPolicy.hasNext(false, 20, 23)) }
}
