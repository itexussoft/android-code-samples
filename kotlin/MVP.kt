package com.itexus.smartgarlands.utils.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.annotation.CallSuper
import android.support.annotation.StringRes
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.itexus.smartgarlands.R
import com.itexus.smartgarlands.utils.classTag
import com.itexus.smartgarlands.utils.validators.LocalizedRuntimeException
import com.itexus.smartgarlands.utils.validators.PhoneNumberDoesNotExistException
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

interface MvpView {
}

interface MvpPresenter<in T : MvpView> {
    fun attach(view: T)
    fun detach()
}

interface ShowProgress {
    fun showProgress(text: String)
    fun showProgressRes(@StringRes text: Int)
    fun hideProgress()
}

interface ShowMessage {
    fun showMessage(text: String)
    fun showMessageRes(@StringRes text: Int)
}

open class BaseMvpPresenter<T : MvpView> : MvpPresenter<T> {
    protected var mvpView: T? = null
    private val lifecycleDisposables = CompositeDisposable()

    protected val isAttached
        get() = mvpView != null

    lateinit var appContext: Context

    @CallSuper
    override fun attach(view: T) {
        mvpView = view
    }

    @CallSuper
    override fun detach() {
        mvpView = null
        lifecycleDisposables.clear()
    }

    /* Extensions */

    fun ShowMessage.showThrowable(t: Throwable) {
        val throwableCause = t.cause
        if (t is LocalizedRuntimeException) {
            this.showMessageRes(t.errorMessageRes)
        } else if (t is FirebaseException) {
            this.showMessage(t.localizedMessage)
        } else if (throwableCause is FirebaseException) {
            this.showMessage(throwableCause.localizedMessage)
        } else if (t is PhoneNumberDoesNotExistException) {
            this.showMessage(t.message!!)
        } else {
            this.showMessageRes(R.string.error)
            //this.showMessage("Error: " + (t.message ?: t.classTag))
        }
    }

    inline fun <T> Completable.attachProgressBar(progressHolder: T?,
            @StringRes successStringRes: Int? = null, @StringRes loadingStringRes: Int = R.string.progress_please_wait) : Completable
            where T: ShowProgress, T: ShowMessage {
        return doOnSubscribe { progressHolder?.showProgressRes(loadingStringRes) }
                .doOnComplete {
                    progressHolder?.hideProgress()
                    successStringRes?.let {
                        progressHolder?.showMessageRes(successStringRes)
                    }
                }
                .doOnError {
                    progressHolder?.hideProgress()
                    progressHolder?.showThrowable(it)
                }
    }

    inline fun <T, reified P> Single<P>.attachProgressBar(progressHolder: T?,
            @StringRes successStringRes: Int? = null, @StringRes loadingStringRes: Int = R.string.progress_please_wait) : Single<P>
            where T: ShowProgress, T: ShowMessage {
        return doOnSubscribe { progressHolder?.showProgressRes(loadingStringRes) }
                .doOnSuccess {
                    progressHolder?.hideProgress()
                    successStringRes?.let {
                        progressHolder?.showMessageRes(successStringRes)
                    }
                }
                .doOnError {
                    progressHolder?.hideProgress()
                    progressHolder?.showThrowable(it)
                }
    }

    fun Disposable.bindToLifecycle() {
        if (!isAttached) {
            throw IllegalStateException("MvpView isn't attached. Cannot bind to lifecycle.")
        }
        lifecycleDisposables.add(this)
    }

    fun T?.executeOnMainThread(block: T.() -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this?.block()
        } else {
            Handler(Looper.getMainLooper()).post { this?.block() }
        }
    }
}