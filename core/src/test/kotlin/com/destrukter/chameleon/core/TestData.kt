package com.destrukter.chameleon.core

fun testRepository(categories: Int = 5): CategoryRepository = CategoryRepository(
    (1..categories).map { c -> Category("Topic $c", (1..36).map { "Word$c-$it" }) }
)
