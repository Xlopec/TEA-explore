package com.max.weatherviewer.screens.feed.update

import com.max.weatherviewer.app.*
import com.max.weatherviewer.domain.Article
import com.max.weatherviewer.domain.toggleFavorite
import com.max.weatherviewer.screens.feed.*
import com.oliynick.max.elm.core.component.UpdateWith
import com.oliynick.max.elm.core.component.command
import com.oliynick.max.elm.core.component.noCommand

// nothing is private in our world
@Suppress("MemberVisibilityCanBePrivate")
object LiveFeedUpdater : FeedUpdater {

    override fun update(
        message: FeedMessage,
        feed: Feed
    ): UpdateWith<Feed, Command> =
        when {
            message is ArticlesLoaded -> Preview(feed.id, feed.criteria, message.articles).noCommand()
            message is LoadArticles -> FeedLoading(feed.id, feed.criteria) command LoadByCriteria(feed.id, feed.criteria)
            message is FeedOperationException -> Error(feed.id, feed.criteria, message.cause).noCommand()
            message is ToggleArticleIsFavorite && feed is Preview -> toggleFavorite(message.article, feed)
            message is ArticleUpdated && feed is Preview -> updateArticle(message.article, feed)
            message is OpenArticle -> openArticle(message.article, feed)
            message is ShareArticle -> shareArticle(message.article, feed)
            // fixme redesign FeedState
            message is OnQueryUpdated && feed.criteria is LoadCriteria.Query -> updateQuery(message.query, feed.criteria as LoadCriteria.Query, feed)
            message is ArticleUpdated -> feed.noCommand()// ignore
            else -> error("Can't handle message $message when state is $feed")
        }

    fun updateArticle(
        article: Article,
        state: Preview
    ): UpdateWith<Feed, Command> {

        val updated = when(state.criteria) {
            is LoadCriteria.Query, LoadCriteria.Trending -> state.updateArticle(article)
            LoadCriteria.Favorite -> if (article.isFavorite) state.prependArticle(article) else state.removeArticle(article)
        }

        return updated.noCommand()
    }

    fun toggleFavorite(
        article: Article,
        state: Preview
    ): UpdateWith<Feed, Command> {

        val toggled = article.toggleFavorite()

        return state.updateArticle(toggled) command toggled.storeCommand()
    }

    fun openArticle(
        article: Article,
        state: Feed
    ): UpdateWith<Feed, DoOpenArticle> = state command DoOpenArticle(article)

    fun shareArticle(
        article: Article,
        state: Feed
    ): UpdateWith<Feed, DoShareArticle> = state command DoShareArticle(article)

    fun updateQuery(
        query: String,
        criteria: LoadCriteria.Query,
        state: Feed
    ): UpdateWith<Feed, FeedCommand> = when(state) {
        is FeedLoading -> state.copy(criteria = criteria.copy(query = query))
        is Preview -> state.copy(criteria = criteria.copy(query = query))
        is Error -> state.copy(criteria = criteria.copy(query = query))
    }.noCommand()

    fun Article.storeCommand() = if (isFavorite) SaveArticle(this) else RemoveArticle(this)

}