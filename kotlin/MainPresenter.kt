package com.itexus.smartgarlands.ui.main

import com.itexus.smartgarlands.R
import com.itexus.smartgarlands.domain.DataManager
import com.itexus.smartgarlands.model.GarlandType
import com.itexus.smartgarlands.persistence.realm.model.Animation
import com.itexus.smartgarlands.persistence.realm.model.GarlandPreview
import com.itexus.smartgarlands.persistence.realm.model.Lamp
import com.itexus.smartgarlands.utils.common.BaseMvpPresenter
import com.itexus.smartgarlands.utils.common.Constants
import com.itexus.smartgarlands.utils.common.animations.TextAnimation
import com.itexus.smartgarlands.utils.schedulers.SchedulersProvider
import com.itexus.smartgarlands.utils.subscribeWithErrorLog
import com.itexus.smartgarlands.utils.validators.LocalizedRuntimeException
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmObjectChangeListener
import io.realm.exceptions.RealmPrimaryKeyConstraintException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainPresenter @Inject constructor(
        val schedulers: SchedulersProvider,
        val dataManager: DataManager
) : BaseMvpPresenter<MainContract.View>(), MainContract.Presenter {

    private var realm: Realm? = null

    private val previewChangeListener = RealmObjectChangeListener<GarlandPreview> { preview, t ->
        if (isAttached) {
            mvpView?.refreshPreview()
        }
    }
    private var preview: GarlandPreview = GarlandPreview()
        set(value) {
            if (field.isManaged && field.isValid) {
                field.removeChangeListener(previewChangeListener)
            }
            field = value
            if (field.isManaged && field.isValid) {
                value.addChangeListener(previewChangeListener)
            }
        }

    override fun attach(view: MainContract.View) {
        super.attach(view)
        realm = Realm.getDefaultInstance()
    }

    override fun detach() {
        realm?.close()
        super.detach()
    }

    override fun loadAnimations(type: GarlandType,
                                isSavedAnimationMode: Boolean) {
        Single.zip<GarlandPreview, List<Animation>, Pair<GarlandPreview, List<Animation>>>(
            dataManager.getGarlandPreviewByType(type),
            dataManager.getAnimations(type, isOnlyReceived = !isSavedAnimationMode),
            BiFunction { preview, animations -> Pair(preview, animations) }
        )
                .retryWhen { errorFlowable ->
                    Flowable.interval(Constants.DB_FETCH_RETRY_TIME, TimeUnit.MILLISECONDS)
                }
                .observeOn(schedulers.ui())
                .subscribeWithErrorLog { pair ->
                    preview = pair.first
                    mvpView?.setGarlandsAnimation(pair.first, pair.second)
                }
                .bindToLifecycle()
    }

    override fun refreshGarlandPreviewForType(type: GarlandType) {
        dataManager.getGarlandPreviewByType(type)
                .observeOn(schedulers.ui())
                .subscribeWithErrorLog {
                    preview = it
                    mvpView?.setGarlandPreview(preview)
                }
                .bindToLifecycle()
    }

    override fun generateTextAnimation(text: String) {
        Single.fromCallable {
            TextAnimation(text).generateAnimation(name = text, type = GarlandType.NET)
                    .apply { isCustom = true }
        }
                .flatMapCompletable(dataManager::saveAnimation)
                //.observeOn(schedulers.ui())
                .doOnSubscribe { mvpView?.showProgressRes(R.string.progress_creating_animation) }
                .doOnTerminate { mvpView?.hideProgress() }
                .doOnError { error ->
                    if (error is RealmPrimaryKeyConstraintException) {
                        mvpView?.showMessageRes(R.string.error_animation_with_given_name_already_exists)
                    } else if (error is LocalizedRuntimeException) {
                        mvpView?.showMessageRes(error.errorMessageRes)
                    }
                }
                .subscribeOn(schedulers.realm())
                .subscribeWithErrorLog()
                .bindToLifecycle()
    }

    override fun getAnimationLampsForTimestamp(animationName: String,
                                               timestamp: Int): List<Lamp>? {
        return dataManager.getAnimationLampsForTimestamp(animationName, timestamp)
    }
}