package com.gtarayici.browser.data

/** Şema v1 → v2: profiller eklenir, geçmiş ve yer imleri profile bağlanır. */
internal val MIGRATION_1_2_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
        "`contextId` TEXT, `locked` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",

    "CREATE TABLE IF NOT EXISTS `history_new` (`profileId` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`lastVisited` INTEGER NOT NULL, `visitCount` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `url`))",
    "INSERT INTO `history_new` (`profileId`, `url`, `title`, `lastVisited`, `visitCount`) " +
        "SELECT '$DEFAULT_PROFILE_ID', `url`, `title`, `lastVisited`, `visitCount` FROM `history`",
    "DROP TABLE `history`",
    "ALTER TABLE `history_new` RENAME TO `history`",
    "CREATE INDEX IF NOT EXISTS `index_history_lastVisited` ON `history` (`lastVisited`)",

    "CREATE TABLE IF NOT EXISTS `bookmarks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` TEXT NOT NULL, " +
        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    "INSERT INTO `bookmarks_new` (`id`, `profileId`, `url`, `title`, `createdAt`) " +
        "SELECT `id`, '$DEFAULT_PROFILE_ID', `url`, `title`, `createdAt` FROM `bookmarks`",
    "DROP TABLE `bookmarks`",
    "ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_profileId_url` ON `bookmarks` (`profileId`, `url`)",
)
