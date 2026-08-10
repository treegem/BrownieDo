package eu.sweetgeorgie.browniedo.data.list

import eu.sweetgeorgie.browniedo.domain.list.TodoList
import org.junit.Assert.assertEquals
import org.junit.Test

class ListOrderTest {

    @Test
    fun `sorts by name`() {
        val lists = listOf(list("Zuhause"), list("Einkauf"), list("Garten"))

        assertEquals(
            listOf("Einkauf", "Garten", "Zuhause"),
            lists.sortedWith(LIST_ORDER).map(TodoList::name)
        )
    }

    @Test
    fun `ignores upper and lower case`() {
        val lists = listOf(list("baumarkt"), list("Apotheke"), list("Bäckerei"))

        assertEquals(
            listOf("Apotheke", "baumarkt", "Bäckerei"),
            lists.sortedWith(LIST_ORDER).map(TodoList::name)
        )
    }

    private fun list(name: String) = TodoList(id = name.lowercase(), name = name, isShared = false)
}
