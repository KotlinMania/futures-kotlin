package io.github.kotlinmania.futures

internal fun selectNextSomePlainStreamProbe(stream: Stream<Int>): SelectNextSome<Int> =
    stream.selectNextSome()

internal fun selectNextSomePlainConstructorProbe(stream: Stream<Int>): SelectNextSome<Int> =
    SelectNextSome(stream)
