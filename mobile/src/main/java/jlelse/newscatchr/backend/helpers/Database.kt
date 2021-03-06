/*
 * NewsCatchr  Copyright (C) 2016  Jan-Lukas Else
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jlelse.newscatchr.backend.helpers

import com.mcxiaoke.koi.async.asyncUnsafe
import io.paperdb.Paper
import jlelse.newscatchr.backend.Article
import jlelse.newscatchr.backend.Feed
import jlelse.newscatchr.backend.apis.Pocket
import jlelse.newscatchr.extensions.*

/**
 * Database
 */
object Database {

	private val FAVORITES = "feeds_database"
	private val BOOKMARKS = "bookmarks_database"
	private val READ_URLS = "urls_database"
	private val LAST_FEEDS = "last_feeds"

	var allFavorites: Array<Feed>
		get() = tryOrNull { Paper.book(FAVORITES).read<Array<Feed>>(FAVORITES, arrayOf<Feed>()) } ?: arrayOf<Feed>()
		set(value) {
			tryOrNull { Paper.book(FAVORITES).write(FAVORITES, value.onlySaved()) }
		}

	val allFavoritesUrls = mutableListOf<String>().apply { allFavorites.forEach { add(it.url()!!) } }.toTypedArray()

	fun addFavorite(feed: Feed?) = addFavorites(arrayOf(feed))

	fun addFavorites(feeds: Array<out Feed?>?) {
		allFavorites = allFavorites.toMutableList().apply { feeds?.removeEmptyFeeds()?.forEach { if (!isSavedFavorite(it.url())) add(it) } }.toTypedArray()
	}

	fun deleteFavorite(url: String?) {
		if (url.notNullOrBlank()) allFavorites = allFavorites.toMutableList().filterNot { it.url() == url }.toTypedArray()
	}

	fun updateFavoriteTitle(feedUrl: String?, newTitle: String?) {
		if (feedUrl.notNullOrBlank() && newTitle.notNullOrBlank()) {
			allFavorites = allFavorites.apply {
				forEach {
					if (it.url() == feedUrl) it.title = newTitle
				}
			}
		}
	}

	var allBookmarks: Array<Article>
		get() = tryOrNull { Paper.book(BOOKMARKS).read<Array<Article>>(BOOKMARKS, arrayOf<Article>()) } ?: arrayOf<Article>()
		set(value) {
			tryOrNull { Paper.book(BOOKMARKS).write<Array<Article>>(BOOKMARKS, value.removeEmptyArticles()) }
		}

	val allBookmarkUrls = mutableListOf<String>().apply { allBookmarks.forEach { add(it.url!!) } }.toTypedArray()

	private fun addBookmarks(vararg articles: Article?) {
		allBookmarks = allBookmarks.toMutableList().apply {
			articles.removeEmptyArticles().forEach { if (!isSavedBookmark(it.url)) add(0, it) }
		}.toTypedArray()
	}

	fun addBookmark(article: Article?) {
		tryOrNull(article != null) {
			if (Preferences.pocketSync && Preferences.pocketUserName.notNullOrBlank() && Preferences.pocketAccessToken.notNullOrBlank()) {
				asyncUnsafe {
					article!!.pocketId = PocketHandler().addToPocket(article)
					article.fromPocket = true
					addBookmarks(article)
				}
			} else {
				addBookmarks(article)
			}
		}
	}

	fun deleteBookmark(url: String?) {
		tryOrNull(url.notNullOrBlank()) {
			allBookmarks.toMutableList().filter { it.url == url }.forEach {
				val pocket = Preferences.pocketSync && Preferences.pocketUserName.notNullOrBlank() && Preferences.pocketAccessToken.notNullOrBlank()
				if (pocket && it.fromPocket) asyncUnsafe {
					PocketHandler().archiveOnPocket(it)
				}
			}
			allBookmarks = allBookmarks.toMutableList().filterNot { it.url == url }.toTypedArray()
		}
	}

	var allReadUrls: Set<String>
		get() = tryOrNull { Paper.book(READ_URLS).read<Set<String>>(READ_URLS, setOf<String>()) } ?: setOf<String>()
		set(value) {
			tryOrNull { Paper.book(READ_URLS).write(READ_URLS, value.removeBlankStrings()) }
		}

	fun addReadUrl(url: String?) {
		allReadUrls = allReadUrls.toMutableSet().apply { add(url!!) }.toSet()
	}

	var allLastFeeds: Set<Feed>
		get() = tryOrNull { Paper.book(LAST_FEEDS).read<Set<Feed>>(LAST_FEEDS, setOf<Feed>()) } ?: setOf<Feed>()
		set(value) {
			tryOrNull { Paper.book(LAST_FEEDS).write(LAST_FEEDS, value.removeEmptyFeeds()) }
		}

	fun addLastFeed(feed: Feed?) {
		allLastFeeds = allLastFeeds.filter { it.url() != feed?.url() }.toMutableSet().apply {
			if (feed != null) add(feed)
		}.toSet()
	}

	fun isSavedFavorite(url: String?) = url.notNullOrBlank() && allFavoritesUrls.contains(url)

	fun isSavedBookmark(url: String?) = url.notNullOrBlank() && allBookmarkUrls.contains(url)

	fun isSavedReadUrl(url: String?) = url.notNullOrBlank() && allReadUrls.contains(url)

	// Helpers

	class PocketHandler {

		fun addToPocket(item: Article) = tryOrNull { Pocket().add(item.url!!) }

		fun archiveOnPocket(item: Article) = tryOrNull { Pocket().archive(item.pocketId!!) }

	}

}
