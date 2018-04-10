package com.boomylabs.listly.data.interactor;

import com.boomylabs.listly.data.Converter;
import com.boomylabs.listly.data.http.ListlyApiDecorator;
import com.boomylabs.listly.data.model.dto.ListItemResponse;
import com.boomylabs.listly.data.model.presentation.ListItemSetup;
import com.boomylabs.listly.data.model.test.ListItem;
import com.boomylabs.listly.data.model.test.ListItemType;
import com.boomylabs.listly.persistence.IListItemRepository;
import com.boomylabs.listly.ui.common.pagination.Page;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class DetailsInteractor {

    private static final int DEFAULT_LIST_ITEM_PAGE_SIZE = 10;

    private final ListlyApiDecorator apiDecorator;
    private final IListItemRepository listItemRepository;

    @Inject
    DetailsInteractor(ListlyApiDecorator apiDecorator,
                      IListItemRepository listItemRepository) {
        this.apiDecorator = apiDecorator;
        this.listItemRepository = listItemRepository;
    }

    public Single<Page<ListItem>> getListItemsForId(String listId, String searchQuery,
                                                    String sortType, List<String> filters,
                                                    int page, int amount) {
        return apiDecorator.retrieveListItems(listId, searchQuery, sortType, filters, page, amount)
                .subscribeOn(Schedulers.io())
                .zipWith(Observable.just(amount), Converter::toPage)
                .firstOrError();
    }

    public Single<Page<ListItem>> getQueuedListItemsForId(String listId, String searchQuery,
                                                          String sortType, List<String> filters,
                                                          int page, int amount) {
        return apiDecorator.retrieveQueuedListItems(listId, searchQuery, sortType, filters, page, amount)
                .subscribeOn(Schedulers.io())
                .zipWith(Observable.just(amount), Converter::toPage)
                .firstOrError();
    }

    public Single<ListItem> toogleUpvote(ListItem listItem) {
        return apiDecorator.toggleUpvoteListItem(listItem.getId(), listItem.getListItemType())
                .map(ListItemResponse::getItem)
                .take(1).singleOrError()
                .flatMap(updatedListItem ->
                    listItemRepository.updateListItem(updatedListItem.getListItemType(), updatedListItem)
                        .toSingleDefault(updatedListItem)
                );
    }

    public Single<ListItem> toggleEmoji(ListItem listItem, String emojiAlias) {
        return apiDecorator.toggleListItemEmoji(listItem.getId(), emojiAlias, listItem.getListItemType())
                .map(ListItemResponse::getItem)
                .take(1).singleOrError()
                .flatMap(updatedListItem ->
                        listItemRepository.updateListItem(updatedListItem.getListItemType(), updatedListItem)
                                .toSingleDefault(updatedListItem)
                );
    }

    /** List Items **/

    public Observable<java.util.List<ListItem>> getListItemRepository(String listId, ListItemType type) {
        return listItemRepository.getListItemsObservable(listId, type);
    }

    public Observable<Page<ListItem>> getListItems(String listId, ListItemType type) {
        return Observable.combineLatest(
                listItemRepository.getListItemsObservable(listId, type),
                listItemRepository.getListItemsAreCompletedObservable(listId, type),
                Page::new
        ).filter(page -> page.getItems().size() > 0 || page.isCompleted());
    }

    public Completable refreshListItems(String listId, ListItemType listItemType, ListItemSetup setup) {
        Single<Page<ListItem>> listItemPageSingle;
        switch (listItemType) {
            case LIST:
                listItemPageSingle = getListItemsForId(listId, setup.getSearchQuery(), setup.getSortOption(),
                        setup.getFilters(), 1, DEFAULT_LIST_ITEM_PAGE_SIZE);
                break;
            case QUEUE:
                listItemPageSingle = getQueuedListItemsForId(listId, setup.getSearchQuery(), setup.getSortOption(),
                        setup.getFilters(), 1, DEFAULT_LIST_ITEM_PAGE_SIZE);
                break;
            default:
                listItemPageSingle = Single.error(new IllegalStateException("ListItemType cannot be null!"));
                break;
        }
        return listItemPageSingle.flatMapCompletable(listItemPage -> {
                    java.util.List<ListItem> listItems = listItemPage.getItems();
                    return Completable.concatArray(
                            listItemRepository.clearAll(),
                            listItemRepository.setListItems(listId, listItemType, listItems),
                            listItemRepository.setListItemsAreCompleted(listId, listItemType,
                                    listItems.size() < DEFAULT_LIST_ITEM_PAGE_SIZE)
                    );
                });
    }

    public Completable loadMoreListItems(String listId, ListItemType listItemType, ListItemSetup setup) {
        return listItemRepository.getListItems(listId, listItemType)
                .flatMap(listItems -> {
                    int pageNumber = (int) Math.ceil(1.0 * listItems.size() / DEFAULT_LIST_ITEM_PAGE_SIZE) + 1;
                    switch (listItemType) {
                        case LIST:
                            return getListItemsForId(listId, setup.getSearchQuery(), setup.getSortOption(),
                                    setup.getFilters(),
                                    pageNumber,
                                    DEFAULT_LIST_ITEM_PAGE_SIZE);
                        case QUEUE:
                            return getQueuedListItemsForId(listId, setup.getSearchQuery(), setup.getSortOption(),
                                    setup.getFilters(),
                                    pageNumber,
                                    DEFAULT_LIST_ITEM_PAGE_SIZE);
                        default:
                            return Single.error(new IllegalStateException("ListItemType cannot be null!"));
                    }
                })
                .flatMapCompletable(listItemPage -> {
                    java.util.List<ListItem> listItems = listItemPage.getItems();
                    return Completable.concatArray(
                            listItemRepository.addListItems(listId, listItemType, listItems),
                            listItemRepository.setListItemsAreCompleted(listId, listItemType,
                                    listItems.size() < DEFAULT_LIST_ITEM_PAGE_SIZE)
                    );
                });
    }
}
