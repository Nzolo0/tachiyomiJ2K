package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val controller: RepoController,
    private val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutinePresenter<RepoController>() {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * List containing repos.
     */
    private var repos: MutableList<String> = mutableListOf()

    /**
     * Called when the presenter is created.
     */
    fun getRepos() {
        scope.launch(Dispatchers.IO) {
            repos.clear()
            repos.addAll(preferences.extensionRepos().get())
            withContext(Dispatchers.Main) {
                controller.setRepos(getReposWithCreate())
            }
        }
    }

    private fun getReposWithCreate(): List<RepoItem> {
        return (listOf(CREATE_REPO_ITEM) + repos).map(::RepoItem)
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String): Boolean {
        if (isInvalidRepo(name)) return false

        repos.add(0, name)
        preferences.extensionRepos().set(repos.toSet())
        controller.setRepos(getReposWithCreate())
        return true
    }

    /**
     * Deletes the repo from the database.
     *
     * @param repo The repo to delete.
     */
    fun deleteRepo(repo: String?) {
        val safeRepo = repo ?: return
        repos.remove(safeRepo)
        preferences.extensionRepos().set(repos.toSet())
        controller.setRepos(getReposWithCreate())
    }

    /**
     * Renames a repo.
     *
     * @param repo The repo to rename.
     * @param name The new name of the repo.
     */
    fun renameRepo(repo: String, name: String): Boolean {
        if (!repo.equals(name, true)) {
            if (isInvalidRepo(name)) return false
            repos[repos.indexOf(repo)] = name
            preferences.extensionRepos().set(repos.toSet())
            controller.setRepos(getReposWithCreate())
        }
        return true
    }

    private fun isInvalidRepo(name: String): Boolean {
        // Do not allow duplicate repos.
        if (repoExists(name)) {
            controller.onRepoExistsError()
            return true
        }

        // Do not allow invalid formats
        if (!name.matches(repoRegex)) {
            controller.onRepoInvalidNameError()
            return true
        }
        return false
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun repoExists(name: String): Boolean {
        return repos.any { it.equals(name, true) }
    }

    companion object {
        val repoRegex = """^[a-zA-Z0-9-_.]*?\/[a-zA-Z0-9-_.]*?$""".toRegex()
        const val CREATE_REPO_ITEM = "create_repo"
    }
}
