package org.kiwix.kiwixmobile.core.history.viewmodel

import DeleteSelectedOrAllHistoryItems
import OpenHistoryItem
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.functions.Function5
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.HistoryDao
import org.kiwix.kiwixmobile.core.extensions.foldOverAddingHeaders
import org.kiwix.kiwixmobile.core.history.adapter.HistoryListItem
import org.kiwix.kiwixmobile.core.history.adapter.HistoryListItem.DateItem
import org.kiwix.kiwixmobile.core.history.adapter.HistoryListItem.HistoryItem
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.CreatedWithIntent
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.DeleteHistoryItems
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.ExitActionModeMenu
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.ExitHistory
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.Filter
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.OnItemClick
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.OnItemLongClick
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.RequestDeleteAllHistoryItems
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.RequestDeleteSelectedHistoryItems
import org.kiwix.kiwixmobile.core.history.viewmodel.Action.ToggleShowHistoryFromAllBooks
import org.kiwix.kiwixmobile.core.history.viewmodel.State.NoResults
import org.kiwix.kiwixmobile.core.history.viewmodel.State.Results
import org.kiwix.kiwixmobile.core.history.viewmodel.State.SelectionResults
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.Finish
import org.kiwix.kiwixmobile.core.utils.DialogShower
import org.kiwix.kiwixmobile.core.utils.KiwixDialog.DeleteAllHistory
import org.kiwix.kiwixmobile.core.utils.KiwixDialog.DeleteSelectedHistory
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
  private val historyDao: HistoryDao,
  private val zimReaderContainer: ZimReaderContainer,
  private val sharedPreferenceUtil: SharedPreferenceUtil
) : ViewModel() {
  val state = MutableLiveData<State>().apply { value = NoResults("", listOf()) }
  val effects = PublishProcessor.create<SideEffect<*>>()
  val actions = PublishProcessor.create<Action>()
  private val filter = BehaviorProcessor.createDefault("")
  private val compositeDisposable = CompositeDisposable()
  private val currentBook = BehaviorProcessor.createDefault("")
  private val showAllSwitchToggle = BehaviorProcessor.createDefault(false)
  private val deselectAllItems = BehaviorProcessor.createDefault(false)
  private var isInSelectionMode = false

  init {
    compositeDisposable.addAll(viewStateReducer(), actionMapper(), updateSelectionMode())
  }

  private fun updateSelectionMode() =
    deselectAllItems.subscribe({ isInSelectionMode = it }, Throwable::printStackTrace)

  private fun viewStateReducer() = Flowable.combineLatest(
    currentBook,
    searchResults(),
    deselectAllItems,
    filter,
    showAllSwitchToggle,
    Function5(::updateResultsState)
  ).subscribe(state::postValue, Throwable::printStackTrace)

  private fun searchResults(): Flowable<List<HistoryListItem>> =
    Flowable.combineLatest(
      filter,
      showAllSwitchToggle,
      historyDao.history(),
      Function3(::searchResults)
    )

  private fun searchResults(
    searchString: String,
    showAllToggle: Boolean,
    historyList: List<HistoryListItem>
  ): List<HistoryListItem> = historyList
      .filterIsInstance<HistoryItem>()
      .filter { h ->
        h.historyTitle.contains(searchString, true) &&
          (h.zimName == zimReaderContainer.name || showAllToggle)
      }

  private fun updateResultsState(
    currentBook: String,
    historyItemSearchResults: List<HistoryListItem>,
    shouldDeselectAllItems: Boolean,
    searchString: String,
    showAllSwitchOn: Boolean
  ): State {
    if(shouldDeselectAllItems){
      deselectAllHistoryItems(historyItemSearchResults)
    }
    val selectedItems = filterOutAllSelectedHistoryItems(historyItemSearchResults)
    val historyListWithDateItems = addDateHeadersToHistoryItems(historyItemSearchResults)
    return selectStateBasedOnInput(
      currentBook,
      historyListWithDateItems,
      selectedItems,
      searchString,
      showAllSwitchOn
    )
  }

  private fun selectStateBasedOnInput(
    currentBook: String,
    historyListWithDateItems: List<HistoryListItem>,
    selectedItems: List<HistoryListItem>,
    searchString: String,
    showAllSwitchOn: Boolean
  ) = when{
    historyListWithDateItems.isEmpty() -> NoResults(searchString, historyListWithDateItems)
    selectedItems.isNotEmpty() -> SelectionResults(
      searchString,
      historyListWithDateItems,
      selectedItems,
      showAllSwitchOn,
      currentBook
    )
    else -> Results(searchString, historyListWithDateItems, showAllSwitchOn, currentBook)
  }

  private fun addDateHeadersToHistoryItems(historyItemSearchResults: List<HistoryListItem>): MutableList<HistoryListItem> {
    return historyItemSearchResults.reversed().foldOverAddingHeaders(
        { historyItem -> DateItem((historyItem as HistoryItem).dateString) },
        { current, next -> (current as HistoryItem).dateString != (next as HistoryItem).dateString }
      )
  }

  private fun filterOutAllSelectedHistoryItems(historyItemSearchResults: List<HistoryListItem>): List<HistoryItem> {
    return historyItemSearchResults.filterIsInstance<HistoryItem>().filter { it.isSelected }
  }

  private fun deselectAllHistoryItems(historyItemSearchResults: List<HistoryListItem>) {
      historyItemSearchResults
        .filterIsInstance<HistoryItem>()
        .forEach { it.isSelected = false }
  }

  override fun onCleared() {
    compositeDisposable.clear()
    super.onCleared()
  }

  private fun actionMapper() = actions.map {
    when (it) {
      ExitHistory -> effects.offer(Finish)
      is Filter -> filter.offer(it.searchTerm)
      is ToggleShowHistoryFromAllBooks ->
        toggleShowAllHistorySwitchAndSaveItsStateToPrefs(it.isChecked)
      is CreatedWithIntent -> filter.offer(it.searchTerm)
      is OnItemLongClick -> selectItemAndOpenSelectionMode(it.historyItem)
      is OnItemClick -> appendItemToSelectionOrOpenIt(it)
      is RequestDeleteAllHistoryItems ->
        openDialogToRequestDeletionOfAllHistoryItems(it.dialogShower)
      is RequestDeleteSelectedHistoryItems ->
        openDialogToRequestDeletionOfSelectedHistoryItems(it.dialogShower)
      ExitActionModeMenu -> deselectAllItems.offer(true)
      DeleteHistoryItems -> effects.offer(DeleteSelectedOrAllHistoryItems(state, historyDao))
    }
  }.subscribe({}, Throwable::printStackTrace)

  private fun toggleShowAllHistorySwitchAndSaveItsStateToPrefs(isChecked: Boolean) {
    showAllSwitchToggle.offer(isChecked)
    sharedPreferenceUtil.setShowHistoryCurrentBook(!isChecked)
  }

  private fun selectItemAndOpenSelectionMode(historyItem: HistoryItem) {
    historyItem.isSelected = true
    deselectAllItems.offer(false)
  }

  private fun openDialogToRequestDeletionOfSelectedHistoryItems(dialogShower: DialogShower) {
    dialogShower.show(DeleteSelectedHistory, {
      actions.offer(DeleteHistoryItems)
    })
  }

  private fun openDialogToRequestDeletionOfAllHistoryItems(dialogShower: DialogShower) {
    dialogShower.show(DeleteAllHistory, {
      actions.offer(DeleteHistoryItems)
    })
  }

  private fun isInSelctionMode(): Boolean {
    return state
      .value
      ?.historyItems
      ?.filterIsInstance<HistoryItem>()
      ?.any { it.isSelected } == true
  }

  private fun appendItemToSelectionOrOpenIt(onItemClick: OnItemClick) {
    val historyItem = onItemClick.historyListItem as HistoryItem
    when {
      historyItem.isSelected -> {
        historyItem.isSelected = false
        deselectAllItems.offer(false)
      }
      isInSelctionMode() -> {
        historyItem.isSelected = true
        deselectAllItems.offer(false)
      }
      else -> {
        effects.offer(OpenHistoryItem(historyItem, zimReaderContainer))
      }
    }
  }
}
