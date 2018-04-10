package com.boomylabs.listly.ui.details;

import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.boomylabs.listly.data.Constants;
import com.boomylabs.listly.data.ListConfiguration;
import com.boomylabs.listly.data.eventbus.RxBus;
import com.boomylabs.listly.data.interactor.DetailsInteractor;
import com.boomylabs.listly.data.interactor.ListInteractor;
import com.boomylabs.listly.data.interactor.ListItemInteractor;
import com.boomylabs.listly.data.interactor.UserInteractor;
import com.boomylabs.listly.data.model.presentation.DetailsInfoOption;
import com.boomylabs.listly.data.model.presentation.ListItemSetup;
import com.boomylabs.listly.data.model.presentation.ListType;
import com.boomylabs.listly.data.model.test.List;
import com.boomylabs.listly.data.model.test.ListItem;
import com.boomylabs.listly.ui.common.flow.Navigator;
import com.boomylabs.listly.ui.common.mvi.MviPresenter;
import com.boomylabs.listly.ui.create.item.detailed.CreateItemDetailedScreen;
import com.boomylabs.listly.ui.utils.CollectionUtils;
import com.boomylabs.listly.ui.utils.Utils;
import com.boomylabs.listly.ui.utils.share.SharingUtils;
import com.google.common.collect.ArrayListMultimap;
import com.jakewharton.rxrelay2.BehaviorRelay;
import com.vdurmont.emoji.Emoji;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

import static com.boomylabs.listly.data.Converter.toDomainType;
import static com.boomylabs.listly.data.model.presentation.DetailsInfoOption.LIST;
import static java.util.Collections.emptyList;

public class DetailsPresenter extends MviPresenter<DetailsView, DetailsViewState> {

    private final DetailsInteractor interactor;
    private final ListInteractor listInteractor;
    private final ListItemInteractor listItemInteractor;
    private final String listId;
    private final ListType listType;
    private final ListConfiguration configuration;
    private final UserInteractor userInteractor;

    private ListItemSetup setup = new ListItemSetup(LIST, "", "", emptyList());
    private ArrayListMultimap<ListItem, String> pendingEmojis = ArrayListMultimap.create();
    private final BehaviorSubject<ListItemSetup> setupSubject = BehaviorSubject
            .createDefault(new ListItemSetup());

    private BehaviorSubject<DetailsViewState> viewStateForwarderSubject =
            BehaviorSubject.create();
    private BehaviorRelay<List> listRelay = BehaviorRelay.create();
    private BehaviorRelay<Integer> reloadListRelay = BehaviorRelay.create();

    private boolean isFilterButtonEnabled = false;

    CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public DetailsPresenter(@Nullable List list, DetailsInteractor interactor,
                            ListInteractor listInteractor,
                            ListItemInteractor listItemInteractor, UserInteractor userInteractor) {
        this.listId = list.getId();
        this.listType = list.getListType();
        this.interactor = interactor;
        this.listInteractor = listInteractor;
        this.listItemInteractor = listItemInteractor;
        this.userInteractor = userInteractor;
        this.configuration = new ListConfiguration();

        listRelay.accept(list);
        userInteractor.setCurrentList(list);
    }

    private Observable<DetailsViewState> getListItems(ListItemSetup setup) {
        return interactor.getListItems(listId, toDomainType(setup.getOption()))
                .map(page ->
                        new DetailsViewState.DetailedState(
                                listRelay.getValue(), setup,
                                renumerateItemsInAscendingOrder(page.getItems()),
                                page.isCompleted()
                        )
                );
    }

    private java.util.List<ListItem> renumerateItemsInAscendingOrder(java.util.List<ListItem> items) {
        long position = 1;
        java.util.List<ListItem> copyList = CollectionUtils.copy(items);
        for (ListItem item : copyList) {
            item.setPosition(position);
            position++;
        }
        return copyList;
    }

    private Observable<DetailsViewState> refreshListItems(ListItemSetup setup) {
        return Observable.just(new DetailsViewState.LoadingState(setup))
                .observeOn(Schedulers.newThread())
                .cast(DetailsViewState.class)
                .concatWith(
                        interactor.refreshListItems(
                                listId, toDomainType(setup.getOption()), setup
                        )
                                .retryWhen(ignored -> Flowable.interval(5, TimeUnit.SECONDS).onBackpressureLatest())
                                .toObservable()
                );
    }

    private Observable<DetailsViewState> changeListItemType(ListItem listItem) {
        return Observable.fromCallable(DetailsViewState.LoadingDialogState::new)
                .observeOn(Schedulers.newThread())
                .cast(DetailsViewState.class)
                .concatWith(
                        Completable.concatArray(
                                listItemInteractor.changeListItemType(listItem)
                        ).toObservable()
                );
    }

    private Observable<DetailsViewState> deleteListItem(ListItem listItem) {
        return Observable.fromCallable(DetailsViewState.LoadingDialogState::new)
                .observeOn(Schedulers.newThread())
                .cast(DetailsViewState.class)
                .concatWith(listItemInteractor.deleteListItem(listItem).toObservable());
    }

    @Override
    protected void bindIntents() {
        RxBus.subscribe(RxBus.SUBJECT_LIST_ITEM_FILTER_RESULT_DIALOG, this,
                (setup) -> setupSubject.onNext((ListItemSetup) setup));

        RxBus.subscribe(RxBus.SUBJECT_DISABLE_FILTER_BUTTON, this, o -> {
            isFilterButtonEnabled = false;
        });

        RxBus.subscribe(RxBus.SUBJECT_REFRESH_LIST_ITEMS, this, o -> {
            reloadListRelay.accept(1);
        });

        Observable<DetailsViewState> listItemsChangeObservable =
                Observable.combineLatest(
                        intent(DetailsView::detailsInfoOptionChangeIntent).startWith(DetailsInfoOption.LIST),
                        setupSubject,
                        (detailsInfoOption1, setup1) -> {
                            setup1 = new ListItemSetup(setup1);
                            setup1.setOption(detailsInfoOption1);
                            return setup1;
                        }
                )
                        .distinctUntilChanged()
                        .switchMap(listItemSetup -> {
                            setup = listItemSetup;
                            return Observable.just(1).concatWith(reloadListRelay)
                                    .switchMap(ignored ->
                                            refreshListItems(listItemSetup)
                                                    .concatWith(getListItems(listItemSetup))
                                    );
                        })
                        .observeOn(AndroidSchedulers.mainThread());

        intent(DetailsView::loadMore)
                .take(1)
                .flatMapCompletable(ignored ->
                        interactor.loadMoreListItems(listId, toDomainType(setup.getOption()), setup)
                )
                .repeat()
                .subscribe();

        intent(DetailsView::forceRefresh)
                .flatMapSingle(ignored -> listInteractor.getListById(listId, listType))
                .retryWhen(throwableObserver ->
                        Observable.interval(5, TimeUnit.MILLISECONDS)
                )
                .subscribe(listRelay);

        Observable<DetailsViewState> detailsViewStateObservable = intent(DetailsView::initIntent)
                .flatMap(ignored -> listRelay.map(list -> new DetailsViewState.DefaultState(list, setup)))
                .cast(DetailsViewState.class)
                .mergeWith(listItemsChangeObservable)
                .mergeWith(
                        intent(DetailsView::changeListItemTypeIntent).flatMap(this::changeListItemType)
                )
                .mergeWith(
                        intent(DetailsView::deleteListItemIntent).flatMap(this::deleteListItem)
                )
                .mergeWith(bindEmojiObservables())
                .onErrorReturn(DetailsViewState.ErrorState::new)
                .repeat()
                .mergeWith(viewStateForwarderSubject)
                .observeOn(AndroidSchedulers.mainThread());

        subscribeViewState(detailsViewStateObservable, DetailsView::render);

        disposables.add(
                intent(DetailsView::openFilterTagsIntent)
                        .flatMap(ignored -> listRelay.take(1))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(list -> {
                            if (!isFilterButtonEnabled) {
                                isFilterButtonEnabled = true;
                                configuration.setFilterInfo(
                                        list.getItemTagsByAlpha() == null ? Collections.emptyList() : list.getItemTagsByAlpha(),
                                        setup);
                                Navigator.getInstance().goToListItemFilter(configuration);
                            }
                        })
        );

        setupSideEffects();
        setupListItemActions();

        RxBus.subscribe(RxBus.SUBJECT_SHARE, this, data -> {
            if (data instanceof ListItem) {
                Navigator.getInstance().share(SharingUtils.listItemToContentString((ListItem) data));
            }
        });
    }

    private void setupSideEffects() {
        disposables.add(
                intent(DetailsView::reportListIntent)
                        .flatMapCompletable(complaint ->
                                listRelay.take(1).flatMapCompletable(list -> listInteractor.reportList(list, complaint))
                        )
                        .doOnComplete(() -> viewStateForwarderSubject.onNext(new DetailsViewState.InfoState(DetailsViewState.InfoState.STATE_REPORTED)))
                        .subscribe()
        );

        disposables.add(
                intent(DetailsView::editListIntent)
                        .flatMap(ignored -> listRelay.take(1))
                        .doOnNext(list -> Navigator.getInstance().goToEditOwnedList(list.getId(), list.getListType()))
                        .subscribe()
        );

        disposables.add(
                intent(DetailsView::closeIntent)
                        .doOnNext(ignored -> Navigator.getInstance().goBack())
                        .subscribe()
        );

        disposables.add(
                intent(DetailsView::shareListIntent)
                        .flatMap(ignored -> listRelay.take(1))
                        .doOnNext(list ->
                                Navigator
                                        .getInstance()
                                        .share(SharingUtils.listToContentString(list)))
                        .subscribe()
        );

        disposables.add(
                intent(DetailsView::startCommentIntent)
                        .doOnNext(listItem -> Navigator.getInstance().goToListItemComments(listItem))
                        .subscribe()
        );
    }

    private void setupListItemActions() {
        disposables.add(
                intent(DetailsView::editListItemIntent)
                        .flatMapCompletable(listItem ->
                                Completable.concatArray(
                                        Completable.fromAction(() -> {
                                            if (listItem.getImages() != null && listItem.getImages().getLarge() != null) {
                                                Utils.saveImageToInternal(listItem.getImages().getLarge(), Constants.CacheFileNames.LIST_ITEM);
                                            }
                                        })
                                                .onErrorComplete()
                                                .subscribeOn(Schedulers.io()),
                                        Completable.fromAction(() -> {
                                            CreateItemDetailedScreen.Builder builder = new CreateItemDetailedScreen.Builder()
                                                    .setMode(CreateItemDetailedScreen.Mode.EDIT)
                                                    .setListItem(listItem);
                                            Navigator.getInstance().goToCreateListItemDetailed(builder);
                                        }).subscribeOn(AndroidSchedulers.mainThread()))
                        )
                        .subscribe()
        );
    }

    private Observable<DetailsViewState> bindEmojiObservables() {
        final Scheduler emojiScheduler = Schedulers.newThread();

        Observable<Pair<ListItem, String>> upvoteObservable = intent(DetailsView::upvoteListItemToggleIntent)
                .observeOn(emojiScheduler)
                .doOnNext(listItem -> pendingEmojis.put(listItem, Emoji.THUMBS_UP))
                .flatMapSingle(listItem -> interactor.toogleUpvote(listItem)
                        .map(updatedItem -> new Pair<>(updatedItem, Emoji.THUMBS_UP))
                );

        Observable<Pair<ListItem, String>> emojiToggleObservable = intent(DetailsView::emojiListItemToggleIntent)
                .observeOn(emojiScheduler)
                .doOnNext(pair -> pendingEmojis.put(pair.first, pair.second))
                .flatMapSingle(pair -> interactor
                        .toggleEmoji(pair.first, pair.second)
                        .map(updatedItem -> new Pair<>(updatedItem, pair.second))
                );

        return Observable.<Object>mergeArray(upvoteObservable, emojiToggleObservable)
                .ignoreElements()
                .toObservable();
    }

    @Override
    protected void unbindIntents() {
        RxBus.unsubscribe(this);
        super.unbindIntents();
    }
}
