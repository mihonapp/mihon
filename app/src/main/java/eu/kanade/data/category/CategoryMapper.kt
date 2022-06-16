package eu.kanade.data.category

import eu.kanade.domain.category.model.Category

val categoryMapper: (Long, String, Long, Long) -> Category = { id, name, order, flags ->
    Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
    )
}
