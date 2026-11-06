package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryRewriterTest {
    @Test
    fun `rewrites home root`() = assertEquals(
        "/sdcard/OpenCode",
        DirectoryRewriter.rewrite(
            "/data/data/com.termux/files/home",
            "/data/data/com.termux/files/home",
            "/sdcard/OpenCode"
        )
    )

    @Test
    fun `rewrites nested project`() = assertEquals(
        "/sdcard/OpenCode/proj",
        DirectoryRewriter.rewrite(
            "/data/data/com.termux/files/home/proj",
            "/data/data/com.termux/files/home",
            "/sdcard/OpenCode"
        )
    )

    @Test
    fun `does not touch similar prefix`() = assertEquals(
        "/data/data/com.termux/files/home2",
        DirectoryRewriter.rewrite(
            "/data/data/com.termux/files/home2",
            "/data/data/com.termux/files/home",
            "/sdcard/OpenCode"
        )
    )

    @Test
    fun `leaves unrelated absolute path`() = assertEquals(
        "/tmp/x",
        DirectoryRewriter.rewrite("/tmp/x", "/data/data/com.termux/files/home", "/sdcard/OpenCode")
    )
}
